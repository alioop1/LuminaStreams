package com.luminastreams.tv.presentation.details

import org.junit.Assert.assertEquals
import org.junit.Test

/** Characterisation tests for [TrackerParser.extractTracker]. */
class TrackerParserTest {

    @Test
    fun `bracketed tracker name wins`() {
        assertEquals("RuTracker", TrackerParser.extractTracker("Torrentio\n[RuTracker]"))
        assertEquals("YTS", TrackerParser.extractTracker("[YTS] Torrentio"))
    }

    @Test
    fun `second non-generic line is used when there is no bracket`() {
        assertEquals("RuTor", TrackerParser.extractTracker("Torrentio\nRuTor"))
    }

    @Test
    fun `generic second line is skipped`() {
        // "Torrentio\nTorrentio" -> second line is generic -> falls through to known-name scan -> default
        assertEquals("Torrentio", TrackerParser.extractTracker("Torrentio\nTorrentio"))
    }

    @Test
    fun `known tracker name anywhere in the string is matched`() {
        assertEquals("RARBG", TrackerParser.extractTracker("Some RARBG WEB-DL release"))
    }

    @Test
    fun `defaults to Torrentio`() {
        assertEquals("Torrentio", TrackerParser.extractTracker("Torrentio"))
        assertEquals("Torrentio", TrackerParser.extractTracker(""))
    }
}
