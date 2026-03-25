package com.luminastreams.tv.presentation.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

@Immutable
data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isVoiceListening: Boolean = false,
    val containsHebrew: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val trendingSearches: List<SearchResult> = emptyList(),
    val autocompleteSuggestions: List<String> = emptyList(),
    val suggestedCorrection: String? = null,
    val dynamicThemeColor: Color? = null,
    val filters: List<String> = listOf("הכל", "סרטים", "סדרות"),
    val selectedFilter: String = "הכל",
    val exactMatch: SearchResult? = null,
    val focusedItemUrl: String? = null
)

sealed interface SearchEvent {
    data class ShowError(val message: String) : SearchEvent
    data class NavigateToDetails(val id: String) : SearchEvent
    object TriggerHapticFeedback : SearchEvent
}

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class SelectFilter(val filter: String) : SearchIntent
    data class SetFocusedBackground(val url: String?) : SearchIntent
    data class SetVoiceListeningState(val isListening: Boolean) : SearchIntent
    object ClearHistory : SearchIntent
    data class RemoveHistoryItem(val item: String) : SearchIntent
}

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queryFlow = MutableStateFlow("")
    private var allFetchedResults: List<SearchResult> = emptyList()
    private val historyPrefs   = application.getSharedPreferences("lumina_search_history", Context.MODE_PRIVATE)
    private val settingsPrefs  = application.getSharedPreferences("lumina_settings",        Context.MODE_PRIVATE)

    private val popularSearchTerms = listOf(
        "Avatar", "Avengers", "Batman", "Spider-Man", "Superman",
        "Matrix", "Inception", "Interstellar", "Joker", "Star Wars",
        "Harry Potter", "Lord of the Rings", "Deadpool", "X-Men"
    )

    init {
        loadHistory()
        observeQuery()
        fetchTrendingPosters()
    }

    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(400)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collectLatest { validQuery -> performNetworkSearch(validQuery) }
        }
    }

    private fun fetchTrendingPosters() {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val url = URL("https://v3-cinemeta.strem.io/catalog/movie/top.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 3000
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val metas    = JSONObject(response).optJSONArray("metas") ?: return@launch
                    val trending = mutableListOf<SearchResult>()
                    for (i in 0 until minOf(20, metas.length())) {
                        val meta = metas.getJSONObject(i)
                        trending.add(SearchResult(
                            id          = meta.optString("id", ""),
                            title       = meta.optString("name", "Unknown"),
                            posterUrl   = meta.optString("poster", "").replace("http://", "https://"),
                            backdropUrl = meta.optString("background", "").replace("http://", "https://"),
                            type        = MediaType.MOVIE,
                            rating      = meta.optString("imdbRating", "0").toFloatOrNull() ?: 0f,
                            releaseYear = meta.optString("releaseInfo", "")
                        ))
                    }
                    _state.update { it.copy(trendingSearches = trending) }
                }
            } catch (e: Exception) { e.printStackTrace() }
        }
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery          -> handleQueryUpdate(intent.query)
            is SearchIntent.SelectFilter         -> applyFilter(intent.filter)
            is SearchIntent.SetFocusedBackground -> _state.update { it.copy(focusedItemUrl = intent.url) }
            is SearchIntent.ClearHistory         -> clearHistory()
            is SearchIntent.RemoveHistoryItem    -> removeHistoryItem(intent.item)
            is SearchIntent.SetVoiceListeningState -> _state.update { it.copy(isVoiceListening = intent.isListening) }
        }
    }

    private fun handleQueryUpdate(newQuery: String) {
        val isHebrew = newQuery.any { it in '\u0590'..'\u05FF' }
        val suggestions = if (newQuery.length >= 2 && !isHebrew) {
            val historyMatches  = _state.value.searchHistory.filter { it.contains(newQuery, ignoreCase = true) }
            val popularMatches  = popularSearchTerms.filter { it.contains(newQuery, ignoreCase = true) }
            val trendingMatches = _state.value.trendingSearches.map { it.title }.filter { it.contains(newQuery, ignoreCase = true) }
            (historyMatches + popularMatches + trendingMatches).distinct().take(6)
        } else emptyList()

        _state.update {
            it.copy(
                query                  = newQuery,
                exactMatch             = null,
                containsHebrew         = isHebrew,
                autocompleteSuggestions = suggestions
            )
        }

        if (newQuery.isBlank() || isHebrew) {
            allFetchedResults = emptyList()
            _state.update { it.copy(results = emptyList(), isSearching = false, focusedItemUrl = null) }
            return
        }
        _state.update { it.copy(isSearching = true) }
        queryFlow.value = newQuery
    }

    private suspend fun performNetworkSearch(query: String) = coroutineScope {
        try {
            // ✅ REAL: only persist to history if save_history is enabled
            val saveHistory = settingsPrefs.getBoolean("save_history", true)
            if (saveHistory) saveToHistory(query)

            val moviesDeferred = async(Dispatchers.IO) { fetchFromCinemeta(query, "movie",  MediaType.MOVIE) }
            val seriesDeferred = async(Dispatchers.IO) { fetchFromCinemeta(query, "series", MediaType.TV_SHOW) }

            val combined = (moviesDeferred.await() + seriesDeferred.await())
                .filter { it.posterUrl.isNotBlank() }
                .sortedByDescending { it.title.equals(query, ignoreCase = true) }

            allFetchedResults = combined
            val exactMatch = combined.firstOrNull { it.title.equals(query, ignoreCase = true) }
            _state.update { it.copy(isSearching = false, exactMatch = exactMatch) }
            applyFilter(_state.value.selectedFilter)
        } catch (e: Exception) {
            _state.update { it.copy(isSearching = false, results = emptyList()) }
        }
    }

    private fun applyFilter(filter: String) {
        val filteredList = when (filter) {
            "סרטים" -> allFetchedResults.filter { it.type == MediaType.MOVIE }
            "סדרות" -> allFetchedResults.filter { it.type == MediaType.TV_SHOW }
            else    -> allFetchedResults
        }
        _state.update { it.copy(selectedFilter = filter, results = filteredList) }
    }

    private fun fetchFromCinemeta(query: String, catalogType: String, mediaType: MediaType): List<SearchResult> {
        val results = mutableListOf<SearchResult>()
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("https://v3-cinemeta.strem.io/catalog/$catalogType/top/search=$encodedQuery.json")
            val connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 3000
            if (connection.responseCode == 200) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val metas    = JSONObject(response).optJSONArray("metas") ?: return emptyList()
                for (i in 0 until metas.length()) {
                    val meta = metas.getJSONObject(i)
                    results.add(SearchResult(
                        id          = meta.optString("id", ""),
                        title       = meta.optString("name", "Unknown"),
                        posterUrl   = meta.optString("poster", "").replace("http://", "https://"),
                        backdropUrl = meta.optString("background", "").replace("http://", "https://"),
                        type        = mediaType,
                        rating      = meta.optString("imdbRating", "0").toFloatOrNull() ?: 0f,
                        releaseYear = meta.optString("releaseInfo", "")
                    ))
                }
            }
        } catch (e: Exception) {}
        return results
    }

    private fun saveToHistory(query: String) {
        val newHistory = (listOf(query) + _state.value.searchHistory).distinct().take(8)
        historyPrefs.edit().putString("history_items", newHistory.joinToString("||")).apply()
        _state.update { it.copy(searchHistory = newHistory) }
    }

    private fun loadHistory() {
        val historyString = historyPrefs.getString("history_items", "") ?: ""
        _state.update { it.copy(searchHistory = historyString.split("||").filter { it.isNotBlank() }) }
    }

    private fun clearHistory() {
        historyPrefs.edit().remove("history_items").apply()
        _state.update { it.copy(searchHistory = emptyList()) }
    }

    private fun removeHistoryItem(item: String) {
        val newHistory = _state.value.searchHistory.filter { it != item }
        historyPrefs.edit().putString("history_items", newHistory.joinToString("||")).apply()
        _state.update { it.copy(searchHistory = newHistory) }
    }
}
