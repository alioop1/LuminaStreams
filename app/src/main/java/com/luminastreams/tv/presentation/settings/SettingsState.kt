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
    val audioPassthrough: Boolean = false,       // Bitstream offload to AV receiver
    val forceHdr: Boolean = false,               // Boost HDR/DV sources to top of list
    val autoFrameRate: Boolean = false,          // Match display Hz to content frame rate
    val autoPlayNext: Boolean = true,
    val hwAcceleration: Boolean = true,
    val maxQuality: String = "4K",               // "4K" | "1080p" | "720p" — stream filter
    val preferredAudioLang: String = "original", // "original" | "he" | "en"

    // ── Personalization & Subtitles ───────────────────────────────────────────
    val defaultSubtitles: String = "Hebrew",     // "Hebrew" | "English" | "None"
    val yellowSubtitles: Boolean = false,
    val subtitleFontScale: String = "medium",    // "small" | "medium" | "large" | "xlarge"
    val safeSearch: Boolean = false,
    val saveSearchHistory: Boolean = true,

    // ── System, OLED & Performance ────────────────────────────────────────────
    val dimUi: Boolean = true,                   // Dim screen after 2-min idle (OLED guard)
    val liteUiMode: Boolean = false,             // Force LOW DeviceProfile tier
    val preAllocateBuffer: Boolean = false,      // Reserve 64 MB for ExoPlayer

    // ── Status & Info ─────────────────────────────────────────────────────────
    val cacheSizeStr: String = "Calculating...",
    val searchHistoryStatus: String = "Clear",
    val watchHistoryStatus: String = "Clear",
    val appVersion: String = "1.0.0-Lumina",
    val deviceTier: String = ""                 // "HIGH • Adreno 650 • 4 GB RAM" etc.
)