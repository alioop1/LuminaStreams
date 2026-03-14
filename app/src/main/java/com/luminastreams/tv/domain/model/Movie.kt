package com.luminastreams.tv.domain.model

import androidx.compose.runtime.Immutable

@Immutable
data class Movie(
    val id: String,
    val title: String,
    val backdropUrl: String,
    val posterUrl: String,
    val overview: String,
    val rating: Float,
    val genre: String = "פעולה", // Kept safe for your DetailsScreen
    val mediaType: String = "movie", // NEW: "movie" or "tv"
    val genreIds: List<Int> = emptyList(), // NEW: TMDB genre IDs for strict filtering
    val is4K: Boolean = false,
    val resolutionBadge: String = "1080p",
    val progress: Float? = null
)