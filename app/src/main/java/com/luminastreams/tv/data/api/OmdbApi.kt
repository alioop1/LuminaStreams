package com.luminastreams.tv.data.api

import com.google.gson.annotations.SerializedName
import com.luminastreams.tv.core.Constants
import retrofit2.http.GET
import retrofit2.http.Query

data class OmdbRatingDto(
    @SerializedName("Source") val source: String,
    @SerializedName("Value") val value: String
)

data class OmdbResponseDto(
    @SerializedName("Title") val title: String?,
    @SerializedName("Ratings") val ratings: List<OmdbRatingDto>?
)

interface OmdbApi {
    @GET("/")
    suspend fun getTitleDetails(
        @Query("i") imdbId: String,
        @Query("apikey") apiKey: String = Constants.OMDB_API_KEY
    ): OmdbResponseDto
}
