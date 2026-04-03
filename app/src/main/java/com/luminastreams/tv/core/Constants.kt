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
     *
     * Quality floors (TV displays need much higher than phones):
     *   HIGH → original  (OLED, SHIELD, 4K sets — no compromise)
     *   MID  → w1280     (solid 1080p output)
     *   LOW  → w780      (was w500 — raised to prevent visible blur on 720p+ panels)
     */
    fun backdropUrl(path: String?): String {
        if (path.isNullOrBlank() || path == "null") return ""
        val base = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> IMAGE_ORIGINAL
            DeviceProfile.Tier.MID  -> IMAGE_W1280
            DeviceProfile.Tier.LOW  -> IMAGE_W780   // ← was IMAGE_W500, raised for TV panels
        }
        return "$base$path"
    }

    /**
     * Returns the best poster URL for the current device tier.
     *
     *   HIGH → original  (sharp on large TV screens)
     *   MID  → w780      (was w500, raised for portrait poster clarity)
     *   LOW  → w500      (was w342 — w342 is phone-grade, unacceptable on TV)
     */
    fun posterUrl(path: String?): String {
        if (path.isNullOrBlank() || path == "null") return ""
        val base = when (DeviceProfile.tier) {
            DeviceProfile.Tier.HIGH -> IMAGE_ORIGINAL
            DeviceProfile.Tier.MID  -> IMAGE_W780   // ← was IMAGE_W500
            DeviceProfile.Tier.LOW  -> IMAGE_W500   // ← was IMAGE_W342
        }
        return "$base$path"
    }

    // ── SharedPreferences keys ────────────────────────────────────────────────
    const val PREFS_SETTINGS  = "lumina_settings"
    const val KEY_RD_TOKEN    = "rd_api_token"
}