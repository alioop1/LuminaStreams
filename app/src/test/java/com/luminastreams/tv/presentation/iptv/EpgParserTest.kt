package com.luminastreams.tv.presentation.iptv

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.util.TimeZone

/**
 * [EpgParser.parseTime] — XMLTV timestamps. Locks in the fix where a timestamp with
 * no timezone offset is read as UTC (not the device zone), which otherwise shifts
 * every programme by the local offset.
 */
class EpgParserTest {

    private val expectedUtc1830 =
        OffsetDateTime.of(2024, 6, 1, 18, 30, 0, 0, ZoneOffset.UTC).toInstant().toEpochMilli()

    @Test
    fun `parses timestamp with explicit offset`() {
        // 2024-06-01 20:30:00 +0200  ==  2024-06-01 18:30:00 UTC
        assertEquals(expectedUtc1830, EpgParser.parseTime("20240601203000 +0200"))
    }

    @Test
    fun `timestamp without offset is treated as UTC regardless of device zone`() {
        val original = TimeZone.getDefault()
        try {
            TimeZone.setDefault(TimeZone.getTimeZone("America/New_York"))
            assertEquals(expectedUtc1830, EpgParser.parseTime("20240601183000"))
        } finally {
            TimeZone.setDefault(original)
        }
    }

    @Test
    fun `null or blank or garbage returns null`() {
        assertNull(EpgParser.parseTime(null))
        assertNull(EpgParser.parseTime("   "))
        assertNull(EpgParser.parseTime("not-a-date"))
    }
}
