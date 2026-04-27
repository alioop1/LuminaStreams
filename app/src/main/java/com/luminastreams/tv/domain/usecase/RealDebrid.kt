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
import com.luminastreams.tv.core.TrustedHttpClient
import okhttp3.MediaType.Companion.toMediaTypeOrNull
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
        .client(TrustedHttpClient.builder().build())
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RealDebridAuthApi::class.java)

    fun startDeviceAuthFlow(): Flow<AuthResult> = flow {
        try {
            val codeResponse = api.getDeviceCode(CLIENT_ID)
            if (codeResponse.user_code.isEmpty()) {
                emit(AuthResult.Error("שגיאה במשיכת קוד משרת RD"))
                return@flow
            }
            emit(AuthResult.ShowUserCode(codeResponse.user_code, codeResponse.verification_url))

            var credentialsRetrieved = false
            var clientIdToUse = ""
            var clientSecretToUse = ""
            var pollAttempts = 0
            val maxPollAttempts = 60 // ~5 minutes max (interval is usually 5s)

            while (!credentialsRetrieved) {
                delay((codeResponse.interval * 1000).toLong())
                pollAttempts++
                if (pollAttempts > maxPollAttempts) {
                    emit(AuthResult.Error("זמן האימות פג. נסה שוב."))
                    return@flow
                }
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

    private val okHttpClient = TrustedHttpClient.builder().build()

    private val api = Retrofit.Builder()
        .baseUrl("https://api.real-debrid.com/rest/1.0/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RealDebridApi::class.java)

    // 1. טיפול במגנטים (Torrentio וכדומה)
    @Suppress("unused") // Public API — used by callers that don't need torrent tracking
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
                    val errorBody = e.response()?.errorBody()?.string() ?: ""
                    if (errorBody.contains("2004") || errorBody.contains("2000")) {
                        throw Exception("RD_CONFLICT")
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

    suspend fun resolveTorrentFileToStream(
        torrentBytes: ByteArray,
        apiToken: String,
        season: Int? = null,
        episode: Int? = null,
        onTorrentAdded: (String) -> Unit = {},
        onProgress: (Float) -> Unit = {}
    ): Result<String> = withContext(Dispatchers.IO) {
        try {
            if (apiToken.isBlank()) throw Exception("טוקן Real-Debrid חסר או לא מוגדר!")

            // 1. העלאת הקובץ ל-Real Debrid
            val mediaType = "application/x-bittorrent".toMediaTypeOrNull()
            val reqBody = torrentBytes.toRequestBody(mediaType, 0, torrentBytes.size)

            val request = Request.Builder()
                .url("https://api.real-debrid.com/rest/1.0/torrents/addTorrent")
                .header("Authorization", "Bearer $apiToken")
                .put(reqBody)
                .build()

            val response = okHttpClient.newCall(request).execute()

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
                // Check if it's a conflict
                if (responseBodyString.contains("2000") || responseBodyString.contains("2004")) {
                    throw Exception("RD_CONFLICT")
                }
                throw Exception("שגיאה: הקובץ הועלה אך RD לא החזיר מזהה.")
            }
            onTorrentAdded(torrentId)

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
            // Increased timeout to 5 minutes (150 * 2s) for non-cached torrents
            while (readyInfo.links.isEmpty() && attempts < 150) {
                delay(2000)
                readyInfo = api.getTorrentInfo(authHeader, torrentId)
                attempts++

                when (readyInfo.status) {
                    "downloading" -> {
                        onProgress(readyInfo.progress.toFloat())
                    }
                    "error", "dead" -> throw Exception("שגיאה בהורדת הטורנט בשרתי Real Debrid.")
                    "waiting_files_selection" -> {
                        // If it's stuck here, re-try selection once
                        if (attempts % 5 == 0) api.selectFiles(authHeader, torrentId, fileIds)
                    }
                }
                // If it becomes downloaded, the next getTorrentInfo will show links
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