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

    // עדכון הפונקציה loadMedia כדי שתתמוך (אופציונלית) גם בעונה ופרק
    fun loadMedia(videoUrl: String, imdbId: String, season: Int? = null, episode: Int? = null) {
        _state.update { it.copy(videoUrl = videoUrl, isSubtitlesLoading = true) }

        if (imdbId.isNotEmpty()) {
            viewModelScope.launch {
                val subs = fetchStremioSubtitles(imdbId, season, episode)
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

    private suspend fun fetchStremioSubtitles(imdbId: String, season: Int? = null, episode: Int? = null): List<StremioSubtitle> = coroutineScope {
        val formattedId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        val type = if (season != null && episode != null) "series" else "movie"
        val queryId = if (type == "series") "$formattedId:$season:$episode" else formattedId

        // מביא גם מהשרת הרשמי וגם משרת הקהילה (כדי שיהיו לך הרבה אפשרויות להורדה)
        val officialUrl = "https://opensubtitles-v3.strem.io/subtitles/$type/$queryId.json"
        val ufoUrl = "https://opensubtitles.stremio.homes/heb/subtitles/$type/$queryId.json"

        val officialDeferred = async { fetchAndParseStremioJson(officialUrl, "OpenSubtitles") }
        val ufoDeferred = async { fetchAndParseStremioJson(ufoUrl, "OS-Community") }

        val allResults = officialDeferred.await() + ufoDeferred.await()

        // משאירים אך ורק עברית ואנגלית (כל שאר השפות הזרות נמחקות)
        val filteredSubs = allResults.filter {
            val lang = it.lang.lowercase()
            lang.contains("heb") || lang == "he" || lang.contains("עברית") || lang.contains("eng") || lang == "en"
        }

        return@coroutineScope filteredSubs.distinctBy { it.url }
    }

    private suspend fun fetchAndParseStremioJson(urlString: String, sourceName: String): List<StremioSubtitle> = withContext(Dispatchers.IO) {
        val subtitles = mutableListOf<StremioSubtitle>()
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"

            // תוספת קריטית - בלעדיה סטרמיו מתעלם מעברית ומחזיר שפות אחרות
            connection.setRequestProperty("User-Agent", "Mozilla/5.0")
            connection.setRequestProperty("Accept-Language", "he,he-IL,hebrew;q=0.9,en;q=0.8")

            connection.connectTimeout = 4000
            connection.readTimeout = 4000

            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)

                if (jsonObject.has("subtitles")) {
                    val subsArray = jsonObject.getJSONArray("subtitles")
                    for (i in 0 until subsArray.length()) {
                        val subObj = subsArray.getJSONObject(i)
                        val subUrl = subObj.optString("url", "")
                        val lang = subObj.optString("lang", "Unknown")

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

    // ── מקור 2: SubtitleScraper (REST API) ───────────────────────────────────
    private suspend fun fetchFromSubtitleScraper(imdbId: String): List<StremioSubtitle> =
        withContext(Dispatchers.IO) {
            try {
                val result = subtitleScraper.fetchSubtitleInMemory(imdbId, "heb")
                val bytes  = result.getOrNull() ?: return@withContext emptyList()

                val cacheFile = File(app.cacheDir, "subtitle_${imdbId}_heb.srt")
                cacheFile.writeBytes(bytes)

                listOf(
                    StremioSubtitle(
                        url    = "file://${cacheFile.absolutePath}",
                        lang   = "heb",
                        source = "OpenSubtitles.org"
                    )
                )
            } catch (e: Exception) {
                e.printStackTrace()
                emptyList()
            }
        }
}