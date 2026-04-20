package com.luminastreams.tv.data.local

import android.content.Context
import coil.annotation.ExperimentalCoilApi
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

// ── CacheManager ───────────────────────────────────────────────────────────
@Suppress("unused")
class CacheManager(private val context: Context) {

    @OptIn(ExperimentalCoilApi::class)
    @Suppress("unused")
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