package com.luminastreams.tv.presentation.player

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.exoplayer.DecoderReuseEvaluation
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.FormatHolder
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.DefaultMediaCodecAdapterFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecAdapter
import androidx.media3.exoplayer.mediacodec.MediaCodecInfo
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.text.TextOutput
import androidx.media3.exoplayer.text.TextRenderer
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

class CustomRenderersFactory(private val context: Context) : DefaultRenderersFactory(context) {

    override fun getCodecAdapterFactory(): MediaCodecAdapter.Factory {
        return DefaultMediaCodecAdapterFactory(context).forceDisableAsynchronous()
    }

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: java.util.ArrayList<Renderer>
    ) {
        val adapterFactory = DefaultMediaCodecAdapterFactory(context).forceDisableAsynchronous()
        val loggingAdapterFactory = MediaCodecAdapter.Factory { configuration ->
            val mime = configuration.mediaFormat.getString(MediaFormat.KEY_MIME)
            Log.d("LuminaCodec", "⚡ createAdapter START: codec=${configuration.codecInfo.name}, mime=$mime, surface=${configuration.surface}")
            try {
                val adapter = adapterFactory.createAdapter(configuration)
                Log.d("LuminaCodec", "🎉 createAdapter SUCCESS: codec=${configuration.codecInfo.name}")
                adapter
            } catch (e: Exception) {
                Log.e("LuminaCodec", "🚨 createAdapter FAILED: ${e.message}", e)
                throw e
            }
        }
        out.add(
            CustomVideoRenderer(
                context,
                loggingAdapterFactory,
                mediaCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                50
            )
        )
    }

    override fun buildAudioRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        audioSink: androidx.media3.exoplayer.audio.AudioSink,
        eventHandler: Handler,
        eventListener: androidx.media3.exoplayer.audio.AudioRendererEventListener,
        out: java.util.ArrayList<Renderer>
    ) {
        val adapterFactory = DefaultMediaCodecAdapterFactory(context).forceDisableAsynchronous()
        val loggingAudioAdapterFactory = MediaCodecAdapter.Factory { configuration ->
            val mime = configuration.mediaFormat.getString(MediaFormat.KEY_MIME)
            Log.d("LuminaCodec", "🎵 audio createAdapter: codec=${configuration.codecInfo.name}, mime=$mime")
            adapterFactory.createAdapter(configuration)
        }
        // Primary: Hardware / OS MediaCodec audio renderer (fastest, lowest CPU)
        out.add(
            androidx.media3.exoplayer.audio.MediaCodecAudioRenderer(
                context,
                loggingAudioAdapterFactory,
                mediaCodecSelector,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                audioSink
            )
        )

        // Secondary / Universal Software Fallback: Native FFmpeg audio renderer
        // Handles AC3, E-AC3 (Dolby Digital Plus / Atmos), DTS, DTS-HD, TrueHD seamlessly on Windows / WSA
        try {
            if (androidx.media3.decoder.ffmpeg.FfmpegLibrary.isAvailable()) {
                out.add(
                    androidx.media3.decoder.ffmpeg.FfmpegAudioRenderer(
                        eventHandler,
                        eventListener,
                        audioSink
                    )
                )
                Log.i("LuminaCodec", "🎉 Native FFmpeg audio renderer registered (AC-3, E-AC-3, DTS, TrueHD enabled)")
            } else {
                Log.w("LuminaCodec", "⚠️ FFmpegLibrary reported isAvailable = false")
            }
        } catch (t: Throwable) {
            Log.e("LuminaCodec", "🚨 Failed to instantiate FfmpegAudioRenderer: ${t.message}", t)
        }
    }


    override fun buildTextRenderers(
        context: Context,
        output: TextOutput,
        outputLooper: Looper,
        extensionRendererMode: Int,
        out: java.util.ArrayList<Renderer>
    ) {
        out.add(TextRenderer(output, outputLooper, OffsetSubtitleDecoderFactory()))
    }
}

class CustomVideoRenderer(
    context: Context,
    codecAdapterFactory: MediaCodecAdapter.Factory,
    mediaCodecSelector: MediaCodecSelector,
    allowedVideoJoiningTimeMs: Long,
    enableDecoderFallback: Boolean,
    eventHandler: Handler,
    eventListener: VideoRendererEventListener,
    maxDroppedFramesToNotify: Int
) : MediaCodecVideoRenderer(
    context,
    codecAdapterFactory,
    mediaCodecSelector,
    allowedVideoJoiningTimeMs,
    enableDecoderFallback,
    eventHandler,
    eventListener,
    maxDroppedFramesToNotify
) {
    companion object {
        private const val TAG = "LuminaCodec"
    }

    private fun getEffectiveFormat(format: Format): Format {
        if (format.sampleMimeType == MimeTypes.VIDEO_DOLBY_VISION) {
            val fallbackMime = if (format.codecs?.startsWith("dva") == true) MimeTypes.VIDEO_H264 else MimeTypes.VIDEO_H265
            return format.buildUpon()
                .setSampleMimeType(fallbackMime)
                .setCodecs(null)
                .build()
        }
        return format
    }

    override fun supportsFormat(
        mediaCodecSelector: MediaCodecSelector,
        format: Format
    ): Int {
        val effective = getEffectiveFormat(format)
        val res = super.supportsFormat(mediaCodecSelector, effective)
        if (effective !== format) {
            Log.d(TAG, "⚡ DV fallback supportsFormat: ${format.sampleMimeType}(${format.codecs}) -> ${effective.sampleMimeType} = $res")
        }
        return res
    }

    override fun getDecoderInfos(
        mediaCodecSelector: MediaCodecSelector,
        format: Format,
        requiresSecure: Boolean
    ): List<MediaCodecInfo> {
        val effective = getEffectiveFormat(format)
        val decoders = super.getDecoderInfos(mediaCodecSelector, effective, requiresSecure)
        if (effective !== format) {
            Log.d(TAG, "⚡ DV fallback getDecoderInfos for ${effective.sampleMimeType}: ${decoders.map { it.name }}")
        } else {
            Log.d(TAG, "getDecoderInfos for ${format.sampleMimeType}: ${decoders.map { it.name }}")
        }
        return decoders
    }

    override fun getMediaCodecConfiguration(
        codecInfo: MediaCodecInfo,
        format: Format,
        crypto: android.media.MediaCrypto?,
        codecOperatingRate: Float
    ): MediaCodecAdapter.Configuration {
        val effective = getEffectiveFormat(format)
        Log.d(TAG, "⚡ configuring codec ${codecInfo.name} for mime ${effective.sampleMimeType}")
        return super.getMediaCodecConfiguration(codecInfo, effective, crypto, codecOperatingRate)
    }

    override fun onInputFormatChanged(formatHolder: FormatHolder): DecoderReuseEvaluation? {
        val format = formatHolder.format
        if (format != null) {
            val effective = getEffectiveFormat(format)
            if (effective !== format) {
                Log.d(TAG, "⚡ rewriting input format: ${format.sampleMimeType} -> ${effective.sampleMimeType}")
                formatHolder.format = effective
            }
        }
        return super.onInputFormatChanged(formatHolder)
    }

    override fun onCodecInitialized(
        name: String,
        configuration: MediaCodecAdapter.Configuration,
        initializedTimestampMs: Long,
        initializationDurationMs: Long
    ) {
        super.onCodecInitialized(name, configuration, initializedTimestampMs, initializationDurationMs)
        Log.d(TAG, "🎉 MediaCodec initialized: $name in ${initializationDurationMs}ms")
    }

    override fun onCodecError(error: Exception) {
        super.onCodecError(error)
        Log.e(TAG, "🚨 MediaCodec error: ${error.message}", error)
    }

    override fun onOutputFormatChanged(format: Format, mediaFormat: MediaFormat?) {
        super.onOutputFormatChanged(format, mediaFormat)
        Log.d(TAG, "📺 Codec output format changed: ${format.sampleMimeType} ${format.width}x${format.height}")
    }
}
