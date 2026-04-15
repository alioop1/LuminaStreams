package com.luminastreams.tv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class SubtitleScraper {

    companion object {
        // FIX: was "https://api.subdl.com/api/v1/" which produced a malformed URL
        // "https://api.subdl.com/api/v1/?imdb_id=..." — missing the /subtitles endpoint.
        private const val SEARCH_BASE = "https://api.subdl.com/api/v1/subtitles"
        private const val DL_BASE     = "https://dl.subdl.com"
        private const val USER_AGENT  = "Mozilla/5.0 (Android TV; Android 12) LuminaStreams/1.0"
        private const val TIMEOUT_MS  = 12_000
    }

    suspend fun fetchSubtitleInMemory(
        imdbId   : String,
        season   : Int?    = null,
        episode  : Int?    = null,
        langCode : String  = "heb"
    ): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val sdLang = when (langCode.lowercase().take(3)) {
                "he", "heb", "iw" -> "HE"
                "ar", "ara"       -> "AR"
                "ru", "rus"       -> "RU"
                "fr", "fre"       -> "FR"
                "de", "ger"       -> "DE"
                "es", "spa"       -> "ES"
                else              -> "EN"
            }

            val cleanId   = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
            // Correct URL: https://api.subdl.com/api/v1/subtitles?imdb_id=...&lang=...
            var searchUrl = "$SEARCH_BASE?imdb_id=$cleanId&lang=$sdLang&subs_per_page=5"
            if (season != null && episode != null) {
                searchUrl += "&season_number=$season&episode_number=$episode"
            }

            val searchConn = openGet(searchUrl)
            if (searchConn.responseCode != 200) {
                return@withContext Result.failure(Exception("subdl search HTTP ${searchConn.responseCode}"))
            }

            val body = searchConn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val json = JSONObject(body)

            if (!json.optBoolean("status", false)) {
                return@withContext Result.failure(Exception("subdl status=false"))
            }

            val subtitlesArr = json.optJSONArray("subtitles")
            if (subtitlesArr == null || subtitlesArr.length() == 0) {
                return@withContext Result.failure(Exception("לא נמצאו כתוביות ב-subdl"))
            }

            var downloadPath: String? = null
            for (i in 0 until subtitlesArr.length()) {
                val link = subtitlesArr.getJSONObject(i).optString("url", "")
                if (link.isNotEmpty()) { downloadPath = link; break }
            }
            if (downloadPath == null) {
                return@withContext Result.failure(Exception("לא נמצא url בתשובת subdl"))
            }

            val dlUrl  = "$DL_BASE$downloadPath"
            val dlConn = openGet(dlUrl)
            if (dlConn.responseCode != 200) {
                return@withContext Result.failure(Exception("subdl download HTTP ${dlConn.responseCode}"))
            }

            ZipInputStream(BufferedInputStream(dlConn.inputStream)).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name.lowercase()
                    if (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".sub")) {
                        return@withContext Result.success(zis.readBytes())
                    }
                    entry = zis.nextEntry
                }
            }

            Result.failure(Exception("קובץ כתוביות תקין לא נמצא בארכיון"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun openGet(urlString: String): HttpURLConnection {
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.requestMethod           = "GET"
        conn.connectTimeout          = TIMEOUT_MS
        conn.readTimeout             = TIMEOUT_MS
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("Accept",     "application/json, */*")
        return conn
    }
}