package com.luminastreams.tv.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.luminastreams.tv.domain.repository.AuthRepository
import com.luminastreams.tv.domain.repository.LuminaUser
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await

class FirebaseAuthRepository : AuthRepository {
    private val auth = FirebaseAuth.getInstance().apply {
        // Fix for Android TV reCAPTCHA/AppVerification CONFIGURATION_NOT_FOUND error
        // This disables the hidden reCAPTCHA verification step during auth flows
        firebaseAuthSettings.setAppVerificationDisabledForTesting(true)
    }

    override val currentUser: Flow<LuminaUser?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser?.let {
                LuminaUser(it.uid, it.email ?: "")
            }
            trySend(user)
        }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun login(email: String, pass: String): Result<LuminaUser> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user?.let { LuminaUser(it.uid, it.email ?: "") }
            if (user != null) Result.success(user) else Result.failure(Exception("Login failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun register(email: String, pass: String): Result<LuminaUser> {
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user?.let { LuminaUser(it.uid, it.email ?: "") }
            if (user != null) Result.success(user) else Result.failure(Exception("Registration failed"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun logout() {
        auth.signOut()
    }
}