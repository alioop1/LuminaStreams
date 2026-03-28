@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.content.Context
import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import com.luminastreams.tv.core.DeviceProfile
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext
    private val prefs      = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

    // LG OLED / Sony / Philips — audio passthrough ON by default (Dolby Atmos bitstream)
    private val audioPassthrough  = prefs.getBoolean("audio_passthrough",
        DeviceProfile.isLg || DeviceProfile.isSony || DeviceProfile.isPhilips)
    private val hwAcceleration    = prefs.getBoolean("hw_accel",            true)
    private val preAllocateBuffer = prefs.getBoolean("pre_buffer",          false)
    private val audioLangPref     = prefs.getString("preferred_audio_lang", "original") ?: "original"
    private val skipEmbeddedSubs  = prefs.getBoolean("subtitle_cache_only", false)

    val useYellowSubtitles: Boolean = prefs.getBoolean("yellow_subs", false)
    val subtitleFontScale: Float = when (prefs.getString("subtitle_font_scale", "medium")) {
        "small"  -> 0.75f
        "large"  -> 1.30f
        "xlarge" -> 1.60f
        else     -> 1.00f
    }

    // ── State flows ────────────────────────────────────────────────────────────
    private val _contentFrameRate = MutableStateFlow(0f)
    val contentFrameRate: StateFlow<Float> = _contentFrameRate.asStateFlow()

    private val _videoAspectRatio = MutableStateFlow(0f)
    val videoAspectRatio: StateFlow<Float> = _videoAspectRatio.asStateFlow()

    private val _isDolbyVision = MutableStateFlow(false)
    val isDolbyVision: StateFlow<Boolean> = _isDolbyVision.asStateFlow()

    private val _isDolbyAtmos = MutableStateFlow(false)
    val isDolbyAtmos: StateFlow<Boolean> = _isDolbyAtmos.asStateFlow()

    // ── Renderers factory — tier-aware ──────────────────────────────────────────
    private val renderersFactory = DefaultRenderersFactory(appContext).apply {
        setExtensionRendererMode(
            when {
                !hwAcceleration -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                // LOW tier: prefer SW fallback decoders for tricky content (old Mali)
                DeviceProfile.tier == DeviceProfile.Tier.LOW ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                DeviceProfile.isXiaomi || DeviceProfile.isMeCool || DeviceProfile.isAmlogic ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                // LG/Sony/Philips/Shield: native HW decoders handle DV/HDR fine
                DeviceProfile.isLg   || DeviceProfile.isSony   ||
                        DeviceProfile.isPhilips || DeviceProfile.isNvidia ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                DeviceProfile.tier == DeviceProfile.Tier.HIGH ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            }
        )
        setEnableDecoderFallback(true)
    }

    // ── Track selector ─────────────────────────────────────────────────────────
    val trackSelector = DefaultTrackSelector(appContext).apply {
        val builder = buildUponParameters()
            .setPreferredVideoMimeTypes(
                MimeTypes.VIDEO_DOLBY_VISION, MimeTypes.VIDEO_H265,
                MimeTypes.VIDEO_H264, MimeTypes.VIDEO_AV1
            )
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_E_AC3_JOC, MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3, MimeTypes.AUDIO_AAC
            )
            // Tunneling: only enable on capable HIGH-tier devices
            // Enabling tunneling on LOW/MID Amlogic boxes causes black screens
            .setTunnelingEnabled(
                DeviceProfile.isLg || DeviceProfile.isSony || DeviceProfile.isPhilips ||
                        DeviceProfile.isNvidia ||
                        (DeviceProfile.tier == DeviceProfile.Tier.HIGH &&
                                !DeviceProfile.isXiaomi && !DeviceProfile.isMeCool && !DeviceProfile.isAmlogic)
            )
            .setPreferredTextLanguages("iw", "heb", "he")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .let { b -> if (skipEmbeddedSubs) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) else b }
        val params = when (audioLangPref) {
            "he" -> builder.setPreferredAudioLanguages("heb", "iw", "he")
            "en" -> builder.setPreferredAudioLanguages("eng", "en")
            else -> builder
        }
        setParameters(params)
    }

    // ── LoadControl — tier-aware buffer sizes ──────────────────────────────────
    private val loadControl: DefaultLoadControl = run {
        val buf = DeviceProfile.bufferConfig
        if (preAllocateBuffer) {
            // User explicitly requested large pre-buffer (Settings toggle)
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(30_000, 120_000, 5_000, 10_000)
                .setTargetBufferBytes(64 * 1024 * 1024)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    buf.minBufferMs,
                    buf.maxBufferMs,
                    buf.bufferForPlayMs,
                    buf.bufferForReplayMs
                )
                .setTargetBufferBytes(buf.targetBufferBytes)
                .build()
        }
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .setAllowCrossProtocolRedirects(true)
        // Longer connect timeout on low-end (slower network stack)
        .setConnectTimeoutMs(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 20_000 else 10_000)
        .setReadTimeoutMs(if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 30_000 else 15_000)

    private val dataSourceFactory  = DefaultDataSource.Factory(appContext, httpDataSourceFactory)
    private val mediaSourceFactory = DefaultMediaSourceFactory(appContext)
        .setDataSourceFactory(dataSourceFactory)
        .setSubtitleParserFactory(androidx.media3.extractor.text.DefaultSubtitleParserFactory())

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setMediaSourceFactory(mediaSourceFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(), true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exo ->
            if (audioPassthrough) runCatching {
                val cls  = Class.forName("androidx.media3.exoplayer.audio.AudioOffloadPreferences")
                val bldr = cls.getClasses().firstOrNull { it.simpleName == "Builder" } ?: return@runCatching
                val ob   = bldr.getDeclaredConstructor().newInstance()
                bldr.getMethod("setAudioOffloadMode", Int::class.java).invoke(ob, 1)
                val op   = bldr.getMethod("build").invoke(ob)
                exo.javaClass.getMethod("setAudioOffloadPreferences", cls).invoke(exo, op)
            }
        }

    // ── Player state ───────────────────────────────────────────────────────────
    private val _isPlaying       = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError     = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _currentTracks   = MutableStateFlow(Tracks.EMPTY)
    val currentTracks: StateFlow<Tracks> = _currentTracks.asStateFlow()

    private val _subtitleApplied = MutableStateFlow(false)
    val subtitleApplied: StateFlow<Boolean> = _subtitleApplied.asStateFlow()

    private val _currentCues     = MutableStateFlow<List<Cue>>(emptyList())
    val currentCues: StateFlow<List<Cue>> = _currentCues.asStateFlow()

    // ── Manual subtitle ticker ─────────────────────────────────────────────────
    private data class SubEntry(val startMs: Long, val endMs: Long, val text: String)
    private var parsedSubs   : List<SubEntry> = emptyList()
    private var subTickerJob : Job?           = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { _isPlaying.value = p }

            override fun onCues(cueGroup: CueGroup) {
                if (parsedSubs.isEmpty()) _currentCues.value = cueGroup.cues
            }

            override fun onTracksChanged(tracks: Tracks) {
                _currentTracks.value = tracks

                // AFR — fps from selected video track
                val fps = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .firstOrNull { it.frameRate > 0f }?.frameRate
                if (fps != null && fps > 0f) _contentFrameRate.value = fps

                // Dolby Vision
                val hasDv = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .any { it.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION }
                _isDolbyVision.value = hasDv

                // Dolby Atmos (E-AC3 JOC)
                val hasAtmos = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .any {
                        it.sampleMimeType == MimeTypes.AUDIO_E_AC3_JOC ||
                                (it.sampleMimeType == MimeTypes.AUDIO_E_AC3 &&
                                        (it.roleFlags and C.ROLE_FLAG_DESCRIBES_MUSIC_AND_SOUND) != 0)
                    }
                _isDolbyAtmos.value = hasAtmos
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                if (videoSize.width > 0 && videoSize.height > 0) {
                    val pixelAspect = if (videoSize.pixelWidthHeightRatio > 0f)
                        videoSize.pixelWidthHeightRatio else 1f
                    _videoAspectRatio.value =
                        (videoSize.width.toFloat() * pixelAspect) / videoSize.height.toFloat()
                }
                if (_contentFrameRate.value <= 0f) {
                    val fps = player.currentTracks.groups
                        .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                        .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                        .firstOrNull { it.frameRate > 0f }?.frameRate
                    if (fps != null && fps > 0f) _contentFrameRate.value = fps
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                _playerError.value = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED           -> "Decoder init failed."
                    PlaybackException.ERROR_CODE_DECODING_FAILED               -> "Decoding error."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED  -> "Network connection failed."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT -> "Connection timed out."
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition(); player.prepare(); return
                    }
                    else -> "Playback error (${error.errorCode})"
                }
                _isPlaying.value = false
            }

            override fun onPlaybackStateChanged(s: Int) {
                if (s == Player.STATE_ENDED) _isPlaying.value = false
            }
        })
    }

    fun prepareStream(videoUrl: String) {
        stopSubTicker()
        parsedSubs              = emptyList()
        _playerError.value      = null
        _subtitleApplied.value  = false
        _currentCues.value      = emptyList()
        _contentFrameRate.value = 0f
        _videoAspectRatio.value = 0f
        _isDolbyVision.value    = false
        _isDolbyAtmos.value     = false
        try {
            player.setMediaItem(MediaItem.Builder().setUri(videoUrl.toUri()).build())
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerError.value = "Failed to prepare: ${e.message}"
        }
    }

    fun applySubtitle(
        subtitleUrl : String,
        lang        : String  = "heb",
        isVtt       : Boolean = false,
        maxRetries  : Int     = 2
    ) {
        if (subtitleUrl.startsWith("file://")) {
            val f = File(Uri.parse(subtitleUrl).path!!)
            loadAndStartTicker(f, subtitleUrl.endsWith(".vtt", ignoreCase = true))
            return
        }
        scope.launch(Dispatchers.IO) {
            var lastErr: Exception? = null
            repeat(maxRetries + 1) { attempt ->
                try {
                    val conn = URL(subtitleUrl).openConnection() as HttpURLConnection
                    conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                    conn.connectTimeout = 12_000
                    conn.readTimeout    = 12_000
                    if (conn.responseCode in 200..299) {
                        val bytes = conn.inputStream.readBytes()
                        val ext   = if (isVtt || subtitleUrl.contains(".vtt", ignoreCase = true)) "vtt" else "srt"
                        val file  = File(appContext.cacheDir, "lumina_sub.$ext")
                        file.writeBytes(bytes)
                        withContext(Dispatchers.Main) { loadAndStartTicker(file, ext == "vtt") }
                        return@launch
                    }
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < maxRetries) delay(1_500L * (attempt + 1))
                }
            }
            lastErr?.printStackTrace()
        }
    }

    private fun loadAndStartTicker(subFile: File, isVtt: Boolean) {
        stopSubTicker()
        _currentCues.value = emptyList()
        val text = subFile.readText(Charsets.UTF_8)
        parsedSubs = if (isVtt) parseVtt(text) else parseSrt(text)
        if (parsedSubs.isEmpty()) return
        _subtitleApplied.value = true
        // Slower tick interval on LOW tier to reduce CPU overhead
        val tickMs = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 300L else 200L
        subTickerJob = scope.launch {
            while (isActive) {
                val pos    = player.currentPosition
                val active = parsedSubs.filter { it.startMs <= pos && pos < it.endMs }
                _currentCues.value = active.map { entry -> Cue.Builder().setText(entry.text).build() }
                delay(tickMs)
            }
        }
    }

    private fun stopSubTicker() {
        subTickerJob?.cancel()
        subTickerJob           = null
        parsedSubs             = emptyList()
        _currentCues.value     = emptyList()
        _subtitleApplied.value = false
    }

    private val srtTimeRegex = Regex("""(\d{2}):(\d{2}):(\d{2})[,\.](\d{3})""")
    private fun parseTimeMs(s: String): Long {
        val m = srtTimeRegex.find(s) ?: return -1
        val (h, min, sec, ms) = m.destructured
        return h.toLong() * 3_600_000 + min.toLong() * 60_000 + sec.toLong() * 1_000 + ms.toLong()
    }

    private fun parseSrt(text: String): List<SubEntry> {
        val entries = mutableListOf<SubEntry>()
        val blocks  = text.trim().replace("\r\n", "\n").split(Regex("\n{2,}"))
        for (block in blocks) {
            val lines    = block.trim().lines()
            if (lines.size < 2) continue
            val timeLine = lines.firstOrNull { "-->" in it } ?: continue
            val parts    = timeLine.split("-->")
            if (parts.size < 2) continue
            val start = parseTimeMs(parts[0].trim())
            val end   = parseTimeMs(parts[1].trim())
            if (start < 0 || end < 0) continue
            val txt = lines.dropWhile { "-->" !in it }.drop(1)
                .joinToString("\n").trim()
                .replace(Regex("<[^>]+>"), "")
            if (txt.isNotEmpty()) entries.add(SubEntry(start, end, txt))
        }
        return entries
    }

    private fun parseVtt(text: String): List<SubEntry> {
        val entries = mutableListOf<SubEntry>()
        val cleaned = text.replace("\r\n", "\n").removePrefix("\uFEFF")
        val blocks  = cleaned.trim().split(Regex("\n{2,}"))
        for (block in blocks) {
            val lines    = block.trim().lines()
            val timeLine = lines.firstOrNull { "-->" in it } ?: continue
            val timeOnly = timeLine.split(Regex("\\s+")).take(3).joinToString(" ")
            val parts    = timeOnly.split("-->")
            if (parts.size < 2) continue
            val start = parseTimeMs(parts[0].trim())
            val end   = parseTimeMs(parts[1].trim())
            if (start < 0 || end < 0) continue
            val txt = lines.dropWhile { "-->" !in it }.drop(1)
                .joinToString("\n").trim()
                .replace(Regex("<[^>]+>"), "")
            if (txt.isNotEmpty()) entries.add(SubEntry(start, end, txt))
        }
        return entries
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }
    fun seekTo(pos: Long) {
        try { player.seekTo(pos.coerceIn(0, player.duration.coerceAtLeast(0))) }
        catch (_: Exception) {}
    }
    fun clearError() { _playerError.value = null }
    fun release() {
        stopSubTicker()
        scope.cancel()
        try { player.release() } catch (_: Exception) {}
    }
}