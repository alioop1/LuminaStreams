package com.luminastreams.tv.presentation.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.data.remote.SubtitleScraper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

data class StremioSubtitle(val url: String, val lang: String, val source: String)

data class PlayerUiState(
    val videoUrl: String? = null,
    val isSubtitlesLoading: Boolean = false,
    val availableSubtitles: List<StremioSubtitle> = emptyList()
)

class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {

    private val subtitleScraper = SubtitleScraper()

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun loadMedia(videoUrl: String, imdbId: String, season: Int? = null, episode: Int? = null) {
        _state.update { it.copy(videoUrl = videoUrl, isSubtitlesLoading = true) }

        if (imdbId.isNotEmpty()) {
            viewModelScope.launch {
                val subs = fetchAllSubtitles(imdbId, season, episode)
                _state.update {
                    it.copy(
                        isSubtitlesLoading = false,
                        availableSubtitles = subs
                    )
                }
            }
        } else {
            _state.update { it.copy(isSubtitlesLoading = false) }
        }
    }

    private suspend fun fetchAllSubtitles(imdbId: String, season: Int? = null, episode: Int? = null): List<StremioSubtitle> = coroutineScope {
        val formattedId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        val type = if (season != null && episode != null) "series" else "movie"
        val queryId = if (type == "series") "$formattedId:$season:$episode" else formattedId

        val wizdomUrl  = "https://4b139a4b7f94-wizdom-stremio-v2.baby-beamup.club/subtitles/$type/$queryId.json"
        val ktuvitUrl  = "https://4b139a4b7f94-ktuvit-stremio.baby-beamup.club/subtitles/$type/$queryId.json"
        val officialUrl = "https://opensubtitles-v3.strem.io/subtitles/$type/$queryId.json"
        val ufoUrl     = "https://opensubtitles.stremio.homes/heb/subtitles/$type/$queryId.json"

        val wizdomDeferred   = async { fetchAndParseStremioJson(wizdomUrl, "Wizdom") }
        val ktuvitDeferred   = async { fetchAndParseStremioJson(ktuvitUrl, "Ktuvit") }
        val officialDeferred = async { fetchAndParseStremioJson(officialUrl, "OpenSubtitles") }
        val ufoDeferred      = async { fetchAndParseStremioJson(ufoUrl, "OS-Community") }
        val scraperDeferred  = async { fetchFromSubtitleScraper(formattedId) }

        val allResults = wizdomDeferred.await() + ktuvitDeferred.await() +
                         officialDeferred.await() + ufoDeferred.await() + scraperDeferred.await()

        val filteredSubs = allResults.filter {
            val lang = it.lang.lowercase()
            lang.contains("heb") || lang == "he" || lang.contains("עברית") || lang.contains("eng") || lang == "en"
        }

        return@coroutineScope filteredSubs.distinctBy { it.url }.sortedBy { sub ->
            var score = 0
            if (!sub.lang.lowercase().contains("he")) score += 100
            if (sub.source == "OpenSubtitles" || sub.source == "OS-Community") score += 10
            score
        }
    }

    private suspend fun fetchAndParseStremioJson(urlString: String, sourceName: String): List<StremioSubtitle> = withContext(Dispatchers.IO) {
        val subtitles = mutableListOf<StremioSubtitle>()
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", "Stremio/4.4.168")
            connection.setRequestProperty("Accept-Language", "he,he-IL,hebrew;q=0.9,en;q=0.8")
            // ✅ Extended timeouts for Xiaomi MIUI DNS blocking & slow network stacks
            connection.connectTimeout = 10_000
            connection.readTimeout    = 10_000

            if (connection.responseCode in 200..299) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.has("subtitles")) {
                    val subsArray = jsonObject.getJSONArray("subtitles")
                    for (i in 0 until subsArray.length()) {
                        val subObj = subsArray.getJSONObject(i)
                        val subUrl = subObj.optString("url", "")

                        var lang = subObj.optString("lang", "Unknown")
                        if ((lang == "Unknown" || lang.isEmpty()) && (sourceName == "Wizdom" || sourceName == "Ktuvit")) {
                            lang = "heb"
                        }

                        if (subUrl.isNotEmpty()) {
                            subtitles.add(StremioSubtitle(subUrl, lang, sourceName))
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return@withContext subtitles
    }

    private suspend fun fetchFromSubtitleScraper(imdbId: String): List<StremioSubtitle> = withContext(Dispatchers.IO) {
        try {
            val result = subtitleScraper.fetchSubtitleInMemory(imdbId, "heb")
            val bytes  = result.getOrNull() ?: return@withContext emptyList()
            val cacheFile = File(app.cacheDir, "subtitle_${imdbId}_heb.srt")
            cacheFile.writeBytes(bytes)
            listOf(StremioSubtitle(url = "file://${cacheFile.absolutePath}", lang = "heb", source = "OS-Scraper"))
        } catch (e: Exception) {
            emptyList()
        }
    }
}
