package com.luminastreams.tv.presentation.settings

sealed interface SettingsAuthStatus {
    object Idle : SettingsAuthStatus
    object Loading : SettingsAuthStatus
    data class WaitingForUser(val userCode: String, val url: String) : SettingsAuthStatus
    object Success : SettingsAuthStatus
    data class Error(val message: String) : SettingsAuthStatus
}

enum class SettingsCategory(val titleHe: String, val titleEn: String) {
    ACCOUNT("חשבון ורשת", "Real-Debrid & Network"),
    PLAYBACK("וידאו וסאונד", "HDR, Audio & Player"),
    PRIVACY("כתוביות והתאמה אישית", "Subtitles & Interface"),
    SYSTEM("ביצועים ותצוגה", "Performance & Display")
}

data class SettingsState(
    val selectedCategory: SettingsCategory = SettingsCategory.ACCOUNT,

    // ── Real-Debrid Auth ──────────────────────────────────────────────────────
    val rdToken: String = "",
    val authStatus: SettingsAuthStatus = SettingsAuthStatus.Idle,

    // ── Speed Test ────────────────────────────────────────────────────────────
    val rdSpeedTesting: Boolean = false,
    val rdSpeedTestResult: String? = null,

    // ── Playback & Home Theater ───────────────────────────────────────────────
    val audioPassthrough: Boolean = false,
    val autoFrameRate: Boolean = false,          // ✅ REAL: PlayerScreen sets window frame rate
    val hwAcceleration: Boolean = true,
    val preferredAudioLang: String = "original",

    // ── Personalization & Subtitles ───────────────────────────────────────────
    val defaultSubtitles: String = "Hebrew",
    val yellowSubtitles: Boolean = false,
    val subtitleFontScale: String = "medium",
    val saveSearchHistory: Boolean = true,       // ✅ REAL: SearchViewModel checks before saving
    val subtitleCacheOnly: Boolean = false,      // ✅ REAL: ExoPlayer skips stream sub tracks

    // ── System & Performance ──────────────────────────────────────────────────
    val liteUiMode: Boolean = false,
    val reduceMotion: Boolean = false,           // ✅ REAL: DeviceProfile.forceReduceMotion
    val preAllocateBuffer: Boolean = false,

    // ── Status & Info ─────────────────────────────────────────────────────────
    val cacheSizeStr: String = "Calculating...",
    val searchHistoryStatus: String = "Clear",
    val appVersion: String = "1.0.0-Lumina",
    val deviceTier: String = ""
)
