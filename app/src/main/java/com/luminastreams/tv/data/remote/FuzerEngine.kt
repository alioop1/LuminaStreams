package com.luminastreams.tv.data.remote

import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

object FuzerEngine {

    private const val USERNAME = "microxbox93"
    private const val PASSWORD = "AliooP93"
    private const val BASE     = "https://www.fuzer.xyz"

    private val cookieJar = object : CookieJar {
        private val store = HashMap<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { c -> list.removeAll { it.name == c.name }; list.add(c) }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host] ?: emptyList()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(25, TimeUnit.SECONDS)
        .readTimeout(25, TimeUnit.SECONDS)
        .build()

    // FIX: @Volatile ensures visibility across threads; loginMutex prevents concurrent logins
    @Volatile private var isLoggedIn = false
    private val loginMutex = Mutex()

    private val hashedPassword: String by lazy { md5(PASSWORD) }

    // ── Public API ─────────────────────────────────────────────────────────────
    suspend fun search(query: String): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureLogin()) return@withContext Result.failure(Exception("Login failed"))

            val enc = try {
                URLEncoder.encode(query, "windows-1255")
            } catch (_: Exception) {
                URLEncoder.encode(query, "UTF-8")
            }

            val searchUrl = "$BASE/browse.php?ref_=basic&query=$enc&matchquery=any"
            val html      = getHtml(searchUrl) ?: return@withContext Result.success(emptyList())
            Result.success(parseHtmlToMovies(html))
        } catch (e: Exception) {
            Result.failure(Exception("Fuzer search: ${e.message}"))
        }
    }

    suspend fun getCategoryPage(catId: Int, page: Int): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            if (!ensureLogin()) return@withContext Result.failure(Exception("Login failed"))
            val html = getHtml("$BASE/browse.php?cat=$catId&page=$page")
                ?: return@withContext Result.success(emptyList())
            Result.success(parseHtmlToMovies(html))
        } catch (_: Exception) {
            Result.failure(Exception("Fuzer getCategoryPage failed"))
        }
    }

    suspend fun downloadTorrentFile(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (!ensureLogin()) return@withContext Result.failure(Exception("Login failed"))
            var finalUrl = url
            if (url.contains("showthread.php")) {
                val html = getHtml(url) ?: return@withContext Result.failure(Exception("Thread fetch failed"))
                val link = Jsoup.parse(html).select("a[href*=attachment.php]").firstOrNull()
                    ?: return@withContext Result.failure(Exception("Torrent link not found"))
                val raw = link.attr("href").replace("&amp;", "&")
                finalUrl = if (raw.startsWith("http")) raw else "$BASE/$raw"
            }
            val bytes = client.newCall(
                Request.Builder().url(finalUrl).headers(defaultHeaders("$BASE/browse.php")).build()
            ).execute().body?.bytes()
            if (bytes != null && bytes.isNotEmpty() &&
                !String(bytes.take(50).toByteArray()).contains("html", ignoreCase = true))
                Result.success(bytes)
            else Result.failure(Exception("Invalid torrent data"))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // ── Helpers ────────────────────────────────────────────────────────────────
    private fun getHtml(url: String): String? {
        val resp  = client.newCall(
            Request.Builder().url(url).headers(defaultHeaders("$BASE/browse.php")).build()
        ).execute()
        val bytes = resp.body?.bytes() ?: return null
        return decodeBody(bytes)
    }

    private fun decodeBody(bytes: ByteArray): String =
        try { String(bytes, Charset.forName("windows-1255")) }
        catch (_: Exception) { String(bytes, Charsets.UTF_8) }

    private fun defaultHeaders(referer: String): Headers = Headers.Builder()
        .add("User-Agent",       "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/122.0.0.0 Safari/537.36")
        .add("Accept",           "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        .add("Accept-Language",  "he-IL,he;q=0.9,en-US;q=0.8,en;q=0.7")
        .add("Accept-Charset",   "windows-1255,utf-8;q=0.7,*;q=0.3")
        .add("Referer",          referer)
        .add("Origin",           BASE)
        .build()

    /**
     * Thread-safe login check. Uses a Mutex so only one coroutine attempts
     * login at a time — prevents duplicate login requests on simultaneous calls.
     */
    private suspend fun ensureLogin(): Boolean = loginMutex.withLock {
        if (isLoggedIn) return@withLock true
        doLogin()
    }

    private fun doLogin(): Boolean {
        try {
            val checkHtml = client.newCall(
                Request.Builder().url("$BASE/index.php").headers(defaultHeaders(BASE)).build()
            ).execute().body?.string() ?: ""
            if (checkHtml.contains("logout", ignoreCase = true) ||
                checkHtml.contains("loggout", ignoreCase = true)) {
                isLoggedIn = true; return true
            }

            val loginPageHtml = client.newCall(
                Request.Builder().url("$BASE/login.php").headers(defaultHeaders(BASE)).build()
            ).execute().body?.string() ?: ""
            val secToken = Jsoup.parse(loginPageHtml)
                .select("input[name=securitytoken]").firstOrNull()?.attr("value") ?: "guest"

            val loginResp = client.newCall(
                Request.Builder()
                    .url("$BASE/login.php?do=login")
                    .post(FormBody.Builder()
                        .add("vb_login_username",        USERNAME)
                        .add("vb_login_password",        "")
                        .add("vb_login_md5password",     hashedPassword)
                        .add("vb_login_md5password_utf", hashedPassword)
                        .add("do",            "login")
                        .add("securitytoken", secToken)
                        .add("cookieuser",    "1")
                        .build())
                    .headers(defaultHeaders("$BASE/login.php"))
                    .build()
            ).execute()

            val loginBody = loginResp.body?.string() ?: ""
            if (loginBody.contains("logout", ignoreCase = true) ||
                loginBody.contains("loggout", ignoreCase = true) ||
                cookieJar.loadForRequest(
                    HttpUrl.Builder().scheme("https").host("www.fuzer.xyz").build()
                ).any { it.name.contains("bbuser", ignoreCase = true) || it.name == "vbulletin_loggedin" }) {
                isLoggedIn = true; return true
            }
        } catch (_: Exception) {}
        return false
    }

    // ── Parser ─────────────────────────────────────────────────────────────────
    private fun parseHtmlToMovies(html: String): List<Movie> {
        val movies = mutableListOf<Movie>()
        val doc    = Jsoup.parse(html)

        val rows = doc.select("tr.trow1, tr.trow2, tr.torrent_row").ifEmpty {
            doc.select("table.torrents tr").drop(1)
        }.ifEmpty {
            doc.select("tr").filter { it.select("a[href*=showthread]").isNotEmpty() }
        }

        val seasonRegex = Pattern.compile("(?i)(?:S|Season|\u05e2\u05d5\u05e0\u05d4)\\s*(\\d{1,2})")
        val cutRegex    = Pattern.compile("(?i)(\\b(19|20)\\d{2}\\b|\\bS\\d+E?|\\bSeason|\\b720p|\\b1080p|\\b2160p|\\b4K\\b)")

        for (row in rows) {
            try {
                val titleLink = row.select("a[href*=showthread]").firstOrNull() ?: continue
                val rawTitle  = titleLink.text().trim()
                if (rawTitle.length < 2) continue

                val rawHref   = titleLink.attr("href").replace("&amp;", "&")
                val threadUrl = if (rawHref.startsWith("http")) rawHref else "$BASE/${rawHref.removePrefix("/")}"

                var posterUrl = titleLink.attr("imgsrc").trim()
                if (posterUrl.isEmpty()) posterUrl = row.select("img[src]").firstOrNull()?.attr("src") ?: ""
                if (posterUrl.isNotEmpty() && !posterUrl.startsWith("http")) posterUrl = "$BASE/$posterUrl"

                val quality = when {
                    rawTitle.contains("2160p", true) || rawTitle.contains("4K", true) -> "4K"
                    rawTitle.contains("1080p", true) -> "FHD"
                    rawTitle.contains("720p",  true) -> "HD"
                    else -> ""
                }

                var clean = rawTitle
                val cut = cutRegex.matcher(clean)
                if (cut.find()) clean = clean.substring(0, cut.start())
                clean = clean
                    .replace(".", " ").replace("_", " ").replace("-", " ")
                    .replace(Regex("(?i)(HebDub|Dubbed|Remux|KNiVES|SPARKS|BluRay|WEB-DL|HDRip|BRRip|DVDRip)"), "")
                    .replace(Regex("[^\\p{L}\\p{N}\\s\u0590-\u05FF]"), "")
                    .replace(Regex("\\s+"), " ").trim()
                if (clean.length < 2) clean = rawTitle.take(60)

                movies.add(Movie(
                    id              = threadUrl,
                    title           = clean,
                    backdropUrl     = posterUrl,
                    posterUrl       = posterUrl,
                    overview        = rawTitle,
                    rating          = 0f,
                    genre           = quality,
                    mediaType       = if (seasonRegex.matcher(rawTitle).find() || rawTitle.contains("\u05e2\u05d5\u05e0\u05d4", true)) "tv" else "movie",
                    resolutionBadge = quality,
                    is4K            = quality == "4K"
                ))
            } catch (_: Exception) { continue }
        }
        return movies.distinctBy { it.id }
    }

    private fun md5(input: String): String = try {
        MessageDigest.getInstance("MD5").digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }
}