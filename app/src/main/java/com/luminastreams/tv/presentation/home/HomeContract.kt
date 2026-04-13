package com.luminastreams.tv.presentation.home

import com.luminastreams.tv.domain.model.Movie

// ─────────────────────────────────────────────────────────────────────────────
// Studio brand definitions
// ─────────────────────────────────────────────────────────────────────────────

enum class StudioBrand(
    val displayName: String,
    val tmdbMovieCompanyIds: String,   // pipe-separated TMDB company IDs for movies
    val tmdbMovieProviderIds: String,  // pipe-separated watch-provider IDs for movies
    val tmdbTvNetworkIds: String,      // pipe-separated network IDs for TV
    val releaseYear: Int               // used for "featured" auto-selection (most recent launch)
) {
    NETFLIX(
        displayName          = "Netflix",
        tmdbMovieCompanyIds  = "",
        tmdbMovieProviderIds = "8",
        tmdbTvNetworkIds     = "213",
        releaseYear          = 2007
    ),
    APPLE_TV(
        displayName          = "Apple TV+",
        tmdbMovieCompanyIds  = "",
        tmdbMovieProviderIds = "350",
        tmdbTvNetworkIds     = "2552",
        releaseYear          = 2019
    ),
    DISNEY(
        displayName          = "Disney+",
        tmdbMovieCompanyIds  = "2|3|420",
        tmdbMovieProviderIds = "",
        tmdbTvNetworkIds     = "2739",
        releaseYear          = 2019
    ),
    HBO(
        displayName          = "Max",
        tmdbMovieCompanyIds  = "",
        tmdbMovieProviderIds = "1899|384",
        tmdbTvNetworkIds     = "49|3186",
        releaseYear          = 2010
    ),
    AMAZON(
        displayName          = "Prime Video",
        tmdbMovieCompanyIds  = "",
        tmdbMovieProviderIds = "119",
        tmdbTvNetworkIds     = "1024",
        releaseYear          = 2016
    ),
    PARAMOUNT(
        displayName          = "Paramount+",
        tmdbMovieCompanyIds  = "4",
        tmdbMovieProviderIds = "",
        tmdbTvNetworkIds     = "4330|67",
        releaseYear          = 2021
    ),
    HULU(
        displayName          = "Hulu",
        tmdbMovieCompanyIds  = "",
        tmdbMovieProviderIds = "15",
        tmdbTvNetworkIds     = "453",
        releaseYear          = 2008
    );

    companion object {
        /** Returns the studio with the most-recent `releaseYear` — used as default featured studio on launch. */
        fun featuredDefault(): StudioBrand = entries.maxBy { it.releaseYear }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Row definitions
// ─────────────────────────────────────────────────────────────────────────────

sealed class RowDef {
    abstract val id: String
    data class Regular(override val id: String, val title: String, val movies: List<Movie>) : RowDef()
    data class Studio(override val id: String, val brand: StudioBrand, val movies: List<Movie>) : RowDef()
    /** Persistent studio-brand selector ribbon — never removed from composition. */
    object StudioRibbon : RowDef() { override val id = "ribbon" }
}

// ─────────────────────────────────────────────────────────────────────────────
// Studio catalog row — organized by genre category
// ─────────────────────────────────────────────────────────────────────────────

data class StudioCategoryRow(
    val genreLabel: String,          // e.g. "Action", "Drama", "Animation"
    val movies: List<Movie>
)

data class StudioCatalog(
    val brand: StudioBrand,
    val newReleases: List<Movie>,    // Landscape (16:9) hero row
    val categoryRows: List<StudioCategoryRow>  // Portrait (2:3) category rows
)

// ─────────────────────────────────────────────────────────────────────────────
// Full home state
// ─────────────────────────────────────────────────────────────────────────────

data class HomeState(
    val isLoading:            Boolean       = true,
    val error:                String?       = null,
    val selectedTab:          String        = "ראשי",
    val selectedStudioFilter: String?       = null,
    // ── Studio navigation ────────────────────────────────────────────────────
    val currentStudioId:      StudioBrand   = StudioBrand.featuredDefault(),
    val currentStudioCatalog: StudioCatalog? = null,
    val studioCatalogLoading: Boolean       = false,
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

/**
 * Fuzer category IDs — used by HomeViewModel.loadFuzerContent().
 * Each entry maps a human-readable name to the numeric cat ID on fuzer.xyz.
 */
object FuzerCats {
    const val MOVIES         = 2
    const val SERIES         = 1
    const val MOVIES_HD      = 42
    const val SERIES_HD      = 41
    const val MOVIES_4K      = 66
    const val SERIES_4K      = 65
    const val DUBBED_MOVIES  = 84
    const val DUBBED_SERIES  = 83
}
