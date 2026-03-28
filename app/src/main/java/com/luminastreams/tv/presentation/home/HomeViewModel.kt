package com.luminastreams.tv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.Constants
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.data.remote.FuzerEngine
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
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

    private val pageMap = mutableMapOf<String, Int>()
    // fix: use thread-safe set — loadMore runs on Dispatchers.IO
    private val loadingSet: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())


    private val imgBase = "https://image.tmdb.org/t/p"
    private val base = "https://api.themoviedb.org/3"

    // fix: choose backdrop size based on device tier so LOW/MID devices
    // don't waste bandwidth & memory on full 1280px images.
    private val backdropSize: String get() = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> "w1280"
        DeviceProfile.Tier.MID  -> "w780"
        DeviceProfile.Tier.LOW  -> "w500"
    }

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(8, 5, TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .build()

    init { loadAll() }

    fun selectTab(tab: String) = _state.update { it.copy(selectedTab = tab) }

    fun setStudioFilter(studio: String?) =
        _state.update { it.copy(selectedStudioFilter = studio) }

    fun retry() {
        if (_state.value.selectedTab == "Fuzer") loadFuzerContent()
        else loadAll()
    }

    // ── Endless Scroll Pagination —————————————————————————————
    fun loadMore(id: String) {
        if (id == "ribbon" || id.startsWith("fuzer") || loadingSet.contains(id)) return
        loadingSet.add(id)
        val nextPage = (pageMap[id] ?: 1) + 1

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val k = Constants.TMDB_API_KEY
                val region = "US"
                var url = ""
                var mt = ""

                when (id) {
                    "movieTrending"  -> { url = "$base/trending/movie/week?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                    "moviePremieres" -> { url = "$base/movie/now_playing?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                    "movieAction"    -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieDrama"     -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieScifi"     -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_genres=878&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieTopRated"  -> { url = "$base/movie/top_rated?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                    "movieNetflix"   -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieAppleTV"   -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieDisney"    -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=337&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieHBO"       -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieAmazon"    -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieParamount" -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=531&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "movieHulu"      -> { url = "$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                    "tvTrending"     -> { url = "$base/trending/tv/week?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                    "tvPremieres"    -> { url = "$base/tv/on_the_air?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                    "tvDrama"        -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvCrime"        -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvScifi"        -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_genres=10765&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvTopRated"     -> { url = "$base/tv/top_rated?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                    "tvNetflix"      -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvAppleTV"      -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvDisney"       -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=337&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvHBO"          -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=1899&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvAmazon"       -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvParamount"    -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=531&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    "tvHulu"         -> { url = "$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                }

                if (url.isNotEmpty()) {
                    val newItems = fetch(url, mt)
                    if (newItems.isNotEmpty()) {
                        pageMap[id] = nextPage
                        _state.update { s ->
                            when (id) {
                                "movieTrending"  -> s.copy(movieTrending = s.movieTrending + newItems)
                                "moviePremieres" -> s.copy(moviePremieres = s.moviePremieres + newItems)
                                "movieAction"    -> s.copy(movieAction = s.movieAction + newItems)
                                "movieDrama"     -> s.copy(movieDrama = s.movieDrama + newItems)
                                "movieScifi"     -> s.copy(movieScifi = s.movieScifi + newItems)
                                "movieTopRated"  -> s.copy(movieTopRated = s.movieTopRated + newItems)
                                "movieNetflix"   -> s.copy(movieNetflix = s.movieNetflix + newItems)
                                "movieAppleTV"   -> s.copy(movieAppleTV = s.movieAppleTV + newItems)
                                "movieDisney"    -> s.copy(movieDisney = s.movieDisney + newItems)
                                "movieHBO"       -> s.copy(movieHBO = s.movieHBO + newItems)
                                "movieAmazon"    -> s.copy(movieAmazon = s.movieAmazon + newItems)
                                "movieParamount" -> s.copy(movieParamount = s.movieParamount + newItems)
                                "movieHulu"      -> s.copy(movieHulu = s.movieHulu + newItems)
                                "tvTrending"     -> s.copy(tvTrending = s.tvTrending + newItems)
                                "tvPremieres"    -> s.copy(tvPremieres = s.tvPremieres + newItems)
                                "tvDrama"        -> s.copy(tvDrama = s.tvDrama + newItems)
                                "tvCrime"        -> s.copy(tvCrime = s.tvCrime + newItems)
                                "tvScifi"        -> s.copy(tvScifi = s.tvScifi + newItems)
                                "tvTopRated"     -> s.copy(tvTopRated = s.tvTopRated + newItems)
                                "tvNetflix"      -> s.copy(tvNetflix = s.tvNetflix + newItems)
                                "tvAppleTV"      -> s.copy(tvAppleTV = s.tvAppleTV + newItems)
                                "tvDisney"       -> s.copy(tvDisney = s.tvDisney + newItems)
                                "tvHBO"          -> s.copy(tvHBO = s.tvHBO + newItems)
                                "tvAmazon"       -> s.copy(tvAmazon = s.tvAmazon + newItems)
                                "tvParamount"    -> s.copy(tvParamount = s.tvParamount + newItems)
                                "tvHulu"         -> s.copy(tvHulu = s.tvHulu + newItems)
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
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(fuzerIsLoading = true, fuzerError = null) }
            try {
                // אנו מביאים את המידע בצורה טורית (Sequential) עם השהייה קטנה (Delay)
                // כדי למנוע ממערכת הפורום לזהות אותנו כהתקפת הצפה (Flood Control)
                val moviesR       = FuzerEngine.getCategoryPage(FuzerCats.MOVIES, 1).getOrElse { emptyList() }
                delay(300)
                val seriesR       = FuzerEngine.getCategoryPage(FuzerCats.SERIES, 1).getOrElse { emptyList() }
                delay(300)
                val moviesHdR     = FuzerEngine.getCategoryPage(FuzerCats.MOVIES_HD, 1).getOrElse { emptyList() }
                delay(300)
                val seriesHdR     = FuzerEngine.getCategoryPage(FuzerCats.SERIES_HD, 1).getOrElse { emptyList() }
                delay(300)
                val movies4kR     = FuzerEngine.getCategoryPage(FuzerCats.MOVIES_4K, 1).getOrElse { emptyList() }
                delay(300)
                val series4kR     = FuzerEngine.getCategoryPage(FuzerCats.SERIES_4K, 1).getOrElse { emptyList() }
                delay(300)
                val dubbedMoviesR = FuzerEngine.getCategoryPage(FuzerCats.DUBBED_MOVIES, 1).getOrElse { emptyList() }
                delay(300)
                val dubbedSeriesR = FuzerEngine.getCategoryPage(FuzerCats.DUBBED_SERIES, 1).getOrElse { emptyList() }

                _state.update { s -> s.copy(
                    fuzerIsLoading    = false,
                    fuzerItems        = moviesR + seriesR,
                    fuzerMovies       = moviesR,
                    fuzerSeries       = seriesR,
                    fuzerMoviesHD     = moviesHdR,
                    fuzerSeriesHD     = seriesHdR,
                    fuzerMovies4K     = movies4kR,
                    fuzerSeries4K     = series4kR,
                    fuzerDubbedMovies = dubbedMoviesR,
                    fuzerDubbedSeries = dubbedSeriesR,
                ) }
            } catch (e: Exception) {
                _state.update { it.copy(fuzerIsLoading = false, fuzerError = "שגיאת טעינה: ${e.message}") }
            }
        }
    }

    // ── Load all rows —————————————————————————————————
    private fun loadAll() {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val k = Constants.TMDB_API_KEY
                val region = "US"

                // ════ גל 1: מה שנראה מיד על המסך ════
                coroutineScope {
                    val mTrend  = async { fetch("$base/trending/movie/week?api_key=$k&language=en-US", "movie") }
                    val tvTrend = async { fetch("$base/trending/tv/week?api_key=$k&language=en-US", "tv") }
                    val mNow    = async { fetch("$base/movie/now_playing?api_key=$k&language=en-US", "movie") }
                    val mNflx   = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc", "movie") }

                    _state.update { s -> s.copy(
                        isLoading      = false,
                        movieTrending  = mTrend.await(),
                        tvTrending     = tvTrend.await(),
                        moviePremieres = mNow.await(),
                        movieNetflix   = mNflx.await()
                    )}
                }

                // ════ גל 2: שאר הנתונים ════
                coroutineScope {
                    val mAction    = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc", "movie") }
                    val mDrama     = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc", "movie") }
                    val mScifi     = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_genres=878&sort_by=popularity.desc", "movie") }
                    val mTop       = async { fetch("$base/movie/top_rated?api_key=$k&language=en-US", "movie") }

                    val mHBO       = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899&watch_region=$region&sort_by=popularity.desc", "movie") }
                    val mAmazon    = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc", "movie") }
                    val mApple     = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc", "movie") }
                    val mDisney    = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=337&watch_region=$region&sort_by=popularity.desc", "movie") }
                    val mParamount = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=531&watch_region=$region&sort_by=popularity.desc", "movie") }
                    val mHulu      = async { fetch("$base/discover/movie?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc", "movie") }

                    val tvAir      = async { fetch("$base/tv/on_the_air?api_key=$k&language=en-US", "tv") }
                    val tvDrama    = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc", "tv") }
                    val tvCrime    = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc", "tv") }
                    val tvScifi    = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_genres=10765&sort_by=popularity.desc", "tv") }
                    val tvTop      = async { fetch("$base/tv/top_rated?api_key=$k&language=en-US", "tv") }
                    val tvNflx     = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc", "tv") }
                    val tvApple    = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc", "tv") }
                    val tvDisney   = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=337&watch_region=$region&sort_by=popularity.desc", "tv") }
                    val tvHBO      = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=1899&watch_region=$region&sort_by=popularity.desc", "tv") }
                    val tvAmazon   = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc", "tv") }
                    val tvParamount = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=531&watch_region=$region&sort_by=popularity.desc", "tv") }
                    val tvHulu     = async { fetch("$base/discover/tv?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc", "tv") }

                    _state.update { s -> s.copy(
                        movieAction   = mAction.await(),
                        movieDrama    = mDrama.await(),
                        movieScifi    = mScifi.await(),
                        movieTopRated = mTop.await()
                    )}
                    delay(100)

                    _state.update { s -> s.copy(
                        movieHBO       = mHBO.await(),
                        movieAmazon    = mAmazon.await(),
                        movieAppleTV   = mApple.await(),
                        movieDisney    = mDisney.await(),
                        movieParamount = mParamount.await(),
                        movieHulu      = mHulu.await()
                    )}
                    delay(100)

                    _state.update { s -> s.copy(
                        tvPremieres = tvAir.await(),
                        tvDrama     = tvDrama.await(),
                        tvCrime     = tvCrime.await(),
                        tvScifi     = tvScifi.await(),
                        tvTopRated  = tvTop.await()
                    )}
                    delay(100)

                    _state.update { s -> s.copy(
                        tvNetflix   = tvNflx.await(),
                        tvAppleTV   = tvApple.await(),
                        tvDisney    = tvDisney.await(),
                        tvHBO       = tvHBO.await(),
                        tvAmazon    = tvAmazon.await(),
                        tvParamount = tvParamount.await(),
                        tvHulu      = tvHulu.await()
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Network error: ${e.message}") }
            }
        }
    }

    // ── Fetch helper —————————————————————————————————
    private suspend fun fetch(url: String, mediaType: String): List<Movie> =
        withContext(Dispatchers.IO) {
            try {
                val body = http.newCall(Request.Builder().url(url).build())
                    .execute().use { it.body?.string() } ?: return@withContext emptyList()
                val arr = JSONObject(body).optJSONArray("results")
                    ?: return@withContext emptyList()

                val out = mutableListOf<Movie>()
                for (i in 0 until arr.length()) {
                    val j = arr.getJSONObject(i)
                    val mt = j.optString("media_type").ifBlank { mediaType }

                    val backdropRaw = j.optString("backdrop_path")
                    if (backdropRaw.isBlank() || backdropRaw == "null") continue

                    val title = if (mt == "tv")
                        j.optString("name").ifBlank { j.optString("original_name") }
                    else
                        j.optString("title").ifBlank { j.optString("original_title") }

                    val date = if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")
                    val poster = j.optString("poster_path").let {
                        if (it.isNotBlank() && it != "null") "$imgBase/w500$it" else ""
                    }
                    if (poster.isBlank()) continue

                    out += Movie(
                        id               = "${mt}_${j.optInt("id")}",
                        title            = title,
                        posterUrl        = poster,
                        // fix: use tier-appropriate backdrop size (1280/780/500)
                        backdropUrl      = "$imgBase/$backdropSize$backdropRaw",
                        overview         = j.optString("overview"),
                        year             = date.take(4).toIntOrNull() ?: 0,
                        genre            = genreLabel(j.optJSONArray("genre_ids")?.optInt(0, 0) ?: 0, mt),
                        rating           = j.optDouble("vote_average", 0.0).toFloat(),
                        mediaType        = mt,
                        resolutionBadge  = ""
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
}
