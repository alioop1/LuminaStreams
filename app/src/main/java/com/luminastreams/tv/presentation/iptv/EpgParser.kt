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
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

object EpgParser {

    private const val TAG = "EPG_DEBUG"

    suspend fun parse(url: String): Result<Map<String, List<EpgProgram>>> = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "Starting EPG download from: $url")
            val stream = fetchEpgStream(url)

            val handler = XmlTvHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            val parser = factory.newSAXParser()

            parser.parse(stream, handler)

            Log.d(TAG, "Parsing Complete! Total unique channels mapped: ${handler.channelsMap.size}")

            val finalMap = mutableMapOf<String, List<EpgProgram>>()
            for ((id, list) in handler.channelsMap) {
                finalMap[id.lowercase()] = list
                handler.displayNames[id]?.forEach { displayName ->
                    if (displayName.isNotEmpty()) {
                        finalMap[displayName.lowercase()] = list
                    }
                }
            }

            Result.success(finalMap)
        } catch (e: Exception) {
            Log.e(TAG, "Error in EPG: ${e.message}", e)
            Result.failure(e)
        }
    }

    private fun fetchEpgStream(urlString: String): InputStream {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < 5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 30_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0")
            conn.setRequestProperty("Accept-Encoding", "gzip, deflate")
            conn.instanceFollowRedirects = false

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (newUrl != null) {
                    currentUrl = newUrl
                    redirects++
                    continue
                }
            }

            if (responseCode !in 200..299) {
                conn.disconnect()
                throw Exception("HTTP Error: $responseCode")
            }

            val encoding = conn.contentEncoding ?: ""
            val contentType = conn.contentType ?: ""

            return if (encoding.contains("gzip", true) || currentUrl.endsWith(".gz", true)) {
                GZIPInputStream(conn.inputStream)
            } else if (currentUrl.endsWith(".zip", true) || contentType.contains("zip", true)) {
                val zis = ZipInputStream(conn.inputStream)
                zis.nextEntry
                zis
            } else {
                conn.inputStream
            }
        }
        throw Exception("Too many redirects")
    }

    private val formatter = DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
    private val formatterFallback = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")

    fun parseTimeSafely(timeStr: String): Long {
        val cleanTime = timeStr.trim()
        if (cleanTime.isEmpty()) return 0L
        return try {
            ZonedDateTime.parse(cleanTime, formatter).toInstant().toEpochMilli()
        } catch (e: Exception) {
            try {
                val parts = cleanTime.split(" ")
                val timePart = if (parts.isNotEmpty()) parts[0] else cleanTime
                val localDate = java.time.LocalDateTime.parse(timePart, formatterFallback)
                localDate.atZone(java.time.ZoneId.of("UTC")).toInstant().toEpochMilli()
            } catch (ex: Exception) { 0L }
        }
    }

    private class XmlTvHandler : DefaultHandler() {
        val channelsMap = mutableMapOf<String, MutableList<EpgProgram>>()
        val displayNames = mutableMapOf<String, MutableList<String>>()
        val channelLogos = mutableMapOf<String, String>()

        private val cutoffTime = System.currentTimeMillis() - (12 * 3600 * 1000L)
        private val stringCache = mutableMapOf<String, String>()

        private var inChannel = false
        private var inProgramme = false
        private var currentChannelId = ""
        private var currentStart = 0L
        private var currentStop = 0L
        private var currentTitle = ""
        private var currentDesc = ""
        private var currentCategory = ""
        private var currentIcon = ""

        private var capturingDisplayName = false
        private var capturingTitle = false
        private var capturingDesc = false
        private var capturingCategory = false
        private val sb = StringBuilder()

        private fun getCachedString(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return ""
            return stringCache.getOrPut(trimmed) { trimmed }
        }

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            when (qName.lowercase()) {
                "channel" -> {
                    inChannel = true
                    currentChannelId = attrs.getValue("id") ?: ""
                }
                "icon" -> {
                    val src = attrs.getValue("src") ?: ""
                    if (src.isNotBlank()) {
                        if (inProgramme) currentIcon = src
                        else if (inChannel && currentChannelId.isNotEmpty()) channelLogos[currentChannelId] = src
                    }
                }
                "display-name" -> {
                    if (inChannel && currentChannelId.isNotEmpty()) { capturingDisplayName = true; sb.clear() }
                }
                "programme" -> {
                    inProgramme = true
                    currentChannelId = getCachedString(attrs.getValue("channel") ?: "")
                    currentStart = parseTimeSafely(attrs.getValue("start") ?: "")
                    currentStop = parseTimeSafely(attrs.getValue("stop") ?: "")
                    currentTitle = ""; currentDesc = ""; currentCategory = ""; currentIcon = ""
                }
                "title" -> if (inProgramme) { capturingTitle = true; sb.clear() }
                "desc" -> if (inProgramme) { capturingDesc = true; sb.clear() }
                "category" -> if (inProgramme) { capturingCategory = true; sb.clear() }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capturingTitle || capturingDesc || capturingCategory || capturingDisplayName) {
                sb.append(ch, start, length)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (qName.lowercase()) {
                "channel" -> inChannel = false
                "display-name" -> if (capturingDisplayName) {
                    displayNames.getOrPut(currentChannelId) { mutableListOf() }.add(getCachedString(sb.toString()))
                    capturingDisplayName = false
                }
                "title" -> if (capturingTitle) { currentTitle = sb.toString().trim(); capturingTitle = false }
                "desc" -> if (capturingDesc) { currentDesc = sb.toString().trim(); capturingDesc = false }
                "category" -> if (capturingCategory) { currentCategory = sb.toString().trim(); capturingCategory = false }
                "programme" -> {
                    if (inProgramme && currentTitle.isNotEmpty() && currentStart > 0 && currentStop > 0) {
                        if (currentStop > cutoffTime) {
                            val list = channelsMap.getOrPut(currentChannelId) { mutableListOf() }
                            val finalIcon = if (currentIcon.isNotBlank()) currentIcon else (channelLogos[currentChannelId] ?: "")

                            list.add(
                                EpgProgram(
                                    channelId = currentChannelId,
                                    title = currentTitle,
                                    description = currentDesc,
                                    startTime = currentStart,
                                    endTime = currentStop,
                                    category = getCachedString(currentCategory),
                                    posterUrl = getCachedString(finalIcon)
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