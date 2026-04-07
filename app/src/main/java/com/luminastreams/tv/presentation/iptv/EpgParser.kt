package com.luminastreams.tv.presentation.iptv

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.GZIPInputStream
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

object EpgParser {

    private const val TAG = "EPG_DEBUG"
    private const val MAX_PROGRAMS_PER_CHANNEL = 96
    // Max bytes to buffer for two-pass parsing (32 MB). Feeds larger than this
    // fall back to single-pass with broad token matching only.
    private const val TWO_PASS_LIMIT_BYTES = 32 * 1024 * 1024

    data class EpgResult(
        val programs: Map<String, List<EpgProgram>>,
        val channelLogos: Map<String, String>,
        val channelDisplayNames: Map<String, List<String>>
    )

    /**
     * Parse strategy:
     *
     * 1. Build [allowedTokens] — a flat HashSet of lowercase string tokens derived from
     *    every M3U channel's tvg-id, tvg-name, and display name.  For each token we also
     *    add prefix/infix variants (split on '.', '-', ' ') so that an EPG channel id
     *    like "CNN.us" matches a token set that contains "cnn".
     *
     * 2. If the raw feed bytes fit in TWO_PASS_LIMIT_BYTES:
     *    - Pass 1 (ChannelScanHandler): read only <channel> blocks, collecting each
     *      channel's id + display-names, and cross-reference against allowedTokens.
     *      Builds the exact set of EPG channel IDs we actually want (allowedEpgIds).
     *    - Pass 2 (XmlTvHandler): full parse, but <programme> tags whose channel
     *      attribute is NOT in allowedEpgIds are skipped immediately (O(1) HashSet).
     *
     * 3. For large feeds that exceed TWO_PASS_LIMIT_BYTES: single pass using the broader
     *    allowedTokens set for fuzzy matching — trades some over-collection for safety.
     *
     * Memory guarantee: at most (allowedChannels × MAX_PROGRAMS_PER_CHANNEL) EpgProgram
     * objects are ever alive at once.  For a 10 000-channel playlist that's ~960 000
     * objects ≈ 50 MB worst case.
     */
    suspend fun parse(url: String, allowedIds: Set<String>): Result<EpgResult> =
        withContext(Dispatchers.IO) {
            try {
                // Build fuzzy token set from the caller's allowed channel identifiers.
                val allowedTokens = buildAllowedTokens(allowedIds)
                Log.d(TAG, "EPG tokens built: ${allowedTokens.size} from ${allowedIds.size} channel ids")

                val factory = SAXParserFactory.newInstance().apply {
                    isNamespaceAware = false
                    isValidating    = false
                    safeFeature(this, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
                    safeFeature(this, "http://xml.org/sax/features/external-general-entities",           false)
                    safeFeature(this, "http://xml.org/sax/features/external-parameter-entities",         false)
                }

                Log.d(TAG, "Downloading EPG from $url")
                val rawBytes = fetchEpgBytes(url)
                Log.d(TAG, "EPG downloaded: ${rawBytes.size} bytes")

                // ---------- resolve the exact set of EPG channel IDs ----------
                val allowedEpgIds: Set<String> = if (rawBytes.size <= TWO_PASS_LIMIT_BYTES) {
                    // Pass 1 — scan <channel> blocks only
                    val scanHandler = ChannelScanHandler(allowedTokens)
                    factory.newSAXParser().parse(ByteArrayInputStream(rawBytes), scanHandler)
                    Log.d(TAG, "Pass-1 matched ${scanHandler.matchedIds.size} EPG channel ids")
                    scanHandler.matchedIds
                } else {
                    // Feed too large to buffer twice — use token matching directly in pass 2
                    Log.d(TAG, "Feed > ${TWO_PASS_LIMIT_BYTES / 1024 / 1024}MB, using single-pass token filter")
                    emptySet() // signal to XmlTvHandler to use token matching
                }

                // ---------- Pass 2 — full programme parse ----------
                val handler = XmlTvHandler(
                    allowedEpgIds  = allowedEpgIds,
                    allowedTokens  = allowedTokens,
                    useFuzzyFallback = allowedEpgIds.isEmpty()
                )
                factory.newSAXParser().parse(ByteArrayInputStream(rawBytes), handler)
                Log.d(TAG, "EPG pass-2: ${handler.channelsMap.size} channels, ${handler.programCount} programmes")

                // ---------- build final maps ----------
                val finalPrograms = LinkedHashMap<String, List<EpgProgram>>(handler.channelsMap.size * 2)
                val finalLogos    = LinkedHashMap<String, String>(handler.channelLogos.size * 2)

                for ((id, deque) in handler.channelsMap) {
                    val sorted  = deque.sortedBy { it.startTime }
                    val idLower = id.lowercase()
                    finalPrograms[idLower] = sorted
                    handler.displayNames[id]?.forEach { dn ->
                        if (dn.isNotEmpty()) finalPrograms[dn.lowercase()] = sorted
                    }
                    val logo = handler.channelLogos[id] ?: handler.channelLogos[idLower]
                    if (!logo.isNullOrBlank()) {
                        finalLogos[idLower] = logo
                        handler.displayNames[id]?.forEach { dn ->
                            if (dn.isNotEmpty()) finalLogos[dn.lowercase()] = logo
                        }
                    }
                }

                Log.d(TAG, "EPG result: ${finalPrograms.size} programme keys, ${finalLogos.size} logo keys")
                Result.success(
                    EpgResult(
                        programs             = finalPrograms,
                        channelLogos         = finalLogos,
                        channelDisplayNames  = handler.displayNames.mapKeys { it.key.lowercase() }
                    )
                )
            } catch (e: Exception) {
                Log.e(TAG, "EPG parse error: ${e.message}", e)
                Result.failure(e)
            }
        }

    // -------------------------------------------------------------------------
    // Token builder
    // -------------------------------------------------------------------------

    /**
     * For each raw id in [allowedIds] (already lowercase) we emit:
     *  - the full id               e.g. "bbc one hd"
     *  - each dot/hyphen/space/underscore segment  e.g. "bbc", "one", "hd"
     *  - the full id with dots/hyphens/spaces removed  e.g. "bbconehd"
     *
     * This means an EPG channel id of "BBCOneHD.uk" will be lowercased to
     * "bbconehd.uk", segmented to ["bbconehd", "uk"], and the squashed variant
     * "bbconehd" will match the token we derived from the M3U name "BBC One HD".
     */
    private fun buildAllowedTokens(allowedIds: Set<String>): HashSet<String> {
        val tokens = HashSet<String>(allowedIds.size * 6)
        for (id in allowedIds) {
            if (id.isBlank()) continue
            val lo = id.lowercase()
            tokens.add(lo)
            // segments split on common delimiters
            lo.split('.', '-', '_', ' ').forEach { seg ->
                if (seg.length >= 2) tokens.add(seg)
            }
            // squashed (no delimiters)
            val squashed = lo.filter { it.isLetterOrDigit() }
            if (squashed.length >= 3) tokens.add(squashed)
        }
        return tokens
    }

    /**
     * Returns true if the EPG channel id [epgId] matches any allowed token using
     * the same segmentation logic applied in [buildAllowedTokens].
     */
    private fun epgIdMatchesTokens(epgId: String, tokens: HashSet<String>): Boolean {
        val lo = epgId.lowercase()
        if (lo in tokens) return true
        // check segments
        lo.split('.', '-', '_', ' ').forEach { seg ->
            if (seg.length >= 2 && seg in tokens) return true
        }
        // check squashed
        val squashed = lo.filter { it.isLetterOrDigit() }
        return squashed.length >= 3 && squashed in tokens
    }

    // -------------------------------------------------------------------------
    // Network: download full feed to byte array (bounded by server response)
    // -------------------------------------------------------------------------

    private fun fetchEpgBytes(urlString: String): ByteArray {
        var currentUrl = urlString
        var redirects  = 0
        while (redirects < 10) {
            val conn = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout    = 120_000
                setRequestProperty("User-Agent",       "Mozilla/5.0 (Android TV) Chrome/112.0.0.0")
                setRequestProperty("Accept-Encoding", "gzip, deflate")
                instanceFollowRedirects = false
            }
            val code = conn.responseCode
            if (code in 300..399) {
                val loc = conn.getHeaderField("Location")
                conn.disconnect()
                if (!loc.isNullOrBlank()) {
                    currentUrl = if (loc.startsWith("http")) loc
                    else URL(URL(currentUrl), loc).toString()
                    redirects++
                    continue
                }
            }
            if (code !in 200..299) {
                conn.disconnect()
                throw Exception("HTTP $code for $currentUrl")
            }
            val enc      = conn.contentEncoding ?: ""
            val ct       = conn.contentType     ?: ""
            val lowerUrl = currentUrl.lowercase()
            val stream: InputStream = when {
                enc.contains("gzip", true) || lowerUrl.endsWith(".gz") ->
                    GZIPInputStream(conn.inputStream.buffered(131_072))
                lowerUrl.endsWith(".zip") || ct.contains("zip", true) -> {
                    val zis   = ZipInputStream(conn.inputStream)
                    var entry = zis.nextEntry
                    while (entry != null) {
                        if (entry.name.endsWith(".xml", true) ||
                            entry.name.endsWith(".xmltv", true)) break
                        entry = zis.nextEntry
                    }
                    if (entry == null) throw Exception("No XML in ZIP")
                    zis
                }
                else -> conn.inputStream.buffered(131_072)
            }
            return stream.use { it.readBytes() }
        }
        throw Exception("Too many redirects")
    }

    // -------------------------------------------------------------------------
    // Pass 1: lightweight channel-id scanner
    // -------------------------------------------------------------------------

    /**
     * Only reads <channel id="..."> blocks and their <display-name> children.
     * Cross-references each channel with [allowedTokens] and collects the raw
     * EPG channel id strings that we actually want programmes for.
     */
    private class ChannelScanHandler(private val allowedTokens: HashSet<String>) : DefaultHandler() {
        val matchedIds = HashSet<String>(256)

        private var inChannel  = false
        private var currentId  = ""
        private val displayNamesForCurrent = mutableListOf<String>()
        private val sb = StringBuilder(128)

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            when (qName.lowercase()) {
                "channel" -> {
                    currentId = attrs.getValue("id") ?: ""
                    inChannel = currentId.isNotBlank()
                    displayNamesForCurrent.clear()
                    sb.clear()
                }
                "display-name" -> if (inChannel) sb.clear()
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inChannel) sb.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            when (qName.lowercase()) {
                "display-name" -> {
                    if (inChannel) {
                        val dn = sb.toString().trim()
                        if (dn.isNotEmpty()) displayNamesForCurrent.add(dn)
                        sb.clear()
                    }
                }
                "channel" -> {
                    if (inChannel) {
                        // Check raw id + every display-name against the allowed-token set
                        val candidates = buildList {
                            add(currentId)
                            addAll(displayNamesForCurrent)
                        }
                        val matched = candidates.any { candidate ->
                            epgIdMatchesTokens(candidate, allowedTokens)
                        }
                        if (matched) matchedIds.add(currentId)
                    }
                    inChannel = false
                    currentId = ""
                    displayNamesForCurrent.clear()
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Pass 2: full programme parser with tight channel filter
    // -------------------------------------------------------------------------

    private class XmlTvHandler(
        private val allowedEpgIds:    Set<String>,
        private val allowedTokens:    HashSet<String>,
        private val useFuzzyFallback: Boolean
    ) : DefaultHandler() {

        val channelsMap  = LinkedHashMap<String, ArrayDeque<EpgProgram>>(512)
        val displayNames = LinkedHashMap<String, MutableList<String>>(512)
        val channelLogos = LinkedHashMap<String, String>(512)
        var programCount = 0

        private val cutoffTime  = System.currentTimeMillis() - 3_600_000L      // 1 h ago
        private val futureLimit = System.currentTimeMillis() + 14L * 86_400_000 // +14 days

        private var inChannel       = false
        private var inProgramme     = false
        private var skipProgramme   = false
        private var currentChannelId         = ""
        private var currentProgramChannelId  = ""
        private var currentStart = 0L
        private var currentStop  = 0L

        private val sb          = StringBuilder(512)
        private val programData = HashMap<String, String>(8)

        // ---- time parser (same fast implementation, no Calendar allocation on skip) ----
        private fun parseTimeFast(s: String): Long {
            var len = s.length
            while (len > 0 && s[len - 1] <= ' ') len--
            var st = 0
            while (st < len && s[st] <= ' ') st++
            if (len - st < 14) return 0L
            return try {
                var y=0; var mo=0; var d=0; var h=0; var m=0; var sec=0
                for (i in 0..3)   y   = y   * 10 + (s[st+i]-'0')
                for (i in 4..5)   mo  = mo  * 10 + (s[st+i]-'0')
                for (i in 6..7)   d   = d   * 10 + (s[st+i]-'0')
                for (i in 8..9)   h   = h   * 10 + (s[st+i]-'0')
                for (i in 10..11) m   = m   * 10 + (s[st+i]-'0')
                for (i in 12..13) sec = sec * 10 + (s[st+i]-'0')
                val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                cal.set(y, mo-1, d, h, m, sec)
                cal.set(Calendar.MILLISECOND, 0)
                var offset = 0
                if (len - st >= 20 && s[st+14] == ' ') {
                    val sign = if (s[st+15] == '-') -1 else 1
                    val oh   = (s[st+16]-'0')*10 + (s[st+17]-'0')
                    val om   = (s[st+18]-'0')*10 + (s[st+19]-'0')
                    offset   = sign * (oh*60 + om) * 60_000
                }
                cal.timeInMillis - offset
            } catch (_: Exception) { 0L }
        }

        private fun isChannelAllowed(id: String): Boolean {
            val lo = id.lowercase()
            return if (useFuzzyFallback) epgIdMatchesTokens(lo, allowedTokens)
                   else lo in allowedEpgIds
        }

        override fun startElement(uri: String, localName: String, qName: String, attrs: Attributes) {
            val tag = qName.lowercase()
            sb.clear()
            when (tag) {
                "channel" -> {
                    val id = attrs.getValue("id") ?: ""
                    if (id.isNotBlank() && isChannelAllowed(id)) {
                        inChannel        = true
                        inProgramme      = false
                        skipProgramme    = false
                        currentChannelId = id
                    }
                }
                "programme" -> {
                    val ch = attrs.getValue("channel") ?: ""
                    if (ch.isNotBlank()) {
                        inProgramme = true
                        inChannel   = false
                        if (!isChannelAllowed(ch)) {
                            skipProgramme = true
                            return
                        }
                        currentStart = parseTimeFast(attrs.getValue("start") ?: "")
                        currentStop  = parseTimeFast(attrs.getValue("stop")  ?: "")
                        skipProgramme = currentStop < cutoffTime || currentStart > futureLimit
                        if (!skipProgramme) {
                            currentProgramChannelId = ch
                            programData.clear()
                        }
                    }
                }
                "icon" -> {
                    val src = attrs.getValue("src") ?: ""
                    if (src.isNotBlank()) {
                        when {
                            inChannel && currentChannelId.isNotBlank() -> {
                                channelLogos[currentChannelId]           = src
                                channelLogos[currentChannelId.lowercase()] = src
                            }
                            inProgramme && !skipProgramme -> programData["icon"] = src
                        }
                    }
                }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (inChannel || (inProgramme && !skipProgramme))
                sb.appendRange(ch, start, start + length)
        }

        override fun endElement(uri: String, localName: String, qName: String) {
            val tag = qName.lowercase()
            when {
                tag == "channel" -> {
                    inChannel        = false
                    currentChannelId = ""
                }
                tag == "programme" -> {
                    if (!skipProgramme && currentStart > 0) {
                        val title = programData["title"]
                        if (!title.isNullOrBlank()) {
                            val prog = EpgProgram(
                                channelId   = currentProgramChannelId,
                                title       = title,
                                description = programData["desc"]       ?: "",
                                startTime   = currentStart,
                                endTime     = currentStop,
                                category    = programData["category"]   ?: "",
                                posterUrl   = programData["icon"]       ?: "",
                                episodeNum  = programData["episodeNum"] ?: "",
                                rating      = programData["rating"]     ?: ""
                            )
                            val deque = channelsMap.getOrPut(currentProgramChannelId) { ArrayDeque(MAX_PROGRAMS_PER_CHANNEL + 1) }
                            if (deque.size >= MAX_PROGRAMS_PER_CHANNEL) deque.removeFirst()
                            deque.addLast(prog)
                            programCount++
                        }
                    }
                    inProgramme             = false
                    skipProgramme           = false
                    currentProgramChannelId = ""
                    currentStart            = 0L
                    currentStop             = 0L
                    programData.clear()
                }
                inChannel -> {
                    val text = sb.toString().trim()
                    if (tag == "display-name" && text.isNotEmpty())
                        displayNames.getOrPut(currentChannelId) { mutableListOf() }.add(text)
                }
                inProgramme && !skipProgramme -> {
                    val text = sb.toString().trim()
                    when (tag) {
                        "title"       -> if (!programData.containsKey("title")       && text.isNotEmpty()) programData["title"]      = text
                        "desc"        -> if (!programData.containsKey("desc")        && text.isNotEmpty()) programData["desc"]       = text
                        "category"    -> if (!programData.containsKey("category")    && text.isNotEmpty()) programData["category"]   = text
                        "episode-num" -> if (!programData.containsKey("episodeNum")  && text.isNotEmpty()) programData["episodeNum"] = text
                        "value"       -> if (!programData.containsKey("rating")      && text.isNotEmpty()) programData["rating"]     = text
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Helpers
    // -------------------------------------------------------------------------

    private fun safeFeature(f: SAXParserFactory, name: String, value: Boolean) {
        try { f.setFeature(name, value) } catch (_: Exception) {}
    }

    // Make epgIdMatchesTokens accessible to ChannelScanHandler (same file)
    private fun epgIdMatchesTokens(epgId: String, tokens: HashSet<String>): Boolean {
        val lo = epgId.lowercase()
        if (lo in tokens) return true
        lo.split('.', '-', '_', ' ').forEach { seg ->
            if (seg.length >= 2 && seg in tokens) return true
        }
        val squashed = lo.filter { it.isLetterOrDigit() }
        return squashed.length >= 3 && squashed in tokens
    }
}
