package com.luminastreams.tv.data.repository

import com.luminastreams.tv.data.local.dao.UserDao
import com.luminastreams.tv.data.local.entities.UserEntity
import com.luminastreams.tv.domain.repository.AuthRepository
import com.luminastreams.tv.domain.repository.LuminaUser
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.security.MessageDigest

class RoomAuthRepository(private val userDao: UserDao) : AuthRepository {
    
    private val _currentUser = MutableStateFlow<LuminaUser?>(null)
    override val currentUser: Flow<LuminaUser?> = _currentUser.asStateFlow()

    private fun hashPassword(password: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(password.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    override suspend fun login(email: String, pass: String): Result<LuminaUser> {
        val user = userDao.getUserByEmail(email)
        return if (user != null && user.passwordHash == hashPassword(pass)) {
            val luminaUser = LuminaUser(user.email, user.email, user.name)
            _currentUser.value = luminaUser
            Result.success(luminaUser)
        } else {
            Result.failure(Exception("אימייל או סיסמה שגויים"))
        }
    }

    override suspend fun register(email: String, pass: String): Result<LuminaUser> {
        val existing = userDao.getUserByEmail(email)
        if (existing != null) return Result.failure(Exception("משתמש כבר קיים"))
        
        val newEntity = UserEntity(email, hashPassword(pass))
        userDao.insertUser(newEntity)
        
        val luminaUser = LuminaUser(email, email)
        _currentUser.value = luminaUser
        return Result.success(luminaUser)
    }

    override suspend fun logout() {
        _currentUser.value = null
    }
}
