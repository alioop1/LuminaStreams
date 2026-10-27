package com.luminastreams.tv.domain.repository

import com.luminastreams.tv.presentation.auth.AuthMode
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    val currentUser: Flow<LuminaUser?>
    suspend fun login(email: String, pass: String): Result<LuminaUser>
    suspend fun register(email: String, pass: String): Result<LuminaUser>
    suspend fun logout()
}

data class LuminaUser(
    val id: String,
    val email: String,
    val name: String? = null
)
