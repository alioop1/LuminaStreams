package com.luminastreams.tv.presentation.debrid

import androidx.compose.runtime.Immutable
import com.luminastreams.tv.domain.usecase.AllDebridManager

@Immutable
data class PlaybackTarget(
    val url: String,
    val title: String,
    val backdropUrl: String = "",
    val posterUrl: String = ""
)

@Immutable
data class DebridCloudState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val error: String? = null,
    val isNotAuthenticated: Boolean = false,
    val items: List<DebridTorrentUiItem> = emptyList(),
    val selectedTorrentForFiles: DebridTorrentUiItem? = null,
    val filesForSelectedTorrent: List<AllDebridManager.AdFile> = emptyList(),
    val isLoadingFiles: Boolean = false,
    val resolvingStream: Boolean = false,
    val resolvingMessage: String = "",
    val readyToPlay: PlaybackTarget? = null
)

@Immutable
data class DebridTorrentUiItem(
    val id: Long,
    val rawFilename: String,
    val displayTitle: String,
    val cleanSearchTitle: String,
    val sizeFormatted: String,
    val sizeBytes: Long,
    val status: String,
    val statusCode: Int,       // 4 = Ready, 1..3 = downloading, >4 = error
    val progressPercent: Int,
    val uploadDateFormatted: String,
    val nbLinks: Int,
    // TMDB metadata (asynchronously enriched)
    val posterUrl: String? = null,
    val backdropUrl: String? = null,
    val overview: String? = null,
    val rating: Float = 0f,
    val year: String = "",
    val mediaType: String = "movie"
)

sealed class DebridCloudEvent {
    object Refresh : DebridCloudEvent()
    data class OnTorrentClick(val item: DebridTorrentUiItem) : DebridCloudEvent()
    data class OnPlayFile(val file: AllDebridManager.AdFile, val torrentItem: DebridTorrentUiItem) : DebridCloudEvent()
    data class DeleteTorrent(val torrentId: Long) : DebridCloudEvent()
    object DismissFilesDialog : DebridCloudEvent()
    object ClearPlayTarget : DebridCloudEvent()
}
