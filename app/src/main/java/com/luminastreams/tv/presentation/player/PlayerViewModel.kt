package com.luminastreams.tv.presentation.player

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import java.net.HttpURLConnection
import java.net.URL

// Moved the data model out of the UI and into the business logic layer
data class StremioSubtitle(val url: String, val lang: String, val source: String)

data class PlayerUiState(
    val videoUrl: String? = null,
    val isSubtitlesLoading: Boolean = false,
    val availableSubtitles: List<StremioSubtitle> = emptyList()
)

class PlayerViewModel : ViewModel() {

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    fun loadMedia(videoUrl: String, imdbId: String) {
        _state.update { it.copy(videoUrl = videoUrl, isSubtitlesLoading = true) }

        if (imdbId.isNotEmpty()) {
            // Launch the heavy network call in the ViewModelScope so it survives orientation changes
            viewModelScope.launch {
                val subs = fetchStremioSubtitles(imdbId)
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

    private suspend fun fetchStremioSubtitles(imdbId: String): List<StremioSubtitle> = coroutineScope {
        val openSubUrl = "https://opensubtitles-v3.strem.io/subtitles/movie/$imdbId.json"

        // This is safe and won't block the UI because we shift to Dispatchers.IO inside the parse function
        val openSubDeferred = async { fetchAndParseStremioJson(openSubUrl, "OpenSubtitles") }
        val results = openSubDeferred.await()

        // Sort Hebrew to the top of the list automatically
        return@coroutineScope results.sortedBy { if (it.lang.contains("heb", true)) 0 else 1 }
    }

    private suspend fun fetchAndParseStremioJson(urlString: String, sourceName: String): List<StremioSubtitle> = withContext(Dispatchers.IO) {
        val subtitles = mutableListOf<StremioSubtitle>()
        try {
            val url = URL(urlString)
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            // Strict 2-second timeout so a dead subtitle server never freezes your app
            connection.connectTimeout = 2000
            connection.readTimeout = 2000

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
}