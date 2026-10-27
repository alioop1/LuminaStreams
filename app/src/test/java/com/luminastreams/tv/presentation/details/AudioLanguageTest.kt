package com.luminastreams.tv.presentation.details

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Characterisation tests for [AudioLanguage.detectFromTitle] / [AudioLanguage.fromIso].
 * These pin the CURRENT behaviour so a Torrentio-title-format change (or a careless
 * refactor of the regexes) shows up as a red test rather than silently wrong badges.
 */
class AudioLanguageTest {

    @Test
    fun `detects russian and english from ISO-style tags`() {
        val langs = AudioLanguage.detectFromTitle("Some.Movie.2024.1080p.WEB-DL.RUS.ENG.x264")
        assertTrue(AudioLanguage.RUSSIAN in langs)
        assertTrue(AudioLanguage.ENGLISH in langs)
    }

    @Test
    fun `detects hebrew from HEBDUB`() {
        assertTrue(AudioLanguage.HEBREW in AudioLanguage.detectFromTitle("Movie 2023 1080p HEBDUB"))
    }

    @Test
    fun `detects russian from a cyrillic block`() {
        assertTrue(AudioLanguage.RUSSIAN in AudioLanguage.detectFromTitle("Фильм 2024 ДУБЛИРОВАННЫЙ 1080p"))
    }

    @Test
    fun `detects MULTI`() {
        assertTrue(AudioLanguage.MULTI in AudioLanguage.detectFromTitle("Movie 2024 2160p MULTI HDR"))
        assertTrue(AudioLanguage.MULTI in AudioLanguage.detectFromTitle("Movie DUAL AUDIO"))
    }

    @Test
    fun `falls back to english when nothing is detected`() {
        assertEquals(listOf(AudioLanguage.ENGLISH), AudioLanguage.detectFromTitle("Plain.Movie.2024.1080p.BluRay"))
    }

    @Test
    fun `result has no duplicates`() {
        val langs = AudioLanguage.detectFromTitle("ENG ENGLISH eng")
        assertEquals(langs.toSet().size, langs.size)
    }

    @Test
    fun `fromIso is case-insensitive and maps common codes`() {
        assertEquals(AudioLanguage.ENGLISH, AudioLanguage.fromIso("EN"))
        assertEquals(AudioLanguage.HEBREW, AudioLanguage.fromIso("he"))
        assertEquals(AudioLanguage.RUSSIAN, AudioLanguage.fromIso("ru"))
        assertEquals(AudioLanguage.JAPANESE, AudioLanguage.fromIso("ja"))
    }

    @Test
    fun `fromIso returns null for unknown or empty`() {
        assertNull(AudioLanguage.fromIso(""))
        assertNull(AudioLanguage.fromIso("zz"))
        assertNull(AudioLanguage.fromIso("english"))
    }
}
