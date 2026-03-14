package com.luminastreams.tv.data.api

import com.google.gson.annotations.SerializedName

data class TmdbResponse(val page: Int, val results: List<TmdbMediaDto>)

data class TmdbMediaDto(
    val id: Int, val title: String?, val name: String?,
    @SerializedName("poster_path") val posterPath: String?,
    @SerializedName("backdrop_path") val backdropPath: String?,
    val overview: String?, @SerializedName("vote_average") val voteAverage: Float,
    @SerializedName("media_type") val mediaType: String?,
    @SerializedName("genre_ids") val genreIds: List<Int>? // <-- We now capture the real genres!
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
    val status: String?, @SerializedName("number_of_seasons") val numberOfSeasons: Int,
    val credits: CreditsDto?,
    val external_ids: ExternalIdsDto?
)

data class CreditsDto(val cast: List<CastDto>, val crew: List<CrewDto>?)
data class CastDto(val id: Int, val name: String, val character: String, @SerializedName("profile_path") val profilePath: String?)
data class CrewDto(val id: Int, val name: String, val job: String, val department: String)