package com.luminastreams.tv.presentation.player

import android.content.Context
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector

object PlayerManager {

    // THE FIX: Added an optional subtitleBytes parameter to pass the RAM bytes
    fun buildExoPlayer(context: Context, subtitleBytes: ByteArray? = null): ExoPlayer {
        // 1. כפיית האצת חומרה וביטול רינדור תוכנתי שחונק את ה-CPU
        val renderersFactory = DefaultRenderersFactory(context)
            .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_PREFER)
            .setEnableDecoderFallback(false)

        // 2. תעדוף חכם של ערוצי וידאו ואודיו
        val trackSelector = DefaultTrackSelector(context).apply {
            setParameters(
                buildUponParameters()
                    .setPreferredVideoMimeTypes(MimeTypes.VIDEO_AV1, MimeTypes.VIDEO_H265, MimeTypes.VIDEO_H264)
                    .setPreferredAudioMimeTypes(
                        MimeTypes.AUDIO_E_AC3_JOC, // Dolby Atmos
                        MimeTypes.AUDIO_AC3,       // Dolby Digital
                        MimeTypes.AUDIO_DTS        // DTS
                    )
                    // חובה לטלוויזיות: לאפשר מעבר שמע ישירות לרסיבר (Passthrough)
                    .setTunnelingEnabled(true)
            )
        }

        // 3. הגדרות סאונד קולנועיות
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MOVIE)
            .setSpatializationBehavior(C.SPATIALIZATION_BEHAVIOR_AUTO)
            .build()

        val builder = ExoPlayer.Builder(context, renderersFactory)
            .setTrackSelector(trackSelector)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setVideoScalingMode(C.VIDEO_SCALING_MODE_SCALE_TO_FIT)

        // 4. THE MAGIC INJECTION: If we have subtitle bytes, wire up the custom Memory factory!
        if (subtitleBytes != null) {
            val dataSourceFactory = MemorySubtitleFactory.createDataSourceFactory(subtitleBytes)
            val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
            builder.setMediaSourceFactory(mediaSourceFactory)
        }

        return builder.build()
    }
}