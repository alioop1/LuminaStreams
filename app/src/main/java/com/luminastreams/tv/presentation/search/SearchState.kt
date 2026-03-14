package com.luminastreams.tv.presentation.search

import androidx.compose.runtime.Immutable
import com.luminastreams.tv.domain.model.SearchResult
import androidx.compose.ui.graphics.Color

@Immutable
data class SearchState(
    val query: String = "",
    val isSearching: Boolean = false,
    val isVoiceListening: Boolean = false,
    val containsHebrew: Boolean = false,
    val results: List<SearchResult> = emptyList(),
    val searchHistory: List<String> = emptyList(),
    val trendingSearches: List<SearchResult> = emptyList(),

    // Batch 2: השלמה אוטומטית
    val autocompleteSuggestions: List<String> = emptyList(),

    // Smart Features Additions
    val suggestedCorrection: String? = null,
    val dynamicThemeColor: Color? = null,

    // Filters
    val filters: List<String> = listOf("הכל", "סרטים", "סדרות"),
    val selectedFilter: String = "הכל",

    val exactMatch: SearchResult? = null,
    val focusedItemUrl: String? = null
)

sealed interface SearchEvent {
    data class ShowError(val message: String) : SearchEvent
    data class NavigateToDetails(val id: String) : SearchEvent
    object TriggerHapticFeedback : SearchEvent
}