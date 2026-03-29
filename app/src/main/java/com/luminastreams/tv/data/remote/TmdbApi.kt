package com.luminastreams.tv.data.api

import com.google.gson.annotations.SerializedName
import com.luminastreams.tv.core.Constants
import retrofit2.http.*

/**
 * כל ה-DTOs וממשקי ה-API של TMDB ו-RealDebrid.
 *
 * שינויים מהגרסה הקודמת:
 * 1. הוסרו TorrentioResponse ו-TorrentioStream (מוגדרים ב-DetailsViewModel)
 * 2. נוספו GenreDto, ProductionCompanyDto, NetworkDto
 * 3. TmdbMovieDetailsDto קיבל genres + productionCompanies + images
 * 4. TmdbTvDetailsDto קיבל genres + networks + images
 * 5. תוקן progress ב-RdTorrentInfoResponse ל-Double כדי למנוע קריסות
 * 6. נוספו מודלים לשליפת לוגואים (ImagesDto, LogoDto)
 *
 * Path: app/src/main/java/com/luminastreams/tv/data/api/TmdbApi.kt
 */

// ── Genre DTO ─────────────────────────────────────────────────────────────────
data class GenreDto(
    val id: Int,
    val name: String
)

// ── Production Company DTO ────────────────────────────────────────────────────
data class ProductionCompanyDto(
    val id: Int,
    val name: String,
    @SerializedName("logo_path")      val logoPath: String?,
    @SerializedName("origin_country") val originCountry: String?
)

// ── Network DTO (TV only) ──────────────────────────────────────────────────────
data class NetworkDto(
    val id: Int,
    val name: String,
    @SerializedName("logo_path") val logoPath: String?
)

// ── Images & Logos ────────────────────────────────────────────────────────────
data class ImagesDto(
    val logos: List<LogoDto>?
)

data class LogoDto(
    @SerializedName("file_path") val filePath: String,
    @SerializedName("iso_639_1") val lang: String?
)

// ── TMDB Generic response ─────────────────────────────────────────────────────
data class TmdbResponse(val page: Int, val results: List<TmdbMediaDto>)

data class TmdbMediaDto(
    val id: Int,
    val title: String?,
    val name: String?,
    @SerializedName("poster_path")   val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    val overview: String?,
    @SerializedName("vote_average")  val voteAverage: Float,
    @SerializedName("media_type")    val mediaType: String?,
    @SerializedName("genre_ids")     val genreIds: List<Int>?
)

// ── External IDs ──────────────────────────────────────────────────────────────
data class ExternalIdsDto(@SerializedName("imdb_id") val imdbId: String?)

// ── Movie Details ─────────────────────────────────────────────────────────────
data class TmdbMovieDetailsDto(
    val id: Int,
    val title: String,
    val overview: String?,
    @SerializedName("backdrop_path")       val backdropPath: String?,
    @SerializedName("poster_path")         val posterPath: String?,
    @SerializedName("vote_average")        val voteAverage: Float,
    @SerializedName("release_date")        val releaseDate: String?,
    val runtime: Int?,
    /** רשימת ז'אנרים מלאה (לא רק IDs) */
    val genres: List<GenreDto>?,
    /** חברות הפקה */
    @SerializedName("production_companies") val productionCompanies: List<ProductionCompanyDto>?,
    val credits: CreditsDto?,
    val external_ids: ExternalIdsDto?,
    /** תמונות (כולל לוגואים) */
    val images: ImagesDto?
)

// ── TV Details ────────────────────────────────────────────────────────────────
data class TmdbTvDetailsDto(
    val id: Int,
    val name: String,
    val overview: String?,
    @SerializedName("backdrop_path")   val backdropPath: String?,
    @SerializedName("poster_path")     val posterPath: String?,
    @SerializedName("vote_average")    val voteAverage: Float,
    @SerializedName("first_air_date")  val firstAirDate: String?,
    val status: String?,
    @SerializedName("number_of_seasons") val numberOfSeasons: Int,
    /** רשימת ז'אנרים מלאה */
    val genres: List<GenreDto>?,
    /** רשת שידור (Netflix / HBO / וכו') */
    val networks: List<NetworkDto>?,
    val credits: CreditsDto?,
    val external_ids: ExternalIdsDto?,
    /** תמונות (כולל לוגואים) */
    val images: ImagesDto?
)

// ── Credits ───────────────────────────────────────────────────────────────────
data class CreditsDto(
    val cast: List<CastDto>,
    val crew: List<CrewDto>?
)

data class CastDto(
    val id: Int,
    val name: String,
    val character: String,
    @SerializedName("profile_path") val profilePath: String?
)

data class CrewDto(
    val id: Int,
    val name: String,
    val job: String,
    val department: String
)

// ── RealDebrid DTOs ───────────────────────────────────────────────────────────
data class RdAddMagnetResponse(val id: String, val uri: String)

data class RdTorrentInfoResponse(
    val id: String,
    val filename: String,
    val status: String,
    val progress: Double,
    val links: List<String>,
    val files: List<RdTorrentFile>
)

data class RdTorrentFile(
    val id: Int,
    val path: String,
    val bytes: Long,
    val selected: Int
)

data class RdUnrestrictResponse(
    val id: String,
    val filename: String,
    val mimeType: String,
    val filesize: Long,
    val link: String,
    val download: String
)

// ── TmdbApi Interface ─────────────────────────────────────────────────────────
interface TmdbApi {

    @GET("trending/all/week")
    suspend fun getTrending(
        @Query("api_key")  apiKey:   String,
        @Query("language") language: String = "en-US",
        @Query("page")     page:     Int    = 1
    ): TmdbResponse

    @GET("discover/movie")
    suspend fun discoverMovies(
        @Query("api_key")      apiKey:   String,
        @Query("language")     language: String = "en-US",
        @Query("with_genres")  genres:   String
    ): TmdbResponse

    @GET("discover/tv")
    suspend fun discoverTv(
        @Query("api_key")     apiKey:   String,
        @Query("language")    language: String = "en-US",
        @Query("with_genres") genres:   String
    ): TmdbResponse

    @GET("movie/{movie_id}")
    suspend fun getMovieDetails(
        @Path("movie_id")          movieId:  String,
        @Query("api_key")          apiKey:   String,
        @Query("language")         language: String = "en-US",
        @Query("append_to_response") append: String = "credits,videos,external_ids,images"
    ): TmdbMovieDetailsDto

    @GET("tv/{tv_id}")
    suspend fun getTvDetails(
        @Path("tv_id")               seriesId: String,
        @Query("api_key")            apiKey:   String,
        @Query("language")           language: String = "en-US",
        @Query("append_to_response") append:   String = "credits,videos,external_ids,images"
    ): TmdbTvDetailsDto

    @GET("discover/{type}")
    suspend fun discoverMedia(
        @Path("type")                type:    String,
        @Query("with_genres")        genreId: String? = null,
        @Query("primary_release_year") year:  String? = null,
        @Query("sort_by")            sortBy:  String  = "popularity.desc",
        @Query("language")           language: String = "en-US",
        @Query("page")               page:    Int     = 1,
        @Query("api_key")            apiKey:  String  = Constants.TMDB_API_KEY
    ): TmdbResponse
}

// ── RealDebridApi Interface ───────────────────────────────────────────────────
interface RealDebridApi {

    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") auth:   String,
        @Field("magnet")         magnet: String
    ): RdAddMagnetResponse

    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") auth:      String,
        @Path("id")              torrentId: String
    ): RdTorrentInfoResponse

    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Header("Authorization") auth:      String,
        @Path("id")              torrentId: String,
        @Field("files")          files:     String
    )

    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") auth:       String,
        @Field("link")           hosterLink: String
    ): RdUnrestrictResponse
}