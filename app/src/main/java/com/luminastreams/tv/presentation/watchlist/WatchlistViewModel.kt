package com.luminastreams.tv.presentation.watchlist

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.LuminaApp
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn

class WatchlistViewModel(application: Application) : AndroidViewModel(application) {

    // Connected to the fast SQLite Room Repository
    private val watchlistRepository = (application as LuminaApp).watchlistRepository

    // OPTIMIZATION: Fully Reactive StateFlow.
    // The Repository already returns Flow<List<Movie>>, so we just connect it directly to the UI!
    val movies: StateFlow<List<Movie>> = watchlistRepository.getWatchlistFlow()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
}