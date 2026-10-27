package com.luminastreams.tv.presentation.auth

import androidx.compose.runtime.Immutable

@Immutable
data class AuthState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val mode: AuthMode = AuthMode.LOGIN,
    val isSuccess: Boolean = false,
    val loggedInEmail: String? = null,
    // Device linking via QR code
    val deviceLinkCode: String? = null,
    val isLinking: Boolean = false,
    val linkStatus: String? = null
)

enum class AuthMode { LOGIN, REGISTER }

sealed class AuthEvent {
    data class Login(val email: String, val pass: String) : AuthEvent()
    data class Register(val email: String, val pass: String, val confirmPass: String) : AuthEvent()
    object ToggleMode : AuthEvent()
    object ClearError : AuthEvent()
    object Logout : AuthEvent()
    object StartDeviceLink : AuthEvent()
}
