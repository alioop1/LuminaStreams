package com.luminastreams.tv.presentation.settings

sealed interface SettingsAuthStatus {
    object Idle : SettingsAuthStatus
    object Loading : SettingsAuthStatus
    data class WaitingForUser(val userCode: String, val url: String) : SettingsAuthStatus
    object Success : SettingsAuthStatus
    data class Error(val message: String) : SettingsAuthStatus
}

enum class SettingsCategory(val titleHe: String, val titleEn: String) {
    ACCOUNT("חשבון וחיבורים", "Account & Sync"),
    PLAYBACK("נגן ווידאו", "Playback"),
    PRIVACY("חיפוש ופרטיות", "Search & Privacy"),
    SYSTEM("מערכת וכללי", "System & General")
}

data class SettingsState(
    val selectedCategory: SettingsCategory = SettingsCategory.ACCOUNT,

    // Real-Debrid Auth
    val rdToken: String = "",
    val authStatus: SettingsAuthStatus = SettingsAuthStatus.Idle,

    // Toggles & Settings
    val isHebrew: Boolean = true,
    val maxResolution: String = "4K",
    val autoPlayNext: Boolean = true,
    val defaultSubtitles: String = "Hebrew",
    val hwAcceleration: Boolean = true,
    val safeSearch: Boolean = false,
    val saveSearchHistory: Boolean = true,
    val themeColor: String = "Netflix Red",

    // Active "Action" statuses (לפיצ'רים האמיתיים)
    val cacheSizeStr: String = "142 MB",
    val searchHistoryStatus: String = "Clear",
    val watchHistoryStatus: String = "Clear"
)