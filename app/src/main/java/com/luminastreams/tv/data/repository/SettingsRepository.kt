package com.luminastreams.tv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.SharedPreferencesMigration
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.*

// Migrate old SharedPreferences "lumina_settings" into DataStore seamlessly
private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(
    name = "lumina_settings_v2",
    produceMigrations = { context ->
        listOf(SharedPreferencesMigration(context, "lumina_settings"))
    }
)

/**
 * Data class representing the full snapshot of application settings.
 */
data class SettingsSnapshot(
    val appLanguage: String = "he",
    val debridToken: String = com.luminastreams.tv.BuildConfig.AD_DEFAULT_TOKEN,
    val audioPassthrough: Boolean = false,
    val autoFrameRate: Boolean = false,
    val hwAcceleration: Boolean = true,
    val preferredAudioLang: String = "original",
    val defaultSubtitles: String = "Hebrew",
    val yellowSubtitles: Boolean = false,
    val subtitleFontScale: String = "medium",
    val saveSearchHistory: Boolean = true,
    val liteUiMode: Boolean = false,
    val reduceMotion: Boolean = false,
    val preAllocateBuffer: Boolean = false,
    val maxResolution: String = "Unlimited",
    val maxFileSize: String = "Unlimited",
    val ktuvitEmail: String = "",
    val ktuvitPassword: String = ""
)

class SettingsRepository(private val context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val APP_LANG = stringPreferencesKey("app_lang")
        val AD_API_TOKEN = stringPreferencesKey("ad_api_token")
        val AUDIO_PASSTHROUGH = booleanPreferencesKey("audio_passthrough")
        val AFR_ENABLED = booleanPreferencesKey("afr")
        val HW_ACCEL = booleanPreferencesKey("hw_accel")
        val PREFERRED_AUDIO_LANG = stringPreferencesKey("preferred_audio_lang")
        val DEFAULT_SUBS_LANG = stringPreferencesKey("def_subs")
        val YELLOW_SUBS = booleanPreferencesKey("yellow_subs")
        val SUBTITLE_FONT_SCALE = stringPreferencesKey("subtitle_font_scale")
        val SAVE_HISTORY = booleanPreferencesKey("save_history")
        val LITE_UI = booleanPreferencesKey("lite_ui")
        val REDUCE_MOTION = booleanPreferencesKey("reduce_motion")
        val PRE_BUFFER = booleanPreferencesKey("pre_buffer")
        val MAX_RESOLUTION = stringPreferencesKey("max_resolution")
        val MAX_FILE_SIZE = stringPreferencesKey("max_file_size")
        val KTUVIT_EMAIL = stringPreferencesKey("ktuvit_email")
        val KTUVIT_PASSWORD = stringPreferencesKey("ktuvit_password")
    }

    private fun mapPrefs(prefs: Preferences) = SettingsSnapshot(
        appLanguage = prefs[APP_LANG] ?: "he",
        debridToken = prefs[AD_API_TOKEN] ?: com.luminastreams.tv.BuildConfig.AD_DEFAULT_TOKEN,
        audioPassthrough = prefs[AUDIO_PASSTHROUGH] ?: false,
        autoFrameRate = prefs[AFR_ENABLED] ?: false,
        hwAcceleration = prefs[HW_ACCEL] ?: true,
        preferredAudioLang = prefs[PREFERRED_AUDIO_LANG] ?: "original",
        defaultSubtitles = prefs[DEFAULT_SUBS_LANG] ?: "Hebrew",
        yellowSubtitles = prefs[YELLOW_SUBS] ?: false,
        subtitleFontScale = prefs[SUBTITLE_FONT_SCALE] ?: "medium",
        saveSearchHistory = prefs[SAVE_HISTORY] ?: true,
        liteUiMode = prefs[LITE_UI] ?: false,
        reduceMotion = prefs[REDUCE_MOTION] ?: false,
        preAllocateBuffer = prefs[PRE_BUFFER] ?: false,
        maxResolution = prefs[MAX_RESOLUTION] ?: "Unlimited",
        maxFileSize = prefs[MAX_FILE_SIZE] ?: "Unlimited",
        ktuvitEmail = prefs[KTUVIT_EMAIL] ?: "",
        ktuvitPassword = prefs[KTUVIT_PASSWORD] ?: ""
    )

    private val snapshotFlow: Flow<SettingsSnapshot> = dataStore.data
        .catch { e ->
            if (e is java.io.IOException) {
                emit(emptyPreferences())
            } else {
                throw e
            }
        }
        .map { mapPrefs(it) }

    // A unified StateFlow that holds the latest settings asynchronously
    val settingsFlow: StateFlow<SettingsSnapshot> = snapshotFlow
        .stateIn(
            // Use a SupervisorJob so a single failed collector doesn't tear down the
            // whole settings stream. The scope's lifetime is tied to the singleton
            // repository (i.e. the Application), so this is intentional and bounded.
            scope = CoroutineScope(Dispatchers.IO + SupervisorJob()),
            started = SharingStarted.Eagerly,
            initialValue = SettingsSnapshot() // Wait for first emit, but defaults provided
        )

    /**
     * Get a synchronous snapshot of the current settings.
     * Eagerly loaded, safe to use immediately if initialized early.
     */
    fun current(): SettingsSnapshot = settingsFlow.value

    /**
     * Suspends until the first *real* value is read from DataStore (including the
     * SharedPreferences → DataStore migration). Unlike [current] / [settingsFlow].value,
     * this never returns the placeholder defaults before disk has been read.
     */
    suspend fun awaitSnapshot(): SettingsSnapshot = snapshotFlow.first()

    suspend fun updateSetting(key: Preferences.Key<*>, value: Any) {
        dataStore.edit { prefs ->
            @Suppress("UNCHECKED_CAST")
            when (value) {
                is String -> prefs[key as Preferences.Key<String>] = value
                is Boolean -> prefs[key as Preferences.Key<Boolean>] = value
                is Int -> prefs[key as Preferences.Key<Int>] = value
                is Float -> prefs[key as Preferences.Key<Float>] = value
                is Long -> prefs[key as Preferences.Key<Long>] = value
                else -> throw IllegalArgumentException(
                    "updateSetting(${key.name}): unsupported value type ${value::class.java.name}"
                )
            }
        }
    }
    
    suspend fun clearDebridToken() {
        dataStore.edit { 
            it.remove(AD_API_TOKEN)
        }
    }
}
