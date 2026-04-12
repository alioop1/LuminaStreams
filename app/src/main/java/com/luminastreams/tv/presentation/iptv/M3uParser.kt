package com.luminastreams.tv.presentation.iptv

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

object M3uParser {

    suspend fun parse(url: String): Result<List<IptvChannel>> = withContext(Dispatchers.IO) {
        try {
            val content = fetchContent(url)
            val channels = parseM3u(content)
            Result.success(channels)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchContent(urlString: String): String {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < 10) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
                setRequestProperty("Accept", "*/*")
                instanceFollowRedirects = false
            }

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (!newUrl.isNullOrBlank()) {
                    currentUrl = if (newUrl.startsWith("http")) newUrl
                    else URL(URL(currentUrl), newUrl).toString()
                    redirects++
                    continue
                }
            }

            if (responseCode !in 200..299) {
                conn.disconnect()
                throw Exception("HTTP Error: $responseCode")
            }

            return try {
                val charset = detectCharset(conn) ?: Charsets.UTF_8
                BufferedReader(InputStreamReader(conn.inputStream, charset)).use { it.readText() }
            } finally {
                conn.disconnect()
            }
        }
        throw Exception("Too many redirects")
    }

    private fun detectCharset(conn: HttpURLConnection): Charset? {
        val ct = conn.contentType ?: return null
        return ct.split(";").map { it.trim() }
            .firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")
            ?.let { try { Charset.forName(it) } catch (_: Exception) { null } }
    }

    private fun parseM3u(content: String): List<IptvChannel> {
        if (!content.trimStart().startsWith("#EXTM3U", ignoreCase = true)) {
            // Try to parse as plain URL list
            return parsePlainUrls(content)
        }

        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines()
        var channelNumber = 1

        // Full attr regex - handles quoted and unquoted values
        val attrRegex = Regex("""([a-zA-Z0-9_\-]+)=(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            when {
                line.startsWith("#EXTINF:", ignoreCase = true) -> {
                    val attrs = mutableMapOf<String, String>()

                    // Parse all attributes
                    attrRegex.findAll(line).forEach { match ->
                        val key = match.groupValues[1].lowercase()
                        // Value is in group 2 (double-quoted), 3 (single-quoted), or 4 (unquoted)
                        val value = match.groupValues[2].ifEmpty {
                            match.groupValues[3].ifEmpty { match.groupValues[4] }
                        }
                        attrs[key] = value.trim()
                    }

                    // Channel name: everything after the last comma
                    val commaIdx = line.lastIndexOf(',')
                    val name = if (commaIdx >= 0 && commaIdx < line.length - 1)
                        line.substring(commaIdx + 1).trim()
                    else attrs["tvg-name"] ?: "Channel $channelNumber"

                    if (name.isBlank()) { i++; continue }

                    // Determine group
                    var group = attrs["group-title"] ?: ""
                    // Look ahead for #EXTGRP
                    if (group.isEmpty()) {
                        for (j in i + 1 until minOf(i + 4, lines.size)) {
                            val peek = lines[j].trim()
                            when {
                                peek.startsWith("#EXTGRP:", ignoreCase = true) ->
                                    group = peek.substringAfter(":").trim()
                                peek.isNotEmpty() && !peek.startsWith("#") -> break
                            }
                        }
                    }

                    // Find stream URL (next non-comment, non-empty line)
                    var streamUrl = ""
                    var j = i + 1
                    while (j < lines.size) {
                        val nextLine = lines[j].trim()
                        when {
                            nextLine.isEmpty() -> j++
                            nextLine.startsWith("#EXTGRP:", ignoreCase = true) -> j++
                            nextLine.startsWith("#") -> j++ // skip other directives
                            else -> { streamUrl = nextLine; break }
                        }
                    }

                    if (streamUrl.isNotEmpty()) {
                        val tvgId = attrs["tvg-id"] ?: ""
                        val tvgName = attrs["tvg-name"] ?: ""
                        val logo = attrs["tvg-logo"] ?: attrs["logo"] ?: ""
                        val catchupSrc = attrs["catchup-source"] ?: attrs["catchup"] ?: ""
                        val catchupDays = attrs["catchup-days"]?.toIntOrNull() ?: 0
                        @Suppress("unused", "UNUSED_VARIABLE")
                        val userAgent = attrs["user-agent"] ?: ""

                        // Resolution detection from name or attrs
                        val resolution = when {
                            name.contains("4K", true) || name.contains("UHD", true) -> "4K"
                            name.contains("FHD", true) || name.contains("1080", true) -> "FHD"
                            name.contains("HD", true) || name.contains("720", true) -> "HD"
                            else -> ""
                        }

                        // Adult content detection
                        val isAdult = group.contains("adult", true) ||
                                group.contains("18+", true) ||
                                group.contains("xxx", true) ||
                                group.contains("erotic", true)

                        // Country from tvg-id or name
                        val country = tvgId.substringAfterLast(".").uppercase().let {
                            if (it.length == 2) it else ""
                        }

                        val channelId = when {
                            tvgId.isNotEmpty() -> tvgId
                            tvgName.isNotEmpty() -> tvgName
                            else -> "${name}_$channelNumber"
                        }

                        channels.add(
                            IptvChannel(
                                id = channelId,
                                name = name,
                                logoUrl = logo,
                                streamUrl = streamUrl,
                                groupTitle = group.ifEmpty { "General" },
                                tvgId = tvgId,
                                tvgName = tvgName,
                                isAdult = isAdult,
                                number = channelNumber,
                                catchupSource = catchupSrc,
                                catchupDays = catchupDays,
                                hasArchive = catchupSrc.isNotEmpty() || catchupDays > 0,
                                resolution = resolution,
                                country = country,
                            )
                        )
                        channelNumber++
                    }
                }
            }
            i++
        }

        return channels.filter { !it.isAdult }
    }

    private fun parsePlainUrls(content: String): List<IptvChannel> {
        return content.lines()
            .filter { it.trim().startsWith("http", ignoreCase = true) }
            .mapIndexed { idx, url ->
                IptvChannel(
                    id = "ch_$idx",
                    name = "Channel ${idx + 1}",
                    logoUrl = "",
                    streamUrl = url.trim(),
                    groupTitle = "General",
                    number = idx + 1
                )
            }
    }
}