package com.luminastreams.tv.data.local

import android.content.Context
import coil.imageLoader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheManager(private val context: Context) {

    suspend fun clearAllCaches(): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // 1. מחיקת מטמון התמונות של Coil (זיכרון ודיסק)
            context.imageLoader.memoryCache?.clear()
            context.imageLoader.diskCache?.clear()

            // 2. מחיקת מטמון המערכת של האפליקציה
            deleteDir(context.cacheDir)

            // 3. מחיקת מטמון קוד מרומפל (WebViews/ExoPlayer buffers)
            context.externalCacheDir?.let { deleteDir(it) }

            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun deleteDir(dir: File?): Boolean {
        if (dir != null && dir.isDirectory) {
            val children = dir.list()
            if (children != null) {
                for (i in children.indices) {
                    val success = deleteDir(File(dir, children[i]))
                    if (!success) return false
                }
            }
        }
        return dir?.delete() ?: false
    }
}