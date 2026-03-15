package com.luminastreams.tv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MediaRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var loadingJob: Job? = null

    init { loadDomainContent("סרטים") }

    private fun loadDomainContent(domain: String) {
        loadingJob?.cancel()
        loadingJob = viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, selectedTab = domain) }
            try {
                if (domain == "סרטים") loadMoviesParallel() else loadTvParallel()
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Network error: ${e.message}") }
            }
        }
    }

    // טוען את כל השורות במקביל בו זמנית — אפס delays סדרתיים
    private suspend fun loadMoviesParallel() = coroutineScope {
        val trendingD  = async { repository.discoverMedia("movie", null, null, "popularity.desc",     1).getOrNull() ?: emptyList() }
        val newD       = async { repository.discoverMedia("movie", null, null, "primary_release_date.desc", 1).getOrNull() ?: emptyList() }
        val actionD    = async { repository.discoverMedia("movie", "28",  null, "popularity.desc",    1).getOrNull() ?: emptyList() }
        val dramaD     = async { repository.discoverMedia("movie", "18",  null, "popularity.desc",    1).getOrNull() ?: emptyList() }
        val scifiD     = async { repository.discoverMedia("movie", "878", null, "popularity.desc",    1).getOrNull() ?: emptyList() }
        val topD       = async { repository.discoverMedia("movie", null, null, "vote_average.desc",   1).getOrNull() ?: emptyList() }

        val trending = trendingD.await()
        // עדכן טרנדינג מיד — המשתמש רואה תוך ששאר השורות טוענות
        _state.update {
            it.copy(
                isLoading       = false,
                movieTrending   = trending,
                focusedItem     = trending.firstOrNull()
            )
        }

        _state.update {
            it.copy(
                moviePremieres  = newD.await(),
                movieAction     = actionD.await(),
                movieDrama      = dramaD.await(),
                movieScifi      = scifiD.await(),
                movieTopRated   = topD.await()
            )
        }
    }

    private suspend fun loadTvParallel() = coroutineScope {
        val trendingD  = async { repository.discoverMedia("tv", null, null, "popularity.desc",          1).getOrNull() ?: emptyList() }
        val newD       = async { repository.discoverMedia("tv", null, null, "first_air_date.desc",       1).getOrNull() ?: emptyList() }
        val dramaD     = async { repository.discoverMedia("tv", "18",  null, "popularity.desc",          1).getOrNull() ?: emptyList() }
        val crimeD     = async { repository.discoverMedia("tv", "80",  null, "popularity.desc",          1).getOrNull() ?: emptyList() }
        val scifiD     = async { repository.discoverMedia("tv", "10765", null, "popularity.desc",        1).getOrNull() ?: emptyList() }
        val topD       = async { repository.discoverMedia("tv", null, null, "vote_average.desc",         1).getOrNull() ?: emptyList() }

        val trending = trendingD.await()
        _state.update {
            it.copy(
                isLoading    = false,
                tvTrending   = trending,
                focusedItem  = trending.firstOrNull()
            )
        }

        _state.update {
            it.copy(
                tvPremieres  = newD.await(),
                tvDrama      = dramaD.await(),
                tvCrime      = crimeD.await(),
                tvScifi      = scifiD.await(),
                tvTopRated   = topD.await()
            )
        }
    }

    fun updateFocusedItem(movie: Movie, rowTitle: String, isVertical: Boolean) {
        val s = _state.value
        if (s.focusedItem?.id == movie.id && s.focusedRowTitle == rowTitle) return
        _state.update { it.copy(focusedItem = movie, focusedRowTitle = rowTitle, isFocusedVertical = isVertical) }
    }

    fun selectTab(tab: String) {
        if (_state.value.selectedTab == tab) return
        loadDomainContent(tab)
    }

    fun clearGenre() = _state.update { it.copy(selectedGenreId = null, isFilterComplete = false, discoveryResults = emptyList()) }
    fun setGenreFilter(id: String?, name: String) { _state.update { it.copy(selectedGenreId = id, selectedGenreName = name) }; executeDeepDiscovery() }
    fun onStudioClicked(studioName: String) {
        val g = when(studioName.uppercase()) { "MARVEL" -> "28"; "DISNEY+" -> "16"; "HBO" -> "18"; "NETFLIX" -> "53"; else -> "28" }
        setGenreFilter(g, studioName)
    }

    private fun executeDeepDiscovery() {
        val curr = _state.value
        if (curr.selectedGenreId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isFilterComplete = true) }
            val type = if (curr.selectedTab == "סדרות") "tv" else "movie"
            val list = repository.discoverMedia(type, curr.selectedGenreId, curr.selectedYear, "popularity.desc", 1).getOrDefault(emptyList())
            _state.update { it.copy(isLoading = false, discoveryResults = list, focusedItem = list.firstOrNull()) }
        }
    }
}
