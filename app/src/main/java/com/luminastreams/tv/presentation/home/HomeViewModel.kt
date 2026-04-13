package com.luminastreams.tv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.Constants
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.data.remote.FuzerEngine
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.Collections
import java.util.concurrent.TimeUnit

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val pageMap    = mutableMapOf<String, Int>()
    private val loadingSet: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())

    private val imgBase = "https://image.tmdb.org/t/p"

    // ── Image size selection ────────────────────────────────────────────────────
    //
    // FIXED: Previously LOW tier used "w500" for backdrops and "w342" for posters.
    // Both are phone-grade resolutions — completely unacceptable on even a 720p TV.
    //
    // New floors:
    //   HIGH → original / w780   (4K displays, no compromise)
    //   MID  → w1280 / w780      (1080p panels)
    //   LOW  → w780 / w500       (720p panels, still 4× better than w342)
    //
    // These match Constants.kt exactly so there is one source of truth.
    //
    private val backdropSize: String get() = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> "original"   // full-res for SHIELD / LG OLED
        DeviceProfile.Tier.MID  -> "w1280"      // sharp on 1080p
        DeviceProfile.Tier.LOW  -> "w780"       // ← was "w500"; raised for TV panels
    }

    private val posterSize: String get() = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> "original"   // ← was "w780"; original for 4K grids
        DeviceProfile.Tier.MID  -> "w780"       // ← was "w500"
        DeviceProfile.Tier.LOW  -> "w500"       // ← was "w342"; minimum for TV
    }

    // Connection pool: fewer parallel connections on LOW/MID → less RAM pressure
    private val maxConnections = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> 8
        DeviceProfile.Tier.MID  -> 5
        DeviceProfile.Tier.LOW  -> 3
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(maxConnections, 5, TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .build()

    init {
        loadAll()
        // Auto-select the featured studio (most recent launch year) on startup
        selectStudio(StudioBrand.featuredDefault())
    }

    fun selectTab(tab: String) = _state.update { it.copy(selectedTab = tab) }

    fun setStudioFilter(studio: String?) =
        _state.update { it.copy(selectedStudioFilter = studio) }

    fun retry() {
        if (_state.value.selectedTab == "Fuzer") loadFuzerContent()
        else loadAll()
    }

    // ── Studio Selection ─────────────────────────────────────────────────────

    private var studioCatalogJob: Job? = null

    /**
     * Called when the user focuses/selects a studio from the persistent ribbon.
     * Cancels any prior in-flight load, updates [currentStudioId], then
     * fetches the new catalog (new-releases + categorized genre rows).
     */
    fun selectStudio(brand: StudioBrand) {
        if (_state.value.currentStudioId == brand &&
            _state.value.currentStudioCatalog != null) return

        studioCatalogJob?.cancel()
        _state.update { it.copy(currentStudioId = brand, studioCatalogLoading = true) }

        studioCatalogJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val catalog = loadStudioCatalog(brand)
                _state.update { it.copy(currentStudioCatalog = catalog, studioCatalogLoading = false) }
            } catch (e: Exception) {
                _state.update { it.copy(studioCatalogLoading = false) }
            }
        }
    }

    /**
     * Loads a full [StudioCatalog] for the given [StudioBrand]:
     * - New Releases row (sorted by release_date desc) — shown as 16:9 landscape cards
     * - Genre category rows — shown as 2:3 portrait cards
     */
    private suspend fun loadStudioCatalog(brand: StudioBrand): StudioCatalog = coroutineScope {
        val k      = Constants.TMDB_API_KEY
        val region = "US"
        val lang   = "en-US"

        val movieFilter = when {
            brand.tmdbMovieProviderIds.isNotEmpty() ->
                "with_watch_providers=${brand.tmdbMovieProviderIds}&watch_region=$region"
            brand.tmdbMovieCompanyIds.isNotEmpty()  ->
                "with_companies=${brand.tmdbMovieCompanyIds}"
            else -> ""
        }
        val tvFilter = if (brand.tmdbTvNetworkIds.isNotEmpty())
            "with_networks=${brand.tmdbTvNetworkIds}" else movieFilter

        // ── New Releases (hero landscape row) — sorted by release_date desc ──
        val newReleasesDef = async {
            fetch(
                "$BASE/discover/movie?api_key=$k&language=$lang" +
                (if (movieFilter.isNotEmpty()) "&$movieFilter" else "") +
                "&sort_by=release_date.desc",
                "movie"
            ).take(15)
        }

        // ── Genre category rows (portrait cards) ──────────────────────────────
        data class GenreSpec(val label: String, val movieGenreId: Int, val tvGenreId: Int)

        val genres = listOf(
            GenreSpec("Action",    28,    10759),
            GenreSpec("Drama",     18,    18),
            GenreSpec("Animation", 16,    16),
            GenreSpec("Comedy",    35,    35),
            GenreSpec("Sci-Fi",    878,   10765),
            GenreSpec("Thriller",  53,    80),
            GenreSpec("Family",    10751, 10762),
        )

        // Fire all genre requests in parallel, merge movie + TV results per genre
        val genreDeferred = genres.map { spec ->
            spec to async {
                val mUrl = "$BASE/discover/movie?api_key=$k&language=$lang" +
                    "&with_genres=${spec.movieGenreId}" +
                    (if (movieFilter.isNotEmpty()) "&$movieFilter" else "") +
                    "&sort_by=popularity.desc"
                val tUrl = "$BASE/discover/tv?api_key=$k&language=$lang" +
                    "&with_genres=${spec.tvGenreId}" +
                    (if (tvFilter.isNotEmpty()) "&$tvFilter" else "") +
                    "&sort_by=popularity.desc"
                val movies = async { fetch(mUrl, "movie") }
                val shows  = async { fetch(tUrl, "tv") }
                (movies.await() + shows.await())
                    .sortedByDescending { it.rating }
                    .take(20)
            }
        }

        val newReleases = newReleasesDef.await()
        val categoryRows = genreDeferred.mapNotNull { (spec, deferred) ->
            val items = deferred.await()
            if (items.isNotEmpty()) StudioCategoryRow(spec.label, items) else null
        }

        StudioCatalog(brand = brand, newReleases = newReleases, categoryRows = categoryRows)
    }

    // ── Pagination ─────────────────────────────────────────────────────────────
    fun loadMore(id: String) {
        if (id == "ribbon" || id.startsWith("fuzer") || loadingSet.contains(id)) return
        loadingSet.add(id)
        val nextPage = (pageMap[id] ?: 1) + 1

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val k      = Constants.TMDB_API_KEY
                val region = "US"
                var url    = ""
                var mt     = ""

                when (id) {
                    "movieTrending"  -> { url = "$BASE/trending/movie/week?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                    "moviePremieres" -> { url = "$BASE/movie/now_playing?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                    "movieAction"    -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieDrama"     -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieScifi"     -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=878&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieTopRated"  -> { url = "$BASE/movie/top_rated?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                    "movieNetflix"   -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieAppleTV"   -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieDisney"    -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_companies=2|3|420&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieHBO"       -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899|384&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieAmazon"    -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieParamount" -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_companies=4&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieHulu"      -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "tvTrending"     -> { url = "$BASE/trending/tv/week?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                    "tvPremieres"    -> { url = "$BASE/tv/on_the_air?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                    "tvDrama"        -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvCrime"        -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvScifi"        -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=10765&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvTopRated"     -> { url = "$BASE/tv/top_rated?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                    "tvNetflix"      -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=213&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvAppleTV"      -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2552&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvDisney"       -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2739&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvHBO"          -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=49|3186&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvAmazon"       -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=1024&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvParamount"    -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=4330|67&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvHulu"         -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=453&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                }

                if (url.isNotEmpty()) {
                    val newItems = fetch(url, mt)
                    if (newItems.isNotEmpty()) {
                        pageMap[id] = nextPage
                        _state.update { s ->
                            when (id) {
                                "movieTrending"  -> s.copy(movieTrending  = s.movieTrending  + newItems)
                                "moviePremieres" -> s.copy(moviePremieres = s.moviePremieres + newItems)
                                "movieAction"    -> s.copy(movieAction    = s.movieAction    + newItems)
                                "movieDrama"     -> s.copy(movieDrama     = s.movieDrama     + newItems)
                                "movieScifi"     -> s.copy(movieScifi     = s.movieScifi     + newItems)
                                "movieTopRated"  -> s.copy(movieTopRated  = s.movieTopRated  + newItems)
                                "movieNetflix"   -> s.copy(movieNetflix   = s.movieNetflix   + newItems)
                                "movieAppleTV"   -> s.copy(movieAppleTV   = s.movieAppleTV   + newItems)
                                "movieDisney"    -> s.copy(movieDisney    = s.movieDisney    + newItems)
                                "movieHBO"       -> s.copy(movieHBO       = s.movieHBO       + newItems)
                                "movieAmazon"    -> s.copy(movieAmazon    = s.movieAmazon    + newItems)
                                "movieParamount" -> s.copy(movieParamount = s.movieParamount + newItems)
                                "movieHulu"      -> s.copy(movieHulu      = s.movieHulu      + newItems)
                                "tvTrending"     -> s.copy(tvTrending     = s.tvTrending     + newItems)
                                "tvPremieres"    -> s.copy(tvPremieres    = s.tvPremieres    + newItems)
                                "tvDrama"        -> s.copy(tvDrama        = s.tvDrama        + newItems)
                                "tvCrime"        -> s.copy(tvCrime        = s.tvCrime        + newItems)
                                "tvScifi"        -> s.copy(tvScifi        = s.tvScifi        + newItems)
                                "tvTopRated"     -> s.copy(tvTopRated     = s.tvTopRated     + newItems)
                                "tvNetflix"      -> s.copy(tvNetflix      = s.tvNetflix      + newItems)
                                "tvAppleTV"      -> s.copy(tvAppleTV      = s.tvAppleTV      + newItems)
                                "tvDisney"       -> s.copy(tvDisney       = s.tvDisney       + newItems)
                                "tvHBO"          -> s.copy(tvHBO          = s.tvHBO          + newItems)
                                "tvAmazon"       -> s.copy(tvAmazon       = s.tvAmazon       + newItems)
                                "tvParamount"    -> s.copy(tvParamount    = s.tvParamount    + newItems)
                                "tvHulu"         -> s.copy(tvHulu         = s.tvHulu         + newItems)
                                else -> s
                            }
                        }
                    }
                }
            } finally {
                loadingSet.remove(id)
            }
        }
    }

    // ── Fuzer ─────────────────────────────────────────────────────────────────
    fun loadFuzerContent() {
        viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.fuzerMovies.isEmpty() && _state.value.fuzerSeries.isEmpty()) {
                _state.update { it.copy(fuzerIsLoading = true, fuzerError = null) }
            }

            try {
                coroutineScope {
                    val moviesDef = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES, 1).getOrElse { emptyList() } }
                    val seriesDef = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES, 1).getOrElse { emptyList() } }
                    val moviesR = moviesDef.await()
                    val seriesR = seriesDef.await()
                    _state.update { s -> s.copy(
                        fuzerIsLoading = false,
                        fuzerItems     = moviesR + seriesR,
                        fuzerMovies    = moviesR,
                        fuzerSeries    = seriesR
                    )}
                }

                delay(400)

                coroutineScope {
                    val moviesHdDef = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES_HD, 1).getOrElse { emptyList() } }
                    val seriesHdDef = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES_HD, 1).getOrElse { emptyList() } }
                    val movies4kDef = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES_4K, 1).getOrElse { emptyList() } }
                    val series4kDef = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES_4K, 1).getOrElse { emptyList() } }
                    _state.update { s -> s.copy(
                        fuzerMoviesHD = moviesHdDef.await(),
                        fuzerSeriesHD = seriesHdDef.await(),
                        fuzerMovies4K = movies4kDef.await(),
                        fuzerSeries4K = series4kDef.await()
                    )}
                }

                delay(400)

                coroutineScope {
                    val dubbedMoviesDef = async { FuzerEngine.getCategoryPage(FuzerCats.DUBBED_MOVIES, 1).getOrElse { emptyList() } }
                    val dubbedSeriesDef = async { FuzerEngine.getCategoryPage(FuzerCats.DUBBED_SERIES, 1).getOrElse { emptyList() } }
                    _state.update { s -> s.copy(
                        fuzerDubbedMovies = dubbedMoviesDef.await(),
                        fuzerDubbedSeries = dubbedSeriesDef.await()
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(fuzerIsLoading = false, fuzerError = "שגיאת טעינה: ${e.message}") }
            }
        }
    }

    // ── Load all rows ──────────────────────────────────────────────────────────
    private fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val k      = Constants.TMDB_API_KEY
                val region = "US"

                // Wave 1: above-the-fold
                coroutineScope {
                    val mTrend  = async { fetch("$BASE/trending/movie/week?api_key=$k&language=en-US", "movie") }
                    val tvTrend = async { fetch("$BASE/trending/tv/week?api_key=$k&language=en-US",   "tv") }
                    val mNow    = async { fetch("$BASE/movie/now_playing?api_key=$k&language=en-US",  "movie") }
                    val mNflx   = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc", "movie") }

                    _state.update { s -> s.copy(
                        isLoading      = false,
                        movieTrending  = mTrend.await(),
                        tvTrending     = tvTrend.await(),
                        moviePremieres = mNow.await(),
                        movieNetflix   = mNflx.await()
                    ) }
                }

                // Wave 2: secondary content
                if (DeviceProfile.tier == DeviceProfile.Tier.LOW) {
                    loadWave2Batched(k, region, batchSize = 3, delayMs = 250)
                } else {
                    loadWave2Parallel(k, region)
                }

            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Network error: ${e.message}") }
            }
        }
    }

    private suspend fun loadWave2Parallel(k: String, region: String) {
        coroutineScope {
            val mAction    = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc",   "movie") }
            val mDrama     = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc",   "movie") }
            val mScifi     = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=878&sort_by=popularity.desc",  "movie") }
            val mTop       = async { fetch("$BASE/movie/top_rated?api_key=$k&language=en-US",                                          "movie") }
            val mHBO       = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899|384&watch_region=$region&sort_by=popularity.desc", "movie") }
            val mAmazon    = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc",  "movie") }
            val mApple     = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc",  "movie") }
            val mDisney    = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_companies=2|3|420&sort_by=popularity.desc",  "movie") }
            val mParamount = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_companies=4&sort_by=popularity.desc",  "movie") }
            val mHulu      = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc",   "movie") }
            val tvAir      = async { fetch("$BASE/tv/on_the_air?api_key=$k&language=en-US",                                             "tv") }
            val tvDrama    = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc",        "tv") }
            val tvCrime    = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc",        "tv") }
            val tvScifi    = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=10765&sort_by=popularity.desc",     "tv") }
            val tvTop      = async { fetch("$BASE/tv/top_rated?api_key=$k&language=en-US",                                              "tv") }
            val tvNflx     = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=213&sort_by=popularity.desc",    "tv") }
            val tvApple    = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2552&sort_by=popularity.desc",  "tv") }
            val tvDisney   = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2739&sort_by=popularity.desc",  "tv") }
            val tvHBO      = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=49|3186&sort_by=popularity.desc", "tv") }
            val tvAmazon   = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=1024&sort_by=popularity.desc",  "tv") }
            val tvParamount= async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=4330|67&sort_by=popularity.desc",  "tv") }
            val tvHulu     = async { fetch("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=453&sort_by=popularity.desc",   "tv") }

            _state.update { s -> s.copy(
                movieAction   = mAction.await(),
                movieDrama    = mDrama.await(),
                movieScifi    = mScifi.await(),
                movieTopRated = mTop.await()
            ) }; delay(80)

            _state.update { s -> s.copy(
                movieHBO       = mHBO.await(),
                movieAmazon    = mAmazon.await(),
                movieAppleTV   = mApple.await(),
                movieDisney    = mDisney.await(),
                movieParamount = mParamount.await(),
                movieHulu      = mHulu.await()
            ) }; delay(80)

            _state.update { s -> s.copy(
                tvPremieres = tvAir.await(),
                tvDrama     = tvDrama.await(),
                tvCrime     = tvCrime.await(),
                tvScifi     = tvScifi.await(),
                tvTopRated  = tvTop.await()
            ) }; delay(80)

            _state.update { s -> s.copy(
                tvNetflix   = tvNflx.await(),
                tvAppleTV   = tvApple.await(),
                tvDisney    = tvDisney.await(),
                tvHBO       = tvHBO.await(),
                tvAmazon    = tvAmazon.await(),
                tvParamount = tvParamount.await(),
                tvHulu      = tvHulu.await()
            ) }
        }
    }

    private suspend fun loadWave2Batched(k: String, region: String, batchSize: Int, delayMs: Long) {
        val requests: List<Pair<String, suspend (List<Movie>) -> Unit>> = listOf(
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc") { v -> _state.update { it.copy(movieAction = v) } },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc") { v -> _state.update { it.copy(movieDrama = v) } },
            Pair("$BASE/movie/top_rated?api_key=$k&language=en-US") { v -> _state.update { it.copy(movieTopRated = v) } },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899|384&watch_region=$region&sort_by=popularity.desc") { v -> _state.update { it.copy(movieHBO = v) } },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc") { v -> _state.update { it.copy(movieAmazon = v) } },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc") { v -> _state.update { it.copy(tvDrama = v) } },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc") { v -> _state.update { it.copy(tvCrime = v) } },
            Pair("$BASE/tv/top_rated?api_key=$k&language=en-US") { v -> _state.update { it.copy(tvTopRated = v) } },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=213&sort_by=popularity.desc") { v -> _state.update { it.copy(tvNetflix = v) } },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=49|3186&sort_by=popularity.desc") { v -> _state.update { it.copy(tvHBO = v) } },
        )

        requests.chunked(batchSize).forEach { batch ->
            coroutineScope {
                batch.forEach { (url, updater) ->
                    launch {
                        val mt = if (url.contains("/movie")) "movie" else "tv"
                        updater(fetch(url, mt))
                    }
                }
            }
            delay(delayMs)
        }
    }

    // ── Fetch helper ───────────────────────────────────────────────────────────
    private suspend fun fetch(url: String, mediaType: String): List<Movie> =
        withContext(Dispatchers.IO) {
            try {
                val body = http.newCall(Request.Builder().url(url).build())
                    .execute().use { it.body?.string() } ?: return@withContext emptyList()
                val arr = JSONObject(body).optJSONArray("results")
                    ?: return@withContext emptyList()

                val out = mutableListOf<Movie>()
                for (i in 0 until arr.length()) {
                    val j  = arr.getJSONObject(i)
                    val mt = j.optString("media_type").ifBlank { mediaType }

                    val backdropRaw = j.optString("backdrop_path")
                    if (backdropRaw.isBlank() || backdropRaw == "null") continue

                    val title = if (mt == "tv")
                        j.optString("name").ifBlank { j.optString("original_name") }
                    else
                        j.optString("title").ifBlank { j.optString("original_title") }

                    val date       = if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")
                    val posterPath = j.optString("poster_path")
                    if (posterPath.isBlank() || posterPath == "null") continue

                    out += Movie(
                        id              = "${mt}_${j.optInt("id")}",
                        title           = title,
                        posterUrl       = "$imgBase/$posterSize$posterPath",
                        backdropUrl     = "$imgBase/$backdropSize$backdropRaw",
                        overview        = j.optString("overview"),
                        year            = date.take(4).toIntOrNull() ?: 0,
                        genre           = genreLabel(j.optJSONArray("genre_ids")?.optInt(0, 0) ?: 0, mt),
                        rating          = j.optDouble("vote_average", 0.0).toFloat(),
                        mediaType       = mt,
                        resolutionBadge = ""
                    )
                }
                out
            } catch (_: Exception) { emptyList() }
        }

    private fun genreLabel(id: Int, mt: String): String = when (id) {
        28 -> "Action"; 12 -> "Adventure"; 16 -> "Animation"; 35 -> "Comedy"
        80 -> "Crime"; 99 -> "Documentary"; 18 -> "Drama"; 10751 -> "Family"
        14 -> "Fantasy"; 36 -> "History"; 27 -> "Horror"; 10402 -> "Music"
        9648 -> "Mystery"; 10749 -> "Romance"; 878 -> "Sci-Fi"; 10770 -> "TV Movie"
        53 -> "Thriller"; 10752 -> "War"; 37 -> "Western"
        10759 -> "Action & Adventure"; 10762 -> "Kids"; 10763 -> "News"
        10764 -> "Reality"; 10765 -> "Sci-Fi & Fantasy"; 10766 -> "Soap"
        10767 -> "Talk"; 10768 -> "War & Politics"
        else -> if (mt == "tv") "TV Show" else "Movie"
    }

    private companion object {
        private const val BASE = "https://api.themoviedb.org/3"
    }
}
