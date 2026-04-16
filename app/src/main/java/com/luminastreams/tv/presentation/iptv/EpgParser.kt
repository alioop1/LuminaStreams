package com.luminastreams.tv.presentation.iptv

import android.util.Log
import android.util.Xml
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xmlpull.v1.XmlPullParser
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.zip.GZIPInputStream

object EpgParser {
    private const val TAG = "EpgParser"
    // מפענח את הפורמט הסטנדרטי של XMLTV
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ENGLISH)

    suspend fun parseStreaming(
        epgUrl: String,
        batchSize: Int = 1000,
        onBatchParsed: suspend (List<EpgProgramEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {

        val conn = (URL(epgUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("Accept-Encoding", "gzip")
        }

        try {
            var inputStream: InputStream = conn.inputStream
            // תמיכה אוטומטית בקבצי GZIP (רוב ספקי ה-IPTV משתמשים בזה)
            if ("gzip".equals(conn.contentEncoding, ignoreCase = true) || epgUrl.endsWith(".gz")) {
                inputStream = GZIPInputStream(inputStream)
            }

            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            val batch = mutableListOf<EpgProgramEntity>()

            var currentChannelId = ""
            var title = ""
            var desc = ""
            var start = 0L
            var stop = 0L

            // קריאה זורמת של ה-XML ללא בניית עץ DOM
            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                val startStr = parser.getAttributeValue(null, "start")
                                val stopStr = parser.getAttributeValue(null, "stop")
                                start = parseTime(startStr)
                                stop = parseTime(stopStr)
                            }
                            "title" -> title = parser.nextText()
                            "desc" -> desc = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "programme" && currentChannelId.isNotEmpty()) {
                            // סינון זבל: שומרים רק תוכניות מהעבר הקרוב והעתיד
                            if (stop > System.currentTimeMillis() - 86400000) {
                                batch.add(
                                    EpgProgramEntity(
                                        channelId = currentChannelId,
                                        title = title,
                                        description = desc,
                                        startTime = start,
                                        endTime = stop,
                                        posterUrl = "", // אפשר להוסיף חילוץ אייקון אם צריך
                                        category = ""
                                    )
                                )
                            }

                            // ניקוי משתנים ושחרור המנה (Batch) ל-DB
                            title = ""; desc = ""
                            if (batch.size >= batchSize) {
                                onBatchParsed(batch.toList())
                                batch.clear()
                            }
                        }
                    }
                }
                eventType = parser.next()
            }

            // שמירת שאריות
            if (batch.isNotEmpty()) {
                onBatchParsed(batch)
            }

            Log.d(TAG, "EPG parsing and DB insertion completed.")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPG", e)
        } finally {
            conn.disconnect()
        }
    }

    private fun parseTime(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        return try {
            dateFormat.parse(timeStr)?.time ?: 0L
        } catch (e: Exception) {
            0L
        }
    }
}