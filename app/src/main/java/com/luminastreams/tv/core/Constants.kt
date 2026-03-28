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
    /** Returns the best backdrop URL for the current device tier. */
    fun backdropUrl(path: String?): String {
        if (path.isNullOrBlank() || path == "null") return ""
        val base = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> IMAGE_ORIGINAL   // Nvidia Shield / LG OLED — full-res UHD
            DeviceProfile.Tier.MID  -> IMAGE_W1280      // solid mid-tier streamers
            DeviceProfile.Tier.LOW  -> IMAGE_W500       // 2 GB Mali boxes — save RAM & bandwidth
        }
        return "$base$path"
    }

    /** Returns the best poster URL for the current device tier. */
    fun posterUrl(path: String?): String {
        if (path.isNullOrBlank() || path == "null") return ""
        val base = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> IMAGE_W780
            DeviceProfile.Tier.MID  -> IMAGE_W500
            DeviceProfile.Tier.LOW  -> IMAGE_W342
        }
        return "$base$path"
    }

    // ── SharedPreferences keys ────────────────────────────────────────────────
    const val PREFS_SETTINGS  = "lumina_settings"
    const val KEY_RD_TOKEN    = "rd_api_token"
}