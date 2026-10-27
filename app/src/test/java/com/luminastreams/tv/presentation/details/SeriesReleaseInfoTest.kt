package com.luminastreams.tv.presentation.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class SeriesReleaseInfoTest {

    private fun iso(daysFromNow: Long) = LocalDate.now().plusDays(daysFromNow).toString()

    private fun nextEp(daysFromNow: Long) = NextEpisodeInfo(
        seasonNumber = 3, episodeNumber = 5, name = "The Reckoning", airDateIso = iso(daysFromNow)
    )

    @Test
    fun `parseIsoDate handles date, datetime, blank, garbage`() {
        assertEquals(LocalDate.of(2025, 3, 14), parseIsoDate("2025-03-14"))
        assertEquals(LocalDate.of(2025, 3, 14), parseIsoDate("2025-03-14T20:00:00Z"))
        assertNull(parseIsoDate(""))
        assertNull(parseIsoDate(null))
        assertNull(parseIsoDate("March 14"))
    }

    @Test
    fun `NextEpisodeInfo release + countdown`() {
        assertFalse(nextEp(5).isReleased)
        assertEquals(5L, nextEp(5).daysUntilAir)
        assertTrue(nextEp(-1).isReleased)
        assertNull(NextEpisodeInfo(1, 1, "x", "").daysUntilAir)
    }

    @Test
    fun `release state - not a series`() {
        assertEquals(
            SeriesReleaseState.UNKNOWN,
            resolveSeriesReleaseState(isSeries = false, tmdbStatus = "Ended", next = null, lastAiredIso = "")
        )
    }

    @Test
    fun `release state - future episode is PREMIERES_SOON`() {
        assertEquals(
            SeriesReleaseState.PREMIERES_SOON,
            resolveSeriesReleaseState(true, "Returning Series", nextEp(6), iso(-1))
        )
    }

    @Test
    fun `release state - episode today or in last 3 days is AVAILABLE_NOW`() {
        assertEquals(SeriesReleaseState.AVAILABLE_NOW, resolveSeriesReleaseState(true, "Returning Series", nextEp(0), ""))
        assertEquals(SeriesReleaseState.AVAILABLE_NOW, resolveSeriesReleaseState(true, "Returning Series", nextEp(-2), ""))
    }

    @Test
    fun `release state - stale next pointer falls back to RETURNING`() {
        assertEquals(
            SeriesReleaseState.RETURNING_NO_DATE,
            resolveSeriesReleaseState(true, "Returning Series", nextEp(-30), "")
        )
    }

    @Test
    fun `release state - no next episode uses last-aired recency then status`() {
        assertEquals(
            SeriesReleaseState.AVAILABLE_NOW,
            resolveSeriesReleaseState(true, "Returning Series", null, iso(-1))
        )
        assertEquals(
            SeriesReleaseState.RETURNING_NO_DATE,
            resolveSeriesReleaseState(true, "Returning Series", null, iso(-40))
        )
        assertEquals(
            SeriesReleaseState.ENDED,
            resolveSeriesReleaseState(true, "Ended", null, iso(-400))
        )
        assertEquals(
            SeriesReleaseState.CANCELED,
            resolveSeriesReleaseState(true, "Canceled", null, "")
        )
    }

    @Test
    fun `Episode isUpcoming only when we know it has not aired`() {
        fun ep(air: String) = Episode("id", 1, 1, "t", "", "", airDateIso = air)
        assertTrue(ep(iso(3)).isUpcoming)
        assertFalse(ep(iso(-3)).isUpcoming)
        assertFalse(ep("").isUpcoming)          // unknown date -> not blocked
        assertTrue(ep("").isReleased)
    }

    @Test
    fun `countdownHeadline buckets`() {
        assertEquals("SOON", countdownHeadline(null, hebrew = false))
        assertEquals("NOW", countdownHeadline(0, false))
        assertEquals("TOMORROW", countdownHeadline(1, false))
        assertEquals("IN 3 DAYS", countdownHeadline(3, false))
        assertEquals("IN 1 WEEK", countdownHeadline(10, false))
        assertEquals("IN 2 WEEKS", countdownHeadline(20, false))
        assertEquals("IN 2 MONTHS", countdownHeadline(70, false))
    }
}
