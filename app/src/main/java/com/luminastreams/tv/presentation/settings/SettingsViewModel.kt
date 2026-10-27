package com.luminastreams.tv.presentation.settings

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.core.TrustedHttpClient
import com.luminastreams.tv.domain.usecase.AdAuthResult
import com.luminastreams.tv.domain.usecase.AllDebridAuthManager
import com.luminastreams.tv.core.LuminaApp
import com.luminastreams.tv.data.sync.SyncManager
import com.luminastreams.tv.data.repository.SettingsRepository
import com.luminastreams.tv.domain.repository.AuthRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import okhttp3.Request
import org.json.JSONObject

class SettingsViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "SettingsViewModel"

    private val authManager = AllDebridAuthManager(application)
    private val app = application as LuminaApp
    private val settingsRepository = app.settingsRepository
    private val syncManager: SyncManager = app.syncManager
    private val authRepository: AuthRepository = app.authRepository

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            settingsRepository.settingsFlow.collect { snap ->
                _state.update {
                    it.copy(
                        appLanguage        = snap.appLanguage,
                        debridToken        = snap.debridToken,
                        audioPassthrough   = snap.audioPassthrough,
                        autoFrameRate      = snap.autoFrameRate,
                        hwAcceleration     = snap.hwAcceleration,
                        preferredAudioLang = snap.preferredAudioLang,
                        defaultSubtitles   = snap.defaultSubtitles,
                        yellowSubtitles    = snap.yellowSubtitles,
                        subtitleFontScale  = snap.subtitleFontScale,
                        saveSearchHistory  = snap.saveSearchHistory,
                        liteUiMode         = snap.liteUiMode,
                        reduceMotion       = snap.reduceMotion,
                        preAllocateBuffer  = snap.preAllocateBuffer,
                        maxResolution      = snap.maxResolution,
                        maxFileSize        = snap.maxFileSize
                    )
                }
                DeviceProfile.forceLowTier      = snap.liteUiMode
                DeviceProfile.forceReduceMotion = snap.reduceMotion
            }
        }

        // AD account check is decoupled from the general settings flow
        viewModelScope.launch {
            settingsRepository.settingsFlow
                .map { it.debridToken }
                .distinctUntilChanged()
                .collect { token ->
                    if (token.isNotBlank()) checkAllDebridAccount()
                }
        }
        calculateRealCacheSize()
        loadDeviceInfo()
        checkServerStatuses()

        // Observe Firebase auth state for user email display
        viewModelScope.launch {
            authRepository.currentUser.collect { user ->
                _state.update { it.copy(userEmail = user?.email) }
            }
        }

        // Observe sync status from SyncManager
        viewModelScope.launch {
            syncManager.syncStatus.collect { status ->
                _state.update { it.copy(syncStatus = status) }
            }
        }
        viewModelScope.launch {
            syncManager.isSyncing.collect { syncing ->
                _state.update { it.copy(isSyncing = syncing) }
            }
        }
    }

    fun setCategory(category: SettingsCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    // loadAllSettings removed as it's now observed automatically via Flow

    fun updateToggleSetting(key: String, value: Boolean) {
        _state.update { current ->
            when (key) {
                "audio_passthrough"   -> current.copy(audioPassthrough  = value)
                "afr"                 -> current.copy(autoFrameRate     = value)
                "hw_accel"            -> current.copy(hwAcceleration    = value)
                "yellow_subs"         -> current.copy(yellowSubtitles   = value)
                "save_history"        -> current.copy(saveSearchHistory = value)
                "lite_ui"             -> {
                    DeviceProfile.forceLowTier = value
                    current.copy(liteUiMode = value)
                }
                "reduce_motion"       -> {
                    DeviceProfile.forceReduceMotion = value
                    current.copy(reduceMotion = value)
                }
                "pre_buffer"          -> current.copy(preAllocateBuffer = value)
                else                  -> { Log.e(TAG, "updateToggleSetting: unknown key '$key'"); current }
            }
        }
        viewModelScope.launch {
            val prefKey = when (key) {
                "audio_passthrough" -> SettingsRepository.AUDIO_PASSTHROUGH
                "afr" -> SettingsRepository.AFR_ENABLED
                "hw_accel" -> SettingsRepository.HW_ACCEL
                "yellow_subs" -> SettingsRepository.YELLOW_SUBS
                "save_history" -> SettingsRepository.SAVE_HISTORY
                "lite_ui" -> SettingsRepository.LITE_UI
                "reduce_motion" -> SettingsRepository.REDUCE_MOTION
                "pre_buffer" -> SettingsRepository.PRE_BUFFER
                else -> null
            }
            if (prefKey == null) {
                Log.e(TAG, "updateToggleSetting: key '$key' not persisted (no matching DataStore key)")
            } else {
                settingsRepository.updateSetting(prefKey, value)
            }
        }
    }

    fun updateStringSetting(key: String, value: String) {
        _state.update { current ->
            when (key) {
                "app_lang"             -> current.copy(appLanguage        = value)
                "preferred_audio_lang" -> current.copy(preferredAudioLang = value)
                "def_subs"             -> current.copy(defaultSubtitles   = value)
                "subtitle_font_scale"  -> current.copy(subtitleFontScale  = value)
                "max_resolution"       -> current.copy(maxResolution      = value)
                "max_file_size"        -> current.copy(maxFileSize        = value)
                else                   -> { Log.e(TAG, "updateStringSetting: unknown key '$key'"); current }
            }
        }
        viewModelScope.launch {
            val prefKey = when (key) {
                "app_lang" -> SettingsRepository.APP_LANG
                "preferred_audio_lang" -> SettingsRepository.PREFERRED_AUDIO_LANG
                "def_subs" -> SettingsRepository.DEFAULT_SUBS_LANG
                "subtitle_font_scale" -> SettingsRepository.SUBTITLE_FONT_SCALE
                "max_resolution" -> SettingsRepository.MAX_RESOLUTION
                "max_file_size" -> SettingsRepository.MAX_FILE_SIZE
                else -> null
            }
            if (prefKey == null) {
                Log.e(TAG, "updateStringSetting: key '$key' not persisted (no matching DataStore key)")
            } else {
                settingsRepository.updateSetting(prefKey, value)
            }
        }
    }

    // ── TRUE SERVER VERIFICATION ──────────────────────────────────────────────
    private val trustedClient = TrustedHttpClient.builder().build()

    fun checkAllDebridAccount() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(rdSpeedTesting = true, rdSpeedTestResult = "Contacting Server...") }
            val token = _state.value.debridToken
            if (token.isEmpty()) {
                _state.update { it.copy(rdSpeedTesting = false, rdSpeedTestResult = "⚠ No token configured") }
                return@launch
            }

            try {
                val request = Request.Builder()
                    .url("https://api.alldebrid.com/v4/user?agent=LuminaStreams")
                    .header("Authorization", "Bearer $token")
                    .build()

                val response = trustedClient.newCall(request).execute()
                response.use { resp ->
                    if (resp.code == 200) {
                        val jsonStr = resp.body.string()
                        val json = JSONObject(jsonStr)

                        if (json.optString("status") == "success") {
                            val userJson = json.getJSONObject("data").getJSONObject("user")
                            val isPremium = userJson.optBoolean("isPremium", false)
                            val username = userJson.optString("username", "User")
                            val expDate = java.util.Date(userJson.optLong("premiumUntil", 0) * 1000)
                            val expiration = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US).format(expDate)

                            val statusStr = if (isPremium) "🟢 Premium ($username) | Exp: $expiration"
                            else "🔴 Free Account ($username)"

                            _state.update { it.copy(rdSpeedTestResult = statusStr, rdSpeedTesting = false) }
                        } else {
                            _state.update { it.copy(rdSpeedTestResult = "⚠ Token Invalid", rdSpeedTesting = false) }
                        }
                    } else {
                        _state.update { it.copy(rdSpeedTestResult = "⚠ Error (HTTP ${resp.code})", rdSpeedTesting = false) }
                    }
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                // Retry once after 2s — cold boot may not have network ready
                delay(2000)
                try {
                    val retryReq = Request.Builder()
                        .url("https://api.alldebrid.com/v4/user?agent=LuminaStreams")
                        .header("Authorization", "Bearer $token")
                        .build()
                    val retryResp = trustedClient.newCall(retryReq).execute()
                    retryResp.use { resp ->
                        if (resp.code == 200) {
                            val json = JSONObject(resp.body.string())
                            if (json.optString("status") == "success") {
                                val userJson = json.getJSONObject("data").getJSONObject("user")
                                val isPremium = userJson.optBoolean("isPremium", false)
                                val username = userJson.optString("username", "User")
                                val statusStr = if (isPremium) "🟢 Premium ($username)" else "🔴 Free Account ($username)"
                                _state.update { it.copy(rdSpeedTestResult = statusStr, rdSpeedTesting = false) }
                            } else {
                                _state.update { it.copy(rdSpeedTestResult = "✗ Token Invalid", rdSpeedTesting = false) }
                            }
                        } else {
                            _state.update { it.copy(rdSpeedTestResult = "✗ Network Error: Could not reach AllDebrid", rdSpeedTesting = false) }
                        }
                    }
                } catch (_: Exception) {
                    _state.update { it.copy(rdSpeedTestResult = "✗ Network Error: Could not reach AllDebrid", rdSpeedTesting = false) }
                }
            }
        }
    }

    // ── SERVER HEALTH CHECK ───────────────────────────────────────────────────
    fun checkServerStatuses() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isCheckingServers = true, adServerStatus = "Checking...", torrentioServerStatus = "Checking...") }

            // Retry up to 3 times with increasing delay for cold boot network readiness
            var rdUp = false
            var torrentioUp = false
            for (attempt in 1..3) {
                rdUp = checkUrlStatus("https://api.alldebrid.com/v4/ping")
                torrentioUp = checkUrlStatus("https://torrentio.strem.fun/manifest.json")
                if (rdUp && torrentioUp) break
                if (attempt < 3) delay(if (attempt == 1) 1500L else 3000L)
            }

            _state.update {
                it.copy(
                    isCheckingServers = false,
                    adServerStatus = if (rdUp) "🟢 Online" else "🔴 Offline",
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

    // ── AllDebrid Auth ──────────────────────────────────────────────────────
    fun startAllDebridAuth() {
        viewModelScope.launch {
            _state.update { it.copy(authStatus = SettingsAuthStatus.Loading) }
            authManager.startDeviceAuthFlow().collect { result ->
                when (result) {
                    is AdAuthResult.ShowUserCode -> _state.update {
                        it.copy(authStatus = SettingsAuthStatus.WaitingForUser(result.code, result.url))
                    }
                    is AdAuthResult.Success -> {
                        com.luminastreams.tv.domain.usecase.AllDebridTokenKeeper.stop()
                        com.luminastreams.tv.domain.usecase.AllDebridTokenKeeper.start(getApplication())
                        _state.update { it.copy(authStatus = SettingsAuthStatus.Success) }
                        checkAllDebridAccount()
                    }
                    is AdAuthResult.Error -> _state.update {
                        it.copy(authStatus = SettingsAuthStatus.Error(result.message))
                    }
                }
            }
        }
    }

    fun logoutAllDebrid() {
        viewModelScope.launch {
            settingsRepository.clearDebridToken()
        }
        com.luminastreams.tv.domain.usecase.AllDebridTokenKeeper.stop()
        // Clearing the user token falls back to the built-in key (BuildConfig.AD_DEFAULT_TOKEN).
        // Be honest about which state we actually end up in.
        val hasBuiltIn = com.luminastreams.tv.BuildConfig.AD_DEFAULT_TOKEN.isNotBlank()
        val isHe = settingsRepository.current().appLanguage == "he"
        _state.update {
            it.copy(
                rdSpeedTestResult = if (hasBuiltIn) {
                    if (isHe) "🔑 מחובר לחשבון המובנה" else "🔑 Using built-in account"
                } else null,
                authStatus = SettingsAuthStatus.Idle
            )
        }
        if (hasBuiltIn) {
            com.luminastreams.tv.domain.usecase.AllDebridTokenKeeper.start(getApplication())
        }
    }

    // ── Cloud Sync ────────────────────────────────────────────────────────────────────
    fun triggerManualSync() {
        syncManager.fullSync()
    }
}