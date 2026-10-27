package com.luminastreams.tv.domain.usecase

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Events emitted by [RealDebridTokenKeeper] when authentication state changes.
 * Observers (e.g. MainActivity) can collect [authEvents] to show UI alerts.
 */
sealed class RdAuthEvent {
    /** Credentials were wiped after repeated failures — user must re-authenticate. */
    object CredentialsWiped : RdAuthEvent()
}

/**
 * Centralized Real-Debrid token lifecycle manager.
 *
 * Real-Debrid access tokens expire approximately every 10 minutes.
 * This singleton proactively refreshes the token every 8 minutes
 * so that NO consumer ever encounters an expired token.
 *
 * Usage:
 *   - Call [start] once from LuminaApp.onCreate()
 *   - Call [getValidToken] from anywhere that needs the current RD token
 *   - Call [forceRefreshNow] when a 401/403 is encountered (belt & suspenders)
 *   - Call [stop] if needed (app termination, explicit user logout)
 */
object RealDebridTokenKeeper {

    private const val TAG = "RDTokenKeeper"

    /** Proactive refresh interval — 8 minutes (well before the ~10min expiry) */
    private const val REFRESH_INTERVAL_MS = 8L * 60L * 1000L  // 8 minutes

    /** Minimum interval between forced refreshes to prevent spam */
    private const val MIN_FORCE_REFRESH_INTERVAL_MS = 30_000L  // 30 seconds

    private var scope: CoroutineScope? = null
    private var refreshJob: Job? = null
    private var appContext: Context? = null
    private val mutex = Mutex()

    /** Auth events for UI observers (e.g. Toast when credentials are wiped) */
    private val _authEvents = MutableSharedFlow<RdAuthEvent>(extraBufferCapacity = 1)
    val authEvents: SharedFlow<RdAuthEvent> = _authEvents.asSharedFlow()

    /**
     * Start the background token refresh loop.
     * Safe to call multiple times — only the first call takes effect.
     */
    fun start(context: Context) {
        if (scope != null) return // Already running
        appContext = context.applicationContext
        scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        refreshJob = scope?.launch {
            // Continuous loop
            while (isActive) {
                val ctx = appContext ?: break
                val prefs = ctx.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                val lastRefresh = prefs.getLong("rd_last_refresh", 0L)
                val now = System.currentTimeMillis()
                
                val timeSinceLastRefresh = now - lastRefresh
                
                var token: String? = null
                
                if (timeSinceLastRefresh < REFRESH_INTERVAL_MS) {
                    // Valid token exists and was recently refreshed, wait for the remainder of the interval
                    val delayTime = REFRESH_INTERVAL_MS - timeSinceLastRefresh
                    Log.d(TAG, "⏭️ Token still fresh, waiting ${delayTime / 1000}s before next scheduled refresh")
                    delay(delayTime)
                }
                
                token = doRefresh("scheduled")
                
                if (token == null) {
                    // Check if credentials were wiped (counter reached 3) vs transient failure
                    val creds = hasCredentials()
                    if (!creds) {
                        Log.w(TAG, "🛑 No credentials remaining — stopping refresh loop")
                        break
                    }
                    val failures = ctx.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                        .getInt("rd_consecutive_failures", 0)
                    // Use shorter retry on consecutive failures to recover faster
                    val retryDelay = if (failures > 0) 30_000L else 10_000L
                    Log.d(TAG, "⏳ Refresh failed (consecutive failures: $failures), retrying in ${retryDelay / 1000}s")
                    delay(retryDelay)
                } else {
                    delay(REFRESH_INTERVAL_MS)
                }
            }
        }
        Log.d(TAG, "✅ Token keeper started — refreshing every ${REFRESH_INTERVAL_MS / 60000}min")
    }

    /**
     * Force an immediate token refresh. Called when a 401/403 is detected.
     * Debounced to prevent multiple simultaneous refreshes.
     */
    suspend fun forceRefreshNow(): String? {
        val ctx = appContext ?: return null
        val prefs = ctx.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        val lastRefresh = prefs.getLong("rd_last_refresh", 0L)
        val now = System.currentTimeMillis()
        
        if (now - lastRefresh < MIN_FORCE_REFRESH_INTERVAL_MS) {
            Log.d(TAG, "⏳ Skipping forced refresh — last refresh was ${(now - lastRefresh) / 1000}s ago")
            return getValidToken()
        }
        return doRefresh("forced")
    }

    /**
     * Get the current valid token from SharedPreferences.
     * This always returns the latest token that was saved by the refresh loop.
     */
    fun getValidToken(): String? {
        val ctx = appContext ?: return null
        val prefs = ctx.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        return prefs.getString("rd_api_token", "")?.trim()?.takeIf { it.isNotBlank() }
    }

    /**
     * Check if user has RD credentials configured at all.
     */
    fun hasCredentials(): Boolean {
        val ctx = appContext ?: return false
        val prefs = ctx.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        val clientId = prefs.getString("rd_client_id", "") ?: ""
        val clientSecret = prefs.getString("rd_client_secret", "") ?: ""
        val refreshToken = prefs.getString("rd_refresh_token", "") ?: ""
        return clientId.isNotBlank() && clientSecret.isNotBlank() && refreshToken.isNotBlank()
    }

    fun stop() {
        refreshJob?.cancel()
        scope?.cancel()
        scope = null
        refreshJob = null
        Log.d(TAG, "🛑 Token keeper stopped")
    }

    private suspend fun doRefresh(reason: String): String? {
        val ctx = appContext ?: return null
        if (!hasCredentials()) {
            Log.d(TAG, "⏭️ No RD credentials found, skipping refresh ($reason)")
            return null
        }

        return mutex.withLock {
            try {
                // Double-check if another thread just refreshed the token while we were waiting for the lock
                val prefs = ctx.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                val lastRefresh = prefs.getLong("rd_last_refresh", 0L)
                val now = System.currentTimeMillis()
                
                if (now - lastRefresh < MIN_FORCE_REFRESH_INTERVAL_MS) {
                    Log.d(TAG, "⏳ Skipping refresh inside lock — recently refreshed ($reason)")
                    return@withLock getValidToken()
                }

                val manager = RealDebridManager(ctx)
                val newToken = manager.refreshAccessToken()

                if (newToken != null) {
                    Log.d(TAG, "✅ Token refreshed successfully ($reason)")
                } else {
                    Log.w(TAG, "⚠️ Token refresh returned null ($reason) — credentials may be invalid or network failed")
                    // Check if credentials were just wiped by the refresh call (3 consecutive failures)
                    if (!hasCredentials()) {
                        Log.e(TAG, "🔴 Credentials wiped — notifying UI")
                        _authEvents.tryEmit(RdAuthEvent.CredentialsWiped)
                    }
                }
                newToken
            } catch (e: Exception) {
                Log.e(TAG, "❌ Token refresh failed ($reason): ${e.message}")
                null
            }
        }
    }
}
