package com.luminastreams.tv.presentation.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.LuminaApp
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {

    // OPTIMIZATION: Switched to the fast SQLite Room Repository
    private val watchlistRepository = (application as LuminaApp).watchlistRepository

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    fun loadWatchlist() {
        viewModelScope.launch(Dispatchers.IO) {
            // Uses the sync method to fetch the list on a background thread
            _movies.value = watchlistRepository.getWatchlistSync()
        }
    }
}