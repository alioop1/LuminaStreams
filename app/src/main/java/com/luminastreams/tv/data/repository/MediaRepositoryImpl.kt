package com.luminastreams.tv.data.repository

import android.content.Context
import com.luminastreams.tv.core.Constants
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.data.api.TmdbApi
import com.luminastreams.tv.data.api.TmdbMediaDto
import com.luminastreams.tv.data.api.TmdbMovieDetailsDto
import com.luminastreams.tv.data.api.TmdbTvDetailsDto
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.model.SearchResult
import com.luminastreams.tv.domain.model.MediaType
import com.luminastreams.tv.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MediaRepositoryImpl(private val context: Context) : MediaRepository {

    private val okhttp = OkHttpClient.Builder()
        .connectTimeout(8,  TimeUnit.SECONDS)
        .readTimeout(10,    TimeUnit.SECONDS)
        .writeTimeout(10,   TimeUnit.SECONDS)
        .build()

    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl(Constants.TMDB_BASE_URL)
        .client(okhttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    private val tmdbLang: String get() {
        val prefsLang = context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
            .getString("app_lang", "")
        val deviceLang = java.util.Locale.getDefault().language
        return if (prefsLang == "he" || deviceLang == "he" || deviceLang == "iw") "he" else "en-US"
    }

    private val tierBadge: String get() = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> "4K HDR"
        DeviceProfile.Tier.MID  -> "FHD 1080p"
        DeviceProfile.Tier.LOW  -> "HD 720p"
    }

    private fun TmdbMediaDto.toMovie(type: String) = Movie(
        id              = "${type}_${id}",
        title           = title ?: name ?: "Unknown",
        backdropUrl     = Constants.backdropUrl(backdropPath),
        posterUrl       = Constants.posterUrl(posterPath),
        overview        = overview ?: "",
        rating          = voteAverage,
        mediaType       = type,
        genreIds        = genreIds ?: emptyList(),
        is4K            = DeviceProfile.tier == DeviceProfile.Tier.HIGH,
        resolutionBadge = tierBadge
    )

    private fun TmdbMediaDto.toSearchResult(fallbackType: String): SearchResult {
        val actualType = mediaType ?: fallbackType
        val base = "https://image.tmdb.org/t/p"
        return SearchResult(
            id          = "${actualType}_${id}",
            title       = title ?: name ?: "Unknown",
            posterUrl   = posterPath?.let { if (it.isNotBlank() && it != "null") "$base/w342$it" else "" } ?: "",
            backdropUrl = backdropPath?.let { if (it.isNotBlank() && it != "null") "$base/w780$it" else "" } ?: "",
            type        = if (actualType == "tv") MediaType.TV_SHOW else MediaType.MOVIE,
            rating      = voteAverage,
            releaseYear = (if (actualType == "tv") firstAirDate else releaseDate)?.take(4) ?: "",
            genre       = genreIds?.mapNotNull { tmdbGenreName(it).ifBlank { null } }?.joinToString(", ") ?: ""
        )
    }

    private fun tmdbGenreName(id: Int): String = when (id) {
        28 -> "Action"; 12 -> "Adventure"; 16 -> "Animation"; 35 -> "Comedy"
        80 -> "Crime"; 99 -> "Documentary"; 18 -> "Drama"; 10751 -> "Family"
        14 -> "Fantasy"; 36 -> "History"; 27 -> "Horror"; 10402 -> "Music"
        9648 -> "Mystery"; 10749 -> "Romance"; 878 -> "Sci-Fi"; 10770 -> "TV Movie"
        53 -> "Thriller"; 10752 -> "War"; 37 -> "Western"
        10759 -> "Action & Adventure"; 10762 -> "Kids"; 10763 -> "News"
        10764 -> "Reality"; 10765 -> "Sci-Fi & Fantasy"; 10766 -> "Soap"
        10767 -> "Talk"; 10768 -> "War & Politics"
        else -> ""
    }

    override suspend fun getTrendingMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val movies = coroutineScope {
                val trendingDef = async { api.getTrending(Constants.TMDB_API_KEY, language = tmdbLang) }
                val actionDef   = async { api.discoverMovies(Constants.TMDB_API_KEY, language = tmdbLang, genres = "28,12") }
                val dramaTvDef  = async { api.discoverTv(Constants.TMDB_API_KEY, language = tmdbLang, genres = "18,80") }

                val results = mutableListOf<TmdbMediaDto>()
                results.addAll(trendingDef.await().results)
                results.addAll(actionDef.await().results.map { it.copy(mediaType = "movie") })
                results.addAll(dramaTvDef.await().results.map { it.copy(mediaType = "tv") })
                results
            }
                .filter { it.posterPath != null && it.backdropPath != null }
                .map { it.toMovie(it.mediaType ?: "movie") }
                .distinctBy { it.id }

            Result.success(movies)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getTrendingTv(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val results = coroutineScope {
                val dramaDef  = async { api.discoverTv(Constants.TMDB_API_KEY, language = tmdbLang, genres = "18") }
                val scifiDef  = async { api.discoverTv(Constants.TMDB_API_KEY, language = tmdbLang, genres = "10765") }
                val crimeDef  = async { api.discoverTv(Constants.TMDB_API_KEY, language = tmdbLang, genres = "80") }

                val list = mutableListOf<TmdbMediaDto>()
                list.addAll(dramaDef.await().results.map { it.copy(mediaType = "tv") })
                list.addAll(scifiDef.await().results.map { it.copy(mediaType = "tv") })
                list.addAll(crimeDef.await().results.map { it.copy(mediaType = "tv") })
                list
            }
                .filter { it.posterPath != null && it.backdropPath != null }
                .map { it.toMovie("tv") }
                .distinctBy { it.id }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMulti(query: String, page: Int, isHebrew: Boolean): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val lang = if (isHebrew) "he-IL" else "en-US"
            val response = api.searchMulti(query = query, language = lang, page = page)
            val results = response.results
                .filter { it.mediaType == "movie" || it.mediaType == "tv" }
                .filter { it.posterPath != null }
                .map { it.toSearchResult(it.mediaType ?: "movie") }
            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getDiscoverySearch(page: Int): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val movies = async { api.discoverMedia(type = "movie", uiLanguage = "en-US", page = page, sortBy = "popularity.desc") }
            val tvs    = async { api.discoverMedia(type = "tv", uiLanguage = "en-US", page = page, sortBy = "popularity.desc") }

            val combined = mutableListOf<TmdbMediaDto>()
            combined.addAll(movies.await().results.map { it.copy(mediaType = "movie") })
            combined.addAll(tvs.await().results.map { it.copy(mediaType = "tv") })

            val results = combined
                .filter { it.posterPath != null }
                .map { it.toSearchResult(it.mediaType ?: "movie") }
                .sortedByDescending { it.rating }

            Result.success(results)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getSimilarMedia(id: String, type: String): Result<List<Movie>> =
        withContext(Dispatchers.IO) {
            try {
                val genreIds: List<Int> = if (type == "tv") {
                    val details = api.getTvDetails(id, Constants.TMDB_API_KEY, language = tmdbLang)
                    details.genres?.map { it.id } ?: emptyList()
                } else {
                    val details = api.getMovieDetails(id, Constants.TMDB_API_KEY, language = tmdbLang)
                    details.genres?.map { it.id } ?: emptyList()
                }

                val genreParam = genreIds.take(2).joinToString(",")

                val results = api.discoverMedia(
                    type     = type,
                    genreId  = genreParam.ifBlank { null },
                    year     = null,
                    sortBy   = "popularity.desc",
                    uiLanguage = tmdbLang,
                    page     = 1,
                    apiKey   = Constants.TMDB_API_KEY
                )
                    .results
                    .filter { it.posterPath != null && it.id.toString() != id.removePrefix("${type}_") }
                    .map { it.toMovie(type) }
                    .distinctBy { it.id }
                    .take(20)

                Result.success(results)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun searchMovies(query: String): Result<List<Movie>> = Result.success(emptyList())

    override suspend fun getMovieDetails(id: String): Result<Movie> = Result.failure(Exception("Use getMovieFullDetails"))

    override suspend fun getMovieFullDetails(id: String): Result<TmdbMovieDetailsDto> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(api.getMovieDetails(id, Constants.TMDB_API_KEY, language = tmdbLang))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getTvFullDetails(id: String): Result<TmdbTvDetailsDto> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(api.getTvDetails(id, Constants.TMDB_API_KEY, language = tmdbLang))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun discoverMedia(
        type    : String,
        genreId : String?,
        year    : String?,
        sortBy  : String,
        page    : Int
    ): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val items = api.discoverMedia(
                type     = type,
                genreId  = genreId,
                year     = year,
                sortBy   = sortBy,
                uiLanguage = tmdbLang,
                page     = page,
                apiKey   = Constants.TMDB_API_KEY
            )
                .results
                .filter { it.posterPath != null }
                .map { it.toMovie(type) }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun discoverFiltered(
        type: String,
        genreId: String?,
        tvGenreId: String?,
        releaseDateGte: String?,
        releaseDateLte: String?,
        voteGte: Float?,
        language: String?,
        networkId: String?,
        runtimeGte: Int?,
        runtimeLte: Int?,
        sortBy: String,
        page: Int
    ): Result<List<SearchResult>> = withContext(Dispatchers.IO) {
        try {
            val isMovie = type == "movie"
            val isBoth = type == "both"

            val results = mutableListOf<TmdbMediaDto>()

            if (isBoth || isMovie) {
                val movieResp = api.discoverMedia(
                    type = "movie", genreId = genreId,
                    releaseDateGte = releaseDateGte, releaseDateLte = releaseDateLte,
                    voteGte = voteGte, language = language,
                    runtimeGte = runtimeGte, runtimeLte = runtimeLte,
                    sortBy = sortBy, uiLanguage = tmdbLang, page = page
                )
                results.addAll(movieResp.results.map { it.copy(mediaType = "movie") })
            }
            if (isBoth || !isMovie) {
                val effectiveTvGenreId = tvGenreId ?: genreId
                val tvResp = api.discoverMedia(
                    type = "tv", genreId = effectiveTvGenreId,
                    airDateGte = releaseDateGte, airDateLte = releaseDateLte,
                    voteGte = voteGte, language = language, networkId = networkId,
                    runtimeGte = runtimeGte, runtimeLte = runtimeLte,
                    sortBy = sortBy, uiLanguage = tmdbLang, page = page
                )
                results.addAll(tvResp.results.map { it.copy(mediaType = "tv") })
            }

            val mapped = results
                .filter { it.posterPath != null }
                .map { it.toSearchResult(it.mediaType ?: "movie") }
                .distinctBy { it.id }

            Result.success(mapped)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}