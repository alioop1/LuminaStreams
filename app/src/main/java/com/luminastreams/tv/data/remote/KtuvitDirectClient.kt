package com.luminastreams.tv.data.remote

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Client ישיר ל-ktuvit.me — מבוסס על ktuvitManager.js (maormagori/Ktuvit-api)
 * Flow:
 *   1. login()           → מקבל Login cookie
 *   2. searchKtuvit()    → מחפש לפי שם + סוג → מחזיר KtuvitID
 *   3. getSubsList()     → מחזיר רשימת כתוביות לפי KtuvitID
 *   4. downloadSubtitle()→ two-step download (RequestIdentifier → DownloadFile)
 */
class KtuvitDirectClient(private val context: Context) {

    companion object {
        private const val BASE      = "https://www.ktuvit.me/"
        private const val SEARCH    = "${BASE}Services/ContentProvider.svc/SearchPage_search"
        private const val MOVIE_URL = "${BASE}MovieInfo.aspx?ID="
        private const val EPISODE_URL = "${BASE}Services/GetModuleAjax.ashx?"
        private const val REQ_DL    = "${BASE}Services/ContentProvider.svc/RequestSubtitleDownload"
        private const val DL_FILE   = "${BASE}Services/DownloadFile.ashx?DownloadIdentifier="
        private const val LOGIN_URL = "${BASE}Services/MembershipService.svc/Login"
        private const val UA        = "Mozilla/5.0 (Windows NT 10.0; Win64; x64)"
        private const val PREF      = "ktuvit_auth"
    }

    // ── cookie cache (in-memory + SharedPrefs) ────────────────────────
    private var cookie: String? = null
        get() = field ?: context.getSharedPreferences(PREF, Context.MODE_PRIVATE)
            .getString("cookie", null).also { field = it }

    private fun saveCookie(c: String) {
        cookie = c
        context.getSharedPreferences(PREF, Context.MODE_PRIVATE).edit().putString("cookie", c).apply()
    }

    // ── 1. Login ──────────────────────────────────────────────────────
    /**
     * @param email    כתובת מייל של חשבון Ktuvit.me
     * @param password סיסמה לחשבון (plain text — נשלחת כ-hash MD5 base64 לפי ה-API)
     */
    suspend fun login(email: String, password: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val body = """{"request":{"Email":"$email","Password":"${md5Base64(password)}"}}"""
            val conn = openPost(LOGIN_URL, body, cookie = null)
            if (conn.responseCode !in 200..299) return@withContext false
            // Cookie header: "Login=XXXX; path=/"
            val setCookieHeader = conn.getHeaderField("Set-Cookie") ?: return@withContext false
            val loginCookie = setCookieHeader.split(";").firstOrNull { it.trim().startsWith("Login=") }
                ?.trim()?.removePrefix("Login=") ?: return@withContext false
            if (loginCookie.isBlank()) return@withContext false
            saveCookie(loginCookie)
            true
        } catch (e: Exception) {
            e.printStackTrace(); false
        }
    }

    // ── 2. Search → KtuvitID ──────────────────────────────────────────
    /**
     * @param name   שם הסרט/סדרה
     * @param year   שנת יציאה (אופציונלי)
     * @param isSeries true לסדרה, false לסרט
     * @param imdbId  IMDB ID (tt0000000) לאימות תוצאות
     */
    suspend fun getKtuvitId(
        name: String,
        isSeries: Boolean,
        year: Int? = null,
        imdbId: String? = null
    ): String? = withContext(Dispatchers.IO) {
        try {
            val searchType = if (isSeries) "1" else "0"
            val yearStr = year?.toString() ?: ""
            val body = """{"request":{"FilmName":"${name.replace("\"","")}","Actors":[],"Studios":null,"Directors":[],"Genres":[],"Countries":[],"Languages":[],"Year":"$yearStr","Rating":[],"Page":1,"SearchType":"$searchType","WithSubsOnly":false}}"""
            val conn = openPost(SEARCH, body, cookie)
            if (conn.responseCode !in 200..299) return@withContext null
            val json = conn.inputStream.bufferedReader().readText()
            val films = JSONObject(json).optString("d")
                .let { JSONObject(it) }.optJSONArray("Films") ?: return@withContext null
            // חפש לפי imdbId אם יש, אחרת החזר הראשון
            for (i in 0 until films.length()) {
                val f = films.getJSONObject(i)
                if (imdbId != null && f.optString("ImdbID").let { imdbId.contains(it) && it.isNotEmpty() }) {
                    return@withContext f.optString("ID")
                }
            }
            // fallback: ראשון ברשימה
            if (films.length() > 0) films.getJSONObject(0).optString("ID").ifEmpty { null }
            else null
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    // ── 3. Sub List ───────────────────────────────────────────────────
    data class KtuvitSub(val id: String, val name: String, val downloads: Int, val fileType: String)

    suspend fun getSubsListMovie(ktuvitId: String): List<KtuvitSub> = withContext(Dispatchers.IO) {
        try {
            val conn = openGet("$MOVIE_URL$ktuvitId", cookie)
            if (conn.responseCode !in 200..299) return@withContext emptyList()
            parseSubsHtml(conn.inputStream.bufferedReader().readText())
        } catch (e: Exception) { e.printStackTrace(); emptyList() }
    }

    suspend fun getSubsListEpisode(ktuvitId: String, season: Int, episode: Int): List<KtuvitSub> = withContext(Dispatchers.IO) {
        try {
            val url = "${EPISODE_URL}moduleName=SubtitlesList&SeriesID=$ktuvitId&Season=$season&Episode=$episode"
            val conn = openGet(url, cookie)
            if (conn.responseCode !in 200..299) return@withContext emptyList()
            val html = conn.inputStream.bufferedReader().readText()
            // episode response הוא fragment בלי DOCTYPE
            parseSubsHtml("<!DOCTYPE html><table id=\"subtitlesList\"><thead><tr/></thead>$html</table>")
        } catch (e: Exception) { e.printStackTrace(); emptyList() }
    }

    // HTML parser פשוט ללא jsdom — regex על data-subtitle-id
    private fun parseSubsHtml(html: String): List<KtuvitSub> {
        val results = mutableListOf<KtuvitSub>()
        // כל שורה בטבלה: חפש data-subtitle-id
        val rowRegex  = Regex("""data-subtitle-id=["']([^"']+)["']""", RegexOption.IGNORE_CASE)
        val nameRegex = Regex("""<div[^>]*>\s*([\w .\-\[\](),'!]+)\s*<br""", RegexOption.IGNORE_CASE)
        val dlRegex   = Regex("""<td>(\d+)</td>""", RegexOption.IGNORE_CASE)
        val typeRegex = Regex("""<td>(srt|sub|ass|ssa)</td>""", RegexOption.IGNORE_CASE)

        // מפרק לשורות לפי </tr>
        val rows = html.split("</tr>").drop(1) // drop header
        rows.forEach { row ->
            val subId = rowRegex.find(row)?.groupValues?.get(1)?.trim() ?: return@forEach
            val name  = nameRegex.find(row)?.groupValues?.get(1)?.trim() ?: subId
            val dl    = dlRegex.find(row)?.groupValues?.get(1)?.toIntOrNull() ?: 0
            val type  = typeRegex.find(row)?.groupValues?.get(1)?.lowercase() ?: "srt"
            if (subId.isNotEmpty()) results.add(KtuvitSub(subId, name, dl, type))
        }
        return results.sortedByDescending { it.downloads }
    }

    // ── 4. Download Subtitle ──────────────────────────────────────────
    /**
     * מוריד קובץ כתובית לקובץ זמני ב-cache.
     * @return File path אם הצליח, null אם נכשל
     */
    suspend fun downloadSubtitle(
        ktuvitTitleId: String,
        subId: String,
        fileType: String = "srt"
    ): File? = withContext(Dispatchers.IO) {
        try {
            // שלב א׳: בקשת Download Identifier
            val reqBody = """{"request":{"FilmID":"$ktuvitTitleId","SubtitleID":"$subId","FontSize":0,"FontColor":"","PredefinedLayout":-1}}"""
            val reqConn = openPost(REQ_DL, reqBody, cookie)
            if (reqConn.responseCode !in 200..299) return@withContext null
            val reqJson = reqConn.inputStream.bufferedReader().readText()
            val identifier = JSONObject(reqJson).optString("d")
                .let { JSONObject(it) }.optString("DownloadIdentifier")
            if (identifier.isNullOrEmpty()) return@withContext null

            // שלב ב׳: הורדת הקובץ
            val dlConn = openGet("$DL_FILE$identifier", cookie)
            if (dlConn.responseCode !in 200..299) return@withContext null
            val bytes = dlConn.inputStream.readBytes()
            val outFile = File(context.cacheDir, "ktuvit_${subId}.$fileType")
            outFile.writeBytes(bytes)
            outFile
        } catch (e: Exception) {
            e.printStackTrace(); null
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────
    private fun openPost(url: String, body: String, cookie: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod      = "POST"
        conn.doOutput           = true
        conn.connectTimeout     = 12_000
        conn.readTimeout        = 12_000
        conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
        conn.setRequestProperty("Accept", "application/json, text/javascript, */*; q=0.01")
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        if (cookie != null) conn.setRequestProperty("Cookie", "Login=$cookie")
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        return conn
    }

    private fun openGet(url: String, cookie: String?): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod  = "GET"
        conn.connectTimeout = 12_000
        conn.readTimeout    = 12_000
        conn.setRequestProperty("Accept", "application/json, text/html, */*; q=0.01")
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("X-Requested-With", "XMLHttpRequest")
        if (cookie != null) conn.setRequestProperty("Cookie", "Login=$cookie")
        return conn
    }

    private fun md5Base64(input: String): String {
        val md   = java.security.MessageDigest.getInstance("MD5")
        val hash = md.digest(input.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(hash, android.util.Base64.NO_WRAP)
    }
}
