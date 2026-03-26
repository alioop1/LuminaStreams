package com.luminastreams.tv.presentation.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
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

enum class SearchSource { ALL, FUZER } // Simplified for the new premium toggle UI
enum class QualityFilter { ANY, HD, FHD, UHD }
enum class SortOrder { RELEVANCE, RATING, YEAR }

@Immutable
data class SearchFilters(
    val genre: String? = null,
    val minRating: Float = 0f,
    val quality: QualityFilter = QualityFilter.ANY,
    val dubbedOnly: Boolean = false,
    val sort: SortOrder = SortOrder.RELEVANCE
) {
    val isActive: Boolean get() = genre != null || minRating > 0f || quality != QualityFilter.ANY || dubbedOnly || sort != SortOrder.RELEVANCE
}

@Immutable
data class FilterChip(val id: String, val label: String, val emoji: String = "", val isActive: Boolean = false)

@Immutable
data class SearchState(
    val query: String = "",
    val source: SearchSource = SearchSource.ALL,
    val filters: SearchFilters = SearchFilters(),
    val showFilters: Boolean = false,

    val tmdbResults: List<SearchResult> = emptyList(),
    val fuzerResults: List<SearchResult> = emptyList(),
    val discoveryResults: List<SearchResult> = emptyList(),

    val isTmdbLoading: Boolean = false,
    val isFuzerLoading: Boolean = false,
    val isDiscoveryLoading: Boolean = false,

    val fuzerError: String? = null,
    val searchHistory: List<String> = emptyList(),
    val visibleFilterChips: List<FilterChip> = emptyList()
) {
    private fun applyFilters(list: List<SearchResult>): List<SearchResult> {
        var r = list
        if (filters.genre != null) r = r.filter { it.genre.equals(filters.genre, ignoreCase = true) }
        if (filters.minRating > 0f) r = r.filter { it.rating >= filters.minRating }
        if (filters.quality != QualityFilter.ANY) {
            val tag = when (filters.quality) {
                QualityFilter.HD -> "HD"
                QualityFilter.FHD -> "FHD"
                QualityFilter.UHD -> "4K"
                else -> ""
            }
            r = r.filter { it.qualityTag.equals(tag, ignoreCase = true) }
        }
        if (filters.dubbedOnly) r = r.filter { it.title.contains("מדובב", ignoreCase = true) }
        r = when (filters.sort) {
            SortOrder.RATING -> r.sortedByDescending { it.rating }
            SortOrder.YEAR -> r.sortedByDescending { it.releaseYear.toIntOrNull() ?: 0 }
            SortOrder.RELEVANCE -> r
        }
        return r
    }

    val activeResults: List<SearchResult> get() {
        val base = when {
            source == SearchSource.FUZER -> fuzerResults
            query.isBlank() -> discoveryResults
            else -> tmdbResults
        }
        return if (filters.isActive) applyFilters(base) else base
    }

    val isLoading: Boolean get() = when (source) {
        SearchSource.FUZER -> isFuzerLoading
        else -> if (query.isBlank()) isDiscoveryLoading else isTmdbLoading
    }
}

sealed interface SearchIntent {
    data class UpdateQuery(val query: String) : SearchIntent
    data class SelectSource(val source: SearchSource) : SearchIntent
    data class ApplyChip(val chipId: String) : SearchIntent
}

@OptIn(FlowPreview::class)
class SearchViewModel(application: Application) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()
    private val queryFlow = MutableStateFlow("")
    private val fuzerEngine = FuzerEngine()

    init {
        observeQuery()
        loadDiscovery()
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery -> handleQueryUpdate(intent.query)
            is SearchIntent.SelectSource -> handleSourceChange(intent.source)
            is SearchIntent.ApplyChip -> handleChipTap(intent.chipId)
        }
    }

    private fun handleSourceChange(src: SearchSource) {
        _state.update { it.copy(source = src) }
        if (src == SearchSource.FUZER && _state.value.query.isNotBlank()) {
            viewModelScope.launch { runFuzerSearch(_state.value.query) }
        }
    }

    private fun handleQueryUpdate(newQuery: String) {
        _state.update { it.copy(query = newQuery) }
        if (newQuery.isBlank()) {
            _state.update { it.copy(tmdbResults = emptyList(), fuzerResults = emptyList()) }
            return
        }
        queryFlow.value = newQuery
    }

    private fun observeQuery() {
        viewModelScope.launch {
            queryFlow.debounce(400).distinctUntilChanged().filter { it.isNotBlank() }.collectLatest { q ->
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
            val enc = URLEncoder.encode(query, "UTF-8")
            val key = "9ab4a284f0c028007b78925852196b79"
            val base = "https://image.tmdb.org/t/p"
            val lang = if (query.any { it in '\u0590'..'\u05FF' }) "he-IL" else "en-US"
            val p1 = withContext(Dispatchers.IO) { fetchTmdbPage(enc, lang, key, base, 1) }
            _state.update { it.copy(tmdbResults = p1, isTmdbLoading = false) }
        } catch (_: Exception) {
            _state.update { it.copy(tmdbResults = emptyList(), isTmdbLoading = false) }
        }
    }

    private fun fetchTmdbPage(enc: String, lang: String, key: String, base: String, page: Int): List<SearchResult> {
        val out = mutableListOf<SearchResult>()
        try {
            val con = URL("https://api.themoviedb.org/3/search/multi?api_key=$key&language=$lang&query=$enc&page=$page").openConnection() as HttpURLConnection
            con.connectTimeout = 5000; con.readTimeout = 5000
            if (con.responseCode != 200) return emptyList()
            val arr = JSONObject(con.inputStream.bufferedReader().use { it.readText() }).optJSONArray("results") ?: return emptyList()
            for (i in 0 until arr.length()) {
                val j = arr.getJSONObject(i)
                val mt = j.optString("media_type")
                if (mt != "movie" && mt != "tv") continue
                out += SearchResult(
                    id = "${mt}_${j.optInt("id")}",
                    title = j.optString("title").ifBlank { j.optString("name") },
                    posterUrl = j.optString("poster_path").let { if (it.isNotBlank() && it != "null") "$base/w342$it" else "" },
                    type = if (mt == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                    rating = j.optDouble("vote_average", 0.0).toFloat(),
                    releaseYear = (if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")).take(4)
                )
            }
        } catch (_: Exception) {}
        return out
    }

    private suspend fun runFuzerSearch(query: String) {
        _state.update { it.copy(isFuzerLoading = true) }
        try {
            val raw = withContext(Dispatchers.IO) { fuzerEngine.search(query).getOrElse { emptyList() } }
            val mapped = raw.map { m ->
                val qTag = when {
                    m.title.contains("4K", ignoreCase = true) || m.title.contains("2160p", ignoreCase = true) -> "4K"
                    m.title.contains("1080p", ignoreCase = true) -> "FHD"
                    else -> ""
                }
                SearchResult(
                    id = m.id, title = m.title, posterUrl = m.posterUrl,
                    type = if (m.mediaType == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
                    rating = m.rating, qualityTag = qTag
                )
            }
            _state.update { it.copy(fuzerResults = mapped, isFuzerLoading = false) }
        } catch (_: Exception) {
            _state.update { it.copy(fuzerResults = emptyList(), isFuzerLoading = false) }
        }
    }

    private fun loadDiscovery() {
        viewModelScope.launch {
            _state.update { it.copy(isDiscoveryLoading = true) }
            try {
                val p1 = withContext(Dispatchers.IO) { fetchTmdbPage("a", "en-US", "9ab4a284f0c028007b78925852196b79", "https://image.tmdb.org/t/p", 1) } // Dummy discovery
                _state.update { it.copy(discoveryResults = p1, isDiscoveryLoading = false) }
            } catch (_: Exception) {
                _state.update { it.copy(isDiscoveryLoading = false) }
            }
        }
    }

    private fun handleChipTap(chipId: String) {
        val f = _state.value.filters
        val updated = when (chipId) {
            "dubbed" -> f.copy(dubbedOnly = !f.dubbedOnly)
            "q_4k" -> f.copy(quality = if (f.quality == QualityFilter.UHD) QualityFilter.ANY else QualityFilter.UHD)
            "r8" -> f.copy(minRating = if (f.minRating >= 8f) 0f else 8f)
            else -> f.copy(genre = if (f.genre == chipId.removePrefix("g_")) null else chipId.removePrefix("g_"))
        }
        _state.update { it.copy(filters = updated) }
    }
}