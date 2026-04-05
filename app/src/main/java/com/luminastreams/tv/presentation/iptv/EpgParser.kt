package com.luminastreams.tv.presentation.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

object EpgParser {

    private val dateFormats = listOf(
        SimpleDateFormat("yyyyMMddHHmmss Z", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss", Locale.US),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ", Locale.US),
        SimpleDateFormat("yyyyMMddHHmmss +0000", Locale.US)
    )

    suspend fun parse(url: String): Result<Map<String, List<EpgProgram>>> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)")
            conn.setRequestProperty("Accept-Encoding", "gzip")

            val stream: InputStream = if (conn.contentEncoding == "gzip" ||
                url.endsWith(".gz", ignoreCase = true)) {
                GZIPInputStream(conn.inputStream)
            } else {
                conn.inputStream
            }

            val handler = XmlTvHandler()
            val factory = SAXParserFactory.newInstance()
            factory.isNamespaceAware = false
            factory.isValidating = false
            val parser = factory.newSAXParser()
            parser.parse(stream, handler)

            Result.success(handler.programs.groupBy { it.channelId })
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    fun parseTime(timeStr: String): Long {
        val cleaned = timeStr.trim()
        for (fmt in dateFormats) {
            try {
                return fmt.parse(cleaned)?.time ?: continue
            } catch (_: Exception) { }
        }
        // Try manual parse: yyyyMMddHHmmss +HHMM
        return try {
            val parts = cleaned.split(" ")
            val base = parts[0]
            val year = base.substring(0, 4).toInt()
            val month = base.substring(4, 6).toInt() - 1
            val day = base.substring(6, 8).toInt()
            val hour = base.substring(8, 10).toInt()
            val min = base.substring(10, 12).toInt()
            val sec = if (base.length >= 14) base.substring(12, 14).toInt() else 0
            val cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
            cal.set(year, month, day, hour, min, sec)
            cal.set(java.util.Calendar.MILLISECOND, 0)
            // Apply timezone offset
            if (parts.size > 1) {
                val tz = parts[1]
                val sign = if (tz.startsWith("-")) -1 else 1
                val tzH = tz.drop(1).take(2).toIntOrNull() ?: 0
                val tzM = tz.drop(3).take(2).toIntOrNull() ?: 0
                cal.timeInMillis -= sign * (tzH * 60 + tzM) * 60_000L
            }
            cal.timeInMillis
        } catch (_: Exception) {
            0L
        }
    }

    private class XmlTvHandler : DefaultHandler() {
        val programs = mutableListOf<EpgProgram>()

        private var inProgramme = false
        private var currentChannelId = ""
        private var currentStart = 0L
        private var currentStop = 0L
        private var currentTitle = ""
        private var currentDesc = ""
        private var currentCategory = ""
        private var currentRating = ""
        private var currentPosterUrl = ""
        private var capturingTitle = false
        private var capturingDesc = false
        private var capturingCategory = false
        private var capturingRating = false
        private val sb = StringBuilder()

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            when (qName.lowercase()) {
                "programme" -> {
                    inProgramme = true
                    currentChannelId = attrs.getValue("channel") ?: ""
                    currentStart = EpgParser.parseTime(attrs.getValue("start") ?: "")
                    currentStop = EpgParser.parseTime(attrs.getValue("stop") ?: "")
                    currentTitle = ""; currentDesc = ""; currentCategory = ""
                    currentRating = ""; currentPosterUrl = ""
                }
                "title" -> if (inProgramme) { capturingTitle = true; sb.clear() }
                "desc" -> if (inProgramme) { capturingDesc = true; sb.clear() }
                "category" -> if (inProgramme) { capturingCategory = true; sb.clear() }
                "value" -> if (inProgramme) { capturingRating = true; sb.clear() }
                "icon" -> if (inProgramme) {
                    val src = attrs.getValue("src") ?: ""
                    if (currentPosterUrl.isEmpty()) currentPosterUrl = src
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (capturingTitle || capturingDesc || capturingCategory || capturingRating) {
                sb.append(ch, start, length)
            }
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (qName.lowercase()) {
                "title" -> {
                    if (capturingTitle) { currentTitle = sb.toString().trim(); capturingTitle = false; sb.clear() }
                }
                "desc" -> {
                    if (capturingDesc) { currentDesc = sb.toString().trim(); capturingDesc = false; sb.clear() }
                }
                "category" -> {
                    if (capturingCategory) { currentCategory = sb.toString().trim(); capturingCategory = false; sb.clear() }
                }
                "value" -> {
                    if (capturingRating) { currentRating = sb.toString().trim(); capturingRating = false; sb.clear() }
                }
                "programme" -> {
                    if (inProgramme && currentTitle.isNotEmpty() && currentStart > 0 && currentStop > 0) {
                        programs.add(
                            EpgProgram(
                                channelId = currentChannelId,
                                title = currentTitle,
                                description = currentDesc,
                                startTime = currentStart,
                                endTime = currentStop,
                                category = currentCategory,
                                rating = currentRating,
                                posterUrl = currentPosterUrl
                            )
                        )
                    }
                    inProgramme = false
                }
            }
        }
    }
}
