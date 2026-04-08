package com.luminastreams.tv.core

/**
 * Global constants. NEVER hardcode these values elsewhere — use Constants.XXXX everywhere.
 * Path: app/src/main/java/com/luminastreams/tv/core/Constants.kt
 */
object Constants {

    // ── TMDB ─────────────────────────────────────────────────────────────────
    const val TMDB_API_KEY  = "9ab4a284f0c028007b78925852196b79"
    const val TMDB_BASE_URL = "https://api.themoviedb.org/3/"

    // ── Image base URLs ───────────────────────────────────────────────────────
    const val IMAGE_W300     = "https://image.tmdb.org/t/p/w300"
    const val IMAGE_W342     = "https://image.tmdb.org/t/p/w342"
    const val IMAGE_W500     = "https://image.tmdb.org/t/p/w500"
    const val IMAGE_W780     = "https://image.tmdb.org/t/p/w780"
    const val IMAGE_W1280    = "https://image.tmdb.org/t/p/w1280"
    const val IMAGE_ORIGINAL = "https://image.tmdb.org/t/p/original"

    // ── Tier-aware backdrop helper ─────────────────────────────────────────────
    /**
     * Returns the best backdrop URL for the current device tier.
     * FORCE ORIGINAL (UHD 4K) FOR ALL TIERS AS REQUESTED.
     */
    fun backdropUrl(path: String?): String {
        if (path.isNullOrBlank() || path == "null") return ""
        // תמיד מושך איכות מקסימלית (UHD 4K) ל-Hero
        return "$IMAGE_ORIGINAL$path"
    }

    /**
     * Returns the best poster URL for the current device tier.
     * Minimum 1080p (W780) even on low end devices.
     */
    fun posterUrl(path: String?): String {
        if (path.isNullOrBlank() || path == "null") return ""
        val base = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> IMAGE_ORIGINAL
            DeviceProfile.Tier.MID  -> IMAGE_W780
            DeviceProfile.Tier.LOW  -> IMAGE_W780 // הועלה ל-1080P מינימום
        }
        return "$base$path"
    }

    // ── SharedPreferences keys ────────────────────────────────────────────────
    const val PREFS_SETTINGS  = "lumina_settings"
    const val KEY_RD_TOKEN    = "rd_api_token"
}