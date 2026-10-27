package com.luminastreams.tv.presentation.details

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamQualityCodecTest {

    @Test
    fun `quality from title`() {
        assertEquals(StreamQuality.UHD_4K, StreamQuality.fromString("Movie 2160p"))
        assertEquals(StreamQuality.UHD_4K, StreamQuality.fromString("Movie 4K HDR"))
        assertEquals(StreamQuality.FHD_1080P, StreamQuality.fromString("Movie 1080p"))
        assertEquals(StreamQuality.HD_720P, StreamQuality.fromString("Movie 720p"))
        assertEquals(StreamQuality.SD_480P, StreamQuality.fromString("Movie 480p"))
        assertEquals(StreamQuality.UNKNOWN, StreamQuality.fromString("Movie DVDRip"))
    }

    @Test
    fun `4k takes precedence over 1080p when both present`() {
        assertEquals(StreamQuality.UHD_4K, StreamQuality.fromString("Movie 2160p (1080p upscale)"))
    }

    @Test
    fun `codec from title`() {
        assertEquals(VideoCodec.HEVC, VideoCodec.fromString("Movie x265"))
        assertEquals(VideoCodec.HEVC, VideoCodec.fromString("Movie HEVC"))
        assertEquals(VideoCodec.AVC, VideoCodec.fromString("Movie x264"))
        assertEquals(VideoCodec.AV1, VideoCodec.fromString("Movie AV1"))
        assertEquals(VideoCodec.UNKNOWN, VideoCodec.fromString("Movie"))
    }
}
