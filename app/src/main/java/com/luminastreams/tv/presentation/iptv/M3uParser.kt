package com.luminastreams.tv.presentation.iptv

import android.util.Log
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL

object M3uParser {
    private const val TAG = "M3uParser"

    suspend fun parseStreaming(
        playlistId: String,
        url: String,
        batchSize: Int = 500,
        onBatchParsed: suspend (List<ChannelEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting M3U Download: $url")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 20_000
            readTimeout = 60_000
            setRequestProperty("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
        }

        try {
            if (conn.responseCode !in 200..299) throw Exception("HTTP Error: ${conn.responseCode}")

            val batch = mutableListOf<ChannelEntity>()
            var currentInfo: Map<String, String>? = null
            var currentGroup: String? = null
            var channelNumber = 1

            val attrRegex = Regex("""([a-zA-Z0-9_\-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

            InputStreamReader(conn.inputStream, Charsets.UTF_8).useLines { lines ->
                lines.forEach { rawLine ->
                    val line = rawLine.trim()
                    if (line.isEmpty()) return@forEach

                    when {
                        line.startsWith("#EXTINF:") -> {
                            val attrs = mutableMapOf<String, String>()
                            attrRegex.findAll(line).forEach { match ->
                                val key = match.groupValues[1].lowercase()
                                val value = match.groupValues[2].ifEmpty {
                                    match.groupValues[3].ifEmpty { match.groupValues[4] }
                                }
                                attrs[key] = value.trim()
                            }
                            val name = line.substringAfterLast(',', attrs["tvg-name"] ?: "Channel $channelNumber").trim()
                            attrs["_extracted_name"] = name
                            currentInfo = attrs
                            if (attrs["group-title"] != null) currentGroup = attrs["group-title"]
                        }
                        line.startsWith("#EXTGRP:") -> {
                            currentGroup = line.substringAfter(":").trim()
                        }
                        !line.startsWith("#") -> {
                            currentInfo?.let { attrs ->
                                val group = attrs["group-title"] ?: currentGroup ?: "General"
                                val name = attrs["_extracted_name"] ?: "Channel $channelNumber"
                                val tvgId = attrs["tvg-id"] ?: ""
                                val tvgName = attrs["tvg-name"] ?: ""
                                val logoUrl = attrs["tvg-logo"] ?: attrs["logo"] ?: attrs["icon"] ?: ""

                                val channelId = if (tvgId.isNotEmpty()) tvgId else "${name.hashCode()}_$channelNumber"
                                val isAdult = group.contains("adult", true) || group.contains("xxx", true)

                                if (!isAdult) {
                                    batch.add(
                                        ChannelEntity(
                                            id = channelId,
                                            playlistId = playlistId,
                                            name = name,
                                            logoUrl = logoUrl,
                                            streamUrl = line,
                                            groupTitle = group.ifEmpty { "General" },
                                            tvgId = tvgId,
                                            tvgName = tvgName,
                                            isAdult = false,
                                            number = channelNumber,
                                            resolution = if (name.contains("4K", true)) "4K" else if (name.contains("HD", true)) "HD" else ""
                                        )
                                    )
                                    channelNumber++

                                    if (batch.size >= batchSize) {
                                        onBatchParsed(batch.toList())
                                        batch.clear()
                                    }
                                }
                                currentInfo = null
                            }
                        }
                    }
                }
            }

            if (batch.isNotEmpty()) onBatchParsed(batch)
            Log.d(TAG, "M3U Parsing completed. Total channels: ${channelNumber - 1}")

        } finally {
            conn.disconnect()
        }
    }
}