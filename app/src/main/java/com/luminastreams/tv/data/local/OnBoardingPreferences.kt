package com.luminastreams.tv.data.local

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

val Context.dataStore by preferencesDataStore(name = "onboarding_prefs")

class OnBoardingPreferences(context: Context) {
    private val dataStore = context.dataStore

    companion object {
        val KEY_IS_FIRST_LAUNCH = booleanPreferencesKey("is_first_launch")
    }

    val isFirstLaunchFlow: Flow<Boolean> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences()) // תוקן כאן
            } else {
                throw exception
            }
        }
        .map { preferences ->
            preferences[KEY_IS_FIRST_LAUNCH] ?: true
        }

    suspend fun setCompletedFirstLaunch() {
        dataStore.edit { preferences ->
            preferences[KEY_IS_FIRST_LAUNCH] = false
        }
    }
}