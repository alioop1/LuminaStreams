package com.luminastreams.tv.presentation.auth

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

import com.google.firebase.firestore.FirebaseFirestore
import com.luminastreams.tv.data.sync.SyncManager
import com.luminastreams.tv.domain.repository.AuthRepository
import kotlinx.coroutines.tasks.await

class AuthViewModel(
    private val repository: AuthRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val TAG = "AuthViewModel"
    private val _state = MutableStateFlow(AuthState())
    val state: StateFlow<AuthState> = _state.asStateFlow()
    private val db = FirebaseFirestore.getInstance()

    init {
        viewModelScope.launch {
            repository.currentUser.collect { user ->
                _state.update { it.copy(loggedInEmail = user?.email) }
            }
        }
    }

    fun onEvent(event: AuthEvent) {
        when (event) {
            is AuthEvent.Login -> login(event.email, event.pass)
            is AuthEvent.Register -> register(event.email, event.pass, event.confirmPass)
            AuthEvent.ToggleMode -> _state.update { 
                it.copy(mode = if (it.mode == AuthMode.LOGIN) AuthMode.REGISTER else AuthMode.LOGIN) 
            }
            AuthEvent.ClearError -> _state.update { it.copy(error = null) }
            AuthEvent.Logout -> viewModelScope.launch { repository.logout() }
            AuthEvent.StartDeviceLink -> startDeviceLinking()
        }
    }

    private fun login(email: String, pass: String) {
        if (email.isBlank() || pass.isBlank()) {
            _state.update { it.copy(error = "אנא מלא את כל השדות") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.login(email, pass)
            if (result.isSuccess) {
                syncManager.startRealtimeSync()
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                _state.update { it.copy(isLoading = false, error = translateFirebaseError(result.exceptionOrNull())) }
            }
        }
    }

    private fun register(email: String, pass: String, confirmPass: String) {
        if (email.isBlank() || pass.isBlank() || confirmPass.isBlank()) {
            _state.update { it.copy(error = "אנא מלא את כל השדות") }
            return
        }
        if (pass != confirmPass) {
            _state.update { it.copy(error = "הסיסמאות אינן תואמות") }
            return
        }
        if (pass.length < 6) {
            _state.update { it.copy(error = "הסיסמה חייבת להכיל לפחות 6 תווים") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            val result = repository.register(email, pass)
            if (result.isSuccess) {
                syncManager.startRealtimeSync()
                _state.update { it.copy(isLoading = false, isSuccess = true) }
            } else {
                _state.update { it.copy(isLoading = false, error = translateFirebaseError(result.exceptionOrNull())) }
            }
        }
    }

    /**
     * Device Linking Flow:
     * 1. TV generates a random 6-char uppercase code
     * 2. Writes it to Firestore: device_links/{code} -> { status: "pending", created: timestamp }
     * 3. Polls every 3s to check if the web app wrote email+password
     * 4. When found, auto-logs in and cleans up
     */
    private fun startDeviceLinking() {
        viewModelScope.launch {
            val code = generateLinkCode()
            _state.update { it.copy(deviceLinkCode = code, isLinking = true, linkStatus = null, error = null) }
            Log.d(TAG, "Device linking started with code: $code")

            try {
                // Write pending link to Firestore
                val linkDoc = db.collection("device_links").document(code)
                linkDoc.set(mapOf(
                    "status" to "pending",
                    "created" to System.currentTimeMillis()
                )).await()

                // Poll for completion (max 5 minutes)
                var attempts = 0
                val maxAttempts = 100 // 100 * 3s = 5 minutes
                while (attempts < maxAttempts) {
                    delay(3000)
                    attempts++

                    val doc = linkDoc.get().await()
                    val status = doc.getString("status")

                    if (status == "completed") {
                        val email = doc.getString("email")
                        val password = doc.getString("password")

                        if (!email.isNullOrBlank() && !password.isNullOrBlank()) {
                            Log.d(TAG, "Device link completed! Logging in as $email")
                            _state.update { it.copy(linkStatus = "מתחבר...") }

                            // Auto-login with the credentials from the web
                            val result = repository.login(email, password)
                            if (result.isSuccess) {
                                syncManager.startRealtimeSync()
                                _state.update { it.copy(
                                    isLinking = false,
                                    deviceLinkCode = null,
                                    isSuccess = true,
                                    linkStatus = null
                                )}
                            } else {
                                _state.update { it.copy(
                                    isLinking = false,
                                    error = translateFirebaseError(result.exceptionOrNull()),
                                    linkStatus = null
                                )}
                            }

                            // Cleanup the link document
                            runCatching { linkDoc.delete().await() }
                            return@launch
                        }
                    }
                }

                // Timeout
                _state.update { it.copy(isLinking = false, deviceLinkCode = null, linkStatus = "פג תוקף הקוד") }
                runCatching { linkDoc.delete().await() }

            } catch (e: Exception) {
                Log.e(TAG, "Device linking failed", e)
                _state.update { it.copy(isLinking = false, deviceLinkCode = null, error = "שגיאה בחיבור: ${e.message}") }
            }
        }
    }

    private fun generateLinkCode(): String {
        val chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789" // No I/O/0/1 to avoid confusion
        return (1..6).map { chars.random() }.joinToString("")
    }

    private fun translateFirebaseError(e: Throwable?): String {
        val msg = e?.message ?: return "שגיאה לא ידועה"
        return when {
            "already in use" in msg -> "המייל הזה כבר רשום. נסה להתחבר במקום להירשם"
            "no user record" in msg || "user-not-found" in msg -> "המשתמש לא נמצא. בדוק את המייל או הירשם"
            "password is invalid" in msg || "wrong-password" in msg -> "סיסמה שגויה"
            "badly formatted" in msg || "invalid-email" in msg -> "כתובת המייל אינה תקינה"
            "network" in msg.lowercase() -> "שגיאת רשת. בדוק את החיבור לאינטרנט"
            "too-many-requests" in msg || "too many" in msg.lowercase() -> "יותר מדי ניסיונות. נסה שוב מאוחר יותר"
            "weak-password" in msg -> "הסיסמה חלשה מדי. השתמש לפחות ב-6 תווים"
            else -> msg
        }
    }
}
