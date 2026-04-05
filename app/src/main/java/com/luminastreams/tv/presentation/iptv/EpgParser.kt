package com.luminastreams.tv.presentation.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.GZIPInputStream
import javax.xml.parsers.SAXParserFactory

object EpgParser {

    suspend fun parse(url: String): Result<Map<String, List<EpgProgram>>> = withContext(Dispatchers.IO) {
        try {
            val conn = URL(url).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)")
            conn.setRequestProperty("Accept-Encoding", "gzip")

            val stream: InputStream = if (conn.contentEncoding?.contains("gzip", true) == true ||
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

            Result.success(handler.channelsMap)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    // Thread-local calendar כדי למנוע יצירת אובייקטים חדשים בזיכרון לכל שורה
    private val threadLocalCalendar = object : ThreadLocal<java.util.Calendar>() {
        override fun initialValue(): java.util.Calendar {
            return java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"))
        }
    }

    // פונקציית המרת זמנים סופר-מהירה שלא מייצרת זבל (Garbage)
    fun parseTimeFast(timeStr: String): Long {
        try {
            val cleaned = timeStr.trim()
            if (cleaned.length >= 14) {
                val year = cleaned.substring(0, 4).toInt()
                val month = cleaned.substring(4, 6).toInt() - 1
                val day = cleaned.substring(6, 8).toInt()
                val hour = cleaned.substring(8, 10).toInt()
                val min = cleaned.substring(10, 12).toInt()
                val sec = cleaned.substring(12, 14).toInt()

                val cal = threadLocalCalendar.get()!!
                cal.set(year, month, day, hour, min, sec)
                cal.set(java.util.Calendar.MILLISECOND, 0)

                var time = cal.timeInMillis

                val spaceIdx = cleaned.indexOf(' ')
                if (spaceIdx != -1 && spaceIdx + 5 < cleaned.length) {
                    val tz = cleaned.substring(spaceIdx + 1)
                    if (tz.startsWith("+") || tz.startsWith("-")) {
                        val sign = if (tz.startsWith("-")) -1 else 1
                        val tzH = tz.substring(1, 3).toInt()
                        val tzM = tz.substring(3, 5).toInt()
                        time -= sign * (tzH * 60 + tzM) * 60_000L
                    }
                }
                return time
            }
        } catch (_: Exception) {}
        return 0L
    }

    private class XmlTvHandler : DefaultHandler() {
        val channelsMap = mutableMapOf<String, MutableList<EpgProgram>>()

        // OOM Saver: נשמור רק תוכניות מה-12 שעות האחרונות ולא היסטוריה שלמה
        private val cutoffTime = System.currentTimeMillis() - 12 * 3600 * 1000L

        // Cache למחרוזות שחוזרות על עצמן המון פעמים כדי לחסוך בזיכרון
        private val stringCache = mutableMapOf<String, String>()

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

        private fun getCachedString(value: String): String {
            val trimmed = value.trim()
            if (trimmed.isEmpty()) return ""
            return stringCache.getOrPut(trimmed) { trimmed }
        }

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            when (qName.lowercase()) {
                "programme" -> {
                    inProgramme = true
                    currentChannelId = getCachedString(attrs.getValue("channel") ?: "")
                    currentStart = parseTimeFast(attrs.getValue("start") ?: "")
                    currentStop = parseTimeFast(attrs.getValue("stop") ?: "")
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
                "title" -> if (capturingTitle) { currentTitle = sb.toString().trim(); capturingTitle = false; sb.clear() }
                "desc" -> if (capturingDesc) { currentDesc = sb.toString().trim(); capturingDesc = false; sb.clear() }
                "category" -> if (capturingCategory) { currentCategory = sb.toString().trim(); capturingCategory = false; sb.clear() }
                "value" -> if (capturingRating) { currentRating = sb.toString().trim(); capturingRating = false; sb.clear() }
                "programme" -> {
                    if (inProgramme && currentTitle.isNotEmpty() && currentStart > 0 && currentStop > 0) {
                        // הסינון הזה חוסך 80% מהזיכרון בקבצי EPG גדולים
                        if (currentStop > cutoffTime) {
                            val list = channelsMap.getOrPut(currentChannelId) { mutableListOf() }
                            list.add(
                                EpgProgram(
                                    channelId = currentChannelId,
                                    title = currentTitle,
                                    description = currentDesc,
                                    startTime = currentStart,
                                    endTime = currentStop,
                                    category = getCachedString(currentCategory),
                                    rating = getCachedString(currentRating),
                                    posterUrl = getCachedString(currentPosterUrl)
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