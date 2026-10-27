package com.luminastreams.tv.presentation.iptv

import android.util.Log
import com.luminastreams.tv.core.TrustedHttpClient
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.io.InputStreamReader
import java.util.concurrent.TimeUnit

object M3uParser {
    private const val TAG = "M3uParser"

    suspend fun parseStreaming(
        playlistId: String,
        url: String,
        batchSize: Int = 500,
        onBatchParsed: suspend (List<ChannelEntity>) -> Unit
    ) = withContext(Dispatchers.IO) {

        Log.d(TAG, "Starting M3U Download: $url")
        val client = TrustedHttpClient.builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()

        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            .build()

        val response = client.newCall(request).execute()

        try {
            if (!response.isSuccessful) throw Exception("HTTP Error: ${response.code}")

            val total = InputStreamReader(response.body.byteStream(), Charsets.UTF_8).useLines { lines ->
                parseLines(playlistId, lines, batchSize, onBatchParsed)
            }
            Log.d(TAG, "M3U Parsing completed. Total channels: $total")

        } finally {
            response.close()
        }
    }

    private val attrRegex = Regex("""([a-zA-Z0-9_\-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    /**
     * Pure M3U line-processing state machine \u2014 no I/O. Consumes lines in order and
     * emits [ChannelEntity] batches via [onBatchParsed]. Split out from the network
     * fetch so it can be unit-tested against real playlist snippets.
     */
    suspend fun parseLines(
        playlistId: String,
        lines: Sequence<String>,
        batchSize: Int = 500,
        onBatchParsed: suspend (List<ChannelEntity>) -> Unit
    ): Int {
        val batch = mutableListOf<ChannelEntity>()
        var currentInfo: Map<String, String>? = null
        var currentGroup: String? = null
        var channelNumber = 1
        var isFirstLine = true

        for (rawLine in lines) {
            val line = if (isFirstLine) {
                isFirstLine = false
                rawLine.removePrefix("\uFEFF").trim()
            } else {
                rawLine.trim()
            }
            if (line.isEmpty()) continue

            when {
                line.startsWith("#EXTINF:") -> {
                    try {
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
                    } catch (e: Exception) {
                        continue
                    }
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

        if (batch.isNotEmpty()) onBatchParsed(batch)
        return channelNumber - 1
    }
}