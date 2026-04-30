package com.luminastreams.tv.domain.repository

import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.model.SearchResult
import com.luminastreams.tv.data.api.TmdbMovieDetailsDto
import com.luminastreams.tv.data.api.TmdbTvDetailsDto

// ── MediaRepository ────────────────────────────────────────────────────────
interface MediaRepository {
    suspend fun getTrendingMovies(): Result<List<Movie>>
    suspend fun getTrendingTv(): Result<List<Movie>>
    suspend fun searchMovies(query: String): Result<List<Movie>>
    suspend fun searchMulti(query: String, page: Int, isHebrew: Boolean): Result<List<SearchResult>>
    suspend fun getDiscoverySearch(page: Int): Result<List<SearchResult>>
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

    suspend fun discoverFiltered(
        type: String,
        genreId: String? = null,
        tvGenreId: String? = null,
        releaseDateGte: String? = null,
        releaseDateLte: String? = null,
        voteGte: Float? = null,
        language: String? = null,
        networkId: String? = null,
        runtimeGte: Int? = null,
        runtimeLte: Int? = null,
        sortBy: String = "popularity.desc",
        page: Int = 1
    ): Result<List<SearchResult>>
}