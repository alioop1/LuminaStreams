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
            conn.setRequestProperty("User-Agent", "VLC/3.0.18 LibVLC/3.0.18")
            conn.setRequestProperty("Accept", "*/*")
            conn.instanceFollowRedirects = false

            val responseCode = conn.responseCode
            if (responseCode in 300..399) {
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
        var currentGroup = "General"

        // לוכד ערכים גם אם יש להם מרכאות וגם אם לא
        val attrRegex = Regex("""([a-zA-Z0-9_-]+)=(["']?)(.*?)\2(?=\s+[a-zA-Z0-9_-]+=|$)""")

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTGRP:")) {
                currentGroup = line.substringAfter(":").trim()
            } else if (line.startsWith("#EXTINF:")) {
                val attrs = mutableMapOf<String, String>()
                attrRegex.findAll(line).forEach { match ->
                    attrs[match.groupValues[1].lowercase()] = match.groupValues[3].trim()
                }

                // שם הערוץ נמצא אחרי הפסיק הראשון
                val name = line.substringAfter(",", "Unknown Channel").trim()

                // זיהוי קטגוריה גם בשורה עצמה וגם בשורה מתחת
                var group = attrs["group-title"] ?: ""
                if (group.isEmpty() && i + 1 < lines.size && lines[i + 1].trim().startsWith("#EXTGRP:")) {
                    group = lines[i + 1].trim().substringAfter(":").trim()
                }
                if (group.isEmpty()) group = currentGroup

                // חיפוש לינק שידור בשורות הבאות
                var streamUrl = ""
                for (j in i + 1 until minOf(i + 5, lines.size)) {
                    val nextLine = lines[j].trim()
                    if (nextLine.isNotEmpty() && !nextLine.startsWith("#")) {
                        streamUrl = nextLine
                        break
                    }
                }

                if (streamUrl.isNotEmpty()) {
                    val tvgId = attrs["tvg-id"] ?: ""
                    val tvgName = attrs["tvg-name"] ?: ""
                    val logoUrl = attrs["tvg-logo"] ?: attrs["logo"] ?: ""
                    val channelId = tvgId.ifEmpty { tvgName.ifEmpty { streamUrl } }

                    channels.add(
                        IptvChannel(
                            id = channelId,
                            name = name,
                            logoUrl = logoUrl,
                            streamUrl = streamUrl,
                            groupTitle = group.ifEmpty { "General" },
                            tvgId = tvgId,
                            tvgName = tvgName,
                            isAdult = group.contains("adult", true) || group.contains("18+", true) || group.contains("xxx", true),
                            number = channels.size + 1
                        )
                    )
                }
            }
            i++
        }
        return channels.filter { !it.isAdult }
    }
}