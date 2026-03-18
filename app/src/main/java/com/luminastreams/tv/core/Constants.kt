package com.luminastreams.tv.core

/**
 * מרכז את כל הקבועים הגלובליים של האפליקציה.
 * NEVER hardcode these values elsewhere – use Constants.XXXX everywhere.
 *
 * Path: app/src/main/java/com/luminastreams/tv/core/Constants.kt
 */
object Constants {

    // ── TMDB ─────────────────────────────────────────────────────────────────
    const val TMDB_API_KEY  = "9ab4a284f0c028007b78925852196b79"
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

    // ── Image base URLs ───────────────────────────────────────────────────────
    const val IMAGE_W300  = "https://image.tmdb.org/t/p/w300"
    const val IMAGE_W342  = "https://image.tmdb.org/t/p/w342"
    const val IMAGE_W500  = "https://image.tmdb.org/t/p/w500"
    const val IMAGE_W780  = "https://image.tmdb.org/t/p/w780"
    const val IMAGE_W1280 = "https://image.tmdb.org/t/p/w1280"

    // ── Real-Debrid ───────────────────────────────────────────────────────────
    const val REAL_DEBRID_CLIENT_ID = "X245A4XAIBGVM"
    const val REAL_DEBRID_BASE_URL  = "https://api.real-debrid.com/"

    // ── SharedPreferences keys ────────────────────────────────────────────────
    const val PREFS_SETTINGS  = "lumina_settings"
    const val PREFS_WATCHLIST = "lumina_watchlist"
    const val KEY_RD_TOKEN    = "rd_api_token"

    // ── Performance ───────────────────────────────────────────────────────────
    /** מגביל את ה-image memory cache ל-10% מה-heap במכשירי 2GB */
    const val MAX_MEMORY_PERCENT = 0.10
}