package com.luminastreams.tv.domain.usecase

import com.luminastreams.tv.data.api.RealDebridApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RealDebridManager {

    private val api = Retrofit.Builder()
        .baseUrl("https://api.real-debrid.com/rest/1.0/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RealDebridApi::class.java)

    // הפונקציה עכשיו מקבלת את הטוקן באופן דינמי מההגדרות!
    suspend fun resolveMagnetToStream(magnetUri: String, apiToken: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank() || apiToken == "QKFCDMZG7DVXOPRLFCG63HDT4WFEDCJPRBXUY2H7J465Z4O7MPSA") {
                throw Exception("טוקן Real-Debrid חסר או לא מוגדר בהגדרות!")
            }

            val authHeader = "Bearer $apiToken"

            val addResponse = api.addMagnet(authHeader, magnetUri)
            val torrentId = addResponse.id

            val torrentInfo = api.getTorrentInfo(authHeader, torrentId)
            val videoFiles = torrentInfo.files.filter { it.path.endsWith(".mkv", true) || it.path.endsWith(".mp4", true) }

            if (videoFiles.isEmpty()) throw Exception("לא נמצאו קבצי וידאו בטורנט")
            val mainVideoFile = videoFiles.maxByOrNull { it.bytes }!!

            api.selectFiles(authHeader, torrentId, mainVideoFile.id.toString())

            var readyInfo = api.getTorrentInfo(authHeader, torrentId)
            var attempts = 0
            while (readyInfo.links.isEmpty() && attempts < 10) {
                delay(1000)
                readyInfo = api.getTorrentInfo(authHeader, torrentId)
                attempts++
            }

            if (readyInfo.links.isEmpty()) throw Exception("זמן ההמתנה ל-Real-Debrid פג")

            val unrestrictResponse = api.unrestrictLink(authHeader, readyInfo.links.first())
            Result.success(unrestrictResponse.download)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}