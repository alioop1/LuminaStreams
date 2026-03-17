package com.luminastreams.tv.presentation.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.data.local.WatchlistManager
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {
    private val watchlistManager = WatchlistManager(application)

    private val _movies = MutableStateFlow<List<Movie>>(emptyList())
    val movies: StateFlow<List<Movie>> = _movies.asStateFlow()

    fun loadWatchlist() {
        viewModelScope.launch(Dispatchers.IO) {
            _movies.value = watchlistManager.getWatchlist()
        }
    }
}