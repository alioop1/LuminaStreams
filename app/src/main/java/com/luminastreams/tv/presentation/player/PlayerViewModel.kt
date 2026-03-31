package com.luminastreams.tv.presentation.player

import android.app.Application
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.data.remote.KtuvitDirectClient
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

    private val subtitleScraper  = SubtitleScraper()
    private val ktuvitDirect     = KtuvitDirectClient(app)

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

    private suspend fun fetchAllSubtitles(
        imdbId: String,
        season: Int? = null,
        episode: Int? = null
    ): List<StremioSubtitle> = coroutineScope {
        val formattedId = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
        val type        = if (season != null && episode != null) "series" else "movie"
        val queryId     = if (type == "series") "$formattedId:$season:$episode" else formattedId

        val wizdomUrl   = "https://4b139a4b7f94-wizdom-stremio-v2.baby-beamup.club/subtitles/$type/$queryId.json"
        val ktuvitUrl   = "https://4b139a4b7f94-ktuvit-stremio.baby-beamup.club/subtitles/$type/$queryId.json"
        val officialUrl = "https://opensubtitles-v3.strem.io/subtitles/$type/$queryId.json"
        val ufoUrl      = "https://opensubtitles.stremio.homes/heb/subtitles/$type/$queryId.json"

        val wizdomDeferred    = async { fetchAndParseStremioJson(wizdomUrl,   "Wizdom") }
        val ktuvitProxyDeferred = async { fetchAndParseStremioJson(ktuvitUrl, "Ktuvit") }
        val officialDeferred  = async { fetchAndParseStremioJson(officialUrl, "OpenSubtitles") }
        val ufoDeferred       = async { fetchAndParseStremioJson(ufoUrl,      "OS-Community") }

        // מעבירים את העונה והפרק לסקריפר
        val scraperDeferred   = async { fetchFromSubtitleScraper(formattedId, season, episode) }

        val ktuvitDirectDeferred = async {
            fetchFromKtuvitDirect(formattedId, type, season, episode)
        }

        val allResults = wizdomDeferred.await() +
                ktuvitProxyDeferred.await() +
                officialDeferred.await() +
                ufoDeferred.await() +
                scraperDeferred.await() +
                ktuvitDirectDeferred.await()

        val filteredSubs = allResults.filter {
            val lang = it.lang.lowercase()
            lang.contains("heb") || lang == "he" || lang.contains("עברית") ||
                    lang.contains("eng") || lang == "en"
        }

        return@coroutineScope filteredSubs.distinctBy { it.url }.sortedBy { sub ->
            var score = 0
            if (!sub.lang.lowercase().contains("he")) score += 100
            if (sub.source == "OpenSubtitles" || sub.source == "OS-Community") score += 10
            score
        }
    }

    private suspend fun fetchFromKtuvitDirect(
        imdbId: String,
        type: String,
        season: Int?,
        episode: Int?
    ): List<StremioSubtitle> = withContext(Dispatchers.IO) {
        try {
            val prefs   = app.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
            val email   = prefs.getString("ktuvit_email",    "") ?: ""
            val pass    = prefs.getString("ktuvit_password",  "") ?: ""

            if (email.isBlank() || pass.isBlank()) return@withContext emptyList()

            if (ktuvitDirect.cookie == null) {
                val ok = ktuvitDirect.login(email, pass)
                if (!ok) return@withContext emptyList()
            }

            val isSeries = type == "series"
            val ktuvitId = ktuvitDirect.getKtuvitId(
                name     = "",
                isSeries = isSeries,
                imdbId   = imdbId
            ) ?: return@withContext emptyList()

            val subsList = if (isSeries && season != null && episode != null)
                ktuvitDirect.getSubsListEpisode(ktuvitId, season, episode)
            else
                ktuvitDirect.getSubsListMovie(ktuvitId)

            if (subsList.isEmpty()) return@withContext emptyList()

            val best = subsList.first()
            val file = ktuvitDirect.downloadSubtitle(ktuvitId, best.id, best.fileType)
                ?: return@withContext emptyList()

            listOf(StremioSubtitle(
                url    = "file://${file.absolutePath}",
                lang   = "heb",
                source = "Ktuvit-Direct"
            ))
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    private suspend fun fetchAndParseStremioJson(
        urlString: String,
        sourceName: String
    ): List<StremioSubtitle> = withContext(Dispatchers.IO) {
        val subtitles = mutableListOf<StremioSubtitle>()
        try {
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("User-Agent", "Stremio/4.4.168")
            conn.setRequestProperty("Accept-Language", "he,he-IL,hebrew;q=0.9,en;q=0.8")
            conn.connectTimeout = 10_000
            conn.readTimeout    = 10_000
            if (conn.responseCode in 200..299) {
                val response = conn.inputStream.bufferedReader().use { it.readText() }
                val jsonObject = JSONObject(response)
                if (jsonObject.has("subtitles")) {
                    val subsArray = jsonObject.getJSONArray("subtitles")
                    for (i in 0 until subsArray.length()) {
                        val subObj = subsArray.getJSONObject(i)
                        val subUrl = subObj.optString("url", "")
                        var lang   = subObj.optString("lang", "Unknown")
                        if ((lang == "Unknown" || lang.isEmpty()) &&
                            (sourceName == "Wizdom" || sourceName == "Ktuvit")) lang = "heb"
                        if (subUrl.isNotEmpty()) subtitles.add(StremioSubtitle(subUrl, lang, sourceName))
                    }
                }
            }
        } catch (e: Exception) { e.printStackTrace() }
        subtitles
    }

    private suspend fun fetchFromSubtitleScraper(imdbId: String, season: Int?, episode: Int?): List<StremioSubtitle> =
        withContext(Dispatchers.IO) {
            try {
                val result = subtitleScraper.fetchSubtitleInMemory(imdbId, season, episode, "heb")
                val bytes  = result.getOrNull() ?: return@withContext emptyList()
                val suffix = if (season != null && episode != null) "s${season}e${episode}" else "movie"
                val file   = File(app.cacheDir, "subtitle_${imdbId}_${suffix}_heb.srt")
                file.writeBytes(bytes)
                listOf(StremioSubtitle("file://${file.absolutePath}", "heb", "OS-Scraper"))
            } catch (e: Exception) { emptyList() }
        }
}