package com.luminastreams.tv.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ── AppDatabase ─────────────────────────────────────────────────────────────
// TODO: Replace with Room implementation when added to Gradle
interface AppDatabase {
    suspend fun saveWatchProgress(movieId: String, progressMillis: Long)
    suspend fun getWatchProgress(movieId: String): Long
}

// ── CacheManager ───────────────────────────────────────────────────────────
class CacheManager(private val context: Context) {

    suspend fun clearAllCaches(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()
            deleteDir(context.cacheDir)
            context.externalCacheDir?.let { deleteDir(it) }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            dir.list()?.forEach { if (!deleteDir(File(dir, it))) return false }
        }
        return dir?.delete() ?: false
    }
}

// ── EncryptedStorage ─────────────────────────────────────────────────────────
class EncryptedStorage(context: Context) {
    private val masterKey = MasterKey.Builder(context)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()

    private val sharedPreferences = EncryptedSharedPreferences.create(
        context,
        "lumina_secure_prefs",
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )

    fun saveToken(token: String) = sharedPreferences.edit().putString("RD_TOKEN", token).apply()
    fun getToken(): String? = sharedPreferences.getString("RD_TOKEN", null)
    fun clear() = sharedPreferences.edit().clear().apply()
}
