package com.luminastreams.tv.data.local

import android.content.Context
import org.json.JSONObject

// ══════════════════════════════════════════════════════════════════
//  Watch Progress Model
// ══════════════════════════════════════════════════════════════════

/**
 * Snapshot of how far through a piece of content the user has watched.
 *
 * @param positionMs   Playback position in milliseconds.
 * @param durationMs   Total duration in milliseconds.
 * @param lastWatchedAt Epoch ms of the most recent watch event.
 */
data class WatchProgress(
    val positionMs: Long,
    val durationMs: Long,
    val lastWatchedAt: Long = System.currentTimeMillis()
) {
    /** Completion fraction in [0.0, 1.0]. */
    val fraction: Float
        get() = if (durationMs > 0L)
            (positionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        else 0f

    /** True when ≥ 90 % of the content has been watched. */
    val isFinished: Boolean get() = fraction >= 0.90f

    /** True when ≥ 2 % watched — filters out accidental one-second starts. */
    val hasStarted: Boolean get() = fraction >= 0.02f
}

// ══════════════════════════════════════════════════════════════════
//  WatchProgressManager
// ══════════════════════════════════════════════════════════════════

/**
 * Lightweight SharedPreferences-backed store for movie and episode watch progress.
 *
 * Key scheme:
 *   Movie   → "movie_{imdbId}"
 *   Episode → "ep_{imdbId}_s{season}e{episode}"
 *
 * Place this file in: data/local/WatchProgressManager.kt
 */
class WatchProgressManager(context: Context) {

    private val prefs = context.getSharedPreferences("lumina_watch_v2", Context.MODE_PRIVATE)

    // ── Key builders ──────────────────────────────────────────────────────────

    fun movieKey(imdbId: String): String = "movie_$imdbId"

    fun episodeKey(imdbId: String, season: Int, episode: Int): String =
        "ep_${imdbId}_s${season}e${episode}"

    // ── Write ─────────────────────────────────────────────────────────────────

    fun save(key: String, positionMs: Long, durationMs: Long, tmdbId: String? = null): String? {
        if (key.isBlank() || durationMs <= 0L) return null
        val json = JSONObject().apply {
            put("p", positionMs)
            put("d", durationMs)
            put("t", System.currentTimeMillis())
            if (!tmdbId.isNullOrBlank()) put("tmdb", tmdbId)
        }.toString()
        prefs.edit().putString(key, json).apply()
        return json
    }

    fun deleteProgress(key: String) = prefs.edit().remove(key).apply()

    // ── Read ──────────────────────────────────────────────────────────────────

    fun get(key: String): WatchProgress? {
        val raw = prefs.getString(key, null) ?: return null
        return runCatching {
            val j = JSONObject(raw)
            WatchProgress(j.getLong("p"), j.getLong("d"), j.getLong("t"))
        }.getOrNull()
    }

    fun getMovie(imdbId: String): WatchProgress? = get(movieKey(imdbId))

    fun getEpisode(imdbId: String, season: Int, episode: Int): WatchProgress? =
        get(episodeKey(imdbId, season, episode))

    /**
     * Returns (season, episode, progress) for the most recently watched episode
     * of the given series, or null if nothing has been watched yet.
     */
    fun getLatestEpisodeProgress(imdbId: String): Triple<Int, Int, WatchProgress>? {
        val prefix = "ep_${imdbId}_s"
        return prefs.all.keys
            .filter { it.startsWith(prefix) }
            .mapNotNull { key ->
                val rest = key.removePrefix(prefix)      // e.g. "1e3"
                val eIdx = rest.indexOf('e')
                if (eIdx < 1) return@mapNotNull null
                val s = rest.substring(0, eIdx).toIntOrNull() ?: return@mapNotNull null
                val e = rest.substring(eIdx + 1).toIntOrNull() ?: return@mapNotNull null
                val prog = get(key) ?: return@mapNotNull null
                Triple(s, e, prog)
            }
            .maxByOrNull { (_, _, p) -> p.lastWatchedAt }
    }

    /**
     * Returns the set of episode numbers (1-based) that are fully watched (≥ 90 %)
     * for the given series and season.
     */
    fun finishedEpisodesInSeason(imdbId: String, season: Int): Set<Int> {
        val prefix = "ep_${imdbId}_s${season}e"
        return prefs.all.keys
            .filter { it.startsWith(prefix) }
            .mapNotNull { key ->
                val ep = key.removePrefix(prefix).toIntOrNull() ?: return@mapNotNull null
                if (get(key)?.isFinished == true) ep else null
            }
            .toSet()
    }
    /**
     * Returns all stored progress as a map of key to JSON string.
     */
    fun getAllProgress(): Map<String, String> {
        return prefs.all.mapValues { it.value.toString() }
    }

    /**
     * Merges cloud progress into local preferences.
     */
    fun mergeCloudProgress(cloudMap: Map<String, String>, context: Context? = null) {
        val editor = prefs.edit()
        val bridgeEditor = context?.getSharedPreferences("lumina_id_bridge", Context.MODE_PRIVATE)?.edit()
        cloudMap.forEach { (key, value) ->
            // Skip empty values — these represent deleted progress from the cloud
            if (value.isBlank()) {
                editor.remove(key) // Mirror the deletion locally
                return@forEach
            }
            // Only update if cloud progress is newer or we don't have it locally
            val local = get(key)
            val cloudJson = runCatching { JSONObject(value) }.getOrNull() ?: return@forEach
            val cloud = runCatching {
                WatchProgress(cloudJson.getLong("p"), cloudJson.getLong("d"), cloudJson.getLong("t"))
            }.getOrNull() ?: return@forEach // Skip malformed JSON — don't overwrite valid local data

            if (local == null || cloud.lastWatchedAt > local.lastWatchedAt) {
                editor.putString(key, value)
            }

            // Reconstruct the IMDB→TMDB bridge from the embedded "tmdb" field
            val tmdbId = cloudJson.optString("tmdb", "")
            if (tmdbId.isNotBlank() && bridgeEditor != null) {
                val imdbId = when {
                    key.startsWith("ep_") -> key.removePrefix("ep_").substringBefore("_s")
                    key.startsWith("movie_") -> key.removePrefix("movie_")
                    else -> null
                }
                if (imdbId != null) bridgeEditor.putString(imdbId, tmdbId)
            }
        }
        editor.apply()
        bridgeEditor?.apply()
    }
}
