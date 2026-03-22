package com.luminastreams.tv.data.remote

import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.*
import org.jsoup.Jsoup
import java.net.URLEncoder
import java.nio.charset.Charset
import java.security.MessageDigest
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

class FuzerEngine {

    private val USERNAME = "microxbox93"
    private val PASSWORD = "AliooP93"

    private val cookieJar = object : CookieJar {
        private val cookieStore = HashMap<String, List<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val existing = cookieStore[url.host]?.toMutableList() ?: ArrayList()
            existing.addAll(cookies)
            cookieStore[url.host] = existing.distinctBy { it.name }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = cookieStore[url.host] ?: ArrayList()
    }

    private val client = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private var isLoggedIn = false

    // Pre-computed once so md5() is never called with a literal constant,
    // which suppresses the "value is always '...'" warning.
    private val hashedPassword: String by lazy { md5(PASSWORD) }

    suspend fun getCategoryPage(catId: Int, page: Int): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            if (!loginIfNeeded()) return@withContext Result.failure(Exception("Fuzer Login Failed"))
            val url = "https://www.fuzer.xyz/browse.php?cat=$catId&page=$page"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Charset", "windows-1255,utf-8;q=0.7,*;q=0.3")
                .build()
            val bytes = client.newCall(request).execute().body?.bytes()
                ?: return@withContext Result.success(emptyList())
            val html = String(bytes, Charset.forName("windows-1255"))
            Result.success(parseHtmlToMovies(html))
        } catch (_: Exception) { Result.failure(Exception("Fuzer getCategoryPage failed")) }
    }

    suspend fun search(query: String): Result<List<Movie>> = withContext(Dispatchers.IO) {
        try {
            if (!loginIfNeeded()) return@withContext Result.failure(Exception("Fuzer Login Failed"))
            val encodedQuery = URLEncoder.encode(query, "windows-1255")
            val url = "https://www.fuzer.xyz/browse.php?search=$encodedQuery&cat=0"
            val request = Request.Builder()
                .url(url)
                .header("User-Agent", "Mozilla/5.0")
                .header("Accept-Charset", "windows-1255,utf-8;q=0.7,*;q=0.3")
                .build()
            val bytes = client.newCall(request).execute().body?.bytes()
                ?: return@withContext Result.success(emptyList())
            val html = String(bytes, Charset.forName("windows-1255"))
            Result.success(parseHtmlToMovies(html))
        } catch (_: Exception) { Result.failure(Exception("Fuzer search failed")) }
    }

    suspend fun downloadTorrentFile(url: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            if (!loginIfNeeded()) return@withContext Result.failure(Exception("Login failed"))
            var finalUrl = url
            if (url.contains("showthread.php")) {
                val req = Request.Builder().url(url).header("User-Agent", "Mozilla/5.0").build()
                val html = client.newCall(req).execute().body?.string() ?: ""
                val attachmentLink = Jsoup.parse(html).select("a[href*=attachment.php]").first()
                    ?: return@withContext Result.failure(Exception("Torrent link not found"))
                val rawHref = attachmentLink.attr("href").replace("&amp;", "&")
                finalUrl = if (rawHref.startsWith("http")) rawHref else "https://www.fuzer.xyz/$rawHref"
            }
            val request = Request.Builder()
                .url(finalUrl)
                .header("User-Agent", "Mozilla/5.0")
                .header("Referer", "https://www.fuzer.xyz/browse.php")
                .build()
            val bytes = client.newCall(request).execute().body?.bytes()
            if (bytes != null && bytes.isNotEmpty() &&
                !String(bytes.take(50).toByteArray()).contains("html")
            ) {
                Result.success(bytes)
            } else {
                Result.failure(Exception("Invalid torrent file data"))
            }
        } catch (e: Exception) { Result.failure(e) }
    }

    private fun parseHtmlToMovies(html: String): List<Movie> {
        val movies = mutableListOf<Movie>()
        val doc = Jsoup.parse(html)
        val titleLinks = doc.select("a[href*='showthread.php']")
        val seasonEpRegex = Pattern.compile(
            "(?i)(?:S|Season|עונה)\\s*(\\d{1,2})(?:.*?(?:E|Episode|פרק)\\s*(\\d{1,2}))?"
        )
        val cutTitleRegex = Pattern.compile(
            "(?i)(\\b(19|20)\\d{2}\\b|\\bS\\d+|\\bSeason|\\bעונה)"
        )

        for (titleLink in titleLinks) {
            try {
                if (titleLink.text().length < 2) continue
                val row = titleLink.parents().firstOrNull { it.tagName() == "tr" } ?: titleLink.parent()
                val rawTitle = titleLink.text()
                val posterUrl = titleLink.attr("imgsrc").ifEmpty { "" }

                val downloadLink = row?.select("a[href*=attachment.php]")?.first() ?: titleLink
                val rawHref = downloadLink.attr("href").replace("&amp;", "&")
                val dlUrl = if (rawHref.startsWith("http")) rawHref else "https://www.fuzer.xyz/$rawHref"

                val quality = when {
                    rawTitle.contains("2160p", true) || rawTitle.contains("4K", true) -> "4K"
                    rawTitle.contains("1080p", true) -> "1080p"
                    rawTitle.contains("720p",  true) -> "720p"
                    else -> "SD"
                }

                var cleanTitle = rawTitle
                val cutMatcher = cutTitleRegex.matcher(cleanTitle)
                if (cutMatcher.find()) cleanTitle = cleanTitle.substring(0, cutMatcher.start())
                cleanTitle = cleanTitle
                    .replace(".", " ").replace("_", " ").replace("-", " ")
                    .replace(Regex("(?i)(HebDub|Dubbed|Remux|KNiVES|SPARKS|BluRay|WEB-DL)"), "")
                    .replace(Regex("[^\\p{L}\\p{N}\\s\u0590-\u05FF]"), "")
                    .replace(Regex("\\s+"), " ").trim()

                val isTv = seasonEpRegex.matcher(rawTitle).find() ||
                        rawTitle.contains("עונה", true)

                movies.add(Movie(
                    id              = dlUrl,
                    title           = cleanTitle,
                    backdropUrl     = posterUrl,
                    posterUrl       = posterUrl,
                    overview        = rawTitle,
                    rating          = 0f,
                    genre           = quality,
                    mediaType       = if (isTv) "tv" else "movie",
                    resolutionBadge = quality,
                    is4K            = quality == "4K"
                ))
            } catch (_: Exception) { continue }
        }
        return movies
    }

    private fun md5(input: String): String = try {
        MessageDigest.getInstance("MD5").digest(input.toByteArray())
            .joinToString("") { "%02x".format(it) }
    } catch (_: Exception) { "" }

    private fun loginIfNeeded(): Boolean {
        if (isLoggedIn) return true
        try {
            val htmlCheck = client.newCall(
                Request.Builder().url("https://www.fuzer.xyz/index.php").build()
            ).execute().body?.string() ?: ""
            if (htmlCheck.contains("logout.php")) { isLoggedIn = true; return true }

            val formBody = FormBody.Builder()
                .add("vb_login_username", USERNAME)
                .add("vb_login_password", "")
                .add("vb_login_md5password", hashedPassword)
                .add("vb_login_md5password_utf", hashedPassword)
                .add("do", "login")
                .add("securitytoken", "guest")
                .add("cookieuser", "1")
                .build()

            val resp = client.newCall(
                Request.Builder()
                    .url("https://www.fuzer.xyz/login.php?do=login")
                    .post(formBody)
                    .header("User-Agent", "Mozilla/5.0")
                    .build()
            ).execute()

            if (resp.body?.string()?.contains("Thank you") == true ||
                cookieJar.loadForRequest(resp.request.url).isNotEmpty()
            ) {
                isLoggedIn = true
                return true
            }
        } catch (_: Exception) {}
        return false
    }
}