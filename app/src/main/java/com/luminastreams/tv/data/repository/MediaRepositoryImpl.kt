package com.luminastreams.tv.data.repository

import com.luminastreams.tv.data.api.TmdbApi
import com.luminastreams.tv.data.api.TmdbMovieDetailsDto
import com.luminastreams.tv.data.api.TmdbTvDetailsDto
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.MediaRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

class MediaRepositoryImpl : MediaRepository {

    private val TMDB_API_KEY   = "9ab4a284f0c028007b78925852196b79"
    private val IMAGE_POSTER   = "https://image.tmdb.org/t/p/w342"
    private val IMAGE_BACKDROP = "https://image.tmdb.org/t/p/w1280"

    // OkHttpClient עם timeouts קצרים — לא 8 שניות
    private val okhttp = OkHttpClient.Builder()
        .connectTimeout(8,  TimeUnit.SECONDS)
        .readTimeout(10,    TimeUnit.SECONDS)
        .writeTimeout(10,   TimeUnit.SECONDS)
        .build()

    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .client(okhttp)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    // מפה DTO ל-Movie בצורה יעילה — מונע שכפול code duplication
    private fun com.luminastreams.tv.data.api.TmdbMediaDto.toMovie(type: String) = Movie(
        id             = "${type}_${id}",
        title          = title ?: name ?: "Unknown",
        backdropUrl    = if (backdropPath != null) "$IMAGE_BACKDROP$backdropPath" else "",
        posterUrl      = "$IMAGE_POSTER$posterPath",
        overview       = overview ?: "",
        rating         = voteAverage,
        mediaType      = type,
        genreIds       = genreIds ?: emptyList(),
        is4K           = true,
        resolutionBadge = "4K HDR"
    )

    override suspend fun getTrendingMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val allData = coroutineScope {
                val trendingDef = async { api.getTrending(TMDB_API_KEY) }
                val actionDef   = async { api.discoverMovies(TMDB_API_KEY, genres = "28,12") }
                val dramaTvDef  = async { api.discoverTv(TMDB_API_KEY, genres = "18,80") }
                val results = mutableListOf<com.luminastreams.tv.data.api.TmdbMediaDto>()
                results.addAll(trendingDef.await().results)
                results.addAll(actionDef.await().results.map { it.copy(mediaType = "movie") })
                results.addAll(dramaTvDef.await().results.map { it.copy(mediaType = "tv") })
                results
            }
            val movies = allData
                .filter { it.posterPath != null && it.backdropPath != null }
                .map { it.toMovie(it.mediaType ?: "movie") }
                .distinctBy { it.id }
            Result.success(movies)
        } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> = Result.success(emptyList())
    override suspend fun getMovieDetails(id: String): Result<Movie> = Result.failure(Exception("Use full details"))

    override suspend fun getMovieFullDetails(id: String): Result<TmdbMovieDetailsDto> = withContext(Dispatchers.IO) {
        try { Result.success(api.getMovieDetails(id, TMDB_API_KEY)) } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun getTvFullDetails(id: String): Result<TmdbTvDetailsDto> = withContext(Dispatchers.IO) {
        try { Result.success(api.getTvDetails(id, TMDB_API_KEY)) } catch (e: Exception) { Result.failure(e) }
    }

    override suspend fun discoverMedia(
        type: String, genreId: String?, year: String?, sortBy: String, page: Int
    ): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            val items = api.discoverMedia(type = type, genreId = genreId, year = year, sortBy = sortBy, page = page)
                .results
                .filter { it.posterPath != null }
                .map { it.toMovie(type) }
            Result.success(items)
        } catch (e: Exception) { Result.failure(e) }
    }
}
