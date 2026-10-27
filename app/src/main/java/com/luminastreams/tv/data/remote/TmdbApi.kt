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

data class ReviewResultDto(
    val author: String,
    val content: String,
    @SerializedName("author_details") val authorDetails: AuthorDetailsDto?
)
data class AuthorDetailsDto(val rating: Double?)
data class ReviewsDto(val results: List<ReviewResultDto>?)

data class TmdbMovieDetailsDto(
    val id: Int, val title: String, val overview: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("release_date") val releaseDate: String?,
    @SerializedName("original_language") val originalLanguage: String?,
    val runtime: Int?, val genres: List<GenreDto>?,
    @SerializedName("production_companies") val productionCompanies: List<ProductionCompanyDto>?,
    val credits: CreditsDto?, val external_ids: ExternalIdsDto?,
    val images: ImagesDto?,
    @SerializedName("belongs_to_collection") val belongsToCollection: BelongsToCollectionDto?,
    val reviews: ReviewsDto?,
    val videos: VideosDto?
)

data class TmdbEpisodeStubDto(
    @SerializedName("air_date") val airDate: String?,
    @SerializedName("episode_number") val episodeNumber: Int?,
    @SerializedName("season_number") val seasonNumber: Int?,
    val name: String?,
    @SerializedName("still_path") val stillPath: String?
)

data class TmdbTvDetailsDto(
    val id: Int, val name: String, val overview: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("first_air_date") val firstAirDate: String?,
    @SerializedName("original_language") val originalLanguage: String?,
    val status: String?, @SerializedName("number_of_seasons") val numberOfSeasons: Int,
    @SerializedName("number_of_episodes") val numberOfEpisodes: Int? = null,
    @SerializedName("in_production") val inProduction: Boolean? = null,
    @SerializedName("next_episode_to_air") val nextEpisodeToAir: TmdbEpisodeStubDto? = null,
    @SerializedName("last_episode_to_air") val lastEpisodeToAir: TmdbEpisodeStubDto? = null,
    val genres: List<GenreDto>?, val networks: List<NetworkDto>?,
    val credits: CreditsDto?, val external_ids: ExternalIdsDto?,
    val images: ImagesDto?,
    val reviews: ReviewsDto?,
    val videos: VideosDto?
)

data class VideosDto(val results: List<VideoResultDto>?)
data class VideoResultDto(val key: String, val site: String, val type: String)

data class CreditsDto(val cast: List<CastDto>, val crew: List<CrewDto>?)
data class CastDto(val id: Int, val name: String, val character: String, @SerializedName("profile_path") val profilePath: String?)
data class CrewDto(val id: Int, val name: String, val job: String, val department: String)


data class TmdbPersonDetailsDto(
    val id: Int,
    val name: String,
    val biography: String?,
    val birthday: String?,
    val deathday: String?,
    @SerializedName("place_of_birth") val placeOfBirth: String?,
    @SerializedName("profile_path") val profilePath: String?,
    @SerializedName("combined_credits") val combinedCredits: CombinedCreditsDto?
)

data class CombinedCreditsDto(val cast: List<TmdbMediaDto>?)

interface TmdbApi {
    @GET("trending/all/week") suspend fun getTrending(@Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("page") page: Int = 1): TmdbResponse
    @GET("discover/movie") suspend fun discoverMovies(@Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("with_genres") genres: String): TmdbResponse
    @GET("discover/tv") suspend fun discoverTv(@Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("with_genres") genres: String): TmdbResponse
    @GET("movie/{movie_id}") suspend fun getMovieDetails(@Path("movie_id") movieId: String, @Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("append_to_response") append: String = "credits,videos,external_ids,images,reviews", @Query("include_image_language") imageLangs: String = "he,en,null"): TmdbMovieDetailsDto
    @GET("tv/{tv_id}") suspend fun getTvDetails(@Path("tv_id") seriesId: String, @Query("api_key") apiKey: String, @Query("language") language: String = "en-US", @Query("append_to_response") append: String = "credits,videos,external_ids,images,reviews", @Query("include_image_language") imageLangs: String = "he,en,null"): TmdbTvDetailsDto
    @GET("movie/{movie_id}/images") suspend fun getMovieImages(@Path("movie_id") movieId: String, @Query("api_key") apiKey: String = Constants.TMDB_API_KEY, @Query("include_image_language") imageLangs: String = "he,en,null"): ImagesDto
    @GET("tv/{tv_id}/images") suspend fun getTvImages(@Path("tv_id") tvId: String, @Query("api_key") apiKey: String = Constants.TMDB_API_KEY, @Query("include_image_language") imageLangs: String = "he,en,null"): ImagesDto
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

    @GET("person/{person_id}") suspend fun getPersonDetails(
        @Path("person_id") personId: String,
        @Query("api_key") apiKey: String = Constants.TMDB_API_KEY,
        @Query("append_to_response") append: String = "combined_credits",
        @Query("language") language: String = "en-US"
    ): TmdbPersonDetailsDto
}

interface AllDebridApi {
    @GET("v4/ping")
    suspend fun ping(): retrofit2.Response<Any>

    @GET("v4/user?agent=LuminaStreams")
    suspend fun getUserInfo(@Header("Authorization") auth: String): retrofit2.Response<com.google.gson.JsonElement>

    @GET("v4/pin/get?agent=LuminaStreams")
    suspend fun getPin(): retrofit2.Response<com.google.gson.JsonElement>

    @GET("v4/pin/check?agent=LuminaStreams")
    suspend fun checkPin(
        @Query("check") check: String,
        @Query("pin") pin: String
    ): retrofit2.Response<com.google.gson.JsonElement>

    @FormUrlEncoded
    @POST("v4/magnet/upload?agent=LuminaStreams")
    suspend fun uploadMagnet(
        @Header("Authorization") auth: String,
        @Field("magnets[]") magnets: List<String>
    ): retrofit2.Response<com.google.gson.JsonElement>

    @GET("v4.1/magnet/status?agent=LuminaStreams")
    suspend fun getAllMagnets(
        @Header("Authorization") auth: String
    ): retrofit2.Response<com.google.gson.JsonElement>

    @GET("v4.1/magnet/status?agent=LuminaStreams")
    suspend fun getMagnetStatus(
        @Header("Authorization") auth: String,
        @Query("id") id: String
    ): retrofit2.Response<com.google.gson.JsonElement>

    @GET("v4/magnet/files?agent=LuminaStreams")
    suspend fun getMagnetFiles(
        @Header("Authorization") auth: String,
        @Query("id") id: String
    ): retrofit2.Response<com.google.gson.JsonElement>

    @FormUrlEncoded
    @POST("v4/link/unlock?agent=LuminaStreams")
    suspend fun unlockLink(
        @Header("Authorization") auth: String,
        @Field("link") link: String
    ): retrofit2.Response<com.google.gson.JsonElement>

    @GET("v4/magnet/delete?agent=LuminaStreams")
    suspend fun deleteMagnet(
        @Header("Authorization") auth: String,
        @Query("id") id: String
    ): retrofit2.Response<com.google.gson.JsonElement>
}