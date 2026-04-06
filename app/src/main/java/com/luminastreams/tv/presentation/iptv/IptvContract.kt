// 2. IptvContract.kt
package com.luminastreams.tv.presentation.iptv

import androidx.compose.runtime.Immutable

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
    val number: Int = 0,
    val catchupSource: String = "",
    val catchupDays: Int = 0,
    val hasArchive: Boolean = false,
    val resolution: String = "",
    val country: String = "",
    val language: String = "",
)

@Immutable
data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val category: String = "",
    val rating: String = "",
    val posterUrl: String = "",
    val episodeNum: String = "",
    val isSeries: Boolean = false,
    val isLive: Boolean = false,
    val director: String = "",
    val actors: String = "",
    val year: String = "",
    val language: String = "",
) {
    val durationMs: Long get() = endTime - startTime
    val durationMinutes: Int get() = (durationMs / 60_000).toInt()
    val progressFraction: Float
        get() {
            val now = System.currentTimeMillis()
            if (now < startTime) return 0f
            if (now > endTime) return 1f
            return ((now - startTime).toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
        }
    val isLiveNow: Boolean get() = System.currentTimeMillis() in startTime..endTime
    val isPast: Boolean get() = System.currentTimeMillis() > endTime
    val remainingMinutes: Int get() {
        val remaining = endTime - System.currentTimeMillis()
        return if (remaining > 0) (remaining / 60_000).toInt() else 0
    }
}

@Immutable
data class IptvPlaylist(
    val id: String,
    val name: String,
    val url: String,
    val epgUrl: String = "",
    val channelCount: Int = 0,
    val lastUpdated: Long = 0L,
    val isActive: Boolean = false,
    val logoUrl: String = "",
    val userAgent: String = "",
    val autoRefreshHours: Int = 0,
)

enum class SleepTimer(val minutes: Int, val label: String) {
    OFF(0, "Off"),
    MIN_15(15, "15 min"),
    MIN_30(30, "30 min"),
    MIN_60(60, "1 hour"),
    MIN_90(90, "1.5 hours"),
    MIN_120(120, "2 hours"),
    END_OF_PROGRAM(-1, "End of show")
}

enum class ChannelSortMode(val label: String) {
    DEFAULT("Default"),
    NAME_ASC("Name A-Z"),
    NAME_DESC("Name Z-A"),
    NUMBER("Channel Number"),
    RECENTLY_WATCHED("Recently Watched")
}

enum class StreamQuality(val label: String) {
    AUTO("Auto"),
    LOW("Low (480p)"),
    MEDIUM("Medium (720p)"),
    HIGH("High (1080p)"),
    ULTRA("Ultra (4K)")
}

sealed interface IptvLoadState {
    object Idle : IptvLoadState
    object Loading : IptvLoadState
    data class Error(val message: String) : IptvLoadState
    object Success : IptvLoadState
}

@Immutable
data class IptvState(
    val playlists: List<IptvPlaylist> = emptyList(),
    val activePlaylistId: String? = null,
    val channels: List<IptvChannel> = emptyList(),
    val groups: List<String> = emptyList(),
    val selectedGroup: String = "All",
    val filteredChannels: List<IptvChannel> = emptyList(),
    val searchQuery: String = "",
    val epgData: Map<String, List<EpgProgram>> = emptyMap(),
    val epgLoadState: IptvLoadState = IptvLoadState.Idle,
    val currentChannel: IptvChannel? = null,
    val currentProgram: EpgProgram? = null,
    val nextProgram: EpgProgram? = null,
    val loadState: IptvLoadState = IptvLoadState.Idle,
    val showAddPlaylist: Boolean = false,
    val showEpgGuide: Boolean = false,
    val showChannelInfo: Boolean = false,
    val showQrCode: Boolean = false,
    val qrCodeChannel: IptvChannel? = null,
    val favoriteChannelIds: Set<String> = emptySet(),
    val recentChannelIds: List<String> = emptyList(),
    val viewMode: IptvViewMode = IptvViewMode.CHANNEL_LIST,
    val addPlaylistName: String = "",
    val addPlaylistUrl: String = "",
    val addPlaylistEpgUrl: String = "",
    val localIpAddress: String = "",
    val sleepTimer: SleepTimer = SleepTimer.OFF,
    val sleepTimerRemainingMs: Long = 0L,
    val showSleepTimerPicker: Boolean = false,
    val channelSortMode: ChannelSortMode = ChannelSortMode.DEFAULT,
    val streamQuality: StreamQuality = StreamQuality.AUTO,
    val showMiniPlayer: Boolean = false,
    val isRecording: Boolean = false,
    val recordingChannelId: String? = null,
    val showChannelGrid: Boolean = false,
    val multiViewChannels: List<IptvChannel> = emptyList(),
    val showMultiView: Boolean = false,
    val parentalLockEnabled: Boolean = false,
    val parentalPin: String = "",
    val showParentalPinEntry: Boolean = false,
    val pendingLockedChannel: IptvChannel? = null,
    val epgDayOffset: Int = 0,
    val channelLogos: Map<String, String> = emptyMap(),
    val showSettings: Boolean = false,
    val subtitlesEnabled: Boolean = false,
    val audioTrackIndex: Int = 0,
)

enum class IptvViewMode { CHANNEL_LIST }

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
    data class ShowEditPlaylist(val playlist: IptvPlaylist) : IptvEvent
    object HideAddPlaylist : IptvEvent
    object ShowEpgGuide : IptvEvent
    object HideEpgGuide : IptvEvent
    object RefreshEpg : IptvEvent
    object RefreshCurrentPlaylist : IptvEvent
    data class SetViewMode(val mode: IptvViewMode) : IptvEvent
    data class UpdateAddPlaylistName(val name: String) : IptvEvent
    data class UpdateAddPlaylistUrl(val url: String) : IptvEvent
    data class UpdateAddPlaylistEpgUrl(val url: String) : IptvEvent
    object ConfirmAddPlaylist : IptvEvent
    data class SetSleepTimer(val timer: SleepTimer) : IptvEvent
    object DismissSleepTimer : IptvEvent
    object ShowSleepTimerPicker : IptvEvent
    object HideSleepTimerPicker : IptvEvent
    data class SetChannelSort(val mode: ChannelSortMode) : IptvEvent
    data class SetStreamQuality(val quality: StreamQuality) : IptvEvent
    object ToggleChannelGrid : IptvEvent
    data class AddToMultiView(val channel: IptvChannel) : IptvEvent
    data class RemoveFromMultiView(val channel: IptvChannel) : IptvEvent
    object ToggleMultiView : IptvEvent
    object ToggleRecording : IptvEvent
    data class SetParentalLock(val enabled: Boolean, val pin: String) : IptvEvent
    data class EnterParentalPin(val pin: String) : IptvEvent
    object DismissParentalPin : IptvEvent
    data class SetEpgDayOffset(val offset: Int) : IptvEvent
    object ShowIptvSettings : IptvEvent
    object HideIptvSettings : IptvEvent
    object ToggleSubtitles : IptvEvent
    data class SelectAudioTrack(val index: Int) : IptvEvent
    data class ChannelUp(val currentIndex: Int) : IptvEvent
    data class ChannelDown(val currentIndex: Int) : IptvEvent
}