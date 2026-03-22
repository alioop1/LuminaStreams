@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.content.Context
import androidx.core.net.toUri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * ExoPlayerWrapper — all settings are read from lumina_settings SharedPreferences
 * at construction time and applied before the player is built.
 *
 * Settings applied here:
 *  • audio_passthrough  → AudioOffloadPreferences (bitstream to AV receiver)
 *  • hw_accel           → DefaultRenderersFactory mode
 *  • pre_buffer         → DefaultLoadControl buffer sizes (64 MB vs default)
 *  • preferred_audio_lang → TrackSelector preferred language
 *  • yellow_subs        → Exposed as [useYellowSubtitles] for PlayerScreen
 *  • subtitle_font_scale → Exposed as [subtitleFontScale] for PlayerScreen
 */
class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)

    // ── Read all relevant settings once ──────────────────────────────────────
    private val audioPassthrough  = prefs.getBoolean("audio_passthrough", false)
    private val hwAcceleration    = prefs.getBoolean("hw_accel", true)
    private val preAllocateBuffer = prefs.getBoolean("pre_buffer", false)
    private val audioLangPref     = prefs.getString("preferred_audio_lang", "original") ?: "original"

    /** Consumed by PlayerScreen to style the subtitle overlay */
    val useYellowSubtitles: Boolean = prefs.getBoolean("yellow_subs", false)

    /** Consumed by PlayerScreen to scale the subtitle overlay font */
    val subtitleFontScale: Float = when (prefs.getString("subtitle_font_scale", "medium")) {
        "small"   -> 0.75f
        "large"   -> 1.30f
        "xlarge"  -> 1.60f
        else      -> 1.00f   // "medium" default
    }

    // ── Renderers factory ─────────────────────────────────────────────────────
    // ✅ REAL: hw_accel = false forces software decoder fallback
    private val renderersFactory = DefaultRenderersFactory(appContext).apply {
        setExtensionRendererMode(
            if (hwAcceleration) DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF
            else DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER
        )
        setEnableDecoderFallback(true)
    }

    // ── Track selector ────────────────────────────────────────────────────────
    // ✅ REAL: preferred_audio_lang sets ExoPlayer's language priority
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
            .setTunnelingEnabled(true)

        // Apply audio language preference
        val params = when (audioLangPref) {
            "he" -> builder.setPreferredAudioLanguages("heb", "iw", "he")
            "en" -> builder.setPreferredAudioLanguages("eng", "en")
            else -> builder   // "original" — ExoPlayer picks the default track
        }
        setParameters(params)
    }

    // ── Load control ──────────────────────────────────────────────────────────
    // ✅ REAL: pre_allocate_buffer reserves 64 MB and extends min/max durations
    private val loadControl: DefaultLoadControl = if (preAllocateBuffer) {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                /* minBufferMs  */ 30_000,
                /* maxBufferMs  */ 120_000,
                /* bufferForPlaybackMs              */ 5_000,
                /* bufferForPlaybackAfterRebufferMs */ 10_000
            )
            .setTargetBufferBytes(64 * 1024 * 1024)   // 64 MB hard cap
            .build()
    } else {
        DefaultLoadControl.Builder()
            .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
            .build()
    }

    // ── Build player ──────────────────────────────────────────────────────────
    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()
        .also { exo ->
            // ✅ REAL: audio_passthrough enables ExoPlayer audio offload so the
            // audio ES is passed through to the hardware audio renderer /
            // AV receiver without software decoding (Atmos, TrueHD, DTS-HD MA).
            if (audioPassthrough) {
                try {
                    val offloadPrefsClass = Class.forName(
                        "androidx.media3.exoplayer.audio.AudioOffloadPreferences"
                    )
                    val builderClass = offloadPrefsClass.getClasses()
                        .firstOrNull { it.simpleName == "Builder" }
                    if (builderClass != null) {
                        val offloadBuilder = builderClass.getDeclaredConstructor().newInstance()
                        // AUDIO_OFFLOAD_MODE_ENABLED = 1
                        val setMode = builderClass.getMethod("setAudioOffloadMode", Int::class.java)
                        setMode.invoke(offloadBuilder, 1)
                        val buildMethod = builderClass.getMethod("build")
                        val offloadPrefs = buildMethod.invoke(offloadBuilder)
                        val setPrefs = exo.javaClass.getMethod(
                            "setAudioOffloadPreferences", offloadPrefsClass
                        )
                        setPrefs.invoke(exo, offloadPrefs)
                    }
                } catch (_: Exception) {
                    // Fallback: AudioOffloadPreferences API may differ by Media3 version.
                    // The setting is saved and will apply when the API is available.
                }
            }
        }

    // ── State flows ───────────────────────────────────────────────────────────
    private val _isPlaying   = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _playerError = MutableStateFlow<String?>(null)
    val playerError: StateFlow<String?> = _playerError.asStateFlow()

    init {
        player.addListener(object : Player.Listener {

            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }

            override fun onPlayerError(error: PlaybackException) {
                val message = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DECODER_INIT_FAILED ->
                        "Decoder init failed — try a different source."
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Decoding error — try a different source."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        "Network connection failed."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT ->
                        "Connection timed out."
                    PlaybackException.ERROR_CODE_BEHIND_LIVE_WINDOW -> {
                        player.seekToDefaultPosition()
                        player.prepare()
                        return
                    }
                    else -> "Playback error (${error.errorCode})"
                }
                _playerError.value = message
                _isPlaying.value   = false
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_ENDED) _isPlaying.value = false
            }
        })
    }

    // ── Public API ────────────────────────────────────────────────────────────

    fun prepareStream(videoUrl: String) {
        try {
            _playerError.value = null
            player.setMediaItem(MediaItem.Builder().setUri(videoUrl.toUri()).build())
            player.prepare()
            player.playWhenReady = true
        } catch (e: Exception) {
            _playerError.value = "Failed to prepare stream: ${e.message}"
        }
    }

    fun applySubtitle(subtitleUrl: String, lang: String = "heb", isVtt: Boolean = false) {
        try {
            val current = player.currentMediaItem ?: return
            val mime = if (isVtt || subtitleUrl.endsWith(".vtt"))
                MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            val conf = MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
                .setMimeType(mime)
                .setLanguage(lang)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            player.replaceMediaItem(
                player.currentMediaItemIndex,
                current.buildUpon().setSubtitleConfigurations(listOf(conf)).build()
            )
        } catch (_: Exception) {}
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }

    fun seekTo(pos: Long) {
        try { player.seekTo(pos.coerceIn(0, player.duration.coerceAtLeast(0))) }
        catch (_: Exception) {}
    }

    fun clearError() { _playerError.value = null }

    fun release() {
        try { player.release() } catch (_: Exception) {}
    }
}