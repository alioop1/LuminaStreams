package com.luminastreams.tv.data.api

import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

interface TmdbApi {
    @GET("trending/all/week")
    suspend fun getTrending(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "he-IL",
        @Query("page") page: Int = 1
    ): TmdbResponse

    // Fetch SPECIFIC Movie Genres
    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "he-IL",
        @Query("with_genres") genres: String
    ): TmdbResponse

    // Fetch SPECIFIC TV Genres
    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "he-IL",
        @Query("with_genres") genres: String
    ): TmdbResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id") movieId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "he-IL",
        @Query("append_to_response") append: String = "credits,videos,external_ids"
    ): TmdbMovieDetailsDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id") seriesId: String,
        @Query("api_key") apiKey: String,
        @Query("language") language: String = "he-IL",
        @Query("append_to_response") append: String = "credits,videos,external_ids"
    ): TmdbTvDetailsDto

    // UPDATED: Return type changed to TmdbResponse
    @GET("discover/{type}")
    suspend fun discoverMedia(
        @Path("type") type: String,
        @Query("with_genres") genreId: String? = null,
        @Query("primary_release_year") year: String? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") language: String = "he-IL",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = "9ab4a284f0c028007b78925852196b79"
    ): TmdbResponse
}