package com.luminastreams.tv.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

import com.luminastreams.tv.core.Constants

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "lumina_settings_v2")

class SettingsRepository(private val context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val APP_LANG = stringPreferencesKey("app_lang")
        val RD_API_TOKEN = stringPreferencesKey("rd_api_token")
        val AFR_ENABLED = booleanPreferencesKey("afr")
        val DEFAULT_SUBS_LANG = stringPreferencesKey("def_subs")
    }

    val appLangFlow: Flow<String> = dataStore.data.map { it[APP_LANG] ?: "Hebrew" }
    val rdApiTokenFlow: Flow<String?> = dataStore.data.map { it[RD_API_TOKEN] }
    val afrEnabledFlow: Flow<Boolean> = dataStore.data.map { it[AFR_ENABLED] ?: false }
    val defaultSubsLangFlow: Flow<String> = dataStore.data.map { it[DEFAULT_SUBS_LANG] ?: "Hebrew" }

    suspend fun setAppLang(lang: String) {
        dataStore.edit { it[APP_LANG] = lang }
    }

    suspend fun setRdApiToken(token: String?) {
        dataStore.edit { 
            if (token.isNullOrEmpty()) {
                it.remove(RD_API_TOKEN)
            } else {
                it[RD_API_TOKEN] = token 
            }
        }
    }

    suspend fun setAfrEnabled(enabled: Boolean) {
        dataStore.edit { it[AFR_ENABLED] = enabled }
    }

    suspend fun setDefaultSubsLang(lang: String) {
        dataStore.edit { it[DEFAULT_SUBS_LANG] = lang }
    }
}
