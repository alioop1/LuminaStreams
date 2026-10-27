package com.luminastreams.tv.data.remote

import com.luminastreams.tv.core.TrustedHttpClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONObject
import java.io.BufferedInputStream
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit
import java.util.zip.ZipInputStream

class SubtitleScraper {

    companion object {
        // FIX: was "https://api.subdl.com/api/v1/" which produced a malformed URL
        // "https://api.subdl.com/api/v1/?imdb_id=..." — missing the /subtitles endpoint.
        private const val SEARCH_BASE = "https://api.subdl.com/api/v1/subtitles"
        private const val DL_BASE     = "https://dl.subdl.com"
        private const val USER_AGENT  = "Mozilla/5.0 (Android TV; Android 12) LuminaStreams/1.0"
        private const val TIMEOUT_MS  = 12_000L
    }

    private val client = TrustedHttpClient.builder()
        .connectTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .readTimeout(TIMEOUT_MS, TimeUnit.MILLISECONDS)
        .followRedirects(true)
        .build()

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
            var searchUrl = "$SEARCH_BASE?imdb_id=$cleanId&lang=$sdLang&subs_per_page=5"
            if (season != null && episode != null) {
                searchUrl += "&season_number=$season&episode_number=$episode"
            }

            val searchRequest = Request.Builder()
                .url(searchUrl)
                .header("User-Agent", USER_AGENT)
                .header("Accept", "application/json, */*")
                .build()
            val searchResponse = client.newCall(searchRequest).execute()
            if (!searchResponse.isSuccessful) {
                val code = searchResponse.code
                searchResponse.close()
                return@withContext Result.failure(Exception("subdl search HTTP $code"))
            }

            val body = searchResponse.body?.string() ?: ""
            searchResponse.close()
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

            val dlUrl = "$DL_BASE$downloadPath"
            val dlRequest = Request.Builder()
                .url(dlUrl)
                .header("User-Agent", USER_AGENT)
                .build()
            val dlResponse = client.newCall(dlRequest).execute()
            if (!dlResponse.isSuccessful) {
                val code = dlResponse.code
                dlResponse.close()
                return@withContext Result.failure(Exception("subdl download HTTP $code"))
            }

            val dlBytes = dlResponse.body?.bytes() ?: byteArrayOf()
            dlResponse.close()

            ZipInputStream(BufferedInputStream(ByteArrayInputStream(dlBytes))).use { zis ->
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
}