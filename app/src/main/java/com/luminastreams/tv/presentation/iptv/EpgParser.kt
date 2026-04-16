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
    private val dateFormat = SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ENGLISH)

    // תיקון #4 — חלון של 7 ימים אחורה במקום 24 שעות
    private const val EPG_LOOKBACK_MS = 7 * 24 * 60 * 60 * 1000L // 7 ימים

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

        // תיקון #5 — inputStream מוגדר מחוץ ל-try כדי לסגור אותו ב-finally
        var inputStream: InputStream? = null

        try {
            inputStream = conn.inputStream
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

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "programme" -> {
                                currentChannelId = parser.getAttributeValue(null, "channel") ?: ""
                                start = parseTime(parser.getAttributeValue(null, "start"))
                                stop = parseTime(parser.getAttributeValue(null, "stop"))
                            }
                            "title" -> title = parser.nextText()
                            "desc" -> desc = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "programme" && currentChannelId.isNotEmpty()) {
                            // תיקון #4 — 7 ימים אחורה
                            if (stop > System.currentTimeMillis() - EPG_LOOKBACK_MS) {
                                batch.add(
                                    EpgProgramEntity(
                                        channelId = currentChannelId,
                                        title = title,
                                        description = desc,
                                        startTime = start,
                                        endTime = stop,
                                        posterUrl = "",
                                        category = ""
                                    )
                                )
                            }
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

            if (batch.isNotEmpty()) onBatchParsed(batch)
            Log.d(TAG, "EPG parsing and DB insertion completed.")

        } catch (e: Exception) {
            Log.e(TAG, "Error parsing EPG", e)
        } finally {
            // תיקון #5 — סגירה מפורשת של ה-inputStream לפני disconnect
            try { inputStream?.close() } catch (_: Exception) {}
            conn.disconnect()
        }
    }

    private fun parseTime(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        return try {
            dateFormat.parse(timeStr)?.time ?: 0L
        } catch (_: Exception) {
            0L
        }
    }
}