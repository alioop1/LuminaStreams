package com.luminastreams.tv.domain.repository

import kotlinx.coroutines.flow.Flow

interface UserSyncRepository {
    suspend fun syncFavorites(userId: String, movieIds: List<String>): Result<Unit>
    suspend fun getFavorites(userId: String): Result<List<String>>

    suspend fun syncProgress(userId: String, progressMap: Map<String, String>): Result<Unit>
    suspend fun getProgress(userId: String): Result<Map<String, String>>
    
    fun observeUserData(userId: String): Flow<Map<String, Any>>
}
