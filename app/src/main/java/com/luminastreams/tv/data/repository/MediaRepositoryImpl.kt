package com.luminastreams.tv.data.repository

import android.content.Context
import com.luminastreams.tv.core.Constants
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.data.api.TmdbApi
import com.luminastreams.tv.data.api.TmdbMediaDto
import com.luminastreams.tv.data.api.TmdbMovieDetailsDto
import com.luminastreams.tv.data.api.TmdbTvDetailsDto
import com.luminastreams.tv.domain.model.Movie
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

    // ── getTrendingTv ──────────────────────────────────────────────────────
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

    // ── getSimilarMedia ────────────────────────────────────────────────────
    // משתמש ב-discoverMedia הקיים — מסנן לפי ז'אנרים של הפריט הנוכחי
    override suspend fun getSimilarMedia(id: String, type: String): Result<List<Movie>> =
        withContext(Dispatchers.IO) {
            try {
                // שולף את ז'אנרי הפריט ואז מחפש דומים
                val genreIds: List<Int> = if (type == "tv") {
                    val details = api.getTvDetails(id, Constants.TMDB_API_KEY, language = tmdbLang)
                    details.genres?.map { it.id } ?: emptyList()
                } else {
                    val details = api.getMovieDetails(id, Constants.TMDB_API_KEY, language = tmdbLang)
                    details.genres?.map { it.id } ?: emptyList()
                }

                val genreParam = genreIds.take(2).joinToString(",") // מקסימום 2 ז'אנרים

                val results = api.discoverMedia(
                    type     = type,
                    genreId  = genreParam.ifBlank { null },
                    year     = null,
                    sortBy   = "popularity.desc",
                    language = tmdbLang,
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

    override suspend fun searchMovies(query: String): Result<List<Movie>> =
        Result.success(emptyList())

    override suspend fun getMovieDetails(id: String): Result<Movie> =
        Result.failure(Exception("Use getMovieFullDetails"))

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
                language = tmdbLang,
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
}