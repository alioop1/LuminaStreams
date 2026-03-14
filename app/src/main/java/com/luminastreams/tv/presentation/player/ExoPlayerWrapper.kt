@file:OptIn(androidx.media3.common.util.UnstableApi::class)

package com.luminastreams.tv.presentation.player

import android.content.Context
import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class ExoPlayerWrapper(context: Context) {

    // 1. Context Leak Trap: Solved. We strictly grab the application context.
    private val appContext = context.applicationContext

    val trackSelector = DefaultTrackSelector(appContext)

    // 2. The isolated player instance
    val player: ExoPlayer = ExoPlayer.Builder(appContext)
        .setTrackSelector(trackSelector)
        .build()
        .apply {
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                true
            )
        }

    // 3. Clean state observation for Compose
    private val _isPlaying = MutableStateFlow(true)
    val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlayingNow: Boolean) {
                _isPlaying.value = isPlayingNow
            }
        })
    }

    fun prepareStream(videoUrl: String) {
        val mediaItem = MediaItem.fromUri(Uri.parse(videoUrl))
        player.setMediaItem(mediaItem)
        player.prepare()
        player.playWhenReady = true
    }

    // 4. Hardware-Safe Subtitle Swap
    fun applySubtitle(subtitleUrl: String, lang: String = "heb", isVtt: Boolean = false) {
        val currentMediaItem = player.currentMediaItem ?: return

        val mimeType = if (isVtt || subtitleUrl.endsWith(".vtt")) MimeTypes.TEXT_VTT else MimeTypes.APPLICATION_SUBRIP

        val subtitleConfig = MediaItem.SubtitleConfiguration.Builder(Uri.parse(subtitleUrl))
            .setMimeType(mimeType)
            .setLanguage(lang)
            .setSelectionFlags(C.SELECTION_FLAG_DEFAULT)
            .build()

        // Rebuild the current media item with the new subtitle track attached
        val newMediaItem = currentMediaItem.buildUpon()
            .setSubtitleConfigurations(listOf(subtitleConfig))
            .build()

        // Replace seamlessly without calling .prepare() and flushing the decoder!
        player.replaceMediaItem(player.currentMediaItemIndex, newMediaItem)
    }

    fun play() = player.play()

    fun pause() = player.pause()

    fun seekTo(position: Long) = player.seekTo(position)

    fun release() {
        player.release()
    }
}