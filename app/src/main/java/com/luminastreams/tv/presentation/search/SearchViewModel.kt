package com.luminastreams.tv.presentation.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.data.remote.FuzerEngine
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

// ─── Source selector ──────────────────────────────────────────────
enum class SearchSource { ALL, MOVIES, SERIES, FUZER }

@Immutable
data class SearchState(
    val query: String = "",
    val source: SearchSource = SearchSource.ALL,

    // TMDB
    val tmdbResults: List<SearchResult> = emptyList(),
    val isTmdbLoading: Boolean = false,

    // Fuzer
    val fuzerResults: List<SearchResult> = emptyList(),
    val isFuzerLoading: Boolean = false,
    val fuzerError: String? = null,

    // Discovery (no query)
    val discoveryResults: List<SearchResult> = emptyList(),
    val isDiscoveryLoading: Boolean = false,

    // Shared
    val searchHistory: List<String> = emptyList(),
    val autocompleteSuggestions: List<String> = emptyList(),
    val focusedItemUrl: String? = null,
    val dynamicThemeColor: Color? = null
) {
    // What the grid shows right now
    val activeResults: List<SearchResult> get() = when {
        source == SearchSource.FUZER              -> fuzerResults
        query.isBlank()                           -> discoveryResults
        source == SearchSource.MOVIES             -> tmdbResults.filter { it.type == MediaType.MOVIE }
        source == SearchSource.SERIES             -> tmdbResults.filter { it.type == MediaType.TV_SHOW }
        else                                      -> tmdbResults
    }
    val isLoading: Boolean get() = when (source) {
        SearchSource.FUZER -> isFuzerLoading
        else               -> if (query.isBlank()) isDiscoveryLoading else isTmdbLoading
    }
}

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class SelectSource(val source: SearchSource) : SearchIntent
    data class SetFocusedBackground(val url: String?) : SearchIntent
    object ClearHistory : SearchIntent
    data class RemoveHistoryItem(val item: String) : SearchIntent
}

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queryFlow    = MutableStateFlow("")
    private val historyPrefs = application.getSharedPreferences("lumina_search_history", Context.MODE_PRIVATE)
    private val settingsPrefs= application.getSharedPreferences("lumina_settings",        Context.MODE_PRIVATE)

    private val fuzerEngine by lazy { FuzerEngine() }

    private val popularTerms = listOf(
        "Avatar", "Avengers", "Batman", "Spider-Man", "Superman",
        "Matrix", "Inception", "Interstellar", "Joker", "Star Wars",
        "Harry Potter", "Lord of the Rings", "Deadpool", "Breaking Bad"
    )

    init {
        loadHistory()
        observeQuery()
        loadDiscovery()
    }

    // ─── Intent handler ───────────────────────────────────────────
    fun onIntent(intent: SearchIntent) = when (intent) {
        is SearchIntent.UpdateQuery         -> handleQueryUpdate(intent.query)
        is SearchIntent.SelectSource        -> handleSourceChange(intent.source)
        is SearchIntent.SetFocusedBackground-> _state.update { it.copy(focusedItemUrl = intent.url) }
        is SearchIntent.ClearHistory        -> clearHistory()
        is SearchIntent.RemoveHistoryItem   -> removeHistoryItem(intent.item)
    }

    // ─── Source change ────────────────────────────────────────────
    private fun handleSourceChange(src: SearchSource) {
        _state.update { it.copy(source = src) }
        // אם עוברים ל-Fuzer והחיפוש כבר פעיל, תביא תוצאות פיוזר
        if (src == SearchSource.FUZER && _state.value.fuzerResults.isEmpty()) {
            launchFuzerSearch(_state.value.query)
        }
    }

    // ─── Query update ─────────────────────────────────────────────
    private fun handleQueryUpdate(newQuery: String) {
        val suggestions = buildSuggestions(newQuery)
        _state.update { it.copy(query = newQuery, autocompleteSuggestions = suggestions) }

        if (newQuery.isBlank()) {
            _state.update { it.copy(tmdbResults = emptyList(), fuzerResults = emptyList(), fuzerError = null) }
            return
        }
        queryFlow.value = newQuery
    }

    private fun buildSuggestions(q: String): List<String> {
        if (q.length < 2) return emptyList()
        val hist    = _state.value.searchHistory.filter { it.contains(q, ignoreCase = true) }
        val popular = popularTerms.filter { it.contains(q, ignoreCase = true) }
        return (hist + popular).distinct().take(6)
    }

    // ─── Query observe ────────────────────────────────────────────
    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(380)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collectLatest { q ->
                    // שמירת היסטוריה
                    if (settingsPrefs.getBoolean("save_history", true)) saveToHistory(q)
                    // הרצת שני sources במקביל
                    coroutineScope {
                        launch { runTmdbSearch(q) }
                        launch { if (_state.value.source == SearchSource.FUZER) launchFuzerSearch(q) }
                    }
                }
        }
    }

    // ─── TMDB search ──────────────────────────────────────────────
    private suspend fun runTmdbSearch(query: String) {
        _state.update { it.copy(isTmdbLoading = true) }
        try {
            val isHe  = query.any { it in '\u0590'..'\u05FF' }
            val lang  = if (isHe) "he-IL" else "en-US"
            val enc   = URLEncoder.encode(query, "UTF-8")
            val key   = "9ab4a284f0c028007b78925852196b79"
            val base  = "https://image.tmdb.org/t/p"

            val p1 = withContext(Dispatchers.IO) { fetchTmdbPage(enc, lang, key, base, 1) }
            val p2 = withContext(Dispatchers.IO) { fetchTmdbPage(enc, lang, key, base, 2) }
            val combined = (p1 + p2).distinctBy { it.id }

            _state.update { it.copy(tmdbResults = combined, isTmdbLoading = false) }
        } catch (_: Exception) {
            _state.update { it.copy(tmdbResults = emptyList(), isTmdbLoading = false) }
        }
    }

    private fun fetchTmdbPage(enc: String, lang: String, key: String, base: String, page: Int): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        try {
            val con = URL("https://api.themoviedb.org/3/search/multi?api_key=$key&language=$lang&query=$enc&page=$page&include_adult=false")
                .openConnection() as HttpURLConnection
            con.connectTimeout = 6000; con.readTimeout = 9000
            if (con.responseCode != 200) return emptyList()
            val arr = JSONObject(con.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val j  = arr.getJSONObject(i)
                val mt = j.optString("media_type")
                if (mt != "movie" && mt != "tv") continue
                val title = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                            else             j.optString("title").ifBlank { j.optString("original_title") }
                val poster = j.optString("poster_path").let { p ->
                    if (p.isNotBlank() && p != "null") "$base/w342$p" else ""
                }
                out += SearchResult(
                    id          = "${mt}_${j.optInt("id")}",
                    title       = title,
                    posterUrl   = poster,
                    backdropUrl = j.optString("backdrop_path").let { p ->
                        if (p.isNotBlank() && p != "null") "$base/w780$p" else ""
                    },
                    type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                    rating      = j.optDouble("vote_average", 0.0).toFloat(),
                    releaseYear = (if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")).take(4)
                )
            }
        } catch (_: Exception) {}
        return out
    }

    // ─── Fuzer search ─────────────────────────────────────────────
    private fun launchFuzerSearch(query: String) {
        viewModelScope.launch {
            _state.update { it.copy(isFuzerLoading = true, fuzerError = null) }
            try {
                val raw = withContext(Dispatchers.IO) {
                    fuzerEngine.search(query).getOrElse { emptyList() }
                }
                val filtered = if (query.isBlank()) raw else
                    raw.filter { it.title.contains(query, ignoreCase = true) }
                val mapped = filtered.map { m ->
                    SearchResult(
                        id          = m.id,
                        title       = m.title,
                        posterUrl   = m.posterUrl,
                        backdropUrl = m.backdropUrl,
                        type        = if (m.mediaType == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                        rating      = m.rating,
                        releaseYear = if (m.year > 0) m.year.toString() else ""
                    )
                }
                _state.update { it.copy(fuzerResults = mapped, isFuzerLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(
                    fuzerResults   = emptyList(),
                    isFuzerLoading = false,
                    fuzerError     = "Fuzer unavailable: ${e.message?.take(60)}"
                ) }
            }
        }
    }

    // ─── Discovery (initial / no query) ──────────────────────────
    private fun loadDiscovery() {
        viewModelScope.launch {
            _state.update { it.copy(isDiscoveryLoading = true) }
            try {
                val key  = "9ab4a284f0c028007b78925852196b79"
                val base = "https://image.tmdb.org/t/p"
                val p1 = withContext(Dispatchers.IO) { fetchDiscoveryPage(key, base, 1) }
                val p2 = withContext(Dispatchers.IO) { fetchDiscoveryPage(key, base, 2) }
                _state.update { it.copy(discoveryResults = (p1 + p2).distinctBy { r -> r.id }, isDiscoveryLoading = false) }
            } catch (_: Exception) {
                _state.update { it.copy(isDiscoveryLoading = false) }
            }
        }
    }

    private fun fetchDiscoveryPage(key: String, base: String, page: Int): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        for (mt in listOf("movie", "tv")) {
            try {
                val url = "https://api.themoviedb.org/3/discover/$mt?api_key=$key&language=en-US&page=$page&sort_by=popularity.desc"
                val con = URL(url).openConnection() as HttpURLConnection
                con.connectTimeout = 6000; con.readTimeout = 9000
                if (con.responseCode != 200) continue
                val arr = JSONObject(con.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: continue
                for (i in 0 until arr.length()) {
                    val j     = arr.getJSONObject(i)
                    val title = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                                else             j.optString("title").ifBlank { j.optString("original_title") }
                    val poster = j.optString("poster_path").let { p ->
                        if (p.isNotBlank() && p != "null") "$base/w342$p" else ""
                    }
                    if (poster.isBlank()) continue
                    out += SearchResult(
                        id          = "${mt}_${j.optInt("id")}",
                        title       = title,
                        posterUrl   = poster,
                        backdropUrl = j.optString("backdrop_path").let { p ->
                            if (p.isNotBlank() && p != "null") "$base/w780$p" else ""
                        },
                        type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                        rating      = j.optDouble("vote_average", 0.0).toFloat(),
                        releaseYear = (if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")).take(4)
                    )
                }
            } catch (_: Exception) {}
        }
        return out
    }

    // ─── History ─────────────────────────────────────────────────
    private fun saveToHistory(q: String) {
        val newH = (listOf(q) + _state.value.searchHistory).distinct().take(8)
        historyPrefs.edit().putString("history_items", newH.joinToString("||")).apply()
        _state.update { it.copy(searchHistory = newH) }
    }
    private fun loadHistory() {
        val s = historyPrefs.getString("history_items", "") ?: ""
        _state.update { it.copy(searchHistory = s.split("||").filter { it.isNotBlank() }) }
    }
    private fun clearHistory() {
        historyPrefs.edit().remove("history_items").apply()
        _state.update { it.copy(searchHistory = emptyList()) }
    }
    private fun removeHistoryItem(item: String) {
        val newH = _state.value.searchHistory.filter { it != item }
        historyPrefs.edit().putString("history_items", newH.joinToString("||")).apply()
        _state.update { it.copy(searchHistory = newH) }
    }
}
