package com.luminastreams.tv.presentation.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TorrentioStreamParserTest {

    @Test
    fun `size parsing handles GB and MB`() {
        assertEquals((8.3 * 1_073_741_824).toLong(), TorrentioStreamParser.parseSizeBytes("Movie 2024\n💾 8.3 GB"))
        assertEquals((700.0 * 1_048_576).toLong(), TorrentioStreamParser.parseSizeBytes("Show S01E01 700 MB"))
        assertEquals(0L, TorrentioStreamParser.parseSizeBytes("Movie 2024 1080p"))
    }

    @Test
    fun `seeders and leechers pulled from emoji fragments`() {
        val title = "Movie.2024.2160p\n👤 142 🌐 7 💾 8.3 GB ⚙️ RuTracker"
        assertEquals(142, TorrentioStreamParser.parseSeeders(title))
        assertEquals(7, TorrentioStreamParser.parseLeechers(title))
        assertEquals(0, TorrentioStreamParser.parseSeeders("Movie no stats"))
    }

    @Test
    fun `tracker comes from the gear fragment, else from name`() {
        assertEquals("RuTracker", TorrentioStreamParser.parseTracker("Movie\n⚙️ RuTracker", "Torrentio"))
        assertEquals("YTS", TorrentioStreamParser.parseTracker("Movie no gear", "[YTS] Torrentio"))
    }

    @Test
    fun `parse drops entries with neither hash nor url`() {
        assertNull(TorrentioStreamParser.parse(0, "Torrentio", "Movie", null, null))
        assertNull(TorrentioStreamParser.parse(0, "Torrentio", "Movie", "", "  "))
    }

    @Test
    fun `parse keeps entries with an infoHash`() {
        val s = TorrentioStreamParser.parse(
            index = 3,
            name = "Torrentio\nRARBG\nAD+",
            title = "Some.Movie.2024.2160p.HDR.x265\n👤 90 💾 12.1 GB ⚙️ RARBG",
            url = null,
            infoHash = "abcdef1234567890"
        )!!
        assertEquals("str_3", s.id)
        assertEquals("abcdef1234567890", s.infoHash)
        assertEquals(StreamQuality.UHD_4K, s.quality)
        assertEquals(VideoCodec.HEVC, s.videoCodec)
        assertEquals("RARBG", s.trackerName)
        assertEquals(90, s.seeders)
        assertTrue(s.isCachedRd) // "AD+" present in name
        assertEquals((12.1 * 1_073_741_824).toLong(), s.sizeBytes)
    }

    @Test
    fun `parse keeps entries with only a direct url and is not cached`() {
        val s = TorrentioStreamParser.parse(0, "Torrentio", "Movie 1080p", "https://host/file.mkv", null)!!
        assertEquals("https://host/file.mkv", s.directUrl)
        assertEquals(false, s.isCachedRd)
        assertEquals(StreamQuality.FHD_1080P, s.quality)
    }

    @Test
    fun `filename is the first line of the title`() {
        val s = TorrentioStreamParser.parse(0, "n", "The.Movie.2024.1080p\n👤 5 💾 2 GB", null, "hash")!!
        assertEquals("The.Movie.2024.1080p", s.filename)
    }
}
