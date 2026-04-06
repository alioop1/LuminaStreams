// 3. EpgParser.kt
package com.luminastreams.tv.presentation.iptv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeParseException
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

object EpgParser {

    private const val TAG = "EPG_DEBUG"

    data class EpgResult(
        val programs: Map<String, List<EpgProgram>>,
        val channelLogos: Map<String, String>,
        val channelDisplayNames: Map<String, List<String>>
    )

    suspend fun parse(url: String): Result<EpgResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting EPG download from: $url")
            val stream = fetchEpgStream(url)

            val handler = XmlTvHandler()
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
                try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
                try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            }
            val parser = factory.newSAXParser()
            parser.parse(stream, handler)

            Log.d(TAG, "EPG parsed: ${handler.channelsMap.size} channels, ${handler.channelLogos.size} logos")

            val finalMap = mutableMapOf<String, List<EpgProgram>>()
            val finalLogoMap = mutableMapOf<String, String>()

            for ((id, list) in handler.channelsMap) {
                val sortedList = list.sortedBy { it.startTime }
                finalMap[id.lowercase()] = sortedList

                handler.displayNames[id]?.forEach { displayName ->
                    if (displayName.isNotEmpty()) {
                        finalMap[displayName.lowercase()] = sortedList
                    }
                }

                val logo = handler.channelLogos[id]
                if (!logo.isNullOrBlank()) {
                    finalLogoMap[id.lowercase()] = logo
                    handler.displayNames[id]?.forEach { displayName ->
                        if (displayName.isNotEmpty()) {
                            finalLogoMap[displayName.lowercase()] = logo
                        }
                    }
                }
            }

            Result.success(
                EpgResult(
                    programs = finalMap,
                    channelLogos = finalLogoMap,
                    channelDisplayNames = handler.displayNames.mapKeys { it.key.lowercase() }
                )
            )
        } catch (e: Exception) {
            Log.e(TAG, "Error in EPG parsing: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun fetchEpgStream(urlString: String): InputStream {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < 10) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 30_000
                readTimeout = 120_000
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV; Android 12) Chrome/112.0.0.0")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                setRequestProperty("Accept", "text/xml,application/xml,*/*")
                instanceFollowRedirects = false
            }

            val responseCode = conn.responseCode
            Log.d(TAG, "EPG fetch response: $responseCode for $currentUrl")

            if (responseCode in 300..399) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (!newUrl.isNullOrBlank()) {
                    currentUrl = if (newUrl.startsWith("http")) newUrl
                    else {
                        val base = URL(currentUrl)
                        URL(base, newUrl).toString()
                    }
                    redirects++
                    continue
                }
            }

            if (responseCode !in 200..299) {
                conn.disconnect()
                throw Exception("HTTP Error: $responseCode fetching EPG")
            }

            val contentEncoding = conn.contentEncoding ?: ""
            val contentType = conn.contentType ?: ""
            val lowerUrl = currentUrl.lowercase()

            return when {
                contentEncoding.contains("gzip", true) || lowerUrl.endsWith(".gz") ->
                    GZIPInputStream(conn.inputStream)
                lowerUrl.endsWith(".zip") || contentType.contains("zip", true) -> {
                    val zis = ZipInputStream(conn.inputStream)
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".xml", true) || entry.name.endsWith(".xmltv", true)) {
                            return zis
                        }
                        entry = zis.nextEntry
                    }
                    zis.closeEntry()
                    val zis2 = ZipInputStream(conn.inputStream)
                    zis2.nextEntry
                    zis2
                }
                else -> conn.inputStream
            }
        }
        throw Exception("Too many redirects fetching EPG")
    }

    private val timeFormatters = listOf(
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmmss z"),
        DateTimeFormatter.ofPattern("yyyyMMddHHmmssZ"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ"),
        DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss z"),
    )
    private val localFormatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parseTimeSafely(timeStr: String): Long {
        val cleanTime = timeStr.trim()
        if (cleanTime.isEmpty() || cleanTime.length < 8) return 0L

        for (fmt in timeFormatters) {
            try {
                return ZonedDateTime.parse(cleanTime, fmt).toInstant().toEpochMilli()
            } catch (_: DateTimeParseException) {
            } catch (_: Exception) {}
        }

        return try {
            val timePart = cleanTime.split(" ")[0]
            val ldt = java.time.LocalDateTime.parse(timePart, localFormatter)
            ldt.atZone(java.time.ZoneOffset.UTC).toInstant().toEpochMilli()
        } catch (_: Exception) { 0L }
    }

    private class XmlTvHandler : DefaultHandler() {
        val channelsMap = mutableMapOf<String, MutableList<EpgProgram>>()
        val displayNames = mutableMapOf<String, MutableList<String>>()
        val channelLogos = mutableMapOf<String, String>()

        private val cutoffTime = System.currentTimeMillis() - (24 * 3600 * 1000L)
        private val futureLimit = System.currentTimeMillis() + (14L * 24 * 3600 * 1000)
        private val stringCache = HashMap<String, String>(4096)

        private var inChannel = false
        private var inProgramme = false
        private var currentChannelId = ""
        private var currentProgramChannelId = ""
        private var currentStart = 0L
        private var currentStop = 0L
        private var currentTitle = ""
        private var currentDesc = ""
        private var currentCategory = ""
        private var currentIcon = ""
        private var currentEpisodeNum = ""
        private var currentRating = ""
        private var currentDirector = ""
        private var currentActors = ""
        private var currentYear = ""
        private var currentLang = ""

        private var capturingDisplayName = false
        private var capturingTitle = false
        private var capturingDesc = false
        private var capturingCategory = false
        private var capturingEpisode = false
        private var capturingRating = false
        private var capturingDirector = false
        private var capturingActor = false
        private var capturingYear = false

        private val sb = StringBuilder(512)

        private fun getCached(value: String): String {
            val t = value.trim()
            if (t.isEmpty()) return ""
            return stringCache.getOrPut(t) { t }
        }

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            val tag = qName.lowercase()
            when (tag) {
                "channel" -> {
                    inChannel = true
                    currentChannelId = attrs.getValue("id") ?: ""
                }
                "icon" -> {
                    val src = attrs.getValue("src") ?: ""
                    if (src.isNotBlank()) {
                        if (inChannel && currentChannelId.isNotEmpty()) {
                            channelLogos[currentChannelId] = src
                            channelLogos[currentChannelId.lowercase()] = src
                        } else if (inProgramme) {
                            currentIcon = src
                        }
                    }
                }
                "display-name" -> {
                    if (inChannel && currentChannelId.isNotEmpty()) {
                        capturingDisplayName = true
                        sb.clear()
                    }
                }
                "programme" -> {
                    inProgramme = true
                    currentProgramChannelId = getCached(attrs.getValue("channel") ?: "")
                    currentStart = parseTimeSafely(attrs.getValue("start") ?: "")
                    currentStop = parseTimeSafely(attrs.getValue("stop") ?: "")
                    currentTitle = ""; currentDesc = ""; currentCategory = ""
                    currentIcon = ""; currentEpisodeNum = ""; currentRating = ""
                    currentDirector = ""; currentActors = ""; currentYear = ""; currentLang = ""
                }
                "title" -> if (inProgramme && currentTitle.isEmpty()) { capturingTitle = true; sb.clear() }
                "desc" -> if (inProgramme && currentDesc.isEmpty()) { capturingDesc = true; sb.clear() }
                "category" -> if (inProgramme && currentCategory.isEmpty()) { capturingCategory = true; sb.clear() }
                "episode-num" -> if (inProgramme) {
                    val system = attrs.getValue("system") ?: ""
                    if (system == "xmltv_ns" || system == "onscreen" || currentEpisodeNum.isEmpty()) {
                        capturingEpisode = true; sb.clear()
                    }
                }
                "value" -> if (inProgramme) { capturingRating = true; sb.clear() }
                "director" -> if (inProgramme) { capturingDirector = true; sb.clear() }
                "actor" -> if (inProgramme) { capturingActor = true; sb.clear() }
                "date" -> if (inProgramme) { capturingYear = true; sb.clear() }
                "language" -> if (inProgramme && currentLang.isEmpty()) { capturingDesc = false; sb.clear() }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capturingTitle || capturingDesc || capturingCategory ||
                capturingDisplayName || capturingEpisode || capturingRating ||
                capturingDirector || capturingActor || capturingYear) {
                sb.appendRange(ch, start, start + length)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val tag = qName.lowercase()
            when (tag) {
                "channel" -> { inChannel = false }
                "display-name" -> if (capturingDisplayName) {
                    val name = sb.toString().trim()
                    if (name.isNotEmpty()) {
                        displayNames.getOrPut(currentChannelId) { mutableListOf() }.add(getCached(name))
                    }
                    capturingDisplayName = false
                }
                "title" -> if (capturingTitle) { currentTitle = sb.toString().trim(); capturingTitle = false }
                "desc" -> if (capturingDesc) { currentDesc = sb.toString().trim(); capturingDesc = false }
                "category" -> if (capturingCategory) { currentCategory = sb.toString().trim(); capturingCategory = false }
                "episode-num" -> if (capturingEpisode) {
                    val raw = sb.toString().trim()
                    currentEpisodeNum = if (raw.contains(".")) {
                        val parts = raw.split(".")
                        val s = (parts.getOrNull(0)?.trim()?.toIntOrNull() ?: -1) + 1
                        val e = (parts.getOrNull(1)?.trim()?.split("/")?.firstOrNull()?.toIntOrNull() ?: -1) + 1
                        if (s > 0 && e > 0) "S%02dE%02d".format(s, e) else raw
                    } else raw
                    capturingEpisode = false
                }
                "value" -> if (capturingRating) { currentRating = sb.toString().trim(); capturingRating = false }
                "director" -> if (capturingDirector) { currentDirector = sb.toString().trim(); capturingDirector = false }
                "actor" -> if (capturingActor) {
                    val actor = sb.toString().trim()
                    currentActors = if (currentActors.isEmpty()) actor else "$currentActors, $actor"
                    capturingActor = false
                }
                "date" -> if (capturingYear) { currentYear = sb.toString().trim().take(4); capturingYear = false }
                "programme" -> {
                    if (inProgramme && currentTitle.isNotEmpty() && currentStart > 0 && currentStop > 0) {
                        if (currentStop > cutoffTime && currentStart < futureLimit) {
                            val isSeries = currentEpisodeNum.isNotEmpty()
                            val channelId = currentProgramChannelId

                            val programIcon = if (currentIcon.isNotBlank()) currentIcon
                            else channelLogos[channelId] ?: channelLogos[channelId.lowercase()] ?: ""

                            val list = channelsMap.getOrPut(channelId) { mutableListOf() }
                            list.add(
                                EpgProgram(
                                    channelId = channelId,
                                    title = getCached(currentTitle),
                                    description = getCached(currentDesc),
                                    startTime = currentStart,
                                    endTime = currentStop,
                                    category = getCached(currentCategory),
                                    rating = getCached(currentRating),
                                    posterUrl = getCached(programIcon),
                                    episodeNum = getCached(currentEpisodeNum),
                                    isSeries = isSeries,
                                    director = getCached(currentDirector),
                                    actors = getCached(currentActors),
                                    year = getCached(currentYear),
                                )
                            )
                        }
                    }
                    inProgramme = false
                }
            }
        }
    }
}