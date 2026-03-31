package com.luminastreams.tv.data.repository

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

/**
 * MediaRepositoryImpl — TMDB data source.
 *
 * Image URLs are tier-aware via Constants.backdropUrl / Constants.posterUrl:
 *   HIGH  → /original/ backdrops + /w780/ posters   (Nvidia Shield, LG OLED …)
 *   MID   → /w1280/   backdrops + /w500/ posters
 *   LOW   → /w500/    backdrops + /w342/ posters   (2 GB Mali boxes)
 *
 * resolutionBadge is also tier-aware so the UI badge reflects real capability.
 */
class MediaRepositoryImpl : MediaRepository {

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

    // ── Tier-aware resolution badge ────────────────────────────────────────────
    private val tierBadge: String get() = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> "4K HDR"
        DeviceProfile.Tier.MID  -> "FHD 1080p"
        DeviceProfile.Tier.LOW  -> "HD 720p"
    }

    // ── Internal mapper — tier-aware images + badge ────────────────────────────
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

    // ── Public API ─────────────────────────────────────────────────────────────
    override suspend fun getTrendingMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val movies = coroutineScope {
                val trendingDef = async { api.getTrending(Constants.TMDB_API_KEY) }
                val actionDef   = async { api.discoverMovies(Constants.TMDB_API_KEY, genres = "28,12") }
                val dramaTvDef  = async { api.discoverTv(Constants.TMDB_API_KEY, genres = "18,80") }

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

    override suspend fun searchMovies(query: String): Result<List<Movie>> =
        Result.success(emptyList())

    override suspend fun getMovieDetails(id: String): Result<Movie> =
        Result.failure(Exception("Use getMovieFullDetails"))

    override suspend fun getMovieFullDetails(id: String): Result<TmdbMovieDetailsDto> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(api.getMovieDetails(id, Constants.TMDB_API_KEY))
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    override suspend fun getTvFullDetails(id: String): Result<TmdbTvDetailsDto> =
        withContext(Dispatchers.IO) {
            try {
                Result.success(api.getTvDetails(id, Constants.TMDB_API_KEY))
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
                type    = type,
                genreId = genreId,
                year    = year,
                sortBy  = sortBy,
                page    = page,
                apiKey  = Constants.TMDB_API_KEY
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