@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExoPlayerWrapper(context: Context) {

    private val appContext = context.applicationContext

    private val renderersFactory = DefaultRenderersFactory(appContext).apply {
        setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
        setEnableDecoderFallback(true)
    }

    val trackSelector = DefaultTrackSelector(appContext).apply {
        setParameters(
            buildUponParameters()
                .setPreferredVideoMimeTypes(
                    MimeTypes.VIDEO_AV1,
                    MimeTypes.VIDEO_H265,
                    MimeTypes.VIDEO_H264
                )
                .setPreferredAudioMimeTypes(
                    MimeTypes.AUDIO_AC3,
                    MimeTypes.AUDIO_E_AC3,
                    MimeTypes.AUDIO_AAC
                )
                .setTunnelingEnabled(true)
        )
    }

    val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setTrackSelector(trackSelector)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                .build(),
            /* handleAudioFocus = */ true
        )
        .setHandleAudioBecomingNoisy(true)
        // ✅ FIXED: setPlayWhenReady() does NOT exist on ExoPlayer.Builder in Media3.
        // Set playWhenReady on the player instance after building (see prepareStream below).
        .build()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }
        })
    }

    fun prepareStream(videoUrl: String) {
        try {
            val mediaItem = MediaItem.Builder()
                .setUri(Uri.parse(videoUrl))
                .build()
            player.setMediaItem(mediaItem)
            player.prepare()
            // ✅ Set playWhenReady HERE — on the instance, not the builder
            player.playWhenReady = true
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun applySubtitle(subtitleUrl: String, lang: String = "heb", isVtt: Boolean = false) {
        try {
            val currentMediaItem = player.currentMediaItem ?: return
            val mimeType = if (isVtt || subtitleUrl.endsWith(".vtt")) {
                MimeTypes.TEXT_VTT
            } else {
                MimeTypes.APPLICATION_SUBRIP
            }
            val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
                .setMimeType(mimeType)
                .setLanguage(lang)
                .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
                .build()
            val newMediaItem = currentMediaItem.buildUpon()
                .setSubtitleConfigurations(listOf(subtitleConfig))
                .build()
            player.replaceMediaItem(player.currentMediaItemIndex, newMediaItem)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun play()  { player.play() }
    fun pause() { player.pause() }

    fun seekTo(position: Long) {
        player.seekTo(position.coerceIn(0, player.duration.coerceAtLeast(0)))
    }

    fun release() { player.release() }
}