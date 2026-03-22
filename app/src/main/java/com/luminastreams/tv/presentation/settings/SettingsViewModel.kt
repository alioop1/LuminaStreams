package com.luminastreams.tv.presentation.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.domain.usecase.AuthResult
import com.luminastreams.tv.domain.usecase.RealDebridAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.net.HttpURLConnection
import java.net.URL

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
    private val authManager = RealDebridAuthManager(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadAllSettings()
        calculateRealCacheSize()
        loadDeviceInfo()
    }

    fun setCategory(category: SettingsCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    // ── Load all settings from SharedPreferences ──────────────────────────────
    private fun loadAllSettings() {
        _state.update {
            it.copy(
                rdToken           = prefs.getString("rd_api_token", "") ?: "",

                // Playback
                audioPassthrough  = prefs.getBoolean("audio_passthrough", false),
                forceHdr          = prefs.getBoolean("force_hdr", false),
                autoFrameRate     = prefs.getBoolean("afr", false),
                autoPlayNext      = prefs.getBoolean("auto_play", true),
                hwAcceleration    = prefs.getBoolean("hw_accel", true),
                maxQuality        = prefs.getString("max_quality", "4K") ?: "4K",
                preferredAudioLang= prefs.getString("preferred_audio_lang", "original") ?: "original",

                // Personalization
                defaultSubtitles  = prefs.getString("def_subs", "Hebrew") ?: "Hebrew",
                yellowSubtitles   = prefs.getBoolean("yellow_subs", false),
                subtitleFontScale = prefs.getString("subtitle_font_scale", "medium") ?: "medium",
                safeSearch        = prefs.getBoolean("safe_search", false),
                saveSearchHistory = prefs.getBoolean("save_history", true),

                // System
                dimUi             = prefs.getBoolean("dim_ui", true),
                liteUiMode        = prefs.getBoolean("lite_ui", false),
                preAllocateBuffer = prefs.getBoolean("pre_buffer", false),
            )
        }

        // Apply lite UI mode to DeviceProfile immediately on load
        DeviceProfile.forceLowTier = prefs.getBoolean("lite_ui", false)
    }

    // ── Toggle setting ────────────────────────────────────────────────────────
    fun updateToggleSetting(key: String, value: Boolean) {
        _state.update { current ->
            when (key) {
                "audio_passthrough" -> current.copy(audioPassthrough = value)
                "force_hdr"         -> current.copy(forceHdr = value)
                "afr"               -> current.copy(autoFrameRate = value)
                "auto_play"         -> current.copy(autoPlayNext = value)
                "hw_accel"          -> current.copy(hwAcceleration = value)
                "yellow_subs"       -> current.copy(yellowSubtitles = value)
                "safe_search"       -> current.copy(safeSearch = value)
                "save_history"      -> current.copy(saveSearchHistory = value)
                "dim_ui"            -> current.copy(dimUi = value)
                "lite_ui"           -> {
                    // ✅ REAL: Update DeviceProfile tier immediately for current session
                    DeviceProfile.forceLowTier = value
                    current.copy(liteUiMode = value)
                }
                "pre_buffer"        -> current.copy(preAllocateBuffer = value)
                else                -> current
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    // ── String setting ────────────────────────────────────────────────────────
    fun updateStringSetting(key: String, value: String) {
        _state.update { current ->
            when (key) {
                "max_quality"          -> current.copy(maxQuality = value)
                "preferred_audio_lang" -> current.copy(preferredAudioLang = value)
                "def_subs"             -> current.copy(defaultSubtitles = value)
                "subtitle_font_scale"  -> current.copy(subtitleFontScale = value)
                else                   -> current
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString(key, value).apply()
        }
    }

    // ── RD Speed Test ─────────────────────────────────────────────────────────
    // ✅ REAL: Pings the Real-Debrid API and measures latency
    fun runSpeedTest() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(rdSpeedTesting = true, rdSpeedTestResult = "Testing connection...") }
            val token = _state.value.rdToken
            val startTime = System.currentTimeMillis()
            try {
                val url = URL("https://api.real-debrid.com/rest/1.0/time")
                val conn = url.openConnection() as HttpURLConnection
                conn.requestMethod = "GET"
                conn.connectTimeout = 5_000
                conn.readTimeout    = 5_000
                if (token.isNotEmpty()) conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                val elapsed = System.currentTimeMillis() - startTime

                val result = if (code == 200 || code == 401 /* valid server response */) {
                    val grade = when {
                        elapsed < 150  -> "🟢 Excellent"
                        elapsed < 350  -> "🟡 Good"
                        elapsed < 700  -> "🟠 Fair"
                        else           -> "🔴 Poor"
                    }
                    "$grade — ${elapsed}ms to Real-Debrid servers"
                } else {
                    "⚠ Server returned HTTP $code"
                }
                _state.update { it.copy(rdSpeedTestResult = result, rdSpeedTesting = false) }
            } catch (e: Exception) {
                val elapsed = System.currentTimeMillis() - startTime
                _state.update {
                    it.copy(
                        rdSpeedTestResult = "✗ Failed after ${elapsed}ms — ${e.javaClass.simpleName}",
                        rdSpeedTesting = false
                    )
                }
            }
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────
    // ✅ REAL: Walks cache directory and deletes every file
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(cacheSizeStr = "Clearing...") }
            try {
                val app = getApplication<Application>()
                // Clear app cache dir (includes Coil disk cache)
                app.cacheDir.walkTopDown().forEach { f ->
                    if (f.isFile) f.delete()
                }
                // Clear external cache if present
                app.externalCacheDir?.walkTopDown()?.forEach { f ->
                    if (f.isFile) f.delete()
                }
                // Try clearing Coil in-memory cache via reflection (safe fallback)
                try {
                    val coilClass = Class.forName("coil.Coil")
                    val imageLoaderGetter = coilClass.getMethod("imageLoader", Context::class.java)
                    val loader = imageLoaderGetter.invoke(null, app)
                    loader.javaClass.getMethod("memoryCache").invoke(loader)
                        ?.javaClass?.getMethod("clear")?.invoke(
                            loader.javaClass.getMethod("memoryCache").invoke(loader)
                        )
                } catch (_: Exception) {}
            } catch (_: Exception) {}
            delay(400)
            calculateRealCacheSize()
        }
    }

    private fun calculateRealCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val app = getApplication<Application>()
                var sizeBytes = app.cacheDir.walkTopDown()
                    .filter { it.isFile }.sumOf { it.length() }
                app.externalCacheDir?.walkTopDown()
                    ?.filter { it.isFile }?.sumOf { it.length() }
                    ?.let { sizeBytes += it }

                val str = when {
                    sizeBytes < 1024 * 1024      -> "${sizeBytes / 1024} KB"
                    sizeBytes < 1024 * 1024 * 1024 -> "${"%.1f".format(sizeBytes / 1024.0 / 1024.0)} MB"
                    else -> "${"%.2f".format(sizeBytes / 1024.0 / 1024.0 / 1024.0)} GB"
                }
                _state.update { it.copy(cacheSizeStr = str) }
            } catch (_: Exception) {
                _state.update { it.copy(cacheSizeStr = "Unknown") }
            }
        }
    }

    // ── Device Info ───────────────────────────────────────────────────────────
    // ✅ REAL: Reads DeviceProfile to show GPU/RAM/tier info in About section
    private fun loadDeviceInfo() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (DeviceProfile.totalRamMb > 0) {
                    val tier  = DeviceProfile.tier.name
                    val gpu   = DeviceProfile.gpuRenderer.take(30).let {
                        if (it.length == 30) "$it…" else it
                    }
                    val ram   = "${DeviceProfile.totalRamMb} MB RAM"
                    _state.update { it.copy(deviceTier = "$tier — $gpu — $ram") }
                }
            } catch (_: Exception) {}
        }
    }

    // ── Search history ────────────────────────────────────────────────────────
    // ✅ REAL: Clears SearchViewModel's SharedPreferences key
    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(searchHistoryStatus = "Clearing...") }
            try {
                getApplication<Application>()
                    .getSharedPreferences("lumina_search_history", Context.MODE_PRIVATE)
                    .edit().remove("history_items").apply()
            } catch (_: Exception) {}
            delay(400)
            _state.update { it.copy(searchHistoryStatus = "Cleared ✓") }
            delay(1800)
            _state.update { it.copy(searchHistoryStatus = "Clear") }
        }
    }

    // ── Real-Debrid Auth ──────────────────────────────────────────────────────
    fun startRealDebridAuth() {
        viewModelScope.launch {
            _state.update { it.copy(authStatus = SettingsAuthStatus.Loading) }
            authManager.startDeviceAuthFlow().collect { result ->
                when (result) {
                    is AuthResult.ShowUserCode -> _state.update {
                        it.copy(authStatus = SettingsAuthStatus.WaitingForUser(result.code, result.url))
                    }
                    is AuthResult.Success -> {
                        loadAllSettings()
                        _state.update { it.copy(authStatus = SettingsAuthStatus.Success) }
                    }
                    is AuthResult.Error -> _state.update {
                        it.copy(authStatus = SettingsAuthStatus.Error(result.message))
                    }
                }
            }
        }
    }

    fun logoutRealDebrid() {
        prefs.edit().remove("rd_api_token").apply()
        loadAllSettings()
        _state.update { it.copy(rdSpeedTestResult = null) }
    }
}