package com.luminastreams.tv.presentation.iptv

import androidx.compose.runtime.Immutable

// ── IPTV Data Models ──────────────────────────────────────────────────────────

@Immutable
data class IptvChannel(
    val id: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val groupTitle: String,
    val tvgId: String = "",
    val tvgName: String = "",
    val isAdult: Boolean = false,
    val number: Int = 0
)

@Immutable
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long,   // epoch ms
    val endTime: Long,     // epoch ms
    val category: String = "",
    val rating: String = "",
    val posterUrl: String = ""
) {
    val durationMs: Long get() = endTime - startTime
    val progressFraction: Float
        get() {
            val now = System.currentTimeMillis()
            if (now < startTime) return 0f
            if (now > endTime) return 1f
            return ((now - startTime).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
    val isLive: Boolean
        get() {
            val now = System.currentTimeMillis()
            return now in startTime..endTime
        }
    val isUpcoming: Boolean get() = System.currentTimeMillis() < startTime
}

@Immutable
data class IptvPlaylist(
    val id: String,
    val name: String,
    val url: String,
    val epgUrl: String = "",
    val channelCount: Int = 0,
    val lastUpdated: Long = 0L,
    val isActive: Boolean = false
)

sealed interface IptvLoadState {
    object Idle : IptvLoadState
    object Loading : IptvLoadState
    data class Error(val message: String) : IptvLoadState
    object Success : IptvLoadState
}

@Immutable
data class IptvState(
    // Playlists
    val playlists: List<IptvPlaylist> = emptyList(),
    val activePlaylistId: String? = null,

    // Channels
    val channels: List<IptvChannel> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String = "All",
    val filteredChannels: List<IptvChannel> = emptyList(),
    val searchQuery: String = "",

    // EPG
    val epgData: Map<String, List<EpgProgram>> = emptyMap(),
    val epgLoadState: IptvLoadState = IptvLoadState.Idle,

    // Playback
    val currentChannel: IptvChannel? = null,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,

    // UI
    val loadState: IptvLoadState = IptvLoadState.Idle,
    val showAddPlaylist: Boolean = false,
    val showEpgGuide: Boolean = false,
    val showChannelInfo: Boolean = false,
    val showQrCode: Boolean = false,
    val qrCodeChannel: IptvChannel? = null,
    val favoriteChannelIds: Set<String> = emptySet(),
    val recentChannelIds: List<String> = emptyList(),

    // View mode
    val viewMode: IptvViewMode = IptvViewMode.CHANNEL_LIST,

    // Add playlist form
    val addPlaylistName: String = "",
    val addPlaylistUrl: String = "",
    val addPlaylistEpgUrl: String = ""
)

enum class IptvViewMode { CHANNEL_LIST, EPG_GUIDE, FAVORITES, RECENT }

sealed interface IptvEvent {
    data class LoadPlaylist(val url: String, val name: String = "", val epgUrl: String = "") : IptvEvent
    data class SelectPlaylist(val playlistId: String) : IptvEvent
    data class DeletePlaylist(val playlistId: String) : IptvEvent
    data class SelectChannel(val channel: IptvChannel) : IptvEvent
    data class SelectGroup(val group: String) : IptvEvent
    data class UpdateSearch(val query: String) : IptvEvent
    data class ToggleFavorite(val channelId: String) : IptvEvent
    data class ShowQrCode(val channel: IptvChannel) : IptvEvent
    object HideQrCode : IptvEvent
    object ShowAddPlaylist : IptvEvent
    object HideAddPlaylist : IptvEvent
    object ShowEpgGuide : IptvEvent
    object HideEpgGuide : IptvEvent
    object RefreshEpg : IptvEvent
    data class SetViewMode(val mode: IptvViewMode) : IptvEvent
    data class UpdateAddPlaylistName(val name: String) : IptvEvent
    data class UpdateAddPlaylistUrl(val url: String) : IptvEvent
    data class UpdateAddPlaylistEpgUrl(val url: String) : IptvEvent
    object ConfirmAddPlaylist : IptvEvent
}
