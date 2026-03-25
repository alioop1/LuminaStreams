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
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext
    private val prefs      = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

    private val audioPassthrough  = prefs.getBoolean("audio_passthrough", false)
    private val hwAcceleration    = prefs.getBoolean("hw_accel", true)
    private val preAllocateBuffer = prefs.getBoolean("pre_buffer", false)
    private val audioLangPref     = prefs.getString("preferred_audio_lang", "original") ?: "original"

    val useYellowSubtitles: Boolean = prefs.getBoolean("yellow_subs", false)
    val subtitleFontScale: Float = when (prefs.getString("subtitle_font_scale", "medium")) {
        "small"  -> 0.75f
        "large"  -> 1.30f
        "xlarge" -> 1.60f
        else     -> 1.00f
    }

    private val renderersFactory = DefaultRenderersFactory(appContext).apply {
        setExtensionRendererMode(
            when {
                !hwAcceleration -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                DeviceProfile.isXiaomi || DeviceProfile.isMeCool || DeviceProfile.isAmlogic ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
                DeviceProfile.tier == DeviceProfile.Tier.HIGH ->
                    DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
                else -> DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
            }
        )
        setEnableDecoderFallback(true)
    }

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
            .setTunnelingEnabled(
                DeviceProfile.tier == DeviceProfile.Tier.HIGH &&
                !DeviceProfile.isXiaomi && !DeviceProfile.isMeCool && !DeviceProfile.isAmlogic
            )
            .setPreferredTextLanguages("iw", "heb", "he")
            .setPreferredTextRoleFlags(C.ROLE_FLAG_SUBTITLE)
        val params = when (audioLangPref) {
            "he" -> builder.setPreferredAudioLanguages("heb", "iw", "he")
            "en" -> builder.setPreferredAudioLanguages("eng", "en")
            else -> builder
        }
        setParameters(params)
    }

    private val loadControl = if (preAllocateBuffer) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(30_000, 120_000, 5_000, 10_000)
            .setTargetBufferBytes(64 * 1024 * 1024).build()
    } else {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .setTargetBufferBytes(20 * 1024 * 1024).build()
    }

    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
        .setAllowCrossProtocolRedirects(true)
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

    private val _isPlaying      = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean>  = _isPlaying.asStateFlow()

    private val _playerError    = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    private val _currentTracks  = MutableStateFlow(Tracks.EMPTY)
    val currentTracks: StateFlow<Tracks> = _currentTracks.asStateFlow()

    private val _subtitleApplied = MutableStateFlow(false)
    val subtitleApplied: StateFlow<Boolean> = _subtitleApplied.asStateFlow()

    // ── listener חד-פעמי שממתין ל-onTracksChanged אחרי replaceMediaItem ──────
    // כשהטראק "ext_sub" מופיע — עושה override ומסיר את עצמו
    private var pendingSubListener: Player.Listener? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(p: Boolean) { _isPlaying.value = p }

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

            override fun onTracksChanged(tracks: Tracks) {
                _currentTracks.value = tracks
            }
        })
    }

    fun prepareStream(videoUrl: String) {
        _playerError.value     = null
        _subtitleApplied.value = false
        removePendingSubListener()
        try {
            player.setMediaItem(MediaItem.Builder().setUri(videoUrl.toUri()).build())
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerError.value = "Failed to prepare: ${e.message}"
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // applySubtitle — מוריד מה-URL ומחיל מקומית
    // ────────────────────────────────────────────────────────────────────────
    fun applySubtitle(
        subtitleUrl: String,
        lang: String    = "heb",
        isVtt: Boolean  = false,
        maxRetries: Int = 2
    ) {
        if (subtitleUrl.startsWith("file://")) {
            applyLocalSubtitle(File(Uri.parse(subtitleUrl).path!!), isVtt)
            return
        }
        CoroutineScope(Dispatchers.IO).launch {
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
                        withContext(Dispatchers.Main) { applyLocalSubtitle(file, ext == "vtt") }
                        return@launch
                    }
                } catch (e: Exception) {
                    lastErr = e
                    if (attempt < maxRetries) kotlinx.coroutines.delay(1_500L * (attempt + 1))
                }
            }
            lastErr?.printStackTrace()
        }
    }

    // ────────────────────────────────────────────────────────────────────────
    // applyLocalSubtitle — הלב של הפיקס
    //
    // שלב 1: replaceMediaItem עם SubtitleConfiguration → ExoPlayer יודע על
    //         הכתובית אבל עוד לא בחר בה
    // שלב 2: מוסיפים listener חד-פעמי שמחכה ל-onTracksChanged הבא
    // שלב 3: כש-"ext_sub" מופיע ברשימת הטראקים — עושים override מפורש
    //         ומסירים את ה-listener
    // ────────────────────────────────────────────────────────────────────────
    private fun applyLocalSubtitle(subFile: File, isVtt: Boolean) {
        val current = player.currentMediaItem ?: return
        val mime    = if (isVtt) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
        val savedPos   = player.currentPosition
        val wasPlaying = player.isPlaying

        // הסרת listener קודם אם קיים
        removePendingSubListener()

        // שלב 2: listener חד-פעמי
        val listener = object : Player.Listener {
            override fun onTracksChanged(tracks: Tracks) {
                val textGroup = tracks.groups
                    .filter { it.type == C.TRACK_TYPE_TEXT }
                    .firstOrNull { grp ->
                        (0 until grp.length).any { i ->
                            grp.mediaTrackGroup.getFormat(i).id == "ext_sub"
                        }
                    }

                if (textGroup == null) return // עדיין לא הגיע הטראק שלנו

                // מצאנו! — מסירים את ה-listener לפני שעושים כל שינוי
                player.removeListener(this)
                pendingSubListener = null

                val trackIdx = (0 until textGroup.length).firstOrNull { i ->
                    textGroup.mediaTrackGroup.getFormat(i).id == "ext_sub"
                } ?: 0

                // שלב 3: override מפורש — זה היחיד שמבטיח הצגה
                player.trackSelectionParameters = player.trackSelectionParameters.buildUpon()
                    .clearOverridesOfType(C.TRACK_TYPE_TEXT)
                    .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                    .setOverrideForType(
                        TrackSelectionOverride(textGroup.mediaTrackGroup, trackIdx)
                    )
                    .build()

                _subtitleApplied.value = true
            }
        }
        pendingSubListener = listener
        player.addListener(listener)

        // שלב 1: replaceMediaItem — לא מפסיק את ה-stream!
        player.replaceMediaItem(
            player.currentMediaItemIndex,
            current.buildUpon()
                .setSubtitleConfigurations(listOf(
                    MediaItem.SubtitleConfiguration.Builder(Uri.fromFile(subFile))
                        .setMimeType(mime)
                        .setLanguage("iw")
                        .setId("ext_sub")
                        .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                        .build()
                ))
                .build()
        )

        // שמירת מיקום — replaceMediaItem לפעמים מאפס position
        player.seekTo(savedPos)
        if (wasPlaying) player.play()
    }

    private fun removePendingSubListener() {
        pendingSubListener?.let { player.removeListener(it) }
        pendingSubListener = null
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }
    fun seekTo(pos: Long) {
        try { player.seekTo(pos.coerceIn(0, player.duration.coerceAtLeast(0))) }
        catch (_: Exception) {}
    }
    fun clearError() { _playerError.value = null }
    fun release() {
        removePendingSubListener()
        try { player.release() } catch (_: Exception) {}
    }
}
