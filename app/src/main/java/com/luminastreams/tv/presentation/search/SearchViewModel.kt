package com.luminastreams.tv.presentation.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
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

enum class SearchSource { ALL, MOVIES, SERIES, FUZER }

enum class QualityFilter { ANY, HD, FHD, UHD }

enum class SortBy { POPULARITY, RATING, NEWEST }

@Immutable
data class SearchFilters(
    val genre:       String?       = null,
    val minYear:     Int           = 1970,
    val maxYear:     Int           = 2026,
    val minRating:   Float         = 0f,
    val quality:     QualityFilter = QualityFilter.ANY,
    val dubbedOnly:  Boolean       = false,
    val sortBy:      SortBy        = SortBy.POPULARITY
) {
    val isActive: Boolean get() =
        genre != null || minYear > 1970 || maxYear < 2026 || minRating > 0f ||
                quality != QualityFilter.ANY || dubbedOnly || sortBy != SortBy.POPULARITY
}

@Immutable
data class SearchState(
    val query:              String          = "",
    val source:             SearchSource    = SearchSource.ALL,
    val filters:            SearchFilters   = SearchFilters(),
    val showFilters:        Boolean         = false,

    val tmdbResults:        List<SearchResult> = emptyList(),
    val isTmdbLoading:      Boolean            = false,

    val fuzerResults:       List<SearchResult> = emptyList(),
    val isFuzerLoading:     Boolean            = false,
    val fuzerError:         String?            = null,

    val discoveryResults:   List<SearchResult> = emptyList(),
    val isDiscoveryLoading: Boolean            = false,

    val searchHistory:           List<String> = emptyList(),
    val autocompleteSuggestions: List<String> = emptyList()
) {
    private fun applyFilters(list: List<SearchResult>): List<SearchResult> {
        var r = list

        if (filters.genre != null)
            r = r.filter { it.genre.equals(filters.genre, ignoreCase = true) }

        if (filters.minRating > 0f)
            r = r.filter { it.rating >= filters.minRating }

        if (filters.minYear > 1970 || filters.maxYear < 2026)
            r = r.filter { yr ->
                val y = yr.releaseYear.toIntOrNull() ?: return@filter true
                y in filters.minYear..filters.maxYear
            }

        val qStr = when (filters.quality) {
            QualityFilter.HD  -> "HD"
            QualityFilter.FHD -> "FHD"
            QualityFilter.UHD -> "4K"
            QualityFilter.ANY -> ""
        }
        if (qStr.isNotEmpty()) {
            r = r.filter { it.qualityTag.equals(qStr, ignoreCase = true) }
        }

        if (filters.dubbedOnly)
            r = r.filter { it.title.contains("מדובב", ignoreCase = true) }

        r = when (filters.sortBy) {
            SortBy.RATING -> r.sortedByDescending { it.rating }
            SortBy.NEWEST -> r.sortedByDescending { it.releaseYear.toIntOrNull() ?: 0 }
            SortBy.POPULARITY -> r
        }

        return r
    }

    val activeResults: List<SearchResult> get() {
        val base = when {
            source == SearchSource.FUZER  -> fuzerResults
            query.isBlank()               -> discoveryResults
            source == SearchSource.MOVIES -> tmdbResults.filter { it.type == MediaType.MOVIE }
            source == SearchSource.SERIES -> tmdbResults.filter { it.type == MediaType.TV_SHOW }
            else                          -> tmdbResults
        }
        return if (filters.isActive) applyFilters(base) else base
    }

    val isLoading: Boolean get() = when (source) {
        SearchSource.FUZER -> isFuzerLoading
        else               -> if (query.isBlank()) isDiscoveryLoading else isTmdbLoading
    }
}

sealed interface SearchIntent {
    data class UpdateQuery(val query: String)     : SearchIntent
    data class SelectSource(val source: SearchSource) : SearchIntent
    data class UpdateFilters(val filters: SearchFilters) : SearchIntent
    object ToggleFilters   : SearchIntent
    object ClearFilters    : SearchIntent
    object ClearHistory    : SearchIntent
    data class RemoveHistoryItem(val item: String) : SearchIntent
}

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _state      = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queryFlow     = MutableStateFlow("")
    private val historyPrefs  = application.getSharedPreferences("lumina_search_history", Context.MODE_PRIVATE)

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

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery      -> handleQueryUpdate(intent.query)
            is SearchIntent.SelectSource     -> handleSourceChange(intent.source)
            is SearchIntent.UpdateFilters    -> _state.update { it.copy(filters = intent.filters) }
            is SearchIntent.ToggleFilters    -> _state.update { it.copy(showFilters = !it.showFilters) }
            is SearchIntent.ClearFilters     -> _state.update { it.copy(filters = SearchFilters()) }
            is SearchIntent.ClearHistory     -> clearHistory()
            is SearchIntent.RemoveHistoryItem-> removeHistoryItem(intent.item)
        }
    }

    private fun handleSourceChange(src: SearchSource) {
        _state.update { it.copy(source = src) }
        if (src == SearchSource.FUZER) {
            val q = _state.value.query
            if (q.isNotBlank()) viewModelScope.launch { runFuzerSearch(q) }
        }
    }

    private fun handleQueryUpdate(newQuery: String) {
        _state.update {
            it.copy(
                query = newQuery,
                autocompleteSuggestions = buildSuggestions(newQuery)
            )
        }
        if (newQuery.isBlank()) {
            _state.update { it.copy(
                tmdbResults  = emptyList(),
                fuzerResults = emptyList(),
                fuzerError   = null
            ) }
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

    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow
                .debounce(380)
                .distinctUntilChanged()
                .filter { it.isNotBlank() }
                .collectLatest { q ->
                    saveToHistory(q)
                    coroutineScope {
                        launch { runTmdbSearch(q) }
                        launch { runFuzerSearch(q) }
                    }
                }
        }
    }

    private suspend fun runTmdbSearch(query: String) {
        _state.update { it.copy(isTmdbLoading = true) }
        try {
            val isHe = query.any { it in '\u0590'..'\u05FF' }
            val lang = if (isHe) "he-IL" else "en-US"

            val enc = withContext(Dispatchers.Default) { URLEncoder.encode(query, "UTF-8") }

            val p1 = fetchTmdbPage(enc, lang, 1)
            val p2 = fetchTmdbPage(enc, lang, 2)

            _state.update { it.copy(tmdbResults = (p1 + p2).distinctBy { r -> r.id }, isTmdbLoading = false) }
        } catch (_: Exception) {
            _state.update { it.copy(tmdbResults = emptyList(), isTmdbLoading = false) }
        }
    }

    private suspend fun fetchTmdbPage(enc: String, lang: String, page: Int): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val out = mutableListOf<SearchResult>()
            val key = "9ab4a284f0c028007b78925852196b79"
            val base = "https://image.tmdb.org/t/p"
            try {
                val con = URL("https://api.themoviedb.org/3/search/multi?api_key=$key&language=$lang&query=$enc&page=$page&include_adult=false")
                    .openConnection() as HttpURLConnection
                con.connectTimeout = 6000
                con.readTimeout = 9000

                if (con.responseCode == 200) {
                    val textResponse = con.inputStream.bufferedReader().use { it.readText() }
                    val arr = JSONObject(textResponse).optJSONArray("results")

                    if (arr != null) {
                        for (i in 0 until arr.length()) {
                            val j  = arr.getJSONObject(i)
                            val mt = j.optString("media_type")
                            if (mt != "movie" && mt != "tv") continue
                            val title = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                            else             j.optString("title").ifBlank { j.optString("original_title") }
                            out += SearchResult(
                                id          = "${mt}_${j.optInt("id")}",
                                title       = title,
                                posterUrl   = j.optString("poster_path").let   { p -> if (p.isNotBlank() && p!="null") "$base/w342$p" else "" },
                                backdropUrl = j.optString("backdrop_path").let { p -> if (p.isNotBlank() && p!="null") "$base/w780$p" else "" },
                                type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                                rating      = j.optDouble("vote_average", 0.0).toFloat(),
                                releaseYear = (if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")).take(4),
                                genre       = j.optJSONArray("genre_ids")?.optInt(0)?.let { tmdbGenreName(it) } ?: ""
                            )
                        }
                    }
                }
            } catch (_: Exception) {}
            out
        }
    }

    private suspend fun runFuzerSearch(query: String) {
        _state.update { it.copy(isFuzerLoading = true, fuzerError = null) }
        try {
            val raw: List<com.luminastreams.tv.domain.model.Movie> =
                withContext(Dispatchers.IO) {
                    FuzerEngine.search(query).getOrElse { emptyList() }
                }
            val mapped = raw.map { m ->
                val qTag = when {
                    m.title.contains("4K",    ignoreCase = true) ||
                            m.title.contains("2160p", ignoreCase = true) -> "4K"
                    m.title.contains("1080p", ignoreCase = true) -> "FHD"
                    m.title.contains("720p",  ignoreCase = true) -> "HD"
                    else -> ""
                }
                SearchResult(
                    id          = m.id,
                    title       = m.title,
                    posterUrl   = m.posterUrl,
                    backdropUrl = m.backdropUrl,
                    type        = if (m.mediaType == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                    rating      = m.rating,
                    releaseYear = if (m.year > 0) m.year.toString() else "",
                    qualityTag  = qTag,
                    genre       = m.genre
                )
            }
            _state.update { it.copy(fuzerResults = mapped, isFuzerLoading = false) }
        } catch (e: Exception) {
            _state.update { it.copy(
                fuzerResults   = emptyList(),
                isFuzerLoading = false,
                fuzerError     = e.message?.take(80) ?: "Unknown error"
            ) }
        }
    }

    private fun loadDiscovery() {
        viewModelScope.launch {
            _state.update { it.copy(isDiscoveryLoading = true) }
            try {
                val p1 = fetchDiscoveryPage(1)
                val p2 = fetchDiscoveryPage(2)
                _state.update { it.copy(
                    discoveryResults   = (p1 + p2).distinctBy { r -> r.id },
                    isDiscoveryLoading = false
                ) }
            } catch (_: Exception) {
                _state.update { it.copy(isDiscoveryLoading = false) }
            }
        }
    }

    private suspend fun fetchDiscoveryPage(page: Int): List<SearchResult> {
        return withContext(Dispatchers.IO) {
            val out = mutableListOf<SearchResult>()
            val key = "9ab4a284f0c028007b78925852196b79"
            val base = "https://image.tmdb.org/t/p"

            for (mt in listOf("movie", "tv")) {
                try {
                    val con = URL("https://api.themoviedb.org/3/discover/$mt?api_key=$key&language=en-US&page=$page&sort_by=popularity.desc")
                        .openConnection() as HttpURLConnection
                    con.connectTimeout = 6000
                    con.readTimeout = 9000

                    if (con.responseCode == 200) {
                        val textResponse = con.inputStream.bufferedReader().use { it.readText() }
                        val arr = JSONObject(textResponse).optJSONArray("results")

                        if (arr != null) {
                            for (i in 0 until arr.length()) {
                                val j = arr.getJSONObject(i)
                                val title = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                                else             j.optString("title").ifBlank { j.optString("original_title") }
                                val poster = j.optString("poster_path").let { p -> if (p.isNotBlank() && p!="null") "$base/w342$p" else "" }
                                if (poster.isBlank()) continue
                                out += SearchResult(
                                    id          = "${mt}_${j.optInt("id")}",
                                    title       = title,
                                    posterUrl   = poster,
                                    backdropUrl = j.optString("backdrop_path").let { p -> if (p.isNotBlank() && p!="null") "$base/w780$p" else "" },
                                    type        = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                                    rating      = j.optDouble("vote_average", 0.0).toFloat(),
                                    releaseYear = (if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")).take(4),
                                    genre       = j.optJSONArray("genre_ids")?.optInt(0)?.let { tmdbGenreName(it) } ?: ""
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            out
        }
    }

    private fun saveToHistory(q: String) {
        val h = (listOf(q) + _state.value.searchHistory).distinct().take(8)
        historyPrefs.edit { putString("history_items", h.joinToString("||")) }
        _state.update { it.copy(searchHistory = h) }
    }

    private fun loadHistory() {
        val s = historyPrefs.getString("history_items", "") ?: ""
        _state.update { it.copy(searchHistory = s.split("||").filter { it.isNotBlank() }) }
    }

    private fun clearHistory() {
        historyPrefs.edit { remove("history_items") }
        _state.update { it.copy(searchHistory = emptyList()) }
    }

    private fun removeHistoryItem(item: String) {
        val h = _state.value.searchHistory.filter { it != item }
        historyPrefs.edit { putString("history_items", h.joinToString("||")) }
        _state.update { it.copy(searchHistory = h) }
    }

    private fun tmdbGenreName(id: Int): String = when (id) {
        28 -> "Action"; 12 -> "Adventure"; 16 -> "Animation"; 35 -> "Comedy"
        80 -> "Crime"; 99 -> "Documentary"; 18 -> "Drama"; 10751 -> "Family"
        14 -> "Fantasy"; 36 -> "History"; 27 -> "Horror"; 10402 -> "Music"
        9648 -> "Mystery"; 10749 -> "Romance"; 878 -> "Sci-Fi"; 10770 -> "TV Movie"
        53 -> "Thriller"; 10752 -> "War"; 37 -> "Western"
        10759 -> "Action & Adventure"; 10762 -> "Kids"; 10763 -> "News"
        10764 -> "Reality"; 10765 -> "Sci-Fi & Fantasy"; 10766 -> "Soap"
        10767 -> "Talk"; 10768 -> "War & Politics"
        else -> ""
    }
}