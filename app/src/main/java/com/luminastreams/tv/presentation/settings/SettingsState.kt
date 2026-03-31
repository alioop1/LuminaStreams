package com.luminastreams.tv.presentation.settings

sealed interface SettingsAuthStatus {
    object Idle : SettingsAuthStatus
    object Loading : SettingsAuthStatus
    data class WaitingForUser(val userCode: String, val url: String) : SettingsAuthStatus
    object Success : SettingsAuthStatus
    data class Error(val message: String) : SettingsAuthStatus
}

enum class SettingsCategory {
    LANGUAGE,
    ACCOUNT,
    PLAYBACK,
    PRIVACY,
    SYSTEM
}

data class SettingsState(
    val selectedCategory: SettingsCategory = SettingsCategory.ACCOUNT,

    // ── App Language ──────────────────────────────────────────────────────────
    val appLanguage: String = "he", // "en" or "he"

    // ── Real-Debrid Auth ──────────────────────────────────────────────────────
    val rdToken: String = "",
    val authStatus: SettingsAuthStatus = SettingsAuthStatus.Idle,

    // ── Speed Test ────────────────────────────────────────────────────────────
    val rdSpeedTesting: Boolean = false,
    val rdSpeedTestResult: String? = null,

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
    val subtitleCacheOnly: Boolean = false,

    // ── System & Performance ──────────────────────────────────────────────────
    val liteUiMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val preAllocateBuffer: Boolean = false,

    // ── Status & Info ─────────────────────────────────────────────────────────
    val cacheSizeStr: String = "Calculating...",
    val searchHistoryStatus: String = "Clear",
    val appVersion: String = "1.0.0-Lumina",
    val deviceTier: String = ""
)