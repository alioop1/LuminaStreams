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

    // Max programs stored per channel — covers ~2 days at 30-min slots with buffer.
    private const val MAX_PROGRAMS_PER_CHANNEL = 96

    data class EpgResult(
        val programs: Map<String, List<EpgProgram>>,
        val channelLogos: Map<String, String>,
        val channelDisplayNames: Map<String, List<String>>
    )

    /**
     * Parse an XMLTV EPG feed. allowedIds is used ONLY for post-parse logo/display-name dedup,
     * NOT for filtering programmes during SAX parsing. Filtering during parse was the root cause
     * of EPG data being silently discarded — EPG channel IDs rarely match M3U tvg-id exactly.
     */
    suspend fun parse(url: String, allowedIds: Set<String>): Result<EpgResult> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting EPG download from: $url")
            val stream = fetchEpgStream(url)

            // Parse ALL channels — no filtering during SAX. The ViewModel does fuzzy
            // channel-id matching after parse to link programmes to M3U channels.
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

            Log.d(TAG, "EPG parsed: ${handler.channelsMap.size} channels, ${handler.programCount} programs, ${handler.channelLogos.size} logos")

            val finalMap = mutableMapOf<String, List<EpgProgram>>()
            val finalLogoMap = mutableMapOf<String, String>()

            for ((id, deque) in handler.channelsMap) {
                val sortedList = deque.sortedBy { it.startTime }
                val idLower = id.lowercase()

                finalMap[idLower] = sortedList

                handler.displayNames[id]?.forEach { displayName ->
                    if (displayName.isNotEmpty()) finalMap[displayName.lowercase()] = sortedList
                }

                val logo = handler.channelLogos[id] ?: handler.channelLogos[idLower]
                if (!logo.isNullOrBlank()) {
                    finalLogoMap[idLower] = logo
                    handler.displayNames[id]?.forEach { displayName ->
                        if (displayName.isNotEmpty()) finalLogoMap[displayName.lowercase()] = logo
                    }
                }
            }

            Log.d(TAG, "EPG result: ${finalMap.size} program keys, ${finalLogoMap.size} logo keys")

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
                connectTimeout = 20_000
                readTimeout = 90_000
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
                throw Exception("HTTP Error: $responseCode for $currentUrl")
            }

            val contentEncoding = conn.contentEncoding ?: ""
            val contentType = conn.contentType ?: ""
            val lowerUrl = currentUrl.lowercase()

            return when {
                contentEncoding.contains("gzip", true) || lowerUrl.endsWith(".gz") ->
                    GZIPInputStream(conn.inputStream.buffered(131072))
                lowerUrl.endsWith(".zip") || contentType.contains("zip", true) -> {
                    val zis = ZipInputStream(conn.inputStream)
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".xml", true) || entry.name.endsWith(".xmltv", true)) return zis
                        entry = zis.nextEntry
                    }
                    throw Exception("No XML found in ZIP")
                }
                else -> conn.inputStream.buffered(131072)
            }
        }
        throw Exception("Too many redirects")
    }

    /**
     * SAX handler that parses ALL channels without pre-filtering.
     * Time-range filtering (drop stale/far-future programmes) is still applied
     * to keep memory usage bounded. Ring-buffer cap per channel also retained.
     */
    private class XmlTvHandler : DefaultHandler() {
        val channelsMap = mutableMapOf<String, ArrayDeque<EpgProgram>>()
        val displayNames = mutableMapOf<String, MutableList<String>>()
        val channelLogos = mutableMapOf<String, String>()
        var programCount = 0

        // Drop programmes that ended more than 1 hour ago or start more than 14 days ahead.
        private val cutoffTime = System.currentTimeMillis() - (1 * 3600 * 1000L)
        private val futureLimit = System.currentTimeMillis() + (14L * 24 * 3600 * 1000)

        private var inChannel = false
        private var inProgramme = false
        private var skipProgramme = false

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
                for (i in 0..3) y = y * 10 + (timeStr[st + i] - '0')
                for (i in 4..5) mo = mo * 10 + (timeStr[st + i] - '0')
                for (i in 6..7) d = d * 10 + (timeStr[st + i] - '0')
                for (i in 8..9) h = h * 10 + (timeStr[st + i] - '0')
                for (i in 10..11) m = m * 10 + (timeStr[st + i] - '0')
                for (i in 12..13) s = s * 10 + (timeStr[st + i] - '0')

                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.set(y, mo - 1, d, h, m, s)
                cal.set(Calendar.MILLISECOND, 0)

                var offset = 0
                if (len - st >= 20 && timeStr[st + 14] == ' ') {
                    val sign = if (timeStr[st + 15] == '-') -1 else 1
                    val oh = (timeStr[st + 16] - '0') * 10 + (timeStr[st + 17] - '0')
                    val om = (timeStr[st + 18] - '0') * 10 + (timeStr[st + 19] - '0')
                    offset = sign * ((oh * 60) + om) * 60_000
                }
                cal.timeInMillis - offset
            } catch (e: Exception) { 0L }
        }

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            val tag = qName.lowercase()
            currentTag = tag
            sb.clear()

            when (tag) {
                "channel" -> {
                    val id = attrs.getValue("id") ?: ""
                    if (id.isNotBlank()) {
                        inChannel = true
                        inProgramme = false
                        skipProgramme = false
                        currentChannelId = id
                    }
                }
                "programme" -> {
                    val ch = attrs.getValue("channel") ?: ""
                    if (ch.isNotBlank()) {
                        inProgramme = true
                        inChannel = false
                        currentProgramChannelId = ch
                        currentStart = parseTimeFast(attrs.getValue("start") ?: "")
                        currentStop = parseTimeFast(attrs.getValue("stop") ?: "")
                        // Only skip based on time range — not channel ID matching.
                        skipProgramme = currentStop < cutoffTime || currentStart > futureLimit
                        if (!skipProgramme) programData.clear()
                    }
                }
                "icon" -> {
                    val src = attrs.getValue("src") ?: ""
                    if (src.isNotBlank()) {
                        when {
                            inChannel && currentChannelId.isNotBlank() -> {
                                channelLogos[currentChannelId] = src
                                channelLogos[currentChannelId.lowercase()] = src
                            }
                            inProgramme && !skipProgramme -> programData["icon"] = src
                        }
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inChannel || (inProgramme && !skipProgramme)) {
                sb.appendRange(ch, start, start + length)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val tag = qName.lowercase()

            when {
                tag == "channel" -> {
                    inChannel = false
                    currentChannelId = ""
                }
                tag == "programme" -> {
                    if (!skipProgramme && currentStart > 0) {
                        val title = programData["title"]
                        if (!title.isNullOrBlank()) {
                            val prog = EpgProgram(
                                channelId = currentProgramChannelId,
                                title = title,
                                description = programData["desc"] ?: "",
                                startTime = currentStart,
                                endTime = currentStop,
                                category = programData["category"] ?: "",
                                posterUrl = programData["icon"] ?: "",
                                episodeNum = programData["episodeNum"] ?: "",
                                rating = programData["rating"] ?: ""
                            )
                            val deque = channelsMap.getOrPut(currentProgramChannelId) { ArrayDeque() }
                            if (deque.size >= MAX_PROGRAMS_PER_CHANNEL) deque.removeFirst()
                            deque.addLast(prog)
                            programCount++
                        }
                    }
                    inProgramme = false
                    skipProgramme = false
                    currentProgramChannelId = ""
                    currentStart = 0L
                    currentStop = 0L
                    programData.clear()
                }
                inChannel -> {
                    val text = sb.toString().trim()
                    if (tag == "display-name" && text.isNotEmpty()) {
                        displayNames.getOrPut(currentChannelId) { mutableListOf() }.add(text)
                    }
                }
                inProgramme && !skipProgramme -> {
                    val text = sb.toString().trim()
                    when (tag) {
                        "title"       -> if (programData["title"] == null && text.isNotEmpty()) programData["title"] = text
                        "desc"        -> if (programData["desc"] == null && text.isNotEmpty()) programData["desc"] = text
                        "category"    -> if (programData["category"] == null && text.isNotEmpty()) programData["category"] = text
                        "episode-num" -> if (programData["episodeNum"] == null && text.isNotEmpty()) programData["episodeNum"] = text
                        "value"       -> if (programData["rating"] == null && text.isNotEmpty()) programData["rating"] = text
                    }
                }
            }
        }
    }
}
