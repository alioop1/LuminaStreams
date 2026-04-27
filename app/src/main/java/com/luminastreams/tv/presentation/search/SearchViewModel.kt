package com.luminastreams.tv.presentation.search

import android.app.Application
import android.content.Context
import androidx.compose.runtime.Immutable
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.data.remote.FuzerEngine
import com.luminastreams.tv.data.repository.MediaRepositoryImpl
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.model.SearchResult
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

enum class SearchSource { ALL, MOVIES, SERIES, FUZER }
enum class QualityFilter { ANY, HD, FHD, UHD }
enum class SortBy { POPULARITY, RATING, NEWEST, TITLE }
enum class MediaTypeFilter { ANY, MOVIE, TV_SHOW, ANIME }
enum class RuntimeFilter { ANY, SHORT, MEDIUM, LONG, EPIC }
enum class RatingTier { ANY, MASTERPIECE, GREAT, GOOD }
enum class LanguageFilter { ANY, ENGLISH, HEBREW, KOREAN, JAPANESE, SPANISH, FRENCH }
enum class NetworkFilter { ANY, NETFLIX, HBO, APPLE_TV, DISNEY_PLUS, AMAZON, HULU }

@Immutable
data class SearchFilters(
    val typeFilter:  MediaTypeFilter = MediaTypeFilter.ANY,
    val genre:       String?       = null,
    val minYear:     Int           = 1920,
    val maxYear:     Int           = 2026,
    val minRating:   Float         = 0f,
    val quality:     QualityFilter = QualityFilter.ANY,
    val dubbedOnly:  Boolean       = false,
    val sortBy:      SortBy        = SortBy.POPULARITY,
    val runtime:     RuntimeFilter = RuntimeFilter.ANY,
    val ratingTier:  RatingTier    = RatingTier.ANY,
    val language:    LanguageFilter = LanguageFilter.ANY,
    val network:     NetworkFilter = NetworkFilter.ANY,
    val mood:        String?       = null
) {
    val isActive: Boolean get() =
        typeFilter != MediaTypeFilter.ANY || genre != null || minYear > 1920 || maxYear < 2026 || minRating > 0f ||
                quality != QualityFilter.ANY || dubbedOnly || sortBy != SortBy.POPULARITY ||
                runtime != RuntimeFilter.ANY || ratingTier != RatingTier.ANY ||
                language != LanguageFilter.ANY || network != NetworkFilter.ANY || mood != null
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
    private fun applyFilters(list: List<SearchResult>, skipServerFiltered: Boolean = false): List<SearchResult> {
        var r = list

        r = when (filters.typeFilter) {
            MediaTypeFilter.MOVIE -> r.filter { it.type == MediaType.MOVIE }
            MediaTypeFilter.TV_SHOW -> r.filter { it.type == MediaType.TV_SHOW }
            MediaTypeFilter.ANIME -> r.filter { it.genre.contains("Animation", true) }
            MediaTypeFilter.ANY -> r
        }

        // Only apply genre filter client-side for text-search results (not discovery, which is already server-filtered)
        val genreFilter = filters.genre
        if (genreFilter != null && !skipServerFiltered)
            r = r.filter { it.genre.contains(genreFilter, ignoreCase = true) }

        if (filters.minRating > 0f && !skipServerFiltered)
            r = r.filter { it.rating >= filters.minRating }

        if ((filters.minYear > 1920 || filters.maxYear < 2026) && !skipServerFiltered)
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
            SortBy.NEWEST -> r.sortedWith(
                compareByDescending<SearchResult> { it.releaseYear.toIntOrNull() ?: 0 }
                    .thenByDescending { it.rating }
            )
            SortBy.TITLE -> r.sortedBy { it.title }
            SortBy.POPULARITY -> r
        }

        return r
    }

    val activeResults: List<SearchResult> get() {
        val isDiscovery = query.isBlank() && source != SearchSource.FUZER
        val base = when {
            source == SearchSource.FUZER  -> fuzerResults
            query.isBlank()               -> discoveryResults
            source == SearchSource.MOVIES -> tmdbResults.filter { it.type == MediaType.MOVIE }
            source == SearchSource.SERIES -> tmdbResults.filter { it.type == MediaType.TV_SHOW }
            else                          -> tmdbResults
        }
        // Skip server-filtered fields for discovery results (already filtered by TMDB API)
        return if (filters.isActive) applyFilters(base, skipServerFiltered = isDiscovery) else base
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

    private val repository = MediaRepositoryImpl(application)

    private val _state      = MutableStateFlow(SearchState())
    val state: StateFlow<SearchState> = _state.asStateFlow()

    private val queryFlow     = MutableStateFlow("")
    private val historyPrefs  = application.getSharedPreferences("lumina_search_history", Context.MODE_PRIVATE)

    private val popularTerms = listOf(
        "Avatar", "Avengers", "Batman", "Spider-Man", "Superman",
        "Matrix", "Inception", "Interstellar", "Joker", "Star Wars",
        "Harry Potter", "Lord of the Rings", "Deadpool", "Breaking Bad",
        "פאודה", "קופה ראשית", "הבורר", "שנות ה-80"
    )

    private var discoveryJob: Job? = null

    init {
        loadHistory()
        observeQuery()
        loadDiscovery()
    }

    fun onIntent(intent: SearchIntent) {
        when (intent) {
            is SearchIntent.UpdateQuery      -> handleQueryUpdate(intent.query)
            is SearchIntent.SelectSource     -> handleSourceChange(intent.source)
            is SearchIntent.UpdateFilters    -> { _state.update { it.copy(filters = intent.filters) }; loadFilteredDiscovery(intent.filters) }
            is SearchIntent.ToggleFilters    -> _state.update { it.copy(showFilters = !it.showFilters) }
            is SearchIntent.ClearFilters     -> { _state.update { it.copy(filters = SearchFilters()) }; loadDiscovery() }
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
            val results = coroutineScope {
                val pages = (1..3).map { page ->
                    async { repository.searchMulti(query, page, isHe).getOrDefault(emptyList()) }
                }
                // Bilingual: also search in English if query is Hebrew
                val extraPages = if (isHe) {
                    (1..2).map { page ->
                        async { repository.searchMulti(query, page, false).getOrDefault(emptyList()) }
                    }
                } else emptyList()

                (pages + extraPages).awaitAll().flatten()
            }

            _state.update { it.copy(
                tmdbResults = results.distinctBy { r -> r.id }.filter { it.posterUrl.isNotBlank() },
                isTmdbLoading = false
            ) }
        } catch (_: Exception) {
            _state.update { it.copy(tmdbResults = emptyList(), isTmdbLoading = false) }
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
        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _state.update { it.copy(isDiscoveryLoading = true) }
            try {
                val pages = coroutineScope {
                    (1..3).map { page ->
                        async { repository.getDiscoverySearch(page).getOrDefault(emptyList()) }
                    }.awaitAll().flatten()
                }

                _state.update { it.copy(
                    discoveryResults   = pages.distinctBy { r -> r.id }.filter { it.posterUrl.isNotBlank() },
                    isDiscoveryLoading = false
                ) }
            } catch (_: Exception) {
                _state.update { it.copy(isDiscoveryLoading = false) }
            }
        }
    }

    private fun loadFilteredDiscovery(filters: SearchFilters) {
        if (!filters.isActive) { loadDiscovery(); return }

        discoveryJob?.cancel()
        discoveryJob = viewModelScope.launch {
            _state.update { it.copy(isDiscoveryLoading = true) }
            try {
                val type = when (filters.typeFilter) {
                    MediaTypeFilter.MOVIE -> "movie"
                    MediaTypeFilter.TV_SHOW, MediaTypeFilter.ANIME -> "tv"
                    else -> "both"
                }

                val movieGenreId = filters.genre?.let { genreNameToTmdbId(it, forTv = false) }?.toString()
                val tvGenreId = filters.genre?.let { genreNameToTmdbId(it, forTv = true) }?.toString()
                val genreId = when (type) {
                    "movie" -> movieGenreId
                    "tv" -> tvGenreId
                    else -> movieGenreId  // for "both", repository handles each call separately
                }

                val (dateGte, dateLte) = when {
                    filters.minYear > 1920 || filters.maxYear < 2026 -> {
                        "${filters.minYear}-01-01" to "${filters.maxYear}-12-31"
                    }
                    else -> null to null
                }

                val voteGte = when {
                    filters.ratingTier == RatingTier.MASTERPIECE -> 8.0f
                    filters.ratingTier == RatingTier.GREAT -> 7.0f
                    filters.ratingTier == RatingTier.GOOD -> 6.0f
                    filters.minRating > 0f -> filters.minRating
                    else -> null
                }

                val lang = when (filters.language) {
                    LanguageFilter.ENGLISH -> "en"
                    LanguageFilter.HEBREW -> "he"
                    LanguageFilter.KOREAN -> "ko"
                    LanguageFilter.JAPANESE -> "ja"
                    LanguageFilter.SPANISH -> "es"
                    LanguageFilter.FRENCH -> "fr"
                    else -> null
                }

                val networkId = when (filters.network) {
                    NetworkFilter.NETFLIX -> "213"
                    NetworkFilter.HBO -> "49"
                    NetworkFilter.APPLE_TV -> "2552"
                    NetworkFilter.DISNEY_PLUS -> "2739"
                    NetworkFilter.AMAZON -> "1024"
                    NetworkFilter.HULU -> "453"
                    else -> null
                }

                val (rtGte, rtLte) = when (filters.runtime) {
                    RuntimeFilter.SHORT -> null to 90
                    RuntimeFilter.MEDIUM -> 90 to 120
                    RuntimeFilter.LONG -> 120 to 180
                    RuntimeFilter.EPIC -> 180 to null
                    else -> null to null
                }

                // Sort: default genre browsing = newest first, others respect user choice
                val sort = when (filters.sortBy) {
                    SortBy.RATING -> "vote_average.desc"
                    SortBy.NEWEST -> "primary_release_date.desc"
                    SortBy.TITLE -> "original_title.asc"
                    SortBy.POPULARITY -> "popularity.desc"
                }

                // Unlimited loading: batch pages for genre discovery, scaled by query type
                val maxPages = when {
                    filters.genre != null && type == "both" -> 8   // 8×2 = 16 calls, ~160 results
                    filters.genre != null -> 12                     // 12 calls, ~240 results
                    else -> 5
                }

                // Parallel batch loading for speed
                val allResults = mutableListOf<SearchResult>()
                val batchSize = 5  // 5 pages per batch for speed
                for (batch in 1..maxPages step batchSize) {
                    val pages = (batch until (batch + batchSize).coerceAtMost(maxPages + 1))
                    val batchResults = coroutineScope {
                        pages.map { page ->
                            async {
                                repository.discoverFiltered(
                                    type = type, genreId = genreId, tvGenreId = tvGenreId,
                                    releaseDateGte = dateGte, releaseDateLte = dateLte,
                                    voteGte = voteGte, language = lang, networkId = networkId,
                                    runtimeGte = rtGte, runtimeLte = rtLte,
                                    sortBy = sort, page = page
                                ).getOrDefault(emptyList())
                            }
                        }.awaitAll().flatten()
                    }
                    allResults.addAll(batchResults)

                    // Update UI progressively after first batch
                    if (batch == 1 && allResults.isNotEmpty()) {
                        _state.update { it.copy(
                            discoveryResults = allResults.distinctBy { r -> r.id }
                                .filter { r -> r.posterUrl.isNotBlank() },
                            isDiscoveryLoading = allResults.size < 40 // Still loading if more pages
                        ) }
                    }
                }

                val final = allResults
                    .distinctBy { r -> r.id }
                    .filter { r -> r.posterUrl.isNotBlank() }

                _state.update { it.copy(
                    discoveryResults = final,
                    isDiscoveryLoading = false
                ) }
            } catch (_: Exception) {
                _state.update { it.copy(isDiscoveryLoading = false) }
            }
        }
    }

    private fun genreNameToTmdbId(name: String, forTv: Boolean = false): Int? {
        if (forTv) {
            // TMDB TV genre IDs (different from movie IDs!)
            return when (name.lowercase()) {
                "action" -> 10759; "adventure" -> 10759; "animation", "anime" -> 16; "comedy" -> 35
                "crime" -> 80; "documentary" -> 99; "drama" -> 18; "family" -> 10751
                "fantasy" -> 10765; "horror" -> null; "mystery" -> 9648
                "romance" -> null; "sci-fi", "science fiction" -> 10765
                "thriller" -> null; "war" -> 10768; "western" -> 37; else -> null
            }
        }
        // Movie genre IDs
        return when (name.lowercase()) {
            "action" -> 28; "adventure" -> 12; "animation", "anime" -> 16; "comedy" -> 35
            "crime" -> 80; "documentary" -> 99; "drama" -> 18; "family" -> 10751
            "fantasy" -> 14; "history" -> 36; "horror" -> 27; "music" -> 10402
            "mystery" -> 9648; "romance" -> 10749; "sci-fi", "science fiction" -> 878
            "thriller" -> 53; "war" -> 10752; "western" -> 37; else -> null
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
}