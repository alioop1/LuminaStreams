package com.luminastreams.tv.presentation.iptv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
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

    // כאן הוספנו את allowedIds שמסנן את ה-EPG בטירוף וחוסך שעות של טעינה
    suspend fun parse(url: String, allowedIds: Set<String>): Result<EpgResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting EPG download from: $url")
            val stream = fetchEpgStream(url)

            val handler = XmlTvHandler(allowedIds)
            val factory = SAXParserFactory.newInstance().apply {
                isNamespaceAware = false
                isValidating = false
                try { setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false) } catch (_: Exception) {}
                try { setFeature("http://xml.org/sax/features/external-general-entities", false) } catch (_: Exception) {}
                try { setFeature("http://xml.org/sax/features/external-parameter-entities", false) } catch (_: Exception) {}
            }
            val parser = factory.newSAXParser()
            parser.parse(stream, handler)

            Log.d(TAG, "EPG parsed: ${handler.channelsMap.size} relevant channels")

            val finalMap = mutableMapOf<String, List<EpgProgram>>()
            val finalLogoMap = mutableMapOf<String, String>()

            for ((id, list) in handler.channelsMap) {
                val sortedList = list.sortedBy { it.startTime }
                finalMap[id.lowercase()] = sortedList

                handler.displayNames[id]?.forEach { displayName ->
                    if (displayName.isNotEmpty()) finalMap[displayName.lowercase()] = sortedList
                }

                val logo = handler.channelLogos[id]
                if (!logo.isNullOrBlank()) {
                    finalLogoMap[id.lowercase()] = logo
                    handler.displayNames[id]?.forEach { displayName ->
                        if (displayName.isNotEmpty()) finalLogoMap[displayName.lowercase()] = logo
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
                setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV) Chrome/112.0.0.0")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                instanceFollowRedirects = false
            }

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (!newUrl.isNullOrBlank()) {
                    currentUrl = if (newUrl.startsWith("http")) newUrl else URL(URL(currentUrl), newUrl).toString()
                    redirects++
                    continue
                }
            }

            if (responseCode !in 200..299) {
                conn.disconnect()
                throw Exception("HTTP Error: $responseCode")
            }

            val contentEncoding = conn.contentEncoding ?: ""
            val contentType = conn.contentType ?: ""
            val lowerUrl = currentUrl.lowercase()

            return when {
                contentEncoding.contains("gzip", true) || lowerUrl.endsWith(".gz") -> GZIPInputStream(conn.inputStream)
                lowerUrl.endsWith(".zip") || contentType.contains("zip", true) -> {
                    val zis = ZipInputStream(conn.inputStream)
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".xml", true) || entry.name.endsWith(".xmltv", true)) return zis
                        entry = zis.nextEntry
                    }
                    throw Exception("No XML found in ZIP")
                }
                else -> conn.inputStream
            }
        }
        throw Exception("Too many redirects")
    }

    private class XmlTvHandler(private val allowedIds: Set<String>) : DefaultHandler() {
        val channelsMap = mutableMapOf<String, MutableList<EpgProgram>>()
        val displayNames = mutableMapOf<String, MutableList<String>>()
        val channelLogos = mutableMapOf<String, String>()

        private val cutoffTime = System.currentTimeMillis() - (24 * 3600 * 1000L)
        private val futureLimit = System.currentTimeMillis() + (14L * 24 * 3600 * 1000)

        private val calendar = Calendar.getInstance(TimeZone.getTimeZone("UTC"))

        private var inChannel = false
        private var inProgramme = false
        private var ignoreCurrentElement = false

        private var currentChannelId = ""
        private var currentProgramChannelId = ""
        private var currentStart = 0L
        private var currentStop = 0L

        private val sb = StringBuilder(512)
        private var currentTag = ""

        private val programData = mutableMapOf<String, String>()

        private fun parseTimeFast(timeStr: String): Long {
            var len = timeStr.length
            while (len > 0 && timeStr[len - 1] <= ' ') len--
            var st = 0
            while (st < len && timeStr[st] <= ' ') st++
            if (len - st < 14) return 0L

            return try {
                var y = 0; var mo = 0; var d = 0; var h = 0; var m = 0; var s = 0
                for(i in 0..3) y = y * 10 + (timeStr[st + i] - '0')
                for(i in 4..5) mo = mo * 10 + (timeStr[st + i] - '0')
                for(i in 6..7) d = d * 10 + (timeStr[st + i] - '0')
                for(i in 8..9) h = h * 10 + (timeStr[st + i] - '0')
                for(i in 10..11) m = m * 10 + (timeStr[st + i] - '0')
                for(i in 12..13) s = s * 10 + (timeStr[st + i] - '0')

                calendar.set(y, mo - 1, d, h, m, s)
                calendar.set(Calendar.MILLISECOND, 0)

                var offset = 0
                if (len - st >= 19 && timeStr[st + 14] == ' ') {
                    val sign = if (timeStr[st + 15] == '-') -1 else 1
                    val oh = (timeStr[st + 16] - '0') * 10 + (timeStr[st + 17] - '0')
                    val om = (timeStr[st + 18] - '0') * 10 + (timeStr[st + 19] - '0')
                    offset = sign * ((oh * 60) + om) * 60_000
                }
                calendar.timeInMillis - offset
            } catch (e: Exception) { 0L }
        }

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            val tag = qName.lowercase()
            currentTag = tag
            sb.clear()

            when (tag) {
                "channel" -> {
                    val id = attrs.getValue("id") ?: ""
                    // אם הרשימה ריקה זה אומר שלא סונן, אחרת בודקים אם הערוץ בפלייליסט
                    if (allowedIds.isEmpty() || allowedIds.contains(id.lowercase())) {
                        inChannel = true
                        ignoreCurrentElement = false
                        currentChannelId = id
                    } else {
                        ignoreCurrentElement = true
                    }
                }
                "programme" -> {
                    val ch = attrs.getValue("channel") ?: ""
                    if (allowedIds.isEmpty() || allowedIds.contains(ch.lowercase())) {
                        inProgramme = true
                        ignoreCurrentElement = false
                        currentProgramChannelId = ch
                        currentStart = parseTimeFast(attrs.getValue("start") ?: "")
                        currentStop = parseTimeFast(attrs.getValue("stop") ?: "")
                        programData.clear()
                    } else {
                        ignoreCurrentElement = true
                    }
                }
                "icon" -> {
                    if (!ignoreCurrentElement) {
                        val src = attrs.getValue("src") ?: ""
                        if (src.isNotBlank()) {
                            if (inChannel) {
                                channelLogos[currentChannelId] = src
                                channelLogos[currentChannelId.lowercase()] = src
                            } else if (inProgramme) {
                                programData["icon"] = src
                            }
                        }
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (!ignoreCurrentElement && (inChannel || inProgramme)) {
                sb.appendRange(ch, start, start + length)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            if (ignoreCurrentElement) return

            val tag = qName.lowercase()
            when (tag) {
                "channel" -> { inChannel = false }
                "display-name" -> if (inChannel) {
                    val name = sb.toString().trim()
                    if (name.isNotEmpty()) displayNames.getOrPut(currentChannelId) { mutableListOf() }.add(name)
                }
                "title", "desc", "category", "date" -> if (inProgramme) programData[tag] = sb.toString().trim()
                "episode-num" -> if (inProgramme) {
                    val raw = sb.toString().trim()
                    programData["episode-num"] = if (raw.contains(".")) {
                        val parts = raw.split(".")
                        val s = (parts.getOrNull(0)?.trim()?.toIntOrNull() ?: -1) + 1
                        val e = (parts.getOrNull(1)?.trim()?.split("/")?.firstOrNull()?.toIntOrNull() ?: -1) + 1
                        if (s > 0 && e > 0) "S%02dE%02d".format(s, e) else raw
                    } else raw
                }
                "actor", "director" -> if (inProgramme) {
                    val current = programData[tag] ?: ""
                    val newPerson = sb.toString().trim()
                    programData[tag] = if (current.isEmpty()) newPerson else "$current, $newPerson"
                }
                "programme" -> {
                    if (currentStart > 0 && currentStop > cutoffTime && currentStart < futureLimit) {
                        val title = programData["title"] ?: ""
                        if (title.isNotEmpty()) {
                            val icon = programData["icon"] ?: channelLogos[currentProgramChannelId] ?: ""
                            channelsMap.getOrPut(currentProgramChannelId) { mutableListOf() }.add(
                                EpgProgram(
                                    channelId = currentProgramChannelId,
                                    title = title,
                                    description = programData["desc"] ?: "",
                                    startTime = currentStart,
                                    endTime = currentStop,
                                    category = programData["category"] ?: "",
                                    posterUrl = icon,
                                    episodeNum = programData["episode-num"] ?: "",
                                    isSeries = (programData["episode-num"]?.isNotEmpty() == true),
                                    director = programData["director"] ?: "",
                                    actors = programData["actor"] ?: "",
                                    year = programData["date"]?.take(4) ?: ""
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