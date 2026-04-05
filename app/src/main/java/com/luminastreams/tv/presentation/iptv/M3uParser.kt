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
        val conn = URL(urlString).openConnection() as HttpURLConnection
        conn.connectTimeout = 20_000
        conn.readTimeout = 30_000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0 (Android TV)")
        conn.setRequestProperty("Accept", "*/*")
        return try {
            val charset = detectCharset(conn) ?: Charsets.UTF_8
            BufferedReader(InputStreamReader(conn.inputStream, charset)).use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun detectCharset(conn: HttpURLConnection): Charset? {
        val ct = conn.contentType ?: return null
        return ct.split(";").map { it.trim() }.firstOrNull { it.startsWith("charset=", ignoreCase = true) }
            ?.substringAfter("=")?.let {
                try { Charset.forName(it) } catch (_: Exception) { null }
            }
    }

    private fun parseM3u(content: String): List<IptvChannel> {
        val channels = mutableListOf<IptvChannel>()
        val lines = content.lines()
        var channelNumber = 1

        var i = 0
        while (i < lines.size) {
            val line = lines[i].trim()

            if (line.startsWith("#EXTINF:")) {
                val attrs = parseAttributes(line)
                val name = extractChannelName(line)
                val streamUrl = findStreamUrl(lines, i + 1)

                if (streamUrl != null) {
                    val channelId = attrs["tvg-id"]?.ifBlank { null }
                        ?: attrs["tvg-name"]?.ifBlank { null }
                        ?: "${name}_$channelNumber"

                    channels.add(
                        IptvChannel(
                            id = channelId.trim(),
                            name = name.trim(),
                            logoUrl = attrs["tvg-logo"]?.trim() ?: "",
                            streamUrl = streamUrl.trim(),
                            groupTitle = attrs["group-title"]?.trim() ?: "General",
                            tvgId = attrs["tvg-id"]?.trim() ?: "",
                            tvgName = attrs["tvg-name"]?.trim() ?: name.trim(),
                            isAdult = (attrs["group-title"] ?: "").contains("adult", ignoreCase = true) ||
                                    (attrs["group-title"] ?: "").contains("18+", ignoreCase = true) ||
                                    (attrs["group-title"] ?: "").contains("xxx", ignoreCase = true),
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
        // Match key="value" or key=value patterns
        val regex = Regex("""([\w-]+)=["']?([^"',\s]*)["']?""")
        regex.findAll(line).forEach { match ->
            val key = match.groupValues[1].lowercase()
            val value = match.groupValues[2].let {
                // Also handle quoted with spaces by trying quoted version
                val quotedRegex = Regex("""${Regex.escape(match.groupValues[1])}=["']([^"']*)["']""")
                quotedRegex.find(line)?.groupValues?.get(1) ?: it
            }
            attrs[key] = value
        }
        return attrs
    }

    private fun extractChannelName(extinf: String): String {
        // Name is after the last comma
        val commaIdx = extinf.lastIndexOf(',')
        return if (commaIdx >= 0 && commaIdx < extinf.length - 1) {
            extinf.substring(commaIdx + 1).trim()
        } else {
            "Unknown Channel"
        }
    }

    private fun findStreamUrl(lines: List<String>, startIdx: Int): String? {
        for (j in startIdx until minOf(startIdx + 3, lines.size)) {
            val l = lines[j].trim()
            if (l.isNotEmpty() && !l.startsWith("#")) {
                if (l.startsWith("http") || l.startsWith("rtmp") || l.startsWith("rtsp")) {
                    return l
                }
            }
        }
        return null
    }
}
