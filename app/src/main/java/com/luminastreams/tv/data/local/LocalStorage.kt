package com.luminastreams.tv.data.local

import android.content.Context
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import coil.imageLoader
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

// ── AppDatabase ─────────────────────────────────────────────────────────────
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

// ── WatchlistManager ────────────────────────────────────────────────────────
class WatchlistManager(context: Context) {
    private val prefs = context.getSharedPreferences("lumina_watchlist", Context.MODE_PRIVATE)

    fun toggleWatchlist(movie: Movie): Boolean {
        if (prefs.contains(movie.id)) {
            prefs.edit().remove(movie.id).apply()
            return false
        } else {
            val json = JSONObject().apply {
                put("id", movie.id)
                put("title", movie.title)
                put("posterUrl", movie.posterUrl)
                put("backdropUrl", movie.backdropUrl)
                put("rating", movie.rating.toDouble())
                put("mediaType", movie.mediaType)
                put("overview", movie.overview)
                put("year", movie.year)
                put("genre", movie.genre)
            }
            prefs.edit().putString(movie.id, json.toString()).apply()
            return true
        }
    }

    fun isInWatchlist(id: String): Boolean = prefs.contains(id)

    fun getWatchlist(): List<Movie> {
        val list = mutableListOf<Movie>()
        val allEntries: Map<String, *> = prefs.all // תיקון בעיית הקומפילציה
        for ((_, value) in allEntries) {
            try {
                if (value is String) {
                    val j = JSONObject(value)
                    list.add(
                        Movie(
                            id = j.optString("id"),
                            title = j.optString("title"),
                            posterUrl = j.optString("posterUrl"),
                            backdropUrl = j.optString("backdropUrl"),
                            rating = j.optDouble("rating", 0.0).toFloat(),
                            mediaType = j.optString("mediaType"),
                            overview = j.optString("overview", ""),
                            year = j.optInt("year", 0),
                            genre = j.optString("genre", "")
                        )
                    )
                }
            } catch (e: Exception) {}
        }
        return list.reversed()
    }
}