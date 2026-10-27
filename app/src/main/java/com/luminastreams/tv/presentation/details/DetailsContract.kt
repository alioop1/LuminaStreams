package com.luminastreams.tv.presentation.details

import androidx.compose.runtime.Immutable

@Immutable data class CastMember(val id: String, val name: String, val character: String, val imageUrl: String)
@Immutable data class Episode(val id: String, val episodeNumber: Int, val seasonNumber: Int, val title: String, val overview: String, val stillUrl: String, val hasWatched: Boolean = false, val progress: Float = 0f)
@Immutable data class Recommendation(val id: String, val title: String, val posterUrl: String)

enum class StreamQuality(val priority: Int) {
    UHD_4K(9), FHD_1080P(7), HD_720P(6), SD_480P(5), UNKNOWN(0);
    companion object {
        fun fromString(title: String): StreamQuality {
            val t = title.uppercase()
            return when {
                t.contains("4K") || t.contains("2160P") -> UHD_4K
                t.contains("1080P") -> FHD_1080P
                t.contains("720P")  -> HD_720P
                t.contains("480P")  -> SD_480P
                else                -> UNKNOWN
            }
        }
    }
}

enum class VideoCodec {
    HEVC, AVC, AV1, UNKNOWN;
    companion object {
        fun fromString(title: String): VideoCodec {
            val t = title.uppercase()
            return when {
                t.contains("HEVC") || t.contains("X265") || t.contains("H265") -> HEVC
                t.contains("AVC")  || t.contains("X264") || t.contains("H264") -> AVC
                t.contains("AV1")  -> AV1
                else               -> UNKNOWN
            }
        }
    }
}

// ── Audio Language Detection ──────────────────────────────────────────────
enum class AudioLanguage(val displayName: String, val flag: String) {
    RUSSIAN("RUS", "🇷🇺"),
    ENGLISH("ENG", "🇬🇧"),
    HEBREW("HEB", "🇮🇱"),
    UKRAINIAN("UKR", "🇺🇦"),
    SPANISH("SPA", "🇪🇸"),
    FRENCH("FRE", "🇫🇷"),
    GERMAN("GER", "🇩🇪"),
    ITALIAN("ITA", "🇮🇹"),
    JAPANESE("JPN", "🇯🇵"),
    KOREAN("KOR", "🇰🇷"),
    ARABIC("ARA", "🇸🇦"),
    TURKISH("TUR", "🇹🇷"),
    HINDI("HIN", "🇮🇳"),
    PORTUGUESE("POR", "🇧🇷"),
    MULTI("Multi", "🌐"),
    UNKNOWN("", "");

    companion object {
        // Pre-compiled patterns to avoid re-allocation
        private val RUS_WORD = Regex("\\bRUS\\b")
        private val UKR_WORD = Regex("\\bUKR\\b")
        private val ENG_WORD = Regex("\\bENG\\b")
        private val HEB_WORD = Regex("\\bHEB\\b")
        private val SPA_WORD = Regex("\\bSPA\\b")
        private val FRE_WORD = Regex("\\bFRE\\b|\\bFRA\\b")
        private val GER_WORD = Regex("\\bGER\\b|\\bDEU\\b")
        private val ITA_WORD = Regex("\\bITA\\b")
        private val JPN_WORD = Regex("\\bJPN\\b|\\bJAP\\b")
        private val KOR_WORD = Regex("\\bKOR\\b")
        private val ARA_WORD = Regex("\\bARA\\b")
        private val TUR_WORD = Regex("\\bTUR\\b")
        private val HIN_WORD = Regex("\\bHIN\\b")
        private val POR_WORD = Regex("\\bPOR\\b")
        private val CYRILLIC_BLOCK = Regex("[А-ЯЁ]{4,}")

        fun detectFromTitle(title: String): List<AudioLanguage> {
            val t = title.uppercase()
            val langs = mutableListOf<AudioLanguage>()
            // Russian indicators
            if (t.contains("RUSSIAN") || RUS_WORD.containsMatchIn(t)
                || t.contains("ДУБЛИРОВАННЫЙ") || t.contains("ДУБЛЯЖ") || t.contains("ЛИЦЕНЗИЯ")
                || t.contains("ПРОФ") || t.contains("ОЗВУЧКА") || t.contains("ПЕРЕВОД")
                || t.contains("P.DUB") || t.contains("DVO") || t.contains("AVO")
                || CYRILLIC_BLOCK.containsMatchIn(t))
                langs.add(RUSSIAN)
            // Ukrainian
            if (t.contains("UKRAINIAN") || UKR_WORD.containsMatchIn(t))
                langs.add(UKRAINIAN)
            // English
            if (t.contains("ENGLISH") || ENG_WORD.containsMatchIn(t))
                langs.add(ENGLISH)
            // Hebrew
            if (t.contains("HEBREW") || HEB_WORD.containsMatchIn(t)
                || t.contains("HEBDUB") || t.contains("HEB DUB"))
                langs.add(HEBREW)
            // Spanish
            if (t.contains("SPANISH") || t.contains("LATINO") || SPA_WORD.containsMatchIn(t))
                langs.add(SPANISH)
            // French
            if (t.contains("FRENCH") || t.contains("TRUEFRENCH") || FRE_WORD.containsMatchIn(t))
                langs.add(FRENCH)
            // German
            if (t.contains("GERMAN") || GER_WORD.containsMatchIn(t))
                langs.add(GERMAN)
            // Italian
            if (t.contains("ITALIAN") || ITA_WORD.containsMatchIn(t))
                langs.add(ITALIAN)
            // Japanese
            if (t.contains("JAPANESE") || JPN_WORD.containsMatchIn(t))
                langs.add(JAPANESE)
            // Korean
            if (t.contains("KOREAN") || KOR_WORD.containsMatchIn(t))
                langs.add(KOREAN)
            // Arabic
            if (t.contains("ARABIC") || ARA_WORD.containsMatchIn(t))
                langs.add(ARABIC)
            // Turkish
            if (t.contains("TURKISH") || TUR_WORD.containsMatchIn(t))
                langs.add(TURKISH)
            // Hindi
            if (t.contains("HINDI") || HIN_WORD.containsMatchIn(t))
                langs.add(HINDI)
            // Portuguese
            if (t.contains("PORTUGUESE") || POR_WORD.containsMatchIn(t))
                langs.add(PORTUGUESE)
            // Multi / Dual audio
            if (t.contains("MULTI") || t.contains("DUAL AUDIO") || t.contains("DUAL.AUDIO"))
                langs.add(MULTI)
            // Default to English if nothing else detected (most Torrentio results are English)
            return langs.distinct().ifEmpty { listOf(ENGLISH) }
        }
    }
}

// ── Tracker Name Parsing ──────────────────────────────────────────────────
object TrackerParser {
    /** Extract tracker name from Torrentio stream `name` field.
     *  Typical formats: "Torrentio\n[RuTracker]", "[YTS] Torrentio", "Torrentio\nRuTor" */
    fun extractTracker(rawName: String): String {
        // Try bracket format first: [TrackerName]
        val bracket = Regex("\\[([^]]+)]").find(rawName)
        if (bracket != null) return bracket.groupValues[1].trim()
        // Try second line (after newline)
        val lines = rawName.split("\n").map { it.trim() }.filter { it.isNotBlank() }
        if (lines.size >= 2) {
            val second = lines[1]
            // Skip generic words
            if (second.lowercase() !in listOf("torrentio", "unknown")) return second
        }
        // Try known tracker names anywhere in the string
        val known = listOf("RuTracker", "RuTor", "YTS", "RARBG", "1337x", "TorrentGalaxy",
            "ThePirateBay", "TPB", "EZTV", "Nyaa", "KickassTorrents", "LimeTorrents")
        for (k in known) {
            if (rawName.contains(k, ignoreCase = true)) return k
        }
        return "Torrentio"
    }

    /** Get a unique color for each tracker */
    fun trackerColor(tracker: String): Long = when (tracker.lowercase()) {
        "rutracker"      -> 0xFF7C3AED  // Purple
        "rutor"          -> 0xFFF97316  // Orange
        "yts"            -> 0xFF22C55E  // Green
        "rarbg"          -> 0xFFEF4444  // Red
        "torrentgalaxy"  -> 0xFF3B82F6  // Blue
        "1337x"          -> 0xFFE879F9  // Pink
        "thepiratebay", "tpb" -> 0xFFEAB308  // Yellow
        "eztv"           -> 0xFF06B6D4  // Cyan
        "nyaa"           -> 0xFFF472B6  // Rose
        else             -> 0xFF94A3B8  // Slate gray
    }
}

// ── Sort/Filter Mode ──────────────────────────────────────────────────────
enum class StreamSortMode {
    DEFAULT,       // sortScore (cached → quality → size)
    BY_TRACKER,    // grouped by tracker name
    BY_LANGUAGE    // preferred audio language first
}

@Immutable
data class AdvancedStreamSource(
    val id: String, val releaseGroup: String, val filename: String, val infoHash: String?,
    val directUrl: String?, val sizeBytes: Long, val isCachedRd: Boolean, val quality: StreamQuality, val videoCodec: VideoCodec,
    val rawTitle: String = "",
    val trackerName: String = "Torrentio",
    val audioLanguages: List<AudioLanguage> = listOf(AudioLanguage.UNKNOWN)
) {
    val sizeGb: Double get() = sizeBytes / (1024.0 * 1024.0 * 1024.0)
    val sortScore: Int get() = (if (isCachedRd) 10000 else 0) + (quality.priority * 100) + (if (sizeGb in 2.0..20.0) 10 else 0)
}

@Immutable data class Review(val author: String, val content: String, val rating: Double?)

@Immutable
data class MediaDetailsInfo(
    val id: String = "", val imdbId: String = "", val title: String = "",
    val overview: String = "", val posterUrl: String = "", val backdropUrl: String = "",
    val logoUrl: String? = null, val isSeries: Boolean = false, val releaseDate: String = "",
    val runtimeMinutes: Int = 0, val tmdbRating: Double = 0.0, val imdbRating: Double = 0.0,
    val ageRating: String = "TV-MA", val studios: List<String> = emptyList(),
    val genres: List<String> = emptyList(), val director: String = "",
    val cast: List<CastMember> = emptyList(),
    val totalSeasons: Int = 0, val isFavorite: Boolean = false, val trailerUrl: String? = null,
    val collectionName: String? = null, val collectionItems: List<Recommendation> = emptyList(),
    val directorName: String? = null, val directorItems: List<Recommendation> = emptyList(),
    val rottenTomatoesScore: String? = null,
    val metacriticScore: String? = null,
    val reviews: List<Review> = emptyList()
) {
    val displayStudios: String get() = studios.take(2).joinToString(" • ").uppercase()
    val displayGenres:  String get() = genres.take(3).joinToString(" • ")
    val formattedRuntime: String get() = if (runtimeMinutes > 0) "${runtimeMinutes / 60}h ${runtimeMinutes % 60}m" else "N/A"
}

sealed interface ScrapingStatus {
    object Idle : ScrapingStatus
    object Searching : ScrapingStatus
    data class ResolvingDebrid(val streamId: String) : ScrapingStatus
    object Success : ScrapingStatus
    data class Error(val message: String) : ScrapingStatus
}

@Immutable
data class DetailsScreenState(
    val isLoadingData: Boolean = true, val errorData: String? = null, val mediaInfo: MediaDetailsInfo = MediaDetailsInfo(),
    val selectedSeason: Int = 1, val episodes: List<Episode> = emptyList(), val isEpisodesLoading: Boolean = false,
    val scrapingStatus: ScrapingStatus = ScrapingStatus.Idle, val availableStreams: List<AdvancedStreamSource> = emptyList(),
    val readyToPlayUrl: String? = null, val bestSourceHint: String? = null, val isFuzerDirect: Boolean = false,

    // ── Watch progress ─────────────────────────────────────────────────────────
    /** For movies: 0–1 watch fraction. null = never started. */
    val contentProgress: Float? = null,
    /** True when the main content (movie or latest episode) is ≥ 90 % watched. */
    val contentIsFinished: Boolean = false,
    /** TV only: season number of the most recently watched episode. */
    val lastWatchedSeason: Int? = null,
    /** TV only: episode number of the most recently watched episode. */
    val lastWatchedEpisode: Int? = null,

    // ── Stream Sort & Filter ──────────────────────────────────────────────────
    val streamSortMode: StreamSortMode = StreamSortMode.DEFAULT,
    val trackerFilters: Set<String> = emptySet(),         // empty = show all, set of tracker names to show
    val languageFilters: Set<AudioLanguage> = emptySet(),  // empty = show all, set of languages to show
    val showCachedOnly: Boolean = false,                   // only RD+ cached torrents
    val showDvOnly: Boolean = false,                       // only Dolby Vision streams
    val qualityFilter: StreamQuality? = null,               // null = all, else filter by quality

    // ── Actor Details Popup ───────────────────────────────────────────────────
    val showActorPopup: Boolean = false,
    val selectedActorDetails: com.luminastreams.tv.data.api.TmdbPersonDetailsDto? = null,
    val isLoadingActor: Boolean = false
)

sealed interface DetailsEvent {
    data class LoadInitialData(val fullId: String) : DetailsEvent
    data class SelectSeason(val seasonNumber: Int) : DetailsEvent
    data class InitiateScraping(val imdbId: String, val season: Int? = null, val episode: Int? = null) : DetailsEvent
    data class ResolveAndPlayStream(val stream: AdvancedStreamSource) : DetailsEvent
    object ToggleFavorite : DetailsEvent
    object ClearPlayUrl : DetailsEvent
    object CancelScraping : DetailsEvent
    /** Called every time the Details screen becomes visible, to sync latest watch progress. */
    object RefreshProgress : DetailsEvent
    /** Mark a specific episode as fully watched (sets progress to 100%). */
    data class MarkEpisodeWatched(val season: Int, val episode: Int) : DetailsEvent
    /** Remove watch progress for a specific episode. */
    data class MarkEpisodeUnwatched(val season: Int, val episode: Int) : DetailsEvent
    /** Mark an entire season as fully watched. */
    data class MarkSeasonWatched(val season: Int) : DetailsEvent
    /** Remove watch progress for an entire season. */
    data class MarkSeasonUnwatched(val season: Int) : DetailsEvent

    // --- הוספנו כאן ללא סוגריים ---
    object MarkMovieWatched : DetailsEvent
    object MarkMovieUnwatched : DetailsEvent

    data class SelectActor(val actorId: String) : DetailsEvent
    object DismissActorPopup : DetailsEvent

    // ── Stream Sort & Filter ──────────────────────────────────────────────────
    data class SetStreamSortMode(val mode: StreamSortMode) : DetailsEvent
    data class ToggleTrackerFilter(val tracker: String) : DetailsEvent
    data class ToggleLanguageFilter(val language: AudioLanguage) : DetailsEvent
    data class SetCachedOnly(val enabled: Boolean) : DetailsEvent
    data class SetDvOnly(val enabled: Boolean) : DetailsEvent
    data class SetQualityFilter(val quality: StreamQuality?) : DetailsEvent
    object ClearAllFilters : DetailsEvent
}