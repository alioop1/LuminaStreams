package com.luminastreams.tv.presentation.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.util.concurrent.TimeUnit

// NOTE: HomeState is defined in HomeContract.kt — do NOT redeclare here.
class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    // ⚠ Replace with your TMDB v3 API key
    private val apiKey  = "9ab4a284f0c028007b78925852196b79"
    private val imgBase = "https://image.tmdb.org/t/p"
    private val base    = "https://api.themoviedb.org/3"

    private val http = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    init { loadAll() }

    // ── Public API ─────────────────────────────────────────────────────────────
    fun selectTab(tab: String) = _state.update { it.copy(selectedTab = tab) }
    fun retry()                = loadAll()

    // ── Discovery methods (called by DiscoveryScreen) ─────────────────────────
    fun clearGenre() {
        _state.update { it.copy(
            isFilterComplete  = false,
            selectedGenreName = "",
            discoveryResults  = emptyList(),
            focusedItem       = null
        )}
    }

    fun setGenreFilter(genreId: String, genreName: String) {
        _state.update { it.copy(
            isFilterComplete  = true,
            selectedGenreName = genreName,
            discoveryResults  = emptyList(),
            isLoading         = true
        )}
        viewModelScope.launch {
            val tab = _state.value.selectedTab
            val mediaType = if (tab == "סדרות") "tv" else "movie"
            val genreParam = if (genreId.isNotBlank()) "&with_genres=$genreId" else ""
            val url = "$base/discover/$mediaType?api_key=$apiKey&language=en-US$genreParam&sort_by=popularity.desc"
            val results = fetch(url, mediaType)
            _state.update { it.copy(isLoading = false, discoveryResults = results) }
        }
    }

    fun updateFocusedItem(movie: Movie, genreName: String) {
        _state.update { it.copy(focusedItem = movie, selectedGenreName = genreName) }
    }

    // ── Load all rows in parallel ──────────────────────────────────────────────
    private fun loadAll() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                coroutineScope {
                    val mTrend  = async { fetch("$base/trending/movie/week?api_key=$apiKey&language=en-US", "movie") }
                    val mNow    = async { fetch("$base/movie/now_playing?api_key=$apiKey&language=en-US", "movie") }
                    val mAction = async { fetch("$base/discover/movie?api_key=$apiKey&language=en-US&with_genres=28&sort_by=popularity.desc", "movie") }
                    val mDrama  = async { fetch("$base/discover/movie?api_key=$apiKey&language=en-US&with_genres=18&sort_by=popularity.desc", "movie") }
                    val mScifi  = async { fetch("$base/discover/movie?api_key=$apiKey&language=en-US&with_genres=878&sort_by=popularity.desc", "movie") }
                    val mTop    = async { fetch("$base/movie/top_rated?api_key=$apiKey&language=en-US", "movie") }
                    val mNflx   = async { fetch("$base/discover/movie?api_key=$apiKey&language=en-US&with_watch_providers=8&watch_region=US&sort_by=popularity.desc", "movie") }
                    val mApple  = async { fetch("$base/discover/movie?api_key=$apiKey&language=en-US&with_watch_providers=350&watch_region=US&sort_by=popularity.desc", "movie") }
                    val mDisney = async { fetch("$base/discover/movie?api_key=$apiKey&language=en-US&with_watch_providers=337&watch_region=US&sort_by=popularity.desc", "movie") }
                    val tvTrend  = async { fetch("$base/trending/tv/week?api_key=$apiKey&language=en-US", "tv") }
                    val tvAir    = async { fetch("$base/tv/on_the_air?api_key=$apiKey&language=en-US", "tv") }
                    val tvDrama  = async { fetch("$base/discover/tv?api_key=$apiKey&language=en-US&with_genres=18&sort_by=popularity.desc", "tv") }
                    val tvCrime  = async { fetch("$base/discover/tv?api_key=$apiKey&language=en-US&with_genres=80&sort_by=popularity.desc", "tv") }
                    val tvScifi  = async { fetch("$base/discover/tv?api_key=$apiKey&language=en-US&with_genres=10765&sort_by=popularity.desc", "tv") }
                    val tvTop    = async { fetch("$base/tv/top_rated?api_key=$apiKey&language=en-US", "tv") }
                    val tvNflx   = async { fetch("$base/discover/tv?api_key=$apiKey&language=en-US&with_watch_providers=8&watch_region=US&sort_by=popularity.desc", "tv") }
                    val tvApple  = async { fetch("$base/discover/tv?api_key=$apiKey&language=en-US&with_watch_providers=350&watch_region=US&sort_by=popularity.desc", "tv") }
                    val tvDisney = async { fetch("$base/discover/tv?api_key=$apiKey&language=en-US&with_watch_providers=337&watch_region=US&sort_by=popularity.desc", "tv") }
                    _state.update { s -> s.copy(
                        isLoading      = false,
                        movieTrending  = mTrend.await(),
                        moviePremieres = mNow.await(),
                        movieAction    = mAction.await(),
                        movieDrama     = mDrama.await(),
                        movieScifi     = mScifi.await(),
                        movieTopRated  = mTop.await(),
                        movieNetflix   = mNflx.await(),
                        movieAppleTV   = mApple.await(),
                        movieDisney    = mDisney.await(),
                        tvTrending     = tvTrend.await(),
                        tvPremieres    = tvAir.await(),
                        tvDrama        = tvDrama.await(),
                        tvCrime        = tvCrime.await(),
                        tvScifi        = tvScifi.await(),
                        tvTopRated     = tvTop.await(),
                        tvNetflix      = tvNflx.await(),
                        tvAppleTV      = tvApple.await(),
                        tvDisney       = tvDisney.await(),
                    )}
                }
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Network error: ${e.message}") }
            }
        }
    }

    private suspend fun fetch(url: String, mediaType: String): List<Movie> =
        withContext(Dispatchers.IO) {
            try {
                val body = http.newCall(Request.Builder().url(url).build())
                    .execute().use { it.body?.string() } ?: return@withContext emptyList()
                val arr  = JSONObject(body).optJSONArray("results") ?: return@withContext emptyList()
                val out  = mutableListOf<Movie>()
                for (i in 0 until minOf(arr.length(), 20)) {
                    val j  = arr.getJSONObject(i)
                    val mt = j.optString("media_type").ifBlank { mediaType }
                    val title = if (mt == "tv") j.optString("name").ifBlank { j.optString("original_name") }
                    else             j.optString("title").ifBlank { j.optString("original_title") }
                    val date     = if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")
                    val year     = date.take(4).toIntOrNull() ?: 0
                    val poster   = j.optString("poster_path").let  { if (it.isNotBlank() && it != "null") "$imgBase/w500$it"  else "" }
                    val backdrop = j.optString("backdrop_path").let { if (it.isNotBlank() && it != "null") "$imgBase/w1280$it" else "" }
                    val rating   = j.optDouble("vote_average", 0.0).toFloat()
                    val genreId  = j.optJSONArray("genre_ids")?.optInt(0, 0) ?: 0
                    out += Movie(
                        id = j.optInt("id").toString(), title = title,
                        posterUrl = poster, backdropUrl = backdrop,
                        overview = j.optString("overview"),
                        year = year, genre = genreLabel(genreId, mt),
                        rating = rating, mediaType = mt, resolutionBadge = ""
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