package com.luminastreams.tv.domain.usecase

import android.content.Context
import androidx.core.content.edit
import com.google.gson.annotations.SerializedName
import com.luminastreams.tv.data.api.RealDebridApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*
import java.util.Locale

// ── Auth API interface ─────────────────────────────────────────────────────
interface RealDebridAuthApi {
    @GET("oauth/v2/device/code")
    suspend fun getDeviceCode(@Query("client_id") clientId: String): DeviceCodeResponse

    @GET("oauth/v2/device/credentials")
    suspend fun getCredentials(
        @Query("client_id") clientId: String,
        @Query("code") code: String
    ): retrofit2.Response<CredentialsResponse>

    @FormUrlEncoded
    @POST("oauth/v2/token")
    suspend fun getToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "http://oauth.net/grant_type/device/1.0"
    ): TokenResponse
}

// ── Auth DTOs ──────────────────────────────────────────────────────────────
data class DeviceCodeResponse(
    @SerializedName("device_code") val device_code: String,
    @SerializedName("user_code") val user_code: String,
    @SerializedName("interval") val interval: Int,
    @SerializedName("verification_url") val verification_url: String
)
data class CredentialsResponse(val client_id: String, val client_secret: String)
data class TokenResponse(val access_token: String, val refresh_token: String)

// ── AuthResult ─────────────────────────────────────────────────────────────
sealed class AuthResult {
    data class ShowUserCode(val code: String, val url: String) : AuthResult()
    object Success : AuthResult()
    data class Error(val message: String) : AuthResult()
}

// ── RealDebridAuthManager ──────────────────────────────────────────────────
class RealDebridAuthManager(private val context: Context) {

    private val CLIENT_ID = "X245A4XAIBGVM"

    private val api = Retrofit.Builder()
        .baseUrl("https://api.real-debrid.com/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RealDebridAuthApi::class.java)

    fun startDeviceAuthFlow(): Flow<AuthResult> = flow {
        try {
            val codeResponse = api.getDeviceCode(CLIENT_ID)
            if (codeResponse.user_code.isNullOrEmpty()) {
                emit(AuthResult.Error("שגיאה במשיכת קוד משרת RD"))
                return@flow
            }
            emit(AuthResult.ShowUserCode(codeResponse.user_code, codeResponse.verification_url))

            var credentialsRetrieved = false
            var clientIdToUse = ""
            var clientSecretToUse = ""

            while (!credentialsRetrieved) {
                delay((codeResponse.interval * 1000).toLong())
                val credResponse = api.getCredentials(CLIENT_ID, codeResponse.device_code)
                if (credResponse.isSuccessful && credResponse.body() != null) {
                    clientIdToUse = credResponse.body()!!.client_id
                    clientSecretToUse = credResponse.body()!!.client_secret
                    credentialsRetrieved = true
                } else if (credResponse.code() != 403) {
                    emit(AuthResult.Error("האימות בוטל או פג תוקף."))
                    return@flow
                }
            }

            val tokenResponse = api.getToken(clientIdToUse, clientSecretToUse, codeResponse.device_code)
            context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE).edit {
                putString("rd_api_token", tokenResponse.access_token)
            }
            emit(AuthResult.Success)
        } catch (_: Exception) {
            emit(AuthResult.Error("אין חיבור רשת — שרת RD נכשל"))
        }
    }.flowOn(Dispatchers.IO)
}

// ── RealDebridManager ──────────────────────────────────────────────────────
class RealDebridManager {

    private val api = Retrofit.Builder()
        .baseUrl("https://api.real-debrid.com/rest/1.0/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RealDebridApi::class.java)

    // 1. טיפול במגנטים (Torrentio וכדומה)
    suspend fun resolveMagnetToStream(magnetUri: String, apiToken: String): Result<String> =
        resolveMagnetToStreamTracked(magnetUri, apiToken) { /* no tracking */ }

    /**
     * Same as resolveMagnetToStream but reports the torrent ID via [onTorrentAdded]
     * so the caller can track it for later cleanup (deletion).
     */
    suspend fun resolveMagnetToStreamTracked(
        magnetUri: String,
        apiToken: String,
        onTorrentAdded: (String) -> Unit = {}
    ): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (apiToken.isBlank()) throw Exception("טוקן Real-Debrid חסר או לא מוגדר בהגדרות!")
                val authHeader = "Bearer $apiToken"

                val addResponse = try {
                    api.addMagnet(authHeader, magnetUri)
                } catch (e: retrofit2.HttpException) {
                    val code = e.code()
                    val errorBody = e.response()?.errorBody()?.string() ?: ""
                    // RD returns 2000/2004 codes inside the JSON body
                    if (errorBody.contains("2004") || errorBody.contains("2000") || code == 503) {
                        throw Exception("RD error $code: $errorBody")
                    }
                    throw e
                }

                val torrentId = addResponse.id
                onTorrentAdded(torrentId)

                val torrentInfo = api.getTorrentInfo(authHeader, torrentId)
                val videoFiles = torrentInfo.files.filter {
                    it.path.endsWith(".mkv", true) || it.path.endsWith(".mp4", true) || it.path.endsWith(".avi", true)
                }
                if (videoFiles.isEmpty()) throw Exception("לא נמצאו קבצי וידאו בטורנט")
                val mainVideoFile = videoFiles.maxByOrNull { it.bytes }!!

                // Only select files if status requires it (avoid re-selecting on already active torrents)
                if (torrentInfo.status != "downloaded") {
                    api.selectFiles(authHeader, torrentId, mainVideoFile.id.toString())
                }

                var readyInfo = api.getTorrentInfo(authHeader, torrentId)
                var attempts = 0
                while (readyInfo.links.isEmpty() && attempts < 15) {
                    delay(1500)
                    readyInfo = api.getTorrentInfo(authHeader, torrentId)
                    attempts++
                    // Bail early if torrent entered error state
                    if (readyInfo.status == "error" || readyInfo.status == "dead") {
                        throw Exception("Torrent failed on RD servers")
                    }
                }
                if (readyInfo.links.isEmpty()) throw Exception("זמן ההמתנה ל-Real-Debrid פג")

                val unrestrictResponse = api.unrestrictLink(authHeader, readyInfo.links.first())
                Result.success(unrestrictResponse.download)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

    // 2. הפונקציה החסרה לפיוזר: טיפול בהעלאת קובץ .torrent ישירות ל-RD
    suspend fun resolveTorrentFileToStream(
        torrentBytes: ByteArray,
        apiToken: String,
        season: Int? = null,
        episode: Int? = null,
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן Real-Debrid חסר או לא מוגדר!")

            // 1. העלאת הקובץ ל-Real Debrid
            val client = OkHttpClient()
            val mediaType = "application/x-bittorrent".toMediaTypeOrNull()
            val reqBody = torrentBytes.toRequestBody(mediaType, 0, torrentBytes.size)

            val request = Request.Builder()
                .url("https://api.real-debrid.com/rest/1.0/torrents/addTorrent")
                .header("Authorization", "Bearer $apiToken")
                .put(reqBody)
                .build()

            val response = client.newCall(request).execute()

            // ⚡ FIX: Removed redundant safe call and added clean fallback
            val responseBodyString = response.body.string().ifBlank { "{}" }

            // תפיסה חכמה של ניתוק ממשתמש
            if (response.code == 401 || response.code == 403 || responseBodyString.contains("bad_token", true)) {
                throw Exception("החיבור ל-Real Debrid התנתק או פג תוקף. אנא התחבר מחדש במסך ההגדרות.")
            }

            if (!response.isSuccessful) {
                throw Exception("שגיאת שרת RD: ${response.code}")
            }

            val json = JSONObject(responseBodyString)
            val torrentId = json.optString("id")
            if (torrentId.isEmpty()) {
                throw Exception("שגיאה: הקובץ הועלה אך RD לא החזיר מזהה.")
            }

            val authHeader = "Bearer $apiToken"
            val torrentInfo = api.getTorrentInfo(authHeader, torrentId)

            val videoFiles = torrentInfo.files.filter {
                (it.path.endsWith(".mkv", true) || it.path.endsWith(".mp4", true) || it.path.endsWith(".avi", true)) &&
                        it.bytes > 20_000_000
            }
            if (videoFiles.isEmpty()) throw Exception("לא נמצאו קבצי וידאו בטורנט (אולי ארכיון RAR?)")

            val targetIndex = if (season != null && episode != null) {
                val epString1 = String.format(Locale.US, "s%02de%02d", season, episode)
                val epString2 = String.format(Locale.US, "%dx%02d", season, episode)
                val idx = videoFiles.indexOfFirst {
                    it.path.lowercase().contains(epString1) || it.path.lowercase().contains(epString2)
                }
                if (idx != -1) idx else videoFiles.indexOf(videoFiles.maxByOrNull { it.bytes })
            } else {
                videoFiles.indexOf(videoFiles.maxByOrNull { it.bytes })
            }

            val fileIds = videoFiles.joinToString(",") { it.id.toString() }
            api.selectFiles(authHeader, torrentId, fileIds)

            var readyInfo = api.getTorrentInfo(authHeader, torrentId)
            var attempts = 0

            while (readyInfo.links.isEmpty() && attempts < 60) {
                delay(2000)
                readyInfo = api.getTorrentInfo(authHeader, torrentId)
                attempts++

                if (readyInfo.status == "downloading") {
                    onProgress(readyInfo.progress.toFloat())
                } else if (readyInfo.status == "error" || readyInfo.status == "dead") {
                    throw Exception("שגיאה בהורדת הטורנט בשרתי Real Debrid.")
                }
            }

            if (readyInfo.links.isEmpty()) throw Exception("זמן ההמתנה ל-Real-Debrid פג. ההורדה איטית מדי.")

            val targetLink = readyInfo.links.getOrNull(targetIndex) ?: readyInfo.links.first()
            val unrestrictResponse = api.unrestrictLink(authHeader, targetLink)

            Result.success(unrestrictResponse.download)

        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}