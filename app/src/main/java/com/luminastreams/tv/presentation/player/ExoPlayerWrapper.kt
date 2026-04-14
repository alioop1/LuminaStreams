@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.content.Context
import android.os.Build
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.Tracks
import androidx.media3.common.VideoSize
import androidx.media3.common.text.Cue
import androidx.media3.common.text.CueGroup
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
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
import java.util.Locale

class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext
    private val prefs      = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

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

    // ── Amlogic hardware detection ─────────────────────────────────────────────
    // Covers standard Amlogic, MeCool, AND generic/no-name boxes (YYC/Skyworth)
    // that use Amlogic SoCs but report custom brands.
    private val isAmlogicHardware: Boolean = run {
        val hw  = Build.HARDWARE.lowercase(Locale.ROOT)
        val soc = if (Build.VERSION.SDK_INT >= 31) Build.SOC_MODEL.lowercase(Locale.ROOT) else ""
        val mfr = Build.MANUFACTURER.lowercase(Locale.ROOT)
        hw.contains("amlogic") || hw.contains("meson")  ||
        soc.contains("amlogic") || soc.contains("s905") ||
        soc.contains("s922")    || soc.contains("t962") ||
        mfr.contains("skyworth") || mfr.contains("yyc") ||
        mfr.contains("tanix")    || mfr.contains("h96") ||
        mfr.contains("x96")      ||
        DeviceProfile.isAmlogic  || DeviceProfile.isMeCool ||
        DeviceProfile.isGenericAmlogicBox
    }

    // ── Codec selector ─────────────────────────────────────────────────────────
    // On Amlogic boxes the entire c2.amlogic.* CCodec stack is broken for HEVC:
    //   • c2.amlogic.hevc.decoder times out with "previous call to start
    //     exceeded timeout" on every single channel switch (~3 s freeze).
    //   • After the timeout ExoPlayer falls back to c2.android.hevc.decoder,
    //     which then crashes with error -14 (ENOMEM) at 4K because the failed
    //     c2.amlogic allocation already consumed the contiguous RAM budget.
    //   • The whole cycle repeats, causing a 10–15 s freeze per channel switch.
    //
    // Fix: strip every c2.amlogic.* decoder completely; promote OMX.amlogic.*
    // hardware decoders (present on some Amlogic firmware) to the top so the
    // hardware path is still preferred when available. On boxes with no OMX
    // path (pure CCodec firmware like the YYC/Skyworth in the logs), tunneled
    // video (setTunnelingEnabled below) takes over and lets the system display
    // pipeline decode 4K HDR natively — exactly how TiviMate does it.
    private val codecSelector: MediaCodecSelector =
        if (isAmlogicHardware) {
            MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val all = MediaCodecSelector.DEFAULT
                    .getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                // Drop every c2.amlogic.* decoder
                val filtered = all.filter { !it.name.startsWith("c2.amlogic.", ignoreCase = true) }
                // Promote OMX.amlogic.* to front (hardware path when available)
                val omxAmlogic = filtered.filter {  it.name.startsWith("OMX.amlogic.", ignoreCase = true) }
                val rest       = filtered.filter { !it.name.startsWith("OMX.amlogic.", ignoreCase = true) }
                omxAmlogic + rest
            }
        } else {
            MediaCodecSelector.DEFAULT
        }

    // ── Renderers factory ──────────────────────────────────────────────────────
    private val renderersFactory = DefaultRenderersFactory(appContext).apply {
        setExtensionRendererMode(
            when {
                // Amlogic: disable extension renderers — they conflict with
                // the tunneled video path and cause black-screen regressions.
                isAmlogicHardware ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                !hwAcceleration ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                DeviceProfile.tier == DeviceProfile.Tier.LOW ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                DeviceProfile.isXiaomi ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                DeviceProfile.isLg || DeviceProfile.isSony ||
                DeviceProfile.isPhilips || DeviceProfile.isNvidia ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
                DeviceProfile.tier == DeviceProfile.Tier.HIGH ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_ON
            }
        )
        setEnableDecoderFallback(true)
        if (isAmlogicHardware) {
            setMediaCodecSelector(codecSelector)
        }
    }

    // ── Track selector ─────────────────────────────────────────────────────────
    val trackSelector = DefaultTrackSelector(appContext).apply {
        val builder = buildUponParameters()
            .setPreferredVideoMimeTypes(
                MimeTypes.VIDEO_DOLBY_VISION,
                MimeTypes.VIDEO_H265,
                MimeTypes.VIDEO_H264,
                MimeTypes.VIDEO_AV1
            )
            .setPreferredAudioMimeTypes(
                MimeTypes.AUDIO_E_AC3_JOC,
                MimeTypes.AUDIO_E_AC3,
                MimeTypes.AUDIO_AC3,
                MimeTypes.AUDIO_AAC
            )
            // Tunneled video is the key to 4K HDR on Amlogic boxes:
            // the track selector asks ExoPlayer to use the tunneled renderer,
            // which hands the video surface directly to the Android display
            // pipeline. The system's proprietary Amlogic video firmware decodes
            // the stream natively (including HDR10/HDR10+/HLG metadata) without
            // going through the broken CCodec stack at all. This is identical to
            // what TiviMate and IPTV Smarters do to achieve instant 4K HDR on
            // these boxes. HDR is preserved: the ColorInfo in the stream
            // (BT2020, Limited range, transfer 3 — seen in the logs) is passed
            // through to the display unchanged.
            .setTunnelingEnabled(
                isAmlogicHardware ||
                DeviceProfile.needsTunneledVideo ||
                DeviceProfile.isNvidia
            )
            .setPreferredTextLanguages("iw", "heb", "he")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
            .setAllowAudioMixedMimeTypeAdaptiveness(true)
            .setAllowAudioMixedSampleRateAdaptiveness(true)
            .let { b -> if (skipEmbeddedSubs) b.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true) else b }

        val params = when (audioLangPref) {
            "he" -> builder.setPreferredAudioLanguages("heb", "iw", "he")
            "en" -> builder.setPreferredAudioLanguages("eng", "en")
            else -> builder
        }
        setParameters(params.build())
    }

    // ── LoadControl ────────────────────────────────────────────────────────────
    // Amlogic boxes have 3–4 GB RAM but c2.android.hevc.decoder needs a large
    // contiguous block (~30 MB) for 4K decode buffers. Keeping the network
    // buffer target small (6 MB) ensures that contiguous block is available,
    // preventing the "work failed to complete -14" (ENOMEM) crash seen in logs.
    private val safeTargetBytes = if (isAmlogicHardware) 6 * 1024 * 1024 else 12 * 1024 * 1024

    private val loadControl: DefaultLoadControl = run {
        val buf = DeviceProfile.bufferConfig
        if (preAllocateBuffer) {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(15_000, 30_000, 2_500, 5_000)
                .setTargetBufferBytes(safeTargetBytes)
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        } else {
            DefaultLoadControl.Builder()
                .setBufferDurationsMs(
                    buf.minBufferMs, buf.maxBufferMs,
                    buf.bufferForPlayMs, buf.bufferForReplayMs
                )
                .setTargetBufferBytes(minOf(buf.targetBufferBytes, safeTargetBytes))
                .setPrioritizeTimeOverSizeThresholds(true)
                .build()
        }
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .setAllowCrossProtocolRedirects(true)
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
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_NEVER)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .setSkipSilenceEnabled(false)
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

    private data class SubEntry(val startMs: Long, val endMs: Long, val text: String)
    private var parsedSubs   : List<SubEntry> = emptyList()
    private var subTickerJob : Job?           = null
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    // Retry state for the codec "Released state" crash
    private var lastStreamUrl  : String? = null
    private var codecRetryCount: Int     = 0
    private val maxCodecRetries          = 2

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { _isPlaying.value = p }

            override fun onCues(cueGroup: CueGroup) {
                if (parsedSubs.isEmpty()) _currentCues.value = cueGroup.cues
            }

            override fun onTracksChanged(tracks: Tracks) {
                _currentTracks.value = tracks
                codecRetryCount      = 0   // successful track change resets retry counter

                val fps = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .firstOrNull { it.frameRate > 0f }?.frameRate
                if (fps != null && fps > 0f) _contentFrameRate.value = fps

                val hasDv = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_VIDEO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .any { it.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION }
                _isDolbyVision.value = hasDv

                val hasAtmos = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_AUDIO && it.isSelected }
                    .flatMap { g -> (0 until g.length).map { g.mediaTrackGroup.getFormat(it) } }
                    .any {
                        it.sampleMimeType == MimeTypes.AUDIO_E_AC3_JOC ||
                        it.codecs?.contains("joc",   ignoreCase = true) == true ||
                        it.label?.contains("atmos",  ignoreCase = true) == true ||
                        it.id?.contains("atmos",     ignoreCase = true) == true
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
                // The "Released state" crash happens when MediaCodecRenderer tries
                // to flush() or dequeueInputBuffer() on a codec that was already
                // released mid-decode (seen after the c2.amlogic timeout chain).
                // Auto-retry up to maxCodecRetries times before surfacing the error.
                val cause = error.cause
                val isReleasedStateCrash = cause is IllegalStateException && (
                    cause.message?.contains("Released state",  ignoreCase = true) == true ||
                    cause.message?.contains("queueInputBuffer",ignoreCase = true) == true ||
                    cause.message?.contains("flush()",         ignoreCase = true) == true
                )
                if (isReleasedStateCrash && codecRetryCount < maxCodecRetries) {
                    val url = lastStreamUrl
                    if (url != null) {
                        codecRetryCount++
                        scope.launch {
                            delay(300L * codecRetryCount)
                            player.stop()
                            player.clearMediaItems()
                            player.setMediaItem(MediaItem.Builder().setUri(url.toUri()).build())
                            player.prepare()
                            player.playWhenReady = true
                        }
                        return
                    }
                }

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
        lastStreamUrl           = videoUrl
        codecRetryCount         = 0
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

    fun switchAudioTrack(group: Tracks.Group, trackIndex: Int) {
        val isLive = player.isCurrentMediaItemLive
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_AUDIO)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
            )
            .build()
        if (isLive) player.seekToDefaultPosition()
    }

    fun disableSubtitles() {
        stopSubTicker()
        _currentCues.value = emptyList()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
            .build()
    }

    fun switchSubtitleTrack(group: Tracks.Group, trackIndex: Int) {
        stopSubTicker()
        _currentCues.value = emptyList()
        player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
            .clearOverridesOfType(C.TRACK_TYPE_TEXT)
            .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
            .setOverrideForType(
                androidx.media3.common.TrackSelectionOverride(group.mediaTrackGroup, trackIndex)
            )
            .build()
    }

    fun applySubtitle(
        subtitleUrl : String,
        isVtt       : Boolean = false,
        maxRetries  : Int     = 2
    ) {
        if (subtitleUrl.startsWith("file://")) {
            scope.launch {
                val path = subtitleUrl.toUri().path ?: return@launch
                loadAndStartTickerAsync(File(path), isVtt || subtitleUrl.endsWith(".vtt", ignoreCase = true))
            }
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
                        loadAndStartTickerAsync(file, ext == "vtt")
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

    private suspend fun loadAndStartTickerAsync(subFile: File, isVtt: Boolean) {
        val text: String = withContext(Dispatchers.IO) {
            runCatching { subFile.readText(Charsets.UTF_8) }.getOrNull()
        } ?: return

        val parsed: List<SubEntry> = withContext(Dispatchers.Default) {
            if (isVtt) parseVtt(text) else parseSrt(text)
        }

        withContext(Dispatchers.Main) {
            stopSubTicker()
            _currentCues.value = emptyList()
            parsedSubs = parsed
            if (parsedSubs.isEmpty()) return@withContext
            _subtitleApplied.value = true
            val tickMs = if (DeviceProfile.tier == DeviceProfile.Tier.LOW) 400L else 250L
            subTickerJob = scope.launch(Dispatchers.Default) {
                while (isActive) {
                    val pos    = withContext(Dispatchers.Main) { player.currentPosition }
                    val active = parsedSubs.filter { it.startMs <= pos && pos < it.endMs }
                    val cues   = active.map { entry -> Cue.Builder().setText(entry.text).build() }
                    withContext(Dispatchers.Main) { _currentCues.value = cues }
                    delay(tickMs)
                }
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

    private val srtTimeRegex = Regex("""(\d{2}):(\d{2}):(\d{2})[,.](\d{3})""")
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
