package com.luminastreams.tv.data.remote

import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.FormUrlEncoded
import retrofit2.http.Field

interface RealDebridApi {
    @GET("rest/1.0/torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") auth: String,
        @Path("id") torrentId: String
    ): RdTorrentInfoResponse

    @FormUrlEncoded
    @POST("rest/1.0/unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") auth: String,
        @Field("link") link: String
    ): RdUnrestrictResponse
}

// Data Classes (עם אופטימיזציה ל-Garbage Collector - ללא משתנים מיותרים)
data class RdTorrentInfoResponse(
    val id: String,
    val filename: String,
    val links: List<String>
)

data class RdUnrestrictResponse(
    val id: String,
    val download: String, // הלינק הסופי לנגן (AV1/HEVC)
    val streamable: Int
)