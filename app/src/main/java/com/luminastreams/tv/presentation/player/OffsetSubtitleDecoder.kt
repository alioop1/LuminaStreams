package com.luminastreams.tv.presentation.player

import androidx.media3.common.text.Cue
import androidx.media3.extractor.text.SubtitleDecoder
import androidx.media3.extractor.text.SubtitleInputBuffer
import androidx.media3.extractor.text.SubtitleOutputBuffer

class OffsetSubtitleDecoder(
    private val wrappedDecoder: SubtitleDecoder
) : SubtitleDecoder {

    override fun getName(): String {
        return "OffsetSubtitleDecoder(${wrappedDecoder.name})"
    }

    override fun dequeueInputBuffer(): SubtitleInputBuffer? {
        return wrappedDecoder.dequeueInputBuffer()
    }

    override fun queueInputBuffer(inputBuffer: SubtitleInputBuffer) {
        wrappedDecoder.queueInputBuffer(inputBuffer)
    }

    override fun dequeueOutputBuffer(): SubtitleOutputBuffer? {
        val originalBuffer = wrappedDecoder.dequeueOutputBuffer() ?: return null
        
        if (originalBuffer.isEndOfStream) {
            return originalBuffer
        }

        return object : SubtitleOutputBuffer() {
            override fun release() {
                originalBuffer.release()
            }

            override fun getNextEventTimeIndex(timeUs: Long): Int {
                val offset = SubtitleOffsetManager.getOffset() * 1000L
                return originalBuffer.getNextEventTimeIndex(timeUs - offset)
            }

            override fun getEventTimeCount(): Int {
                return originalBuffer.eventTimeCount
            }

            override fun getEventTime(index: Int): Long {
                val offset = SubtitleOffsetManager.getOffset() * 1000L
                return originalBuffer.getEventTime(index) + offset
            }

            override fun getCues(timeUs: Long): List<Cue> {
                val offset = SubtitleOffsetManager.getOffset() * 1000L
                return originalBuffer.getCues(timeUs - offset)
            }
        }.apply {
            this.timeUs = originalBuffer.timeUs
        }
    }

    override fun flush() {
        wrappedDecoder.flush()
    }

    override fun release() {
        wrappedDecoder.release()
    }

    override fun setPositionUs(positionUs: Long) {
        wrappedDecoder.setPositionUs(positionUs)
    }

    // Media3 1.1+ method
    override fun setOutputStartTimeUs(outputStartTimeUs: Long) {
        // Use reflection just in case we are running on an older Media3 where this isn't implemented
        try {
            wrappedDecoder::class.java.getMethod("setOutputStartTimeUs", Long::class.java)
                .invoke(wrappedDecoder, outputStartTimeUs)
        } catch (_: Exception) {}
    }
}
