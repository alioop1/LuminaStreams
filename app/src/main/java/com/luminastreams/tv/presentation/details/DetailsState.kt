package com.luminastreams.tv.presentation.details

import androidx.compose.runtime.Immutable

@Immutable
data class CastMember(val id: String, val name: String, val character: String, val imageUrl: String)

@Immutable
data class Episode(
    val id: String, val episodeNumber: Int, val seasonNumber: Int,
    val title: String, val overview: String, val stillUrl: String, val hasWatched: Boolean = false,
    val progress: Float = 0f // 0.0 to 1.0 for the Apple TV red progress bar
)

@Immutable
data class Recommendation(val id: String, val title: String, val posterUrl: String)

enum class StreamQuality(val priority: Int, val displayName: String) {
    UHD_4K(9, "4K UHD"), FHD_1080P(7, "1080p"), HD_720P(6, "720p"), SD_480P(5, "480p"), UNKNOWN(0, "Unknown");
    companion object {
        fun fromString(title: String): StreamQuality {
            val t = title.uppercase()
            return when {
                t.contains("4K") || t.contains("2160P") -> UHD_4K
                t.contains("1080P") -> FHD_1080P
                t.contains("720P") -> HD_720P
                t.contains("480P") -> SD_480P
                else -> UNKNOWN
            }
        }
    }
}

enum class VideoCodec(val displayName: String) {
    HEVC("HEVC"), AVC("AVC"), AV1("AV1"), UNKNOWN("Unknown");
    companion object {
        fun fromString(title: String): VideoCodec {
            val t = title.uppercase()
            return when {
                t.contains("HEVC") || t.contains("X265") || t.contains("H265") -> HEVC
                t.contains("AVC") || t.contains("X264") || t.contains("H264") -> AVC
                t.contains("AV1") -> AV1
                else -> UNKNOWN
            }
        }
    }
}

sealed interface ScrapingStatus {
    object Idle : ScrapingStatus
    object Searching : ScrapingStatus
    data class ResolvingDebrid(val streamId: String) : ScrapingStatus
    object Success : ScrapingStatus
    data class Error(val message: String) : ScrapingStatus
}

@Immutable
data class MediaDetailsInfo(
    val id: String = "", val imdbId: String = "", val title: String = "", val overview: String = "",
    val posterUrl: String = "", val backdropUrl: String = "", val logoUrl: String? = null,
    val isSeries: Boolean = false, val releaseDate: String = "", val runtimeMinutes: Int = 0,
    val tmdbRating: Double = 0.0, val imdbRating: Double = 0.0, val ageRating: String = "TV-MA",
    val studios: List<String> = emptyList(), val genres: List<String> = emptyList(), val director: String = "",
    val cast: List<CastMember> = emptyList(), val recommendations: List<Recommendation> = emptyList(),
    val totalSeasons: Int = 0, val isFavorite: Boolean = false, val trailerUrl: String? = null
) {
    val displayStudios: String get() = studios.take(2).joinToString(" • ").uppercase()
    val displayGenres: String get() = genres.take(3).joinToString(" • ")
    val formattedRuntime: String get() = if (runtimeMinutes > 0) "${runtimeMinutes / 60}h ${runtimeMinutes % 60}m" else "N/A"
}

@Immutable
data class AdvancedStreamSource(
    val id: String, val releaseGroup: String, val filename: String, val infoHash: String?, val directUrl: String?,
    val sizeBytes: Long, val isCachedRd: Boolean, val quality: StreamQuality,
    val videoCodec: VideoCodec
) {
    val sizeGb: Double get() = sizeBytes / (1024.0 * 1024.0 * 1024.0)
    val formattedSize: String get() = String.format(java.util.Locale.US, "%.2f GB", sizeGb)
    val sortScore: Int get() = (if (isCachedRd) 10000 else 0) + (quality.priority * 100) + (if (sizeGb in 2.0..20.0) 10 else 0)
}

@Immutable
data class DetailsScreenState(
    val isLoadingData: Boolean = true,
    val errorData: String? = null,
    val mediaInfo: MediaDetailsInfo = MediaDetailsInfo(),
    val selectedSeason: Int = 1,
    val episodes: List<Episode> = emptyList(),
    val isEpisodesLoading: Boolean = false,
    val scrapingStatus: ScrapingStatus = ScrapingStatus.Idle,
    val availableStreams: List<AdvancedStreamSource> = emptyList(),
    val readyToPlayUrl: String? = null,
    val bestSourceHint: String? = null // Smart Feature: Pre-flight best torrent
)

sealed interface DetailsEvent {
    data class LoadInitialData(val fullId: String) : DetailsEvent
    data class SelectSeason(val seasonNumber: Int) : DetailsEvent
    data class InitiateScraping(val imdbId: String, val season: Int? = null, val episode: Int? = null) : DetailsEvent
    data class ResolveAndPlayStream(val stream: AdvancedStreamSource) : DetailsEvent
    object ToggleFavorite : DetailsEvent
    object ClearPlayUrl : DetailsEvent
    object CancelScraping : DetailsEvent
}