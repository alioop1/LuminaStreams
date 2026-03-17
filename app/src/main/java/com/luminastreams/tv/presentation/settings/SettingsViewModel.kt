package com.luminastreams.tv.presentation.settings

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.domain.usecase.AuthResult
import com.luminastreams.tv.domain.usecase.RealDebridAuthManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.io.File

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
    private val authManager = RealDebridAuthManager(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        loadAllSettings()
        calculateRealCacheSize() // חישוב נפח אמיתי בהפעלה
    }

    fun setCategory(category: SettingsCategory) {
        _state.update { it.copy(selectedCategory = category) }
    }

    // פונקציה אמיתית שקוראת את מערכת הקבצים של האנדרואיד ומחשבת כמה מקום תופס המטמון
    private fun calculateRealCacheSize() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val cacheDir = getApplication<Application>().cacheDir
                var sizeBytes = 0L
                if (cacheDir.exists()) {
                    sizeBytes += cacheDir.walkTopDown().filter { it.isFile }.map { it.length() }.sum()
                }

                val sizeMb = sizeBytes / (1024 * 1024)
                _state.update { it.copy(cacheSizeStr = "$sizeMb MB") }
            } catch (e: Exception) {
                _state.update { it.copy(cacheSizeStr = "Unknown") }
            }
        }
    }

    private fun loadAllSettings() {
        _state.update {
            it.copy(
                rdToken = prefs.getString("rd_api_token", "") ?: "",
                isHebrew = prefs.getBoolean("is_hebrew", true),
                maxResolution = prefs.getString("max_res", "4K") ?: "4K",
                autoPlayNext = prefs.getBoolean("auto_play", true),
                defaultSubtitles = prefs.getString("def_subs", "Hebrew") ?: "Hebrew",
                hwAcceleration = prefs.getBoolean("hw_accel", true),
                safeSearch = prefs.getBoolean("safe_search", false),
                saveSearchHistory = prefs.getBoolean("save_history", true),
                themeColor = prefs.getString("theme_color", "Netflix Red") ?: "Netflix Red",

                // ההגדרות החדשות - קריאה אמיתית
                audioPassthrough = prefs.getBoolean("audio_passthrough", false),
                forceHdr = prefs.getBoolean("force_hdr", false),
                autoFrameRate = prefs.getBoolean("afr", false),
                dimUi = prefs.getBoolean("dim_ui", true),
                liteUiMode = prefs.getBoolean("lite_ui", false),
                preAllocateBuffer = prefs.getBoolean("pre_buffer", false),
                yellowSubtitles = prefs.getBoolean("yellow_subs", false)
            )
        }
    }

    fun updateToggleSetting(key: String, value: Boolean) {
        _state.update { current ->
            when (key) {
                "auto_play" -> current.copy(autoPlayNext = value)
                "hw_accel" -> current.copy(hwAcceleration = value)
                "safe_search" -> current.copy(safeSearch = value)
                "save_history" -> current.copy(saveSearchHistory = value)
                "is_hebrew" -> current.copy(isHebrew = value)
                "audio_passthrough" -> current.copy(audioPassthrough = value)
                "force_hdr" -> current.copy(forceHdr = value)
                "afr" -> current.copy(autoFrameRate = value)
                "dim_ui" -> current.copy(dimUi = value)
                "lite_ui" -> current.copy(liteUiMode = value)
                "pre_buffer" -> current.copy(preAllocateBuffer = value)
                "yellow_subs" -> current.copy(yellowSubtitles = value)
                else -> current
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putBoolean(key, value).apply()
        }
    }

    fun updateStringSetting(key: String, value: String) {
        _state.update { current ->
            when (key) {
                "max_res" -> current.copy(maxResolution = value)
                "def_subs" -> current.copy(defaultSubtitles = value)
                "theme_color" -> current.copy(themeColor = value)
                else -> current
            }
        }
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit().putString(key, value).apply()
        }
    }

    // פונקציה שעושה מחיקה *אמיתית* לקבצי המטמון במכשיר
    fun clearCache() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(cacheSizeStr = "Clearing...") }
            try {
                val cacheDir = getApplication<Application>().cacheDir
                if (cacheDir.exists()) {
                    // מחיקת כל הקבצים בתיקיית המטמון של האפליקציה (כולל Coil)
                    cacheDir.listFiles()?.forEach {
                        if (it.isDirectory) it.deleteRecursively() else it.delete()
                    }
                }
            } catch (e: Exception) {}

            delay(600) // השהייה קלה מאוד לטובת חווית משתמש (שיראה שזה עבד)
            calculateRealCacheSize() // חישוב מחדש, אמור להחזיר 0 MB
        }
    }

    fun clearSearchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(searchHistoryStatus = "Clearing...") }
            // כאן ייכנס קוד ה-Room DB שלך בעתיד: searchDao.deleteAll()
            delay(600)
            _state.update { it.copy(searchHistoryStatus = "Cleared!") }
            delay(1500)
            _state.update { it.copy(searchHistoryStatus = "Clear") }
        }
    }

    fun clearWatchHistory() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(watchHistoryStatus = "Clearing...") }
            // כאן ייכנס קוד ה-Room DB שלך בעתיד: watchHistoryDao.deleteAll()
            delay(600)
            _state.update { it.copy(watchHistoryStatus = "Cleared!") }
            delay(1500)
            _state.update { it.copy(watchHistoryStatus = "Clear") }
        }
    }

    // --- Real Debrid ---

    fun startRealDebridAuth() {
        viewModelScope.launch {
            _state.update { it.copy(authStatus = SettingsAuthStatus.Loading) }
            authManager.startDeviceAuthFlow().collect { result ->
                when (result) {
                    is AuthResult.ShowUserCode -> _state.update { it.copy(authStatus = SettingsAuthStatus.WaitingForUser(result.code, result.url)) }
                    is AuthResult.Success -> { loadAllSettings(); _state.update { it.copy(authStatus = SettingsAuthStatus.Success) } }
                    is AuthResult.Error -> _state.update { it.copy(authStatus = SettingsAuthStatus.Error(result.message)) }
                }
            }
        }
    }

    fun logoutRealDebrid() {
        prefs.edit().remove("rd_api_token").apply()
        loadAllSettings()
    }
}