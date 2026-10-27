package com.luminastreams.tv.domain.usecase

import android.content.Context
import android.util.Log
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

    @FormUrlEncoded
    @POST("oauth/v2/token")
    suspend fun refreshToken(
        @Field("client_id") clientId: String,
        @Field("client_secret") clientSecret: String,
        @Field("code") code: String,
        @Field("grant_type") grantType: String = "http://oauth.net/grant_type/device/1.0"
    ): retrofit2.Response<TokenResponse>
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
            context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE).edit(commit = true) {
                putString("rd_api_token", tokenResponse.access_token)
                putString("rd_refresh_token", tokenResponse.refresh_token)
                putString("rd_client_id", clientIdToUse)
                putString("rd_client_secret", clientSecretToUse)
                putLong("rd_last_refresh", System.currentTimeMillis())
            }
            emit(AuthResult.Success)
        } catch (_: Exception) {
            emit(AuthResult.Error("אין חיבור רשת — שרת RD נכשל"))
        }
    }.flowOn(Dispatchers.IO)
}

// ── RealDebridManager ──────────────────────────────────────────────────────
class RealDebridManager(private val context: Context) {

    private val okHttpClient = TrustedHttpClient.builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(20, java.util.concurrent.TimeUnit.SECONDS)
        .retryOnConnectionFailure(false) // ⚡ Disabled to prevent double-sending refresh tokens on timeout
        .build()

    private val api = Retrofit.Builder()
        .baseUrl("https://api.real-debrid.com/rest/1.0/")
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(RealDebridApi::class.java)

    suspend fun refreshAccessToken(): String? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
        val clientId = prefs.getString("rd_client_id", "") ?: ""
        val clientSecret = prefs.getString("rd_client_secret", "") ?: ""
        val refreshToken = prefs.getString("rd_refresh_token", "") ?: ""

        if (clientId.isBlank() || clientSecret.isBlank() || refreshToken.isBlank()) return@withContext null

        val consecutiveFailures = prefs.getInt("rd_consecutive_failures", 0)

        try {
            var reqBody = okhttp3.FormBody.Builder()
                .add("client_id", clientId)
                .add("client_secret", clientSecret)
                .add("code", refreshToken)
                .add("grant_type", "http://oauth.net/grant_type/device/1.0")
                .build()

            var request = okhttp3.Request.Builder()
                .url("https://api.real-debrid.com/oauth/v2/token")
                .post(reqBody)
                .build()

            var response = okHttpClient.newCall(request).execute()

            response.use { resp ->
                if (resp.isSuccessful) {
                    val json = JSONObject(resp.body.string())
                    val newAccessToken = json.optString("access_token")
                    val newRefreshToken = json.optString("refresh_token")

                    if (newAccessToken.isNotBlank()) {
                        val editor = prefs.edit()
                        editor.putString("rd_api_token", newAccessToken)
                        if (newRefreshToken.isNotBlank()) editor.putString("rd_refresh_token", newRefreshToken)
                        editor.putLong("rd_last_refresh", System.currentTimeMillis())
                        editor.putInt("rd_consecutive_failures", 0) // ✅ Reset failure counter on success
                        editor.commit() // ⚡ Must be synchronous to prevent race conditions on app kill
                        Log.d("RDManager", "✅ Token refreshed successfully")
                        return@withContext newAccessToken
                    }
                } else if (resp.code == 400 || resp.code == 401 || resp.code == 403) {
                    val bodyStr = resp.body.string()
                    if (bodyStr.contains("bad_client") || bodyStr.contains("bad_token") || bodyStr.contains("invalid_grant")) {
                        val newFailCount = consecutiveFailures + 1
                        Log.w("RDManager", "⚠️ Token refresh rejected (HTTP ${resp.code}, failure $newFailCount/3): $bodyStr")

                        if (newFailCount >= 3) {
                            // 3+ consecutive failures — credentials are truly dead, wipe them
                            Log.e("RDManager", "🔴 Wiping RD credentials after $newFailCount consecutive failures")
                            prefs.edit().apply {
                                remove("rd_api_token")
                                remove("rd_refresh_token")
                                remove("rd_client_id")
                                remove("rd_client_secret")
                                remove("rd_last_refresh")
                                remove("rd_consecutive_failures")
                            }.commit() // ⚡ Synchronous wipe
                        } else {
                            // Not at threshold yet — keep credentials alive for retry
                            Log.w("RDManager", "⚠️ Keeping credentials alive ($newFailCount/3) — will retry next cycle")
                            prefs.edit()
                                .putInt("rd_consecutive_failures", newFailCount)
                                .commit()
                        }
                        return@withContext null
                    }
                }
            }
        } catch (_: Exception) {}
        null
    }

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
                var authHeader = "Bearer $apiToken"

                var addResponse = try {
                    api.addMagnet(authHeader, magnetUri)
                } catch (e: retrofit2.HttpException) {
                    val errorBody = e.response()?.errorBody()?.string() ?: ""
                    if (e.code() == 401 || e.code() == 403 || errorBody.contains("bad_token", true)) {
                        val newToken = RealDebridTokenKeeper.forceRefreshNow()
                        if (newToken != null) {
                            authHeader = "Bearer $newToken"
                            api.addMagnet(authHeader, magnetUri) // Retry
                        } else {
                            throw Exception("החיבור ל-Real Debrid התנתק או פג תוקף. אנא התחבר מחדש במסך ההגדרות.")
                        }
                    } else if (errorBody.contains("2004") || errorBody.contains("2000")) {
                        throw Exception("RD_CONFLICT")
                    } else {
                        throw e
                    }
                }

                val torrentId = addResponse.id
                onTorrentAdded(torrentId)

                val torrentInfo = api.getTorrentInfo(authHeader, torrentId)
                val videoFiles = torrentInfo.files.filter {
                    it.path.endsWith(".mkv", true) || it.path.endsWith(".mp4", true) || it.path.endsWith(".avi", true)
                }
                if (videoFiles.isEmpty()) throw Exception("לא נמצאו קבצי וידאו בטורנט")
                val mainVideoFile = videoFiles.maxBy { it.bytes }

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

            var request = Request.Builder()
                .url("https://api.real-debrid.com/rest/1.0/torrents/addTorrent")
                .header("Authorization", "Bearer $apiToken")
                .put(reqBody)
                .build()

            var response = okHttpClient.newCall(request).execute()
            var responseBodyString = response.body.string().ifBlank { "{}" }

            var authHeader = "Bearer $apiToken"

            // תפיסה חכמה של ניתוק ממשתמש
            if (response.code == 401 || response.code == 403 || responseBodyString.contains("bad_token", true)) {
                val newToken = RealDebridTokenKeeper.forceRefreshNow()
                if (newToken != null) {
                    authHeader = "Bearer $newToken"
                    request = Request.Builder()
                        .url("https://api.real-debrid.com/rest/1.0/torrents/addTorrent")
                        .header("Authorization", authHeader)
                        .put(reqBody)
                        .build()
                    response = okHttpClient.newCall(request).execute()
                    responseBodyString = response.body.string().ifBlank { "{}" }
                } else {
                    throw Exception("החיבור ל-Real Debrid התנתק או פג תוקף. אנא התחבר מחדש במסך ההגדרות.")
                }
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