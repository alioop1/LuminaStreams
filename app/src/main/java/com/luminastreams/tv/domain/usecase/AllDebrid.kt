package com.luminastreams.tv.domain.usecase

import android.content.Context
import android.util.Log
import com.luminastreams.tv.core.LuminaApp
import com.luminastreams.tv.core.TrustedHttpClient
import com.luminastreams.tv.data.api.AllDebridApi
import com.luminastreams.tv.data.repository.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.json.JSONObject
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import java.util.Locale

sealed class AdAuthResult {
    data class ShowUserCode(val code: String, val url: String) : AdAuthResult()
    object Success : AdAuthResult()
    data class Error(val message: String) : AdAuthResult()
}

class AllDebridAuthManager(private val context: Context) {
    private val api = Retrofit.Builder()
        .baseUrl("https://api.alldebrid.com/")
        .client(TrustedHttpClient.builder().build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AllDebridApi::class.java)

    fun startDeviceAuthFlow(): Flow<AdAuthResult> = flow {
        try {
            val getPinRes = api.getPin()
            if (!getPinRes.isSuccessful || getPinRes.body() == null) {
                emit(AdAuthResult.Error("שגיאה במשיכת קוד משרת AllDebrid"))
                return@flow
            }
            val jsonObj = getPinRes.body()!!.asJsonObject
            if (jsonObj.get("status").asString != "success") {
                emit(AdAuthResult.Error("שגיאה משרת AllDebrid"))
                return@flow
            }
            
            val data = jsonObj.getAsJsonObject("data")
            val pin = data.get("pin").asString
            val check = data.get("check").asString
            val userUrl = data.get("base_url").asString + "?pin=" + pin
            
            emit(AdAuthResult.ShowUserCode(pin, userUrl))
            
            var credentialsRetrieved = false
            var attempts = 0
            while (!credentialsRetrieved && attempts < 120) {
                delay(5000)
                attempts++
                
                val checkRes = api.checkPin(check, pin)
                if (checkRes.isSuccessful && checkRes.body() != null) {
                    val checkObj = checkRes.body()!!.asJsonObject
                    if (checkObj.get("status").asString == "success") {
                        val checkData = checkObj.getAsJsonObject("data")
                        if (checkData.get("activated").asBoolean) {
                            val apikey = checkData.get("apikey").asString
                            val app = context.applicationContext as LuminaApp
                            app.settingsRepository.updateSetting(SettingsRepository.AD_API_TOKEN, apikey)
                            credentialsRetrieved = true
                        }
                    }
                }
            }
            
            if (credentialsRetrieved) {
                emit(AdAuthResult.Success)
            } else {
                emit(AdAuthResult.Error("זמן האימות פג. נסה שוב."))
            }
        } catch (e: Exception) {
            emit(AdAuthResult.Error("אין חיבור רשת — שרת AD נכשל"))
        }
    }.flowOn(Dispatchers.IO)
}

class AllDebridManager(private val context: Context) {
    private val okHttpClient = TrustedHttpClient.builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://api.alldebrid.com/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(AllDebridApi::class.java)

    data class AdFile(
        val name: String,
        val size: Long,
        val link: String
    )

    data class AdMagnetItem(
        val id: Long,
        val filename: String,
        val size: Long,
        val hash: String,
        val status: String,
        val statusCode: Int,
        val downloaded: Long,
        val downloadSpeed: Long,
        val seeders: Int,
        val uploadDate: Long,
        val nbLinks: Int
    )

    private val videoExtensions = setOf("mkv", "mp4", "avi", "ts", "mov", "m4v", "webm", "flv")

    fun extractFiles(statusData: com.google.gson.JsonObject): List<AdFile> {
        val filesElement = when {
            statusData.has("files") -> statusData.get("files")
            statusData.has("links") -> statusData.get("links")
            else -> null
        } ?: return emptyList()

        val list = mutableListOf<AdFile>()

        fun parseElement(element: com.google.gson.JsonElement) {
            if (element.isJsonObject) {
                val obj = element.asJsonObject
                if (obj.has("e") && obj.get("e").isJsonArray) {
                    for (child in obj.getAsJsonArray("e")) {
                        parseElement(child)
                    }
                }
                val link = obj.get("l")?.asString ?: obj.get("link")?.asString ?: ""
                val name = obj.get("n")?.asString ?: obj.get("filename")?.asString ?: ""
                val size = obj.get("s")?.asLong ?: obj.get("size")?.asLong ?: 0L
                if (link.isNotBlank()) {
                    list.add(AdFile(name, size, link))
                }
            } else if (element.isJsonArray) {
                for (item in element.asJsonArray) {
                    parseElement(item)
                }
            } else if (element.isJsonPrimitive) {
                val link = element.asString
                if (link.isNotBlank()) {
                    list.add(AdFile(link.substringAfterLast('/'), 0L, link))
                }
            }
        }

        parseElement(filesElement)
        return list
    }

    private fun selectBestFile(files: List<AdFile>, season: Int? = null, episode: Int? = null): AdFile? {
        if (files.isEmpty()) return null
        if (files.size == 1) return files[0]

        val nonSamples = files.filter {
            val lower = it.name.lowercase(Locale.ROOT)
            !lower.contains("sample") && !lower.endsWith(".nfo") && !lower.endsWith(".txt") && !lower.endsWith(".jpg")
        }
        val candidates = if (nonSamples.isNotEmpty()) nonSamples else files

        if (season != null && episode != null) {
            val epPattern1 = String.format(Locale.US, "s%02de%02d", season, episode)
            val epPattern2 = String.format(Locale.US, "%dx%02d", season, episode)
            val epPattern3 = String.format(Locale.US, "e%02d", episode)
            val matched = candidates.filter {
                val lower = it.name.lowercase(Locale.ROOT)
                lower.contains(epPattern1) || lower.contains(epPattern2) || lower.contains(epPattern3)
            }
            if (matched.isNotEmpty()) {
                return matched.maxByOrNull { it.size }
            }
        }

        val videoCandidates = candidates.filter { file ->
            val ext = file.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
            ext in videoExtensions
        }
        return (if (videoCandidates.isNotEmpty()) videoCandidates else candidates).maxByOrNull { it.size } ?: files[0]
    }

    private fun parseStatusData(statusJson: com.google.gson.JsonObject): com.google.gson.JsonObject {
        val dataObj = statusJson.getAsJsonObject("data") ?: throw Exception("תגובה לא חוקית מ-AllDebrid (data missing)")
        val magnetsElem = dataObj.get("magnets") ?: throw Exception("תגובה לא חוקית מ-AllDebrid (magnets missing)")
        return if (magnetsElem.isJsonArray) {
            val arr = magnetsElem.asJsonArray
            if (arr.size() == 0) throw Exception("רשימת מגנטים ריקה מ-AllDebrid")
            arr.get(0).asJsonObject
        } else {
            magnetsElem.asJsonObject
        }
    }

    suspend fun resolveMagnetToStream(magnetUri: String, apiToken: String): Result<String> =
        resolveMagnetToStreamTracked(magnetUri, apiToken) { }

    suspend fun resolveMagnetToStreamTracked(
        magnetUri: String,
        apiToken: String,
        onTorrentAdded: (String) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן AllDebrid חסר או לא מוגדר בהגדרות!")
            val authHeader = "Bearer $apiToken"
            
            val uploadRes = api.uploadMagnet(authHeader, listOf(magnetUri))
            if (!uploadRes.isSuccessful || uploadRes.body() == null) {
                 throw Exception("שגיאה בהעלאת מגנט: ${uploadRes.code()}")
            }
            val uploadJson = uploadRes.body()!!.asJsonObject
            if (uploadJson.get("status")?.asString != "success") {
                 val errMsg = uploadJson.getAsJsonObject("error")?.get("message")?.asString ?: "Unknown error"
                 throw Exception("שגיאה מ-AllDebrid: $errMsg")
            }
            
            val magnetsArr = uploadJson.getAsJsonObject("data").getAsJsonArray("magnets")
            if (magnetsArr == null || magnetsArr.size() == 0) {
                 throw Exception("AllDebrid לא החזיר פרטי מגנט")
            }
            val magnetInfo = magnetsArr.get(0).asJsonObject
            
            if (magnetInfo.has("error")) {
                 val errMsg = magnetInfo.getAsJsonObject("error")?.get("message")?.asString ?: "שגיאה במגנט"
                 throw Exception("שגיאה בהעלאת מגנט: $errMsg")
            }
            
            val torrentId = magnetInfo.get("id").asString
            onTorrentAdded(torrentId)
            
            var attempts = 0
            var readyFiles: List<AdFile> = emptyList()
            
            while (attempts < 30) {
                 val statusRes = api.getMagnetStatus(authHeader, torrentId)
                 if (statusRes.isSuccessful && statusRes.body() != null) {
                      val statusJson = statusRes.body()!!.asJsonObject
                      if (statusJson.get("status")?.asString == "success") {
                           val statusData = parseStatusData(statusJson)
                           val statusCode = statusData.get("statusCode")?.asInt ?: 0
                           if (statusCode == 4) { // Ready
                                readyFiles = extractFiles(statusData)
                                if (readyFiles.isNotEmpty()) break
                           } else if (statusCode > 4) { // Error states
                                val statusText = statusData.get("status")?.asString ?: "Error"
                                throw Exception("שגיאה בהורדת הטורנט בשרתי AllDebrid ($statusText)")
                           }
                      }
                 }
                 delay(2000)
                 attempts++
            }
            
            if (readyFiles.isEmpty()) throw Exception("זמן ההמתנה ל-AllDebrid פג")
            
            val targetFile = selectBestFile(readyFiles)
                ?: throw Exception("לא נמצאו קבצים להפעלה בטורנט")
            
            val unlockRes = api.unlockLink(authHeader, targetFile.link)
            if (!unlockRes.isSuccessful || unlockRes.body() == null) {
                 throw Exception("שגיאה בפתיחת הקישור: ${unlockRes.code()}")
            }
            val unlockJson = unlockRes.body()!!.asJsonObject
            if (unlockJson.get("status")?.asString != "success") {
                 val errMsg = unlockJson.getAsJsonObject("error")?.get("message")?.asString ?: "Unknown error"
                 throw Exception("שגיאה בפתיחת הקישור מ-AllDebrid: $errMsg")
            }
            val streamUrl = unlockJson.getAsJsonObject("data").get("link").asString
            
            Result.success(streamUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun resolveTorrentFileToStream(
        torrentBytes: ByteArray,
        apiToken: String,
        season: Int? = null,
        episode: Int? = null,
        onTorrentAdded: (String) -> Unit = {},
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן AllDebrid חסר או לא מוגדר!")

            val mediaType = "application/x-bittorrent".toMediaTypeOrNull()
            val reqBody = okhttp3.MultipartBody.Builder()
                .setType(okhttp3.MultipartBody.FORM)
                .addFormDataPart("files[]", "upload.torrent", torrentBytes.toRequestBody(mediaType, 0, torrentBytes.size))
                .build()

            val request = Request.Builder()
                .url("https://api.alldebrid.com/v4/magnet/upload/file?agent=LuminaStreams")
                .header("Authorization", "Bearer $apiToken")
                .post(reqBody)
                .build()

            val response = okHttpClient.newCall(request).execute()
            val responseBodyString = response.body.string().ifBlank { "{}" }

            if (!response.isSuccessful) {
                throw Exception("שגיאת שרת AllDebrid: ${response.code}")
            }

            val uploadJson = JSONObject(responseBodyString)
            if (uploadJson.optString("status") != "success") {
                 val errMsg = uploadJson.optJSONObject("error")?.optString("message") ?: "Unknown error"
                 throw Exception("שגיאה מ-AllDebrid: $errMsg")
            }

            val dataObj = uploadJson.optJSONObject("data") ?: throw Exception("תגובה לא חוקית מ-AllDebrid")
            val filesArr = dataObj.optJSONArray("files") ?: throw Exception("AllDebrid לא החזיר קבצים")
            if (filesArr.length() == 0) throw Exception("AllDebrid החזיר רשימת קבצים ריקה")
            val fileInfo = filesArr.getJSONObject(0)
            
            if (fileInfo.has("error")) {
                 val errMsg = fileInfo.optJSONObject("error")?.optString("message")
                     ?: fileInfo.optString("error", "שגיאה בקובץ הטורנט")
                 throw Exception("שגיאה בהעלאת קובץ: $errMsg")
            }

            val torrentId = fileInfo.optString("id", "")
            if (torrentId.isBlank()) throw Exception("לא התקבל מזהה טורנט מ-AllDebrid")
            onTorrentAdded(torrentId)

            var attempts = 0
            var readyFiles: List<AdFile> = emptyList()
            
            val authHeader = "Bearer $apiToken"

            while (attempts < 150) {
                 val statusRes = api.getMagnetStatus(authHeader, torrentId)
                 if (statusRes.isSuccessful && statusRes.body() != null) {
                      val statusJson = statusRes.body()!!.asJsonObject
                      if (statusJson.get("status")?.asString == "success") {
                           val statusData = parseStatusData(statusJson)
                           val statusCode = statusData.get("statusCode")?.asInt ?: 0
                           
                           if (statusCode == 4) { // Ready
                                readyFiles = extractFiles(statusData)
                                if (readyFiles.isNotEmpty()) break
                           } else if (statusCode in 1..3) {
                                // downloading / processing
                                val downloaded = statusData.get("downloaded")?.asLong ?: 0L
                                val size = statusData.get("size")?.asLong ?: 0L
                                if (size > 0L) {
                                    onProgress((downloaded.toFloat() / size.toFloat()) * 100f)
                                }
                           } else if (statusCode > 4) { // Error states
                                val statusText = statusData.get("status")?.asString ?: "Error"
                                throw Exception("שגיאה בהורדת הקובץ בשרתי AllDebrid ($statusText)")
                           }
                      }
                 }
                 delay(2000)
                 attempts++
            }

            if (readyFiles.isEmpty()) throw Exception("זמן ההמתנה ל-AllDebrid פג")

            val targetFile = selectBestFile(readyFiles, season, episode)
                ?: throw Exception("לא נמצאו קבצים להפעלה בטורנט")

            val unlockRes = api.unlockLink(authHeader, targetFile.link)
            if (!unlockRes.isSuccessful || unlockRes.body() == null) {
                 throw Exception("שגיאה בפתיחת הקישור: ${unlockRes.code()}")
            }
            val unlockJson = unlockRes.body()!!.asJsonObject
            if (unlockJson.get("status")?.asString != "success") {
                 val errMsg = unlockJson.getAsJsonObject("error")?.get("message")?.asString ?: "Unknown error"
                 throw Exception("שגיאה בפתיחת הקישור מ-AllDebrid: $errMsg")
            }
            val streamUrl = unlockJson.getAsJsonObject("data").get("link").asString

            Result.success(streamUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getUserMagnets(apiToken: String): Result<List<AdMagnetItem>> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן AllDebrid חסר")
            val authHeader = "Bearer $apiToken"
            val res = api.getAllMagnets(authHeader)
            if (!res.isSuccessful || res.body() == null) {
                throw Exception("שגיאה במשיכת רשימת טורנטים: ${res.code()}")
            }
            val json = res.body()!!.asJsonObject
            if (json.get("status")?.asString != "success") {
                val msg = json.getAsJsonObject("error")?.get("message")?.asString ?: "שגיאה ב-AllDebrid"
                throw Exception(msg)
            }
            val dataObj = json.getAsJsonObject("data") ?: throw Exception("תגובה לא תקינה מ-AllDebrid")
            val magnetsElem = dataObj.get("magnets") ?: return@withContext Result.success(emptyList())
            val list = mutableListOf<AdMagnetItem>()
            if (magnetsElem.isJsonArray) {
                for (elem in magnetsElem.asJsonArray) {
                    if (elem.isJsonObject) {
                        val obj = elem.asJsonObject
                        list.add(
                            AdMagnetItem(
                                id = obj.get("id")?.asLong ?: 0L,
                                filename = obj.get("filename")?.asString ?: "",
                                size = obj.get("size")?.asLong ?: 0L,
                                hash = obj.get("hash")?.asString ?: "",
                                status = obj.get("status")?.asString ?: "",
                                statusCode = obj.get("statusCode")?.asInt ?: 0,
                                downloaded = obj.get("downloaded")?.asLong ?: 0L,
                                downloadSpeed = obj.get("downloadSpeed")?.asLong ?: 0L,
                                seeders = obj.get("seeders")?.asInt ?: 0,
                                uploadDate = obj.get("uploadDate")?.asLong ?: 0L,
                                nbLinks = obj.get("nbLinks")?.asInt ?: 0
                            )
                        )
                    }
                }
            }
            Result.success(list)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getMagnetFiles(apiToken: String, magnetId: Long): Result<List<AdFile>> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן AllDebrid חסר")
            val authHeader = "Bearer $apiToken"
            val statusRes = api.getMagnetStatus(authHeader, magnetId.toString())
            if (!statusRes.isSuccessful || statusRes.body() == null) {
                throw Exception("שגיאה בקבלת פרטי הטורנט: ${statusRes.code()}")
            }
            val json = statusRes.body()!!.asJsonObject
            if (json.get("status")?.asString != "success") {
                val msg = json.getAsJsonObject("error")?.get("message")?.asString ?: "שגיאה ב-AllDebrid"
                throw Exception(msg)
            }
            val statusData = parseStatusData(json)
            val files = extractFiles(statusData)
            Result.success(files)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun unlockLinkDirect(apiToken: String, link: String): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן AllDebrid חסר")
            val authHeader = "Bearer $apiToken"
            val unlockRes = api.unlockLink(authHeader, link)
            if (!unlockRes.isSuccessful || unlockRes.body() == null) {
                throw Exception("שגיאה בפתיחת הקישור: ${unlockRes.code()}")
            }
            val unlockJson = unlockRes.body()!!.asJsonObject
            if (unlockJson.get("status")?.asString != "success") {
                val errMsg = unlockJson.getAsJsonObject("error")?.get("message")?.asString ?: "Unknown error"
                throw Exception("שגיאה בפתיחת הקישור מ-AllDebrid: $errMsg")
            }
            val streamUrl = unlockJson.getAsJsonObject("data").get("link").asString
            Result.success(streamUrl)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun deleteMagnet(apiToken: String, magnetId: Long): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן AllDebrid חסר")
            val authHeader = "Bearer $apiToken"
            val delRes = api.deleteMagnet(authHeader, magnetId.toString())
            if (delRes.isSuccessful) {
                Result.success(true)
            } else {
                Result.failure(Exception("שגיאה במחיקת הטורנט: ${delRes.code()}"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
