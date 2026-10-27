package com.luminastreams.tv.presentation.details

/**
 * Turns one raw Torrentio `stream` entry into an [AdvancedStreamSource].
 *
 * Torrentio packs size / seeders / tracker into the `title` field as emoji-prefixed
 * fragments (e.g. "Movie.2024.2160p\n👤 42 💾 8.3 GB ⚙️ RuTracker"). That format is
 * fragile and changes upstream from time to time, so the parsing lives here in one
 * isolated, unit-tested place instead of inline in the ViewModel.
 */
object TorrentioStreamParser {

    private val SIZE_RE     = Regex("([0-9.]+)\\s*(GB|MB)")
    private val TRACKER_RE  = Regex("⚙️\\s*([^\\n]+)")
    private val SEEDERS_RE  = Regex("👤\\s*(\\d+)")
    private val LEECHERS_RE = Regex("🌐\\s*(\\d+)")

    fun parseSizeBytes(title: String): Long {
        val m = SIZE_RE.find(title.uppercase()) ?: return 0L
        val v = m.groupValues[1].toDoubleOrNull() ?: 0.0
        return if (m.groupValues[2] == "GB") (v * 1_073_741_824).toLong() else (v * 1_048_576).toLong()
    }

    fun parseSeeders(title: String): Int = SEEDERS_RE.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    fun parseLeechers(title: String): Int = LEECHERS_RE.find(title)?.groupValues?.get(1)?.toIntOrNull() ?: 0

    /** Tracker from the "⚙️ <name>" fragment, falling back to [TrackerParser.extractTracker] on the name field. */
    fun parseTracker(title: String, name: String): String =
        TRACKER_RE.find(title)?.groupValues?.get(1)?.trim() ?: TrackerParser.extractTracker(name)

    /**
     * @return null when the entry is unplayable — it has neither a magnet hash nor a
     * direct URL, so nothing (not AllDebrid, not ExoPlayer) can resolve it.
     */
    fun parse(index: Int, name: String?, title: String?, url: String?, infoHash: String?): AdvancedStreamSource? {
        val titleSafe = title ?: return null
        val nameSafe = name ?: "Unknown"
        val hasInfoHash = !infoHash.isNullOrBlank()
        val hasDirectUrl = !url.isNullOrBlank()
        if (!hasInfoHash && !hasDirectUrl) return null

        val upper = titleSafe.uppercase()
        return AdvancedStreamSource(
            id = "str_$index",
            releaseGroup = nameSafe.replace("\n", " "),
            filename = titleSafe.substringBefore("\n"),
            infoHash = infoHash,
            directUrl = url,
            sizeBytes = parseSizeBytes(titleSafe),
            isCachedRd = nameSafe.contains("AD+"),
            quality = StreamQuality.fromString(upper),
            videoCodec = VideoCodec.fromString(upper),
            rawTitle = titleSafe,
            trackerName = parseTracker(titleSafe, nameSafe),
            audioLanguages = AudioLanguage.detectFromTitle("$titleSafe $nameSafe"),
            seeders = parseSeeders(titleSafe),
            leechers = parseLeechers(titleSafe)
        )
    }
}
