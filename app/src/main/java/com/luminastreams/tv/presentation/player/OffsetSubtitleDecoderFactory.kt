package com.luminastreams.tv.presentation.player

import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.exoplayer.text.SubtitleDecoderFactory
import androidx.media3.common.Format

class OffsetSubtitleDecoderFactory : SubtitleDecoderFactory {
    override fun supportsFormat(format: Format): Boolean {
        return SubtitleDecoderFactory.DEFAULT.supportsFormat(format)
    }

    override fun createDecoder(format: Format): SubtitleDecoder {
        val defaultDecoder = SubtitleDecoderFactory.DEFAULT.createDecoder(format)
        return OffsetSubtitleDecoder(defaultDecoder)
    }
}
