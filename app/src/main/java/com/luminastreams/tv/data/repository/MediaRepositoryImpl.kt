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
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class MediaRepositoryImpl : MediaRepository {

    private val TMDB_API_KEY = "9ab4a284f0c028007b78925852196b79"
    private val IMAGE_POSTER_URL = "https://image.tmdb.org/t/p/w342"
    private val IMAGE_BACKDROP_URL = "https://image.tmdb.org/t/p/w1280"

    private val api: TmdbApi = Retrofit.Builder()
        .baseUrl("https://api.themoviedb.org/3/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(TmdbApi::class.java)

    override suspend fun getTrendingMovies(): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            // Fetch everything simultaneously to keep loading times fast
            val allData = coroutineScope {
                val trendingDef = async { api.getTrending(TMDB_API_KEY) }
                val actionMoviesDef = async { api.discoverMovies(TMDB_API_KEY, genres = "28,12") } // Action & Adventure
                val dramaTvDef = async { api.discoverTv(TMDB_API_KEY, genres = "18,80") } // Drama & Crime

                val results = mutableListOf<com.luminastreams.tv.data.api.TmdbMediaDto>()
                results.addAll(trendingDef.await().results)

                // Force media_type for specific discover queries since TMDB omits it there
                results.addAll(actionMoviesDef.await().results.map { it.copy(mediaType = "movie") })
                results.addAll(dramaTvDef.await().results.map { it.copy(mediaType = "tv") })

                results
            }

            val movies = allData.mapNotNull { dto ->
                if (dto.posterPath == null || dto.backdropPath == null) return@mapNotNull null
                val type = dto.mediaType ?: "movie"

                Movie(
                    id = "${type}_${dto.id}",
                    title = dto.title ?: dto.name ?: "Unknown",
                    backdropUrl = "$IMAGE_BACKDROP_URL${dto.backdropPath}",
                    posterUrl = "$IMAGE_POSTER_URL${dto.posterPath}",
                    overview = dto.overview ?: "",
                    rating = dto.voteAverage,
                    mediaType = type,
                    genreIds = dto.genreIds ?: emptyList(),
                    is4K = true,
                    resolutionBadge = "4K HDR"
                )
            }
            Result.success(movies.distinctBy { it.id }) // Remove duplicates from combined lists
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun searchMovies(query: String): Result<List<Movie>> = Result.success(emptyList())
    override suspend fun getMovieDetails(id: String): Result<Movie> = Result.failure(Exception("Use full details instead"))

    override suspend fun getMovieFullDetails(id: String): Result<TmdbMovieDetailsDto> = withContext(Dispatchers.IO) {
        Result.success(api.getMovieDetails(id, TMDB_API_KEY))
    }

    override suspend fun getTvFullDetails(id: String): Result<TmdbTvDetailsDto> = withContext(Dispatchers.IO) {
        Result.success(api.getTvDetails(id, TMDB_API_KEY))
    }

    // UPDATED: Changed return type to List<Movie> and added mapping logic
    override suspend fun discoverMedia(type: String, genreId: String?, year: String?, sortBy: String, page: Int): Result<List<Movie>> {
        return try {
            val response = api.discoverMedia(type = type, genreId = genreId, year = year, sortBy = sortBy, page = page)
            val items = response.results.mapNotNull { dto ->
                if (dto.posterPath == null) return@mapNotNull null

                Movie(
                    id = "${type}_${dto.id}",
                    title = dto.title ?: dto.name ?: "Unknown",
                    backdropUrl = if (dto.backdropPath != null) "$IMAGE_BACKDROP_URL${dto.backdropPath}" else "",
                    posterUrl = "$IMAGE_POSTER_URL${dto.posterPath}",
                    overview = dto.overview ?: "",
                    rating = dto.voteAverage,
                    mediaType = type,
                    genreIds = dto.genreIds ?: emptyList(),
                    is4K = true,
                    resolutionBadge = "4K HDR"
                )
            }
            Result.success(items)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}