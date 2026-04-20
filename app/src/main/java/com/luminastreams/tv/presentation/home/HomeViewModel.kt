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
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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

    private val _uiRows = MutableStateFlow<List<RowDef>>(emptyList())
    val uiRows: StateFlow<List<RowDef>> = _uiRows.asStateFlow()

    private val pageMap = mutableMapOf<String, Int>()
    private val loadingSet: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private var activeJob: Job? = null
    private var currentIsRtl = false

    private val imgBase = "https://image.tmdb.org/t/p"

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
        viewModelScope.launch(Dispatchers.Default) {
            _state.collectLatest { currentState ->
                recalculateRows(currentState)
            }
        }
        loadAll()
    }

    fun selectTab(tab: String) = _state.update { it.copy(selectedTab = tab) }

    fun setStudioFilter(studio: String?) = _state.update { it.copy(selectedStudioFilter = studio) }

    fun setLanguage(isRtl: Boolean) {
        if (currentIsRtl != isRtl) {
            currentIsRtl = isRtl
            _state.update { it.copy() }
        }
    }

    fun retry() {
        if (_state.value.selectedTab == "Fuzer") loadFuzerContent() else loadAll()
    }

    private fun mergeStudioContent(movies: List<Movie>, series: List<Movie>): List<Movie> {
        return (movies + series).sortedByDescending { it.rating }.distinctBy { it.id }
    }

    private fun generateStudioRows(baseId: String, brand: StudioBrand, movies: List<Movie>, _tr: (String, String) -> String): List<RowDef> {
        val list = mutableListOf<RowDef>()
        val uniqueMovies = movies.distinctBy { it.id }
        if (uniqueMovies.isEmpty()) return list

        list.add(RowDef.Studio("${baseId}::new", brand, uniqueMovies))

        val ani = uniqueMovies.filter {
            it.genre.contains("Animation", ignoreCase = true) ||
                    it.genre.contains("Kids", ignoreCase = true) ||
                    it.genre.contains("Family", ignoreCase = true) ||
                    it.genre.contains("אנימציה")
        }
        if (ani.isNotEmpty()) {
            list.add(RowDef.Regular("${baseId}::ani", _tr("Animation", "אנימציה"), ani))
        }

        val top = uniqueMovies.filter { it.rating > 0f }.sortedByDescending { it.rating }
        if (top.isNotEmpty()) {
            list.add(RowDef.Regular("${baseId}::top", _tr("Best of All Time", "הכי טוב בכל הזמנים"), top))
        }
        return list
    }

    private suspend fun recalculateRows(currentState: HomeState) = withContext(Dispatchers.Default) {
        val trFunc = { en: String, he: String -> if (currentIsRtl) he else en }
        val filter = currentState.selectedStudioFilter

        val homeHbo       = mergeStudioContent(currentState.movieHBO, currentState.tvHBO)
        val homeNetflix   = mergeStudioContent(currentState.movieNetflix, currentState.tvNetflix)
        val homeAmazon    = mergeStudioContent(currentState.movieAmazon, currentState.tvAmazon)
        val homeAppleTv   = mergeStudioContent(currentState.movieAppleTV, currentState.tvAppleTV)
        val homeDisney    = mergeStudioContent(currentState.movieDisney, currentState.tvDisney)
        val homeParamount = mergeStudioContent(currentState.movieParamount, currentState.tvParamount)
        val homeHulu      = mergeStudioContent(currentState.movieHulu, currentState.tvHulu)

        val amazonMovies  = currentState.movieAmazon.ifEmpty { currentState.tvAmazon }
        val amazonSeries  = currentState.tvAmazon.ifEmpty { currentState.movieAmazon }

        val newRows = buildList {
            when (currentState.selectedTab) {
                "ראשי" -> {
                    if (currentState.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", trFunc("Trending Movies", "סרטים פופולריים"), currentState.movieTrending.distinctBy { it.id }))
                    if (homeHbo.isNotEmpty()) add(RowDef.Studio("homeHBO", StudioBrand.HBO, homeHbo))
                    if (currentState.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", trFunc("Popular Shows", "סדרות פופולריות"), currentState.tvTrending.distinctBy { it.id }))
                    if (homeNetflix.isNotEmpty()) add(RowDef.Studio("homeNetflix", StudioBrand.NETFLIX, homeNetflix))
                    if (homeAmazon.isNotEmpty()) add(RowDef.Studio("homeAmazon", StudioBrand.AMAZON, homeAmazon))
                    if (homeAppleTv.isNotEmpty()) add(RowDef.Studio("homeAppleTv", StudioBrand.APPLE_TV, homeAppleTv))
                    if (homeDisney.isNotEmpty()) add(RowDef.Studio("homeDisney", StudioBrand.DISNEY, homeDisney))
                    if (homeParamount.isNotEmpty()) add(RowDef.Studio("homeParamount", StudioBrand.PARAMOUNT, homeParamount))
                    if (homeHulu.isNotEmpty()) add(RowDef.Studio("homeHulu", StudioBrand.HULU, homeHulu))
                    if (currentState.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres",trFunc("New in Theaters", "בקולנוע"), currentState.moviePremieres.distinctBy { it.id }))
                }
                "סרטים" -> {
                    add(RowDef.StudioRibbon)
                    if (filter != null) {
                        val amzId = if (currentState.movieAmazon.isNotEmpty()) "movieAmazon" else "tvAmazon"
                        when (filter) {
                            "HBO"       -> addAll(generateStudioRows("movieHBO", StudioBrand.HBO, currentState.movieHBO, trFunc))
                            "AMAZON"    -> addAll(generateStudioRows(amzId, StudioBrand.AMAZON, amazonMovies, trFunc))
                            "PARAMOUNT" -> addAll(generateStudioRows("movieParamount", StudioBrand.PARAMOUNT, currentState.movieParamount, trFunc))
                            "HULU"      -> addAll(generateStudioRows("movieHulu", StudioBrand.HULU, currentState.movieHulu, trFunc))
                            "NETFLIX"   -> addAll(generateStudioRows("movieNetflix", StudioBrand.NETFLIX, currentState.movieNetflix, trFunc))
                            "APPLE_TV"  -> addAll(generateStudioRows("movieAppleTV", StudioBrand.APPLE_TV, currentState.movieAppleTV, trFunc))
                            "DISNEY"    -> addAll(generateStudioRows("movieDisney", StudioBrand.DISNEY, currentState.movieDisney, trFunc))
                        }
                    } else {
                        if (currentState.movieAction.isNotEmpty()) add(RowDef.Regular("movieAction", trFunc("Action & Adventure", "פעולה והרפתקאות"), currentState.movieAction.distinctBy { it.id }))
                        if (currentState.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", trFunc("Trending Now", "פופולרי עכשיו"), currentState.movieTrending.distinctBy { it.id }))
                        if (currentState.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres", trFunc("In Theaters", "בקולנוע"), currentState.moviePremieres.distinctBy { it.id }))
                        if (currentState.movieAnimation.isNotEmpty()) add(RowDef.Regular("movieAnimation", trFunc("Animations", "אנימציה"), currentState.movieAnimation.distinctBy { it.id }))
                    }
                }
                "סדרות" -> {
                    add(RowDef.StudioRibbon)
                    if (filter != null) {
                        val amzId = if (currentState.tvAmazon.isNotEmpty()) "tvAmazon" else "movieAmazon"
                        when (filter) {
                            "HBO"       -> addAll(generateStudioRows("tvHBO", StudioBrand.HBO, currentState.tvHBO, trFunc))
                            "AMAZON"    -> addAll(generateStudioRows(amzId, StudioBrand.AMAZON, amazonSeries, trFunc))
                            "PARAMOUNT" -> addAll(generateStudioRows("tvParamount", StudioBrand.PARAMOUNT, currentState.tvParamount, trFunc))
                            "HULU"      -> addAll(generateStudioRows("tvHulu", StudioBrand.HULU, currentState.tvHulu, trFunc))
                            "NETFLIX"   -> addAll(generateStudioRows("tvNetflix", StudioBrand.NETFLIX, currentState.tvNetflix, trFunc))
                            "APPLE_TV"  -> addAll(generateStudioRows("tvAppleTV", StudioBrand.APPLE_TV, currentState.tvAppleTV, trFunc))
                            "DISNEY"    -> addAll(generateStudioRows("tvDisney", StudioBrand.DISNEY, currentState.tvDisney, trFunc))
                        }
                    } else {
                        if (currentState.tvDrama.isNotEmpty()) add(RowDef.Regular("tvDrama", trFunc("Drama", "דרמה"), currentState.tvDrama.distinctBy { it.id }))
                        if (currentState.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", trFunc("Trending Shows", "סדרות פופולריות"), currentState.tvTrending.distinctBy { it.id }))
                        if (currentState.tvPremieres.isNotEmpty()) add(RowDef.Regular("tvPremieres", trFunc("New Episodes", "פרקים חדשים"), currentState.tvPremieres.distinctBy { it.id }))
                        if (currentState.tvAnimation.isNotEmpty()) add(RowDef.Regular("tvAnimation", trFunc("Animations", "אנימציה"), currentState.tvAnimation.distinctBy { it.id }))
                    }
                }
                "Fuzer" -> {
                    val newContent = (currentState.fuzerMovies + currentState.fuzerSeries).sortedByDescending { it.id }.distinctBy { it.id }
                    if (newContent.isNotEmpty()) add(RowDef.Regular("fuzer_new", trFunc("🆕 New Content", "🆕 תוכן חדש"), newContent))
                    if (currentState.fuzerMovies.isNotEmpty()) add(RowDef.Regular("fuzer_m", trFunc("🎬 Movies", "🎬 סרטים"), currentState.fuzerMovies.distinctBy { it.id }))
                    if (currentState.fuzerMoviesHD.isNotEmpty()) add(RowDef.Regular("fuzer_mhd", trFunc("🎬 Movies HD", "🎬 סרטים HD"), currentState.fuzerMoviesHD.distinctBy { it.id }))
                    if (currentState.fuzerMovies4K.isNotEmpty()) add(RowDef.Regular("fuzer_m4k", trFunc("✨ Movies 4K", "✨ סרטים 4K"), currentState.fuzerMovies4K.distinctBy { it.id }))
                    if (currentState.fuzerDubbedMovies.isNotEmpty()) add(RowDef.Regular("fuzer_dm", trFunc("🎤 Dubbed Movies", "🎤 סרטים מדובבים"), currentState.fuzerDubbedMovies.distinctBy { it.id }))
                    if (currentState.fuzerSeries.isNotEmpty()) add(RowDef.Regular("fuzer_tv", trFunc("📺 TV Shows", "📺 סדרות"), currentState.fuzerSeries.distinctBy { it.id }))
                    if (currentState.fuzerSeriesHD.isNotEmpty()) add(RowDef.Regular("fuzer_shd", trFunc("📺 TV Shows HD", "📺 סדרות HD"), currentState.fuzerSeriesHD.distinctBy { it.id }))
                    if (currentState.fuzerSeries4K.isNotEmpty()) add(RowDef.Regular("fuzer_s4k", trFunc("✨ TV Shows 4K", "✨ סדרות 4K"), currentState.fuzerSeries4K.distinctBy { it.id }))
                    if (currentState.fuzerDubbedSeries.isNotEmpty()) add(RowDef.Regular("fuzer_ds", trFunc("🎤 Dubbed Shows", "🎤 סדרות מדובבות"), currentState.fuzerDubbedSeries.distinctBy { it.id }))
                }
            }
        }
        _uiRows.value = newRows
    }

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
                    "movieAnimation" -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
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
                    "tvAnimation"    -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
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
                                "movieAnimation" -> s.copy(movieAnimation = s.movieAnimation + newItems)
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
                                "tvAnimation"    -> s.copy(tvAnimation    = s.tvAnimation    + newItems)
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

    fun loadFuzerContent() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.fuzerMovies.isEmpty() && _state.value.fuzerSeries.isEmpty()) {
                _state.update { it.copy(fuzerIsLoading = true, fuzerError = null) }
            }

            try {
                val moviesR = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES, 1).getOrElse { emptyList() } }
                val seriesR = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES, 1).getOrElse { emptyList() } }

                val mRes = moviesR.await()
                val sRes = seriesR.await()

                _state.update { s -> s.copy(
                    fuzerIsLoading = false,
                    fuzerItems     = mRes + sRes,
                    fuzerMovies    = mRes,
                    fuzerSeries    = sRes
                )}

                val moviesHdDef = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES_HD, 1).getOrElse { emptyList() } }
                val seriesHdDef = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES_HD, 1).getOrElse { emptyList() } }
                val movies4kDef = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES_4K, 1).getOrElse { emptyList() } }
                val series4kDef = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES_4K, 1).getOrElse { emptyList() } }

                val mHd = moviesHdDef.await()
                val sHd = seriesHdDef.await()
                val m4k = movies4kDef.await()
                val s4k = series4kDef.await()

                _state.update { s -> s.copy(
                    fuzerMoviesHD = mHd,
                    fuzerSeriesHD = sHd,
                    fuzerMovies4K = m4k,
                    fuzerSeries4K = s4k
                )}

                val dubbedMoviesDef = async { FuzerEngine.getCategoryPage(FuzerCats.DUBBED_MOVIES, 1).getOrElse { emptyList() } }
                val dubbedSeriesDef = async { FuzerEngine.getCategoryPage(FuzerCats.DUBBED_SERIES, 1).getOrElse { emptyList() } }

                val dmRes = dubbedMoviesDef.await()
                val dsRes = dubbedSeriesDef.await()

                _state.update { s -> s.copy(
                    fuzerDubbedMovies = dmRes,
                    fuzerDubbedSeries = dsRes
                )}

            } catch (e: Exception) {
                _state.update { it.copy(fuzerIsLoading = false, fuzerError = "שגיאת טעינה: ${e.message}") }
            }
        }
    }

    private fun loadAll() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val k      = Constants.TMDB_API_KEY
                val region = "US"

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

                if (DeviceProfile.tier == DeviceProfile.Tier.LOW) {
                    loadWave2Batched(k, region, batchSize = 3, delayMs = 150)
                } else {
                    loadWave2Batched(k, region, batchSize = 6, delayMs = 50)
                }

            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Network error: ${e.message}") }
            }
        }
    }

    private suspend fun loadWave2Batched(k: String, region: String, batchSize: Int, delayMs: Long) {
        val requests: List<Pair<String, (HomeState, List<Movie>) -> HomeState>> = listOf(
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc") { s, v -> s.copy(movieAction = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc") { s, v -> s.copy(movieAnimation = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc") { s, v -> s.copy(tvAnimation = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc") { s, v -> s.copy(movieDrama = v) },
            Pair("$BASE/movie/top_rated?api_key=$k&language=en-US") { s, v -> s.copy(movieTopRated = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899|384&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieHBO = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieAmazon = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc") { s, v -> s.copy(tvDrama = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc") { s, v -> s.copy(tvCrime = v) },
            Pair("$BASE/tv/top_rated?api_key=$k&language=en-US") { s, v -> s.copy(tvTopRated = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=213&sort_by=popularity.desc") { s, v -> s.copy(tvNetflix = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=49|3186&sort_by=popularity.desc") { s, v -> s.copy(tvHBO = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieAppleTV = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_companies=2|3|420&sort_by=popularity.desc") { s, v -> s.copy(movieDisney = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_companies=4&sort_by=popularity.desc") { s, v -> s.copy(movieParamount = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieHulu = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=10765&sort_by=popularity.desc") { s, v -> s.copy(tvScifi = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2552&sort_by=popularity.desc") { s, v -> s.copy(tvAppleTV = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2739&sort_by=popularity.desc") { s, v -> s.copy(tvDisney = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=1024&sort_by=popularity.desc") { s, v -> s.copy(tvAmazon = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=4330|67&sort_by=popularity.desc") { s, v -> s.copy(tvParamount = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=453&sort_by=popularity.desc") { s, v -> s.copy(tvHulu = v) }
        )

        requests.chunked(batchSize).forEach { batch ->
            val results = coroutineScope {
                batch.map { (url, updater) ->
                    async {
                        val mt = if (url.contains("/movie")) "movie" else "tv"
                        updater to fetch(url, mt)
                    }
                }.awaitAll()
            }

            _state.update { currentState ->
                var nextState = currentState
                results.forEach { (updater, data) ->
                    nextState = updater(nextState, data)
                }
                nextState
            }

            delay(delayMs)
        }
    }

    private suspend fun fetch(url: String, mediaType: String): List<Movie> =
        withContext(Dispatchers.IO) {
            try {
                // FIX: OkHttp 5.x Response Body uses a clean, non-null syntax
                val bodyStr = http.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
                val arr = JSONObject(bodyStr).optJSONArray("results") ?: return@withContext emptyList()

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
                        posterUrl       = "$imgBase/original$posterPath",
                        backdropUrl     = "$imgBase/original$backdropRaw",
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