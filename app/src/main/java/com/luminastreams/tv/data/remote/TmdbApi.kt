package com.luminastreams.tv.data.api

import com.google.gson.annotations.SerializedName
import com.luminastreams.tv.core.Constants
import retrofit2.http.*

data class GenreDto(val id: Int, val name: String)

data class ProductionCompanyDto(
    val id: Int, val name: String,
    @SerializedName("logo_path") val logoPath: String?,
    @SerializedName("origin_country") val originCountry: String?
)

data class NetworkDto(
    val id: Int, val name: String,
    @SerializedName("logo_path") val logoPath: String?
)

data class ImagesDto(val logos: List<LogoDto>?)

data class LogoDto(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("iso_639_1") val lang: String?
)

data class TmdbResponse(val page: Int, val results: List<TmdbMediaDto>)

data class TmdbMediaDto(
    val id: Int, val title: String?, val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    val overview: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>?,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("first_air_date") val firstAirDate: String?
)

data class ExternalIdsDto(@SerializedName("imdb_id") val imdbId: String?)

data class BelongsToCollectionDto(
    val id: Int, val name: String,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?
)

data class TmdbMovieDetailsDto(
    val id: Int, val title: String, val overview: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("release_date") val releaseDate: String?,
    val runtime: Int?, val genres: List<GenreDto>?,
    @SerializedName("production_companies") val productionCompanies: List<ProductionCompanyDto>?,
    val credits: CreditsDto?, val external_ids: ExternalIdsDto?,
    val images: ImagesDto?,
    @SerializedName("belongs_to_collection") val belongsToCollection: BelongsToCollectionDto?
)

data class TmdbTvDetailsDto(
    val id: Int, val name: String, val overview: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("first_air_date") val firstAirDate: String?,
    val status: String?, @SerializedName("number_of_seasons") val numberOfSeasons: Int,
    val genres: List<GenreDto>?, val networks: List<NetworkDto>?,
    val credits: CreditsDto?, val external_ids: ExternalIdsDto?,
    val images: ImagesDto?
)

data class CreditsDto(val cast: List<CastDto>, val crew: List<CrewDto>?)
data class CastDto(val id: Int, val name: String, val character: String, @SerializedName("profile_path") val profilePath: String?)
data class CrewDto(val id: Int, val name: String, val job: String, val department: String)

data class RdAddMagnetResponse(val id: String, val uri: String)
data class RdTorrentInfoResponse(val id: String, val filename: String, val status: String, val progress: Double, val links: List<String>, val files: List<RdTorrentFile>)
data class RdTorrentFile(val id: Int, val path: String, val bytes: Long, val selected: Int)
data class RdUnrestrictResponse(val id: String, val filename: String, val mimeType: String, val filesize: Long, val link: String, val download: String)

interface TmdbApi {
    @GET("trending/all/week") suspend fun getTrending(@Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("page") page: Int = 1): TmdbResponse
    @GET("discover/movie") suspend fun discoverMovies(@Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("with_genres") genres: String): TmdbResponse
    @GET("discover/tv") suspend fun discoverTv(@Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("with_genres") genres: String): TmdbResponse
    @GET("movie/{movie_id}") suspend fun getMovieDetails(@Path("movie_id") movieId: String, @Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("append_to_response") append: String = "credits,videos,external_ids,images"): TmdbMovieDetailsDto
    @GET("tv/{tv_id}") suspend fun getTvDetails(@Path("tv_id") seriesId: String, @Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("append_to_response") append: String = "credits,videos,external_ids,images"): TmdbTvDetailsDto
    @GET("discover/{type}") suspend fun discoverMedia(
        @Path("type") type: String,
        @Query("with_genres") genreId: String? = null,
        @Query("primary_release_year") year: String? = null,
        @Query("primary_release_date.gte") releaseDateGte: String? = null,
        @Query("primary_release_date.lte") releaseDateLte: String? = null,
        @Query("first_air_date.gte") airDateGte: String? = null,
        @Query("first_air_date.lte") airDateLte: String? = null,
        @Query("vote_average.gte") voteGte: Float? = null,
        @Query("with_original_language") language: String? = null,
        @Query("with_networks") networkId: String? = null,
        @Query("with_runtime.gte") runtimeGte: Int? = null,
        @Query("with_runtime.lte") runtimeLte: Int? = null,
        @Query("sort_by") sortBy: String = "popularity.desc",
        @Query("language") uiLanguage: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("api_key") apiKey: String = Constants.TMDB_API_KEY
    ): TmdbResponse

    @GET("search/multi") suspend fun searchMulti(
        @Query("query") query: String,
        @Query("language") language: String = "en-US",
        @Query("page") page: Int = 1,
        @Query("include_adult") includeAdult: Boolean = false,
        @Query("api_key") apiKey: String = Constants.TMDB_API_KEY
    ): TmdbResponse
}

interface RealDebridApi {
    @FormUrlEncoded @POST("torrents/addMagnet") suspend fun addMagnet(@Header("Authorization") auth: String, @Field("magnet") magnet: String): RdAddMagnetResponse
    @GET("torrents/info/{id}") suspend fun getTorrentInfo(@Header("Authorization") auth: String, @Path("id") torrentId: String): RdTorrentInfoResponse
    @FormUrlEncoded @POST("torrents/selectFiles/{id}") suspend fun selectFiles(@Header("Authorization") auth: String, @Path("id") torrentId: String, @Field("files") files: String)
    @FormUrlEncoded @POST("unrestrict/link") suspend fun unrestrictLink(@Header("Authorization") auth: String, @Field("link") hosterLink: String): RdUnrestrictResponse
}