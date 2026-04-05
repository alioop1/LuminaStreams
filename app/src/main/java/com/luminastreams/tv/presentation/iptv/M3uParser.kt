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
            Result.success(parseM3u(content))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun fetchContent(urlString: String): String {
        var currentUrl = urlString
        var redirects = 0

        while (redirects < 5) {
            val conn = URL(currentUrl).openConnection() as HttpURLConnection
            conn.connectTimeout = 20_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
            conn.setRequestProperty("Accept", "*/*")
            conn.instanceFollowRedirects = false

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_MOVED_PERM || responseCode == HttpURLConnection.HTTP_MOVED_TEMP || responseCode == HttpURLConnection.HTTP_SEE_OTHER) {
                val newUrl = conn.getHeaderField("Location")
                conn.disconnect()
                if (newUrl != null) { currentUrl = newUrl; redirects++; continue }
            }

            if (responseCode !in 200..299) {
                conn.disconnect()
                throw Exception("HTTP Error: $responseCode")
            }

            return try {
                val charset = detectCharset(conn) ?: Charsets.UTF_8
                BufferedReader(InputStreamReader(conn.inputStream, charset)).use { it.readText() }
            } finally { conn.disconnect() }
        }
        throw Exception("Too many redirects")
    }

    private fun detectCharset(conn: HttpURLConnection): Charset? {
        val ct = conn.contentType ?: return null
        return ct.split(";").map { it.trim() }.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")?.let { try { Charset.forName(it) } catch (_: Exception) { null } }
    }

    private fun parseM3u(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines()
        var channelNumber = 1
        var globalGroup = "General"

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            // שומר קטגוריה גלובלית אם הספק משתמש ב- #EXTGRP שורה לפני הערוץ
            if (line.startsWith("#EXTGRP:")) {
                globalGroup = line.substringAfter(":", "").trim()
            }
            else if (line.startsWith("#EXTINF:")) {
                val attrs = parseAttributes(line)
                val name = extractChannelName(line)

                // תומך גם ב-group-title בתוך השורה וגם בשורת #EXTGRP מיד אחרי השורה (כמו OTTClub)
                var inlineGroup = attrs["group-title"]?.replace("\"", "")?.trim()
                if (inlineGroup.isNullOrBlank() && i + 1 < lines.size && lines[i + 1].trim().startsWith("#EXTGRP:")) {
                    inlineGroup = lines[i + 1].trim().substringAfter(":", "").trim()
                }

                val groupTitle = if (!inlineGroup.isNullOrBlank()) inlineGroup else globalGroup
                val streamUrl = findStreamUrl(lines, i + 1)

                if (streamUrl != null) {
                    val channelId = attrs["tvg-id"]?.ifBlank { null } ?: attrs["tvg-name"]?.ifBlank { null } ?: "${name}_$channelNumber"

                    channels.add(
                        IptvChannel(
                            id = channelId.trim(),
                            name = name.trim(),
                            logoUrl = attrs["tvg-logo"]?.trim() ?: "",
                            streamUrl = streamUrl.trim(),
                            groupTitle = groupTitle,
                            tvgId = attrs["tvg-id"]?.trim() ?: "",
                            tvgName = attrs["tvg-name"]?.trim() ?: name.trim(),
                            isAdult = groupTitle.contains("adult", true) || groupTitle.contains("18+", true) || groupTitle.contains("xxx", true),
                            number = channelNumber
                        )
                    )
                    channelNumber++
                }
            }
            i++
        }
        return channels.filter { !it.isAdult }
    }

    private fun parseAttributes(line: String): Map<String, String> {
        val attrs = mutableMapOf<String, String>()
        val regex = Regex("""([\w-]+)=["']?([^"',]*)["']?""")
        regex.findAll(line).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].trim()
            attrs[key] = value
        }
        return attrs
    }

    private fun extractChannelName(extinf: String): String {
        val commaIdx = extinf.lastIndexOf(',')
        return if (commaIdx >= 0 && commaIdx < extinf.length - 1) extinf.substring(commaIdx + 1).trim() else "Unknown Channel"
    }

    private fun findStreamUrl(lines: List<String>, startIdx: Int): String? {
        for (j in startIdx until minOf(startIdx + 3, lines.size)) {
            val l = lines[j].trim()
            if (l.isNotEmpty() && !l.startsWith("#") && (l.startsWith("http") || l.startsWith("rtmp") || l.startsWith("rtsp"))) return l
        }
        return null
    }
}