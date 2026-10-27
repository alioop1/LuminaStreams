package com.luminastreams.tv.presentation.settings

sealed interface SettingsAuthStatus {
    object Idle : SettingsAuthStatus
    object Loading : SettingsAuthStatus
    data class WaitingForUser(val userCode: String, val url: String) : SettingsAuthStatus
    object Success : SettingsAuthStatus
    data class Error(val message: String) : SettingsAuthStatus
}

enum class SettingsCategory {
    ACCOUNT,
    PLAYBACK,
    SUBTITLES,
    PERFORMANCE,
    SYSTEM_INFO
}

data class SettingsState(
    val selectedCategory: SettingsCategory = SettingsCategory.ACCOUNT,

    // ── App Language ──────────────────────────────────────────────────────────
    val appLanguage: String = "he", // "en" or "he"

    // ── AllDebrid Auth ──────────────────────────────────────────────────────
    val debridToken: String = "",
    val authStatus: SettingsAuthStatus = SettingsAuthStatus.Idle,

    // ── Speed Test & Account Status ───────────────────────────────────────────
    val rdSpeedTesting: Boolean = false,
    val rdSpeedTestResult: String? = null,

    // ── Server Health Status ──────────────────────────────────────────────────
    val adServerStatus: String = "Checking...",
    val torrentioServerStatus: String = "Checking...",
    val isCheckingServers: Boolean = false,

    // ── Playback & Home Theater ───────────────────────────────────────────────
    val audioPassthrough: Boolean = false,
    val autoFrameRate: Boolean = false,
    val hwAcceleration: Boolean = true,
    val preferredAudioLang: String = "original",

    // ── Personalization & Subtitles ───────────────────────────────────────────
    val defaultSubtitles: String = "Hebrew",
    val yellowSubtitles: Boolean = false,
    val subtitleFontScale: String = "medium",
    val saveSearchHistory: Boolean = true,

    // ── System & Performance ──────────────────────────────────────────────────
    val liteUiMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val preAllocateBuffer: Boolean = false,
    val maxResolution: String = "Unlimited",
    val maxFileSize: String = "Unlimited",

    // ── Status & Info ─────────────────────────────────────────────────────────
    val cacheSizeStr: String = "Calculating...",
    val searchHistoryStatus: String = "Clear",
    val appVersion: String = "1.0.0-Lumina",
    val deviceTier: String = "",

    // ── Cloud Sync ────────────────────────────────────────────────────────────
    val syncStatus: String = "",
    val isSyncing: Boolean = false,
    val userEmail: String? = null
)