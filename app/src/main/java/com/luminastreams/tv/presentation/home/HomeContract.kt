package com.luminastreams.tv.presentation.home

import com.luminastreams.tv.domain.model.Movie

enum class StudioBrand { NETFLIX, APPLE_TV, DISNEY, HBO, AMAZON, PARAMOUNT, HULU }

sealed class RowDef {
    abstract val id: String
    data class Regular(override val id: String, val title: String, val movies: List<Movie>) : RowDef()
    data class Studio (override val id: String, val brand: StudioBrand, val movies: List<Movie>) : RowDef()
    object StudioRibbon : RowDef() { override val id = "ribbon" }
}

data class HomeState(
    val isLoading:            Boolean      = true,
    val error:                String?      = null,
    val selectedTab:          String       = "ראשי",
    val selectedStudioFilter: String?      = null,
    // ── Movies ──────────────────────────────────────────────────────────────
    val movieTrending:  List<Movie>  = emptyList(),
    val moviePremieres: List<Movie>  = emptyList(),
    val movieAction:    List<Movie>  = emptyList(),
    val movieDrama:     List<Movie>  = emptyList(),
    val movieScifi:     List<Movie>  = emptyList(),
    val movieTopRated:  List<Movie>  = emptyList(),
    val movieNetflix:   List<Movie>  = emptyList(),
    val movieAppleTV:   List<Movie>  = emptyList(),
    val movieDisney:    List<Movie>  = emptyList(),
    val movieHBO:       List<Movie>  = emptyList(),
    val movieAmazon:    List<Movie>  = emptyList(),
    val movieParamount: List<Movie>  = emptyList(),
    val movieHulu:      List<Movie>  = emptyList(),
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
    val tvHBO:          List<Movie>  = emptyList(),
    val tvAmazon:       List<Movie>  = emptyList(),
    val tvParamount:    List<Movie>  = emptyList(),
    val tvHulu:         List<Movie>  = emptyList(),
    // ── Fuzer ───────────────────────────────────────────────────────────────
    val fuzerItems:           List<Movie>  = emptyList(),
    val fuzerMovies:          List<Movie>  = emptyList(),
    val fuzerSeries:          List<Movie>  = emptyList(),
    val fuzerMoviesHD:        List<Movie>  = emptyList(),
    val fuzerSeriesHD:        List<Movie>  = emptyList(),
    val fuzerMovies4K:        List<Movie>  = emptyList(),
    val fuzerSeries4K:        List<Movie>  = emptyList(),
    val fuzerDubbedMovies:    List<Movie>  = emptyList(),
    val fuzerDubbedSeries:    List<Movie>  = emptyList(),
    val fuzerIsLoading:       Boolean      = false,
    val fuzerError:           String?      = null,
    val fuzerSelectedCat:     Int          = 1,
    val fuzerCurrentPage:     Int          = 1,
    val fuzerHasMore:         Boolean      = true,
    // ── Discovery ───────────────────────────────────────────────────────────
    val isFilterComplete:  Boolean      = false,
    val selectedGenreName: String       = "",
    val discoveryResults:  List<Movie>  = emptyList(),
    val focusedItem:       Movie?       = null,
)

// כל קטגוריות Fuzer עם ID וכותרת
enum class FuzerCategory(val catId: Int, val displayName: String) {
    MOVIES          (1,  "סרטים"),
    SERIES          (2,  "סדרות"),
    MOVIES_HD       (41, "סרטים HD"),
    SERIES_HD       (42, "סדרות HD"),
    MOVIES_4K       (65, "סרטים 4K"),
    SERIES_4K       (66, "סדרות 4K"),
    DUBBED_MOVIES   (83, "סרטים מדובבים"),
    DUBBED_SERIES   (84, "סדרות מדובבות"),
    ANIMATION       (14, "אנימציה"),
    DOCUMENTARY     (33, "דוקומנטרי"),
    KIDS            (31, "ילדים"),
    ISRAELI         (49, "ישראלי"),
    FOREIGN         (6,  "זר"),
    SPORTS          (54, "ספורט"),
}