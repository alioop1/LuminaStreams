package com.luminastreams.tv.data.api

import retrofit2.http.GET
import retrofit2.http.Path

interface TorrentioApi {
    // מנוע Torrentio מחזיר לינקים לפי IMDB ID
    @GET("stream/{type}/{imdbId}.json")
    suspend fun getStreams(
        @Path("type") type: String, // "movie" או "series"
        @Path("imdbId") imdbId: String // למשל "tt0816692" (Interstellar)
    ): TorrentioResponse
}

data class TorrentioResponse(
    val streams: List<TorrentioStream>
)

data class TorrentioStream(
    val name: String, // שם הקבוצה (למשל Torrentio\n1080p)
    val title: String, // שם הקובץ המלא + גודל
    val infoHash: String?, // נשתמש בזה כדי ליצור Magnet
    val url: String? // או לינק ישיר
)