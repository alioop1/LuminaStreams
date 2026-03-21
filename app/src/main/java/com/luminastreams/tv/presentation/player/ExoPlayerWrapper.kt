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

class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext

    private val renderersFactory = DefaultRenderersFactory(appContext).apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF)
        setEnableDecoderFallback(true)
    }

    val trackSelector = DefaultTrackSelector(appContext).apply {
        setParameters(
            buildUponParameters()
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
        )
    }

    private val loadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(15_000, 50_000, 2_500, 5_000)
        .build()

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setTrackSelector(trackSelector)
        .setLoadControl(loadControl)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            true
        )
        .setHandleAudioBecomingNoisy(true)
        .build()

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
                        "Decoder initialization failed. Try a different source."
                    PlaybackException.ERROR_CODE_DECODING_FAILED ->
                        "Decoding error. Try a different source."
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
            val mime    = if (isVtt || subtitleUrl.endsWith(".vtt"))
                MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP
            val conf    = MediaItem.SubtitleConfiguration.Builder(subtitleUrl.toUri())
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