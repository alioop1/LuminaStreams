package com.luminastreams.tv.presentation.home

import com.luminastreams.tv.domain.model.Movie

// ══════════════════════════════════════════════════════════════════════════════
//  HomeContract.kt — single source of truth for HomeScreen state & events
//  Updated: added Netflix / Apple TV+ / Disney+ category fields
// ══════════════════════════════════════════════════════════════════════════════

data class HomeState(
    val isLoading:      Boolean      = true,
    val error:          String?      = null,
    val selectedTab:    String       = "סרטים",
    // ── Movies ──────────────────────────────────────────────────────────────
    val movieTrending:  List<Movie>  = emptyList(),
    val moviePremieres: List<Movie>  = emptyList(),
    val movieAction:    List<Movie>  = emptyList(),
    val movieDrama:     List<Movie>  = emptyList(),
    val movieScifi:     List<Movie>  = emptyList(),
    val movieTopRated:  List<Movie>  = emptyList(),
    val movieNetflix:   List<Movie>  = emptyList(),   // Netflix provider (id=8)
    val movieAppleTV:   List<Movie>  = emptyList(),   // Apple TV+ provider (id=350)
    val movieDisney:    List<Movie>  = emptyList(),   // Disney+ provider (id=337)
    // ── TV Shows ────────────────────────────────────────────────────────────
    val tvTrending:     List<Movie>  = emptyList(),
    val tvPremieres:    List<Movie>  = emptyList(),
    val tvDrama:        List<Movie>  = emptyList(),
    val tvCrime:        List<Movie>  = emptyList(),
    val tvScifi:        List<Movie>  = emptyList(),
    val tvTopRated:     List<Movie>  = emptyList(),
    val tvNetflix:      List<Movie>  = emptyList(),
    val tvAppleTV:      List<Movie>  = emptyList(),
    val tvDisney:       List<Movie>  = emptyList(),
)

sealed class HomeEvent {
    data class SelectTab(val tab: String) : HomeEvent()
    object Retry                          : HomeEvent()
}