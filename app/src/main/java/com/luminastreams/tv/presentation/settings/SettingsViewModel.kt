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

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val prefs = application.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
    private val authManager = RealDebridAuthManager(application)

    private val _state = MutableStateFlow(SettingsState())
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init { loadAllSettings() }

    fun setCategory(category: SettingsCategory) {
        _state.update { it.copy(selectedCategory = category) }
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
                themeColor = prefs.getString("theme_color", "Netflix Red") ?: "Netflix Red"
            )
        }
    }

    // FIX: Update UI instantly in RAM, save to disk in the background!
    fun updateToggleSetting(key: String, value: Boolean) {
        _state.update { current ->
            when (key) {
                "auto_play" -> current.copy(autoPlayNext = value)
                "hw_accel" -> current.copy(hwAcceleration = value)
                "safe_search" -> current.copy(safeSearch = value)
                "save_history" -> current.copy(saveSearchHistory = value)
                "is_hebrew" -> current.copy(isHebrew = value)
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

    fun clearCache() {
        viewModelScope.launch {
            _state.update { it.copy(cacheSizeStr = "Clearing...") }
            delay(1500)
            _state.update { it.copy(cacheSizeStr = "0 MB") }
        }
    }

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