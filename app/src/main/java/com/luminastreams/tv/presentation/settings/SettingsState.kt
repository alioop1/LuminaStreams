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

    // ── Real-Debrid Auth ──
    val rdToken: String = "",
    val authStatus: SettingsAuthStatus = SettingsAuthStatus.Idle,

    // ── General ──
    val isHebrew: Boolean = true,
    val maxResolution: String = "4K",
    val themeColor: String = "Netflix Red",

    // ── Playback & Home Theater (New Features) ──
    val audioPassthrough: Boolean = false, // העברת סאונד ישירות לרסיבר
    val forceHdr: Boolean = false,         // עדיפות ל-HDR/Dolby Vision
    val autoFrameRate: Boolean = false,    // התאמת תדר רענון (AFR)
    val autoPlayNext: Boolean = true,
    val hwAcceleration: Boolean = true,

    // ── Personalization & Subtitles (New Features) ──
    val defaultSubtitles: String = "Hebrew",
    val yellowSubtitles: Boolean = false,  // כתוביות צהובות במקום לבנות
    val safeSearch: Boolean = false,
    val saveSearchHistory: Boolean = true,

    // ── System, OLED & Performance (New Features) ──
    val dimUi: Boolean = true,             // הגנת צריבת מסך OLED
    val liteUiMode: Boolean = false,       // ביטול אנימציות למכשירים חלשים
    val preAllocateBuffer: Boolean = false,// שריון זיכרון למניעת קריסות ב-4K

    // ── Active "Action" statuses ──
    val cacheSizeStr: String = "142 MB",
    val searchHistoryStatus: String = "Clear",
    val watchHistoryStatus: String = "Clear"
)