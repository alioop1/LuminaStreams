package com.luminastreams.tv.presentation.screensaver

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.domain.repository.MediaRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ScreensaverItem(
    val id: String,
    val mediaType: String,
    val title: String,
    val backdropUrl: String,
    val logoUrl: String? = null,
    val logoFetched: Boolean = false
)

class ScreensaverViewModel(private val repository: MediaRepository) : ViewModel() {
    private val _backdrops = MutableStateFlow<List<ScreensaverItem>>(emptyList())
    val backdrops: StateFlow<List<ScreensaverItem>> = _backdrops.asStateFlow()

    private val _currentIndex = MutableStateFlow(0)
    val currentIndex: StateFlow<Int> = _currentIndex.asStateFlow()

    init {
        loadBackdrops()
        startRotation()
    }

    private fun loadBackdrops() {
        viewModelScope.launch {
            val movies = repository.getTrendingMovies().getOrNull() ?: emptyList()
            val tv = repository.getTrendingTv().getOrNull() ?: emptyList()
            
            val combined = (movies + tv)
                .filter { it.rating >= 7.5f && it.backdropUrl.isNotBlank() && it.backdropUrl != "null" }
                .map { 
                    ScreensaverItem(
                        id = it.id,
                        mediaType = it.mediaType,
                        title = it.title,
                        backdropUrl = it.backdropUrl.replace("w780", "original").replace("w1280", "original")
                    )
                }
                .shuffled()
            
            if (combined.isNotEmpty()) {
                _backdrops.value = combined
                
                // Fetch first logo manually since currentIndex collect might not re-trigger
                val index = 0
                val current = combined[index]
                if (!current.logoFetched) {
                    var fetchedLogoUrl: String? = null
                    try {
                        val tmdbId = current.id.substringAfter("_")
                        val logos = if (current.mediaType == "movie") {
                            repository.getMovieImages(tmdbId).getOrNull()?.logos
                        } else {
                            repository.getTvImages(tmdbId).getOrNull()?.logos
                        }
                        
                        val bestLogo = logos?.firstOrNull { it.lang == "en" } 
                            ?: logos?.firstOrNull { it.lang == "he" }
                            ?: logos?.firstOrNull { it.lang == null }
                            ?: logos?.firstOrNull()
                            
                        if (bestLogo != null) {
                            fetchedLogoUrl = "https://image.tmdb.org/t/p/w500${bestLogo.filePath}"
                        }
                    } catch (e: Exception) {
                        if (e is kotlinx.coroutines.CancellationException) throw e
                    }
                    
                    val updatedList = _backdrops.value.toMutableList()
                    updatedList[index] = current.copy(logoUrl = fetchedLogoUrl, logoFetched = true)
                    _backdrops.value = updatedList
                }
            }
        }
    }

    private fun startRotation() {
        viewModelScope.launch {
            while (true) {
                delay(15000) // Rotate every 15 seconds
                if (_backdrops.value.isNotEmpty()) {
                    _currentIndex.value = (_currentIndex.value + 1) % _backdrops.value.size
                }
            }
        }
        
        // Fetch logo whenever currentIndex changes
        viewModelScope.launch {
            currentIndex.collect { index ->
                if (_backdrops.value.isNotEmpty()) {
                    val current = _backdrops.value[index]
                    if (!current.logoFetched) {
                        var fetchedLogoUrl: String? = null
                        try {
                            val tmdbId = current.id.substringAfter("_")
                            val logos = if (current.mediaType == "movie") {
                                repository.getMovieImages(tmdbId).getOrNull()?.logos
                            } else {
                                repository.getTvImages(tmdbId).getOrNull()?.logos
                            }
                            
                            val bestLogo = logos?.firstOrNull { it.lang == "en" } 
                                ?: logos?.firstOrNull { it.lang == "he" }
                                ?: logos?.firstOrNull { it.lang == null }
                                ?: logos?.firstOrNull()
                                
                            if (bestLogo != null) {
                                fetchedLogoUrl = "https://image.tmdb.org/t/p/w500${bestLogo.filePath}"
                            }
                        } catch (e: Exception) {
                            if (e is kotlinx.coroutines.CancellationException) throw e
                        }
                        
                        val updatedList = _backdrops.value.toMutableList()
                        // Ensure we are updating the correct item (it might have moved, but index should match)
                        updatedList[index] = current.copy(logoUrl = fetchedLogoUrl, logoFetched = true)
                        _backdrops.value = updatedList
                    }
                }
            }
        }
    }
}
