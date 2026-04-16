package com.luminastreams.tv.domain.repository

import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.data.api.TmdbMovieDetailsDto
import com.luminastreams.tv.data.api.TmdbTvDetailsDto

// ── MediaRepository ────────────────────────────────────────────────────────
interface MediaRepository {
    suspend fun getTrendingMovies(): Result<List<Movie>>
    suspend fun getTrendingTv(): Result<List<Movie>>
    suspend fun searchMovies(query: String): Result<List<Movie>>
    suspend fun getMovieDetails(id: String): Result<Movie>
    suspend fun getMovieFullDetails(id: String): Result<TmdbMovieDetailsDto>
    suspend fun getTvFullDetails(id: String): Result<TmdbTvDetailsDto>
    suspend fun getSimilarMedia(id: String, type: String): Result<List<Movie>>
    suspend fun discoverMedia(
        type: String,
        genreId: String?,
        year: String?,
        sortBy: String,
        page: Int
    ): Result<List<Movie>>
}

// ── PlayerRepository ───────────────────────────────────────────────────────
interface PlayerRepository {
    suspend fun getStreamUrl(movieId: String, type: String): Result<String>
    suspend fun getEmbedSources(movieId: String, type: String): Result<List<String>>
    suspend fun getTvEpisodeStreamUrl(
        seriesId: String,
        season: Int,
        episode: Int
    ): Result<String>
}