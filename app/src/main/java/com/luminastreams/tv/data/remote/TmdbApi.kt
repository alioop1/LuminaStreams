package com.luminastreams.tv.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

// ── TMDB DTOs ────────────────────────────────────────────────────────────────
data class TmdbResponse(val page: Int, val results: List<TmdbMediaDto>)

data class TmdbMediaDto(
    val id: Int, val title: String?, val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    val overview: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>?
)

data class ExternalIdsDto(@SerializedName("imdb_id") val imdbId: String?)

data class TmdbMovieDetailsDto(
    val id: Int, val title: String, val overview: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("release_date") val releaseDate: String?,
    val runtime: Int?, val credits: CreditsDto?,
    val external_ids: ExternalIdsDto?
)

data class TmdbTvDetailsDto(
    val id: Int, val name: String, val overview: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("first_air_date") val firstAirDate: String?,
    val status: String?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int,
    val credits: CreditsDto?,
    val external_ids: ExternalIdsDto?
)

data class CreditsDto(val cast: List<CastDto>, val crew: List<CrewDto>?)
data class CastDto(val id: Int, val name: String, val character: String, @SerializedName("profile_path") val profilePath: String?)
data class CrewDto(val id: Int, val name: String, val job: String, val department: String)

// ── Torrentio DTOs ──────────────────────────────────────────────────────────
data class TorrentioResponse(val streams: List<TorrentioStream>)

data class TorrentioStream(
    val name: String,
    val title: String,
    val infoHash: String?,
    val url: String?
)

// ── RealDebrid DTOs ─────────────────────────────────────────────────────────
data class RdAddMagnetResponse(val id: String, val uri: String)

data class RdTorrentInfoResponse(
    val id: String,
    val filename: String,
    val status: String,
    val progress: Int,
    val links: List<String>,
    val files: List<RdTorrentFile>
)

data class RdTorrentFile(val id: Int, val path: String, val bytes: Long, val selected: Int)

data class RdUnrestrictResponse(
    val id: String,
    val filename: String,
    val mimeType: String,
    val filesize: Long,
    val link: String,
    val download: String
)

// ── TmdbApi ──────────────────────────────────────────────────────────────────
interface TmdbApi {
    @GET("trending/all/week")
    suspend fun getTrending(@Query("api_key") apiKey: String, @Query("language") language: String = "he-IL", @Query("page") page: Int = 1): TmdbResponse

    @GET("discover/movie")
    suspend fun discoverMovies(@Query("api_key") apiKey: String, @Query("language") language: String = "he-IL", @Query("with_genres") genres: String): TmdbResponse

    @GET("discover/tv")
    suspend fun discoverTv(@Query("api_key") apiKey: String, @Query("language") language: String = "he-IL", @Query("with_genres") genres: String): TmdbResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(@Path("movie_id") movieId: String, @Query("api_key") apiKey: String, @Query("language") language: String = "he-IL", @Query("append_to_response") append: String = "credits,videos,external_ids"): TmdbMovieDetailsDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(@Path("tv_id") seriesId: String, @Query("api_key") apiKey: String, @Query("language") language: String = "he-IL", @Query("append_to_response") append: String = "credits,videos,external_ids"): TmdbTvDetailsDto

    @GET("discover/{type}")
    suspend fun discoverMedia(@Path("type") type: String, @Query("with_genres") genreId: String? = null, @Query("primary_release_year") year: String? = null, @Query("sort_by") sortBy: String = "popularity.desc", @Query("language") language: String = "he-IL", @Query("page") page: Int = 1, @Query("api_key") apiKey: String = "9ab4a284f0c028007b78925852196b79"): TmdbResponse
}

// ── TorrentioApi ─────────────────────────────────────────────────────────────
interface TorrentioApi {
    @GET("stream/{type}/{imdbId}.json")
    suspend fun getStreams(@Path("type") type: String, @Path("imdbId") imdbId: String): TorrentioResponse
}

// ── RealDebridApi ────────────────────────────────────────────────────────────
interface RealDebridApi {
    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(@Header("Authorization") auth: String, @Field("magnet") magnet: String): RdAddMagnetResponse

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(@Header("Authorization") auth: String, @Path("id") torrentId: String): RdTorrentInfoResponse

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(@Header("Authorization") auth: String, @Path("id") torrentId: String, @Field("files") files: String)

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(@Header("Authorization") auth: String, @Field("link") hosterLink: String): RdUnrestrictResponse
}
