package com.luminastreams.tv.presentation.settings

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.core.TrustedHttpClient
import com.luminastreams.tv.domain.usecase.AuthResult
import com.luminastreams.tv.domain.usecase.RealDebridAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
    private val authManager = RealDebridAuthManager(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadAllSettings()
        calculateRealCacheSize()
        loadDeviceInfo()
        checkServerStatuses() // Run health check on launch!
    }

    fun setCategory(category: SettingsCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    private fun loadAllSettings() {
        _state.update {
            it.copy(
                appLanguage        = prefs.getString("app_lang", "he") ?: "he",
                rdToken            = prefs.getString("rd_api_token", "") ?: "",
                audioPassthrough   = prefs.getBoolean("audio_passthrough", false),
                autoFrameRate      = prefs.getBoolean("afr", false),
                hwAcceleration     = prefs.getBoolean("hw_accel", true),
                preferredAudioLang = prefs.getString("preferred_audio_lang", "original") ?: "original",
                defaultSubtitles   = prefs.getString("def_subs", "Hebrew") ?: "Hebrew",
                yellowSubtitles    = prefs.getBoolean("yellow_subs", false),
                subtitleFontScale  = prefs.getString("subtitle_font_scale", "medium") ?: "medium",
                saveSearchHistory  = prefs.getBoolean("save_history", true),
                subtitleCacheOnly  = prefs.getBoolean("subtitle_cache_only", false),
                liteUiMode         = prefs.getBoolean("lite_ui", false),
                reduceMotion       = prefs.getBoolean("reduce_motion", false),
                preAllocateBuffer  = prefs.getBoolean("pre_buffer", false),
            )
        }
        DeviceProfile.forceLowTier      = prefs.getBoolean("lite_ui", false)
        DeviceProfile.forceReduceMotion = prefs.getBoolean("reduce_motion", false)
    }

    fun updateToggleSetting(key: String, value: Boolean) {
        _state.update { current ->
            when (key) {
                "audio_passthrough"   -> current.copy(audioPassthrough  = value)
                "afr"                 -> current.copy(autoFrameRate     = value)
                "hw_accel"            -> current.copy(hwAcceleration    = value)
                "yellow_subs"         -> current.copy(yellowSubtitles   = value)
                "save_history"        -> current.copy(saveSearchHistory = value)
                "subtitle_cache_only" -> current.copy(subtitleCacheOnly = value)
                "lite_ui"             -> {
                    DeviceProfile.forceLowTier = value
                    current.copy(liteUiMode = value)
                }
                "reduce_motion"       -> {
                    DeviceProfile.forceReduceMotion = value
                    current.copy(reduceMotion = value)
                }
                "pre_buffer"          -> current.copy(preAllocateBuffer = value)
                else                  -> current
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit { putBoolean(key, value) }
        }
    }

    fun updateStringSetting(key: String, value: String) {
        _state.update { current ->
            when (key) {
                "app_lang"             -> current.copy(appLanguage        = value)
                "preferred_audio_lang" -> current.copy(preferredAudioLang = value)
                "def_subs"             -> current.copy(defaultSubtitles   = value)
                "subtitle_font_scale"  -> current.copy(subtitleFontScale  = value)
                else                   -> current
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit { putString(key, value) }
        }
    }

    // ── TRUE SERVER VERIFICATION ──────────────────────────────────────────────
    private val trustedClient = TrustedHttpClient.builder().build()

    fun checkRealDebridAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(rdSpeedTesting = true, rdSpeedTestResult = "Contacting Server...") }
            val token = _state.value.rdToken
            if (token.isEmpty()) {
                _state.update { it.copy(rdSpeedTesting = false, rdSpeedTestResult = "⚠ No token configured") }
                return@launch
            }

            try {
                val request = Request.Builder()
                    .url("https://api.real-debrid.com/rest/1.0/user")
                    .header("Authorization", "Bearer $token")
                    .build()

                val response = trustedClient.newCall(request).execute()
                if (response.code == 200) {
                    val jsonStr = response.body.string()
                    val json = JSONObject(jsonStr)

                    val type = json.optString("type", "unknown")
                    val username = json.optString("username", "User")
                    val expiration = json.optString("expiration", "Unknown").take(10)

                    val statusStr = if (type == "premium") "🟢 Premium ($username) | Exp: $expiration"
                    else "🔴 Free Account ($username)"

                    _state.update { it.copy(rdSpeedTestResult = statusStr, rdSpeedTesting = false) }
                } else {
                    _state.update { it.copy(rdSpeedTestResult = "⚠ Token Expired or Invalid (HTTP ${response.code})", rdSpeedTesting = false) }
                }
                response.close()
            } catch (e: Exception) {
                _state.update { it.copy(rdSpeedTestResult = "✗ Network Error: Could not reach Real-Debrid", rdSpeedTesting = false) }
            }
        }
    }

    // ── SERVER HEALTH CHECK ───────────────────────────────────────────────────
    fun checkServerStatuses() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCheckingServers = true, rdServerStatus = "Checking...", torrentioServerStatus = "Checking...") }

            val rdUp = checkUrlStatus("https://api.real-debrid.com/rest/1.0/time")
            val torrentioUp = checkUrlStatus("https://torrentio.strem.fun/manifest.json")

            _state.update {
                it.copy(
                    isCheckingServers = false,
                    rdServerStatus = if (rdUp) "🟢 Online" else "🔴 Offline",
                    torrentioServerStatus = if (torrentioUp) "🟢 Online" else "🔴 Offline"
                )
            }
        }
    }

    private fun checkUrlStatus(urlString: String): Boolean {
        return try {
            val request = Request.Builder().url(urlString).build()
            val response = trustedClient.newCall(request).execute()
            val success = response.code in 200..299
            response.close()
            success
        } catch (e: Exception) {
            false
        }
    }

    // ── Cache ─────────────────────────────────────────────────────────────────
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(cacheSizeStr = "Clearing...") }
            try {
                val app = getApplication<Application>()
                app.cacheDir.walkTopDown().forEach { f -> if (f.isFile) f.delete() }
                app.externalCacheDir?.walkTopDown()?.forEach { f -> if (f.isFile) f.delete() }
                try {
                    val coilClass = Class.forName("coil.Coil")
                    val loader = coilClass.getMethod("imageLoader", Context::class.java).invoke(null, app)
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
                var sizeBytes = app.cacheDir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
                app.externalCacheDir?.walkTopDown()?.filter { it.isFile }?.sumOf { it.length() }?.let { sizeBytes += it }
                val str = when {
                    sizeBytes < 1024 * 1024        -> "${sizeBytes / 1024} KB"
                    sizeBytes < 1024 * 1024 * 1024 -> "${"%.1f".format(sizeBytes / 1024.0 / 1024.0)} MB"
                    else                           -> "${"%.2f".format(sizeBytes / 1024.0 / 1024.0 / 1024.0)} GB"
                }
                _state.update { it.copy(cacheSizeStr = str) }
            } catch (_: Exception) {
                _state.update { it.copy(cacheSizeStr = "Unknown") }
            }
        }
    }

    private fun loadDeviceInfo() {
        viewModelScope.launch(Dispatchers.Default) {
            try {
                if (DeviceProfile.totalRamMb > 0) {
                    val tier = DeviceProfile.tier.name
                    val gpu  = DeviceProfile.gpuRenderer.take(30).let { if (it.length == 30) "$it…" else it }
                    val ram  = "${DeviceProfile.totalRamMb} MB RAM"
                    _state.update { it.copy(deviceTier = "$tier — $gpu — $ram") }
                }
            } catch (_: Exception) {}
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(searchHistoryStatus = "Clearing...") }
            try {
                getApplication<Application>()
                    .getSharedPreferences("lumina_search_history", Context.MODE_PRIVATE)
                    .edit { remove("history_items") }
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
                        checkRealDebridAccount()
                    }
                    is AuthResult.Error -> _state.update {
                        it.copy(authStatus = SettingsAuthStatus.Error(result.message))
                    }
                }
            }
        }
    }

    fun logoutRealDebrid() {
        prefs.edit { remove("rd_api_token") }
        loadAllSettings()
        _state.update { it.copy(rdSpeedTestResult = null) }
    }
}