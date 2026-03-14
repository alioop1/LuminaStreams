package com.luminastreams.tv.domain.usecase

import android.content.Context
import com.google.gson.annotations.SerializedName
import com.luminastreams.tv.data.api.RealDebridApi
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.*

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
            context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                .edit().putString("rd_api_token", tokenResponse.access_token).apply()
            emit(AuthResult.Success)
        } catch (e: Exception) {
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

    suspend fun resolveMagnetToStream(magnetUri: String, apiToken: String): Result<String> =
        withContext(Dispatchers.IO) {
            try {
                if (apiToken.isBlank()) throw Exception("טוקן Real-Debrid חסר או לא מוגדר בהגדרות!")
                val authHeader = "Bearer $apiToken"

                val addResponse = api.addMagnet(authHeader, magnetUri)
                val torrentId = addResponse.id

                val torrentInfo = api.getTorrentInfo(authHeader, torrentId)
                val videoFiles = torrentInfo.files.filter {
                    it.path.endsWith(".mkv", true) || it.path.endsWith(".mp4", true)
                }
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
