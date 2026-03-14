package com.luminastreams.tv.presentation.home

import androidx.compose.runtime.Immutable
import com.luminastreams.tv.domain.model.Movie

// ─────────────────────────────────────────────────────────────────
// HomeContract.kt
// אוסף: State + Events משותפים לשכבת Home.
// HomeScreen.kt → רק UI Composables
// HomeViewModel.kt → רק לוגיקה עסקית
// ─────────────────────────────────────────────────────────────────

// ── UI State ──────────────────────────────────────────────────────────────
@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val selectedTab: String = "סרטים",

    // Movies
    val movieTrending: List<Movie> = emptyList(),
    val moviePremieres: List<Movie> = emptyList(),
    val movieAction: List<Movie> = emptyList(),
    val movieTopRated: List<Movie> = emptyList(),
    val movieComedy: List<Movie> = emptyList(),
    val movieDrama: List<Movie> = emptyList(),
    val movieScifi: List<Movie> = emptyList(),
    val movieHorror: List<Movie> = emptyList(),
    val movieAnimation: List<Movie> = emptyList(),

    // TV
    val tvTrending: List<Movie> = emptyList(),
    val tvPremieres: List<Movie> = emptyList(),
    val tvDrama: List<Movie> = emptyList(),
    val tvComedy: List<Movie> = emptyList(),
    val tvCrime: List<Movie> = emptyList(),
    val tvScifi: List<Movie> = emptyList(),
    val tvDocumentary: List<Movie> = emptyList(),
    val tvTopRated: List<Movie> = emptyList(),

    // Focus & UI
    val focusedItem: Movie? = null,
    val focusedRowTitle: String = "",
    val isFocusedVertical: Boolean = false,

    // Discovery / filter
    val isDiscoveryMode: Boolean = false,
    val selectedGenreId: String? = null,
    val selectedGenreName: String = "",
    val selectedYear: String? = null,
    val isFilterComplete: Boolean = false,
    val discoveryResults: List<Movie> = emptyList()
)

// ── UI Events ─────────────────────────────────────────────────────────────
sealed interface HomeEvent {
    data class SelectTab(val tab: String) : HomeEvent
    data class SetGenreFilter(val id: String, val name: String) : HomeEvent
    data class StudioClicked(val studioName: String) : HomeEvent
    object ClearGenre : HomeEvent
}
