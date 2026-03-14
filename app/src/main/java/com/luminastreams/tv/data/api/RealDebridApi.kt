package com.luminastreams.tv.data.api

import com.google.gson.annotations.SerializedName
import retrofit2.http.*

interface RealDebridApi {

    // 1. הוספת לינק מגנט (Magnet) לשרתי RD
    @FormUrlEncoded
    @POST("torrents/addMagnet")
    suspend fun addMagnet(
        @Header("Authorization") auth: String,
        @Field("magnet") magnet: String
    ): RdAddMagnetResponse

    // 2. קבלת פרטי הטורנט שנוסף כדי למצוא את מזהי קבצי הוידאו בתוכו
    @GET("torrents/info/{id}")
    suspend fun getTorrentInfo(
        @Header("Authorization") auth: String,
        @Path("id") torrentId: String
    ): RdTorrentInfoResponse

    // 3. בחירת הקבצים שאנחנו רוצים לפתוח מתוך הטורנט (בדרך כלל fileId ספציפי או "all")
    @FormUrlEncoded
    @POST("torrents/selectFiles/{id}")
    suspend fun selectFiles(
        @Header("Authorization") auth: String,
        @Path("id") torrentId: String,
        @Field("files") files: String
    )

    // 4. פענוח (Unrestrict) ללינק ה-Premium הישיר
    @FormUrlEncoded
    @POST("unrestrict/link")
    suspend fun unrestrictLink(
        @Header("Authorization") auth: String,
        @Field("link") hosterLink: String
    ): RdUnrestrictResponse
}

// ---- Data Models ----

data class RdAddMagnetResponse(
    val id: String,
    val uri: String
)

data class RdTorrentInfoResponse(
    val id: String,
    val filename: String,
    val status: String,
    val progress: Int,
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
    val download: String // זה הלינק הישיר ל-ExoPlayer!
)