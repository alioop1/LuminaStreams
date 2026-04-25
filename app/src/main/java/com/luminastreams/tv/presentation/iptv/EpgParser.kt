@file:Suppress("unused", "UNUSED_PARAMETER", "UNUSED_VARIABLE", "CatchMayIgnoreException")
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
import kotlinx.coroutines.CancellationException

object EpgParser {
    private const val TAG = "EpgParser"
    // SimpleDateFormat is NOT thread-safe — use ThreadLocal for coroutine IO dispatcher
    private val dateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyyMMddHHmmss Z", Locale.ENGLISH) }
    private val fallbackDateFormat = ThreadLocal.withInitial { SimpleDateFormat("yyyyMMddHHmmss", Locale.ENGLISH) }
    private const val EPG_LOOKBACK_MS = 7 * 24 * 60 * 60 * 1000L // 7 Days

    suspend fun parseStreaming(
        epgUrl: String,
        batchSize: Int = 1000,
        onLogosFound: suspend (Map<String, String>) -> Unit,
        onBatchParsed: suspend (List<EpgProgramEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting EPG Download: $epgUrl")
        var currentUrl = epgUrl
        var conn: HttpURLConnection? = null
        var redirects = 0

        while (redirects < 5) {
            conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 60_000
            conn.instanceFollowRedirects = false

            conn.setRequestProperty("User-Agent", "VLC/3.0.0")
            conn.setRequestProperty("Accept-Encoding", "gzip")

            val status = conn.responseCode
            if (status in 300..399) {
                currentUrl = conn.getHeaderField("Location") ?: currentUrl
                conn.disconnect()
                redirects++
            } else {
                break
            }
        }

        var inputStream: InputStream? = null

        try {
            if (conn!!.responseCode !in 200..299) return@withContext

            var baseStream = conn.inputStream
            if (!baseStream.markSupported()) baseStream = baseStream.buffered()
            baseStream.mark(2)
            val b1 = baseStream.read()
            val b2 = baseStream.read()
            baseStream.reset()

            inputStream = if (b1 == 0x1f && b2 == 0x8b) {
                GZIPInputStream(baseStream)
            } else {
                baseStream
            }

            val parser = Xml.newPullParser()
            parser.setInput(inputStream, "UTF-8")

            var eventType = parser.eventType
            val batch = mutableListOf<EpgProgramEntity>()

            val logosMap = mutableMapOf<String, String>()
            val channelNamesMap = mutableMapOf<String, String>()

            var currentChannelId = ""
            var isInsideChannel = false

            var currentProgChannelId = ""
            var title = ""
            var desc = ""
            var start = 0L
            var stop = 0L

            while (eventType != XmlPullParser.END_DOCUMENT) {
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "channel" -> {
                                currentChannelId = parser.getAttributeValue(null, "id")?.trim() ?: ""
                                isInsideChannel = true
                            }
                            "display-name" -> {
                                if (isInsideChannel && currentChannelId.isNotEmpty()) {
                                    val cName = try { parser.nextText().trim() } catch (e: Exception) { "" }
                                    if (cName.isNotEmpty() && !channelNamesMap.containsKey(currentChannelId)) {
                                        channelNamesMap[currentChannelId] = cName
                                    }
                                }
                            }
                            "icon" -> {
                                if (isInsideChannel && currentChannelId.isNotEmpty()) {
                                    val src = parser.getAttributeValue(null, "src")
                                    if (!src.isNullOrBlank()) {
                                        logosMap[currentChannelId] = src.trim()
                                    }
                                }
                            }
                            "programme" -> {
                                currentProgChannelId = parser.getAttributeValue(null, "channel")?.trim() ?: ""
                                start = parseTime(parser.getAttributeValue(null, "start"))
                                stop = parseTime(parser.getAttributeValue(null, "stop"))
                            }
                            "title" -> {
                                if (currentProgChannelId.isNotEmpty()) title = try { parser.nextText() } catch (e: Exception) { "" }
                            }
                            "desc" -> {
                                if (currentProgChannelId.isNotEmpty()) desc = try { parser.nextText() } catch (e: Exception) { "" }
                            }
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "channel") {
                            isInsideChannel = false
                            currentChannelId = ""
                        } else if (parser.name == "programme" && currentProgChannelId.isNotEmpty()) {
                            if (stop > System.currentTimeMillis() - EPG_LOOKBACK_MS) {
                                val mappedName = channelNamesMap[currentProgChannelId] ?: currentProgChannelId

                                batch.add(
                                    EpgProgramEntity(
                                        channelId = mappedName,
                                        title = title.trim(),
                                        description = desc.trim(),
                                        startTime = start,
                                        endTime = stop,
                                        posterUrl = "",
                                        category = ""
                                    )
                                )
                            }
                            title = ""
                            desc = ""
                            currentProgChannelId = ""

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

            if (logosMap.isNotEmpty()) {
                val mappedLogos = mutableMapOf<String, String>()
                logosMap.forEach { (id, url) ->
                    val realName = channelNamesMap[id] ?: id
                    mappedLogos[realName] = url
                }
                onLogosFound(mappedLogos)
            }

            Log.d(TAG, "EPG and Logos parsing entirely completed.")

        } catch (e: CancellationException) {
            Log.w(TAG, "EPG Parsing was cancelled (Safe abort).")
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "Critical Error parsing EPG", e)
        } finally {
            try { inputStream?.close() } catch (_: Exception) {}
            conn?.disconnect()
        }
    }

    private fun parseTime(timeStr: String?): Long {
        if (timeStr.isNullOrBlank()) return 0L
        return try {
            dateFormat.get()!!.parse(timeStr.trim())?.time ?: 0L
        } catch (e: Exception) {
            try {
                fallbackDateFormat.get()!!.parse(timeStr.trim().take(14))?.time ?: 0L
            } catch (e2: Exception) {
                0L
            }
        }
    }
}