package com.luminastreams.tv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.MediaRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

class HomeViewModel(private val repository: MediaRepository) : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()
    private var loadingJob: Job? = null

    init {
        loadDomainContent("סרטים")
    }

    private fun loadDomainContent(domain: String) {
        loadingJob?.cancel()

        loadingJob = viewModelScope.launch {
            _state.update { currentState ->
                currentState.copy(
                    isLoading = true, error = null, selectedTab = domain, isDiscoveryMode = false,
                    isFilterComplete = false, focusedItem = null,
                    focusedRowTitle = if (domain == "סרטים") "Trending Movies" else "Trending Series"
                )
            }

            try {
                if (domain == "סרטים") loadMovieDomainStaggered() else loadTvDomainStaggered()
            } catch (e: Exception) {
                _state.update { currentState -> currentState.copy(isLoading = false, error = "Network Failure") }
            }
        }
    }

    private suspend fun loadMovieDomainStaggered() {
        val trending = repository.discoverMedia("movie", null, null, "popularity.desc", 1).getOrNull() ?: emptyList()
        _state.update { it.copy(isLoading = false, movieTrending = trending, moviePremieres = trending.shuffled(), focusedItem = trending.firstOrNull(), focusedRowTitle = "Trending Movies") }

        delay(300)
        val action = repository.discoverMedia("movie", "28", null, "popularity.desc", 2).getOrNull() ?: emptyList()
        _state.update { it.copy(movieAction = action) }

        delay(300)
        val topRated = repository.discoverMedia("movie", null, null, "vote_average.desc", 1).getOrNull() ?: emptyList()
        _state.update { it.copy(movieTopRated = topRated) }
    }

    private suspend fun loadTvDomainStaggered() {
        val trending = repository.discoverMedia("tv", null, null, "popularity.desc", 1).getOrNull() ?: emptyList()
        _state.update { it.copy(isLoading = false, tvTrending = trending, tvPremieres = trending.shuffled(), focusedItem = trending.firstOrNull(), focusedRowTitle = "Trending Series") }

        delay(300)
        val comedy = repository.discoverMedia("tv", "35", null, "popularity.desc", 2).getOrNull() ?: emptyList()
        _state.update { it.copy(tvComedy = comedy) }
    }

    fun updateFocusedItem(movie: Movie, rowTitle: String, isVertical: Boolean) {
        val currentState = _state.value
        if (currentState.focusedItem?.id == movie.id && currentState.focusedRowTitle == rowTitle) return
        _state.update { it.copy(focusedItem = movie, focusedRowTitle = rowTitle, isFocusedVertical = isVertical) }
    }

    fun selectTab(tab: String) {
        if (_state.value.selectedTab == tab) return
        loadDomainContent(tab)
    }

    fun onStudioClicked(studioName: String) {
        val safeGenreId = when(studioName.uppercase()) { "MARVEL" -> "28"; "DISNEY+" -> "16"; "HBO" -> "18"; "NETFLIX" -> "53"; else -> "28" }
        setGenreFilter(safeGenreId, studioName)
    }

    fun clearGenre() { _state.update { it.copy(selectedGenreId = null, isFilterComplete = false, discoveryResults = emptyList()) } }
    fun setGenreFilter(id: String?, name: String) { _state.update { it.copy(selectedGenreId = id, selectedGenreName = name) }; executeDeepDiscovery() }

    private fun executeDeepDiscovery() {
        val currState = _state.value
        if (currState.selectedGenreId == null) return
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, isFilterComplete = true) }
            val apiType = if (currState.selectedTab == "סדרות") "tv" else "movie"
            try {
                val resultList = repository.discoverMedia(apiType, currState.selectedGenreId, currState.selectedYear, "popularity.desc", 1).getOrDefault(emptyList())
                _state.update { it.copy(isLoading = false, discoveryResults = resultList, focusedItem = resultList.firstOrNull()) }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Discovery failed") }
            }
        }
    }
}