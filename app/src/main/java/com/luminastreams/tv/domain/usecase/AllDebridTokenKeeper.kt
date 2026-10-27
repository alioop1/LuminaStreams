package com.luminastreams.tv.domain.usecase

import android.content.Context
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Events emitted by [AllDebridTokenKeeper] when authentication state changes.
 */
sealed class AdAuthEvent {
    object CredentialsWiped : AdAuthEvent()
}

/**
 * Centralized AllDebrid token provider.
 * AllDebrid uses a static API key, so we do not need a background refresh loop.
 */
object AllDebridTokenKeeper {

    private var appContext: Context? = null
    
    private val _authEvents = MutableSharedFlow<AdAuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<AdAuthEvent> = _authEvents.asSharedFlow()

    fun start(context: Context) {
        appContext = context.applicationContext
    }

    suspend fun forceRefreshNow(): String? {
        // No refresh needed for static API keys, just return the current token.
        return getValidToken()
    }

    fun getValidToken(): String? {
        val ctx = appContext ?: return null
        val app = ctx as com.luminastreams.tv.core.LuminaApp
        val token = app.settingsRepository.current().debridToken.trim()
        return if (token.isNotBlank()) token else null
    }

    fun hasCredentials(): Boolean {
        return getValidToken() != null
    }

    fun stop() {
        // Nothing to stop
    }
}
