package com.luminastreams.tv.data.remote

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipInputStream

class SubtitleScraper {

    companion object {
        private const val BASE_URL = "https://www.opensubtitles.org"
        // Upgraded User-Agent to prevent bot-blocking
        private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    suspend fun fetchSubtitleInMemory(imdbId: String, langCode: String = "heb"): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val searchUrl = "$BASE_URL/he/search/sublanguageid-$langCode/imdbid-${imdbId.removePrefix("tt")}"

            // Added strict headers to bypass security blocks
            val doc = Jsoup.connect(searchUrl)
                .userAgent(USER_AGENT)
                .header("Accept-Language", "en-US,en;q=0.9")
                .timeout(10000)
                .get()

            // Broadened the selector to catch different URL structures
            val downloadLinkElement = doc.select("a[href*=/subtitleserve/sub/]").first()
                ?: return@withContext Result.failure(Exception("לא נמצאו כתוביות תואמות"))

            val downloadPath = downloadLinkElement.attr("href")
            val downloadUrl = "$BASE_URL$downloadPath"

            val connection = URL(downloadUrl).openConnection() as HttpURLConnection
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.setRequestProperty("Referer", searchUrl)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            connection.inputStream.use { inputStream ->
                ZipInputStream(inputStream).use { zis ->
                    var entry = zis.nextEntry
                    while (entry != null) {
                        // FIX: Now accepts .srt, .vtt, and .sub files instead of just strictly .srt
                        val name = entry.name.lowercase()
                        if (name.endsWith(".srt") || name.endsWith(".vtt") || name.endsWith(".sub")) {
                            val srtBytes = zis.readBytes()
                            return@withContext Result.success(srtBytes)
                        }
                        entry = zis.nextEntry
                    }
                }
            }
            Result.failure(Exception("קובץ כתוביות תקין לא נמצא בתוך הארכיון"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}