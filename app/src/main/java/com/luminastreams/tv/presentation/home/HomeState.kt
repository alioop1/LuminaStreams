package com.luminastreams.tv.presentation.home

import androidx.compose.runtime.Immutable
import com.luminastreams.tv.domain.model.Movie

@Immutable
data class HomeState(
    val isLoading: Boolean = true,
    val error: String? = null,
    val isNetworkAvailable: Boolean = true,

    val focusedItem: Movie? = null,
    val focusedRowTitle: String = "Trending Movies",
    val isFocusedVertical: Boolean = true,

    val selectedTab: String = "סרטים",
    val isSidebarFocused: Boolean = false,

    val isDiscoveryMode: Boolean = false,
    val isFilterComplete: Boolean = false,
    val discoveryResults: List<Movie> = emptyList(),
    val selectedGenreId: String? = null,
    val selectedGenreName: String = "הכל",
    val selectedYear: String? = null,
    val selectedYearName: String = "הכל",

    val movieTrending: List<Movie> = emptyList(),
    val moviePremieres: List<Movie> = emptyList(),
    val movieTopRated: List<Movie> = emptyList(),
    val movieAction: List<Movie> = emptyList(),
    val movieComedy: List<Movie> = emptyList(),
    val movieDrama: List<Movie> = emptyList(),
    val movieScifi: List<Movie> = emptyList(),
    val movieAnimation: List<Movie> = emptyList(),
    val movieHorror: List<Movie> = emptyList(),

    val tvTrending: List<Movie> = emptyList(),
    val tvPremieres: List<Movie> = emptyList(),
    val tvTopRated: List<Movie> = emptyList(),
    val tvDrama: List<Movie> = emptyList(),
    val tvComedy: List<Movie> = emptyList(),
    val tvCrime: List<Movie> = emptyList(),
    val tvScifi: List<Movie> = emptyList(),
    val tvDocumentary: List<Movie> = emptyList()
)