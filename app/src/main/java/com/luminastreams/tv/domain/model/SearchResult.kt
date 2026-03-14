package com.luminastreams.tv.domain.model

import androidx.compose.runtime.Immutable

enum class MediaType(val label: String, val hebrewPlural: String) {
    MOVIE("סרט", "סרטים"),
    TV_SHOW("סדרה", "סדרות"),
    PERSON("שחקן", "שחקנים")
}

@Immutable
data class SearchResult(
    val id: String,
    val title: String,
    val posterUrl: String,
    val backdropUrl: String? = null,
    val logoUrl: String? = null,
    val type: MediaType,
    val rating: Float = 0f,
    val releaseYear: String = "",
    val overview: String = "",
    val matchScore: Int = 0,
    val dominantColor: String? = null
)