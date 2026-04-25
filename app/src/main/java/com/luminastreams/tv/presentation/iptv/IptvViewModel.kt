package com.luminastreams.tv.presentation.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.data.repository.IptvRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(ExperimentalCoroutinesApi::class)
class IptvViewModel(private val repository: IptvRepository) : ViewModel() {

    val activePlaylist = repository.getAllPlaylists()
        .map { list -> list.find { it.isActive } }
        .stateIn(viewModelScope, SharingStarted.Lazily, null)

    private val _selectedGroup = MutableStateFlow("All")
    val selectedGroup = _selectedGroup.asStateFlow()

    private val _activeCategory = MutableStateFlow("All")

    val channels: StateFlow<List<ChannelEntity>> = combine(activePlaylist, _activeCategory) { playlist, category ->
        if (playlist == null) flowOf(emptyList())
        else when (category) {
            "All" -> repository.getChannels(playlist.id)
            "Favorites" -> repository.getFavoriteChannels()
            else -> flow { emit(repository.dao.getChannelsByGroup(playlist.id, category)) }
        }
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    fun selectGroup(group: String) {
        _selectedGroup.value = group
        if (group != "Settings" && group != "EPG Guide") {
            _activeCategory.value = group
        }
    }

    val groups: StateFlow<List<String>> = activePlaylist.flatMapLatest { playlist ->
        if (playlist != null) repository.getGroups(playlist.id).map { listOf("All", "Favorites") + it }
        else flowOf(listOf("All", "Favorites"))
    }.stateIn(viewModelScope, SharingStarted.Lazily, listOf("All", "Favorites"))

    private val _focusedEpg = MutableStateFlow<EpgProgramEntity?>(null)
    val focusedEpg = _focusedEpg.asStateFlow()

    private val _showQrScreen = MutableStateFlow(false)
    val showQrScreen = _showQrScreen.asStateFlow()

    private val _ipAddress = MutableStateFlow("")
    val ipAddress = _ipAddress.asStateFlow()

    private val _isLoading = MutableStateFlow(false)

    @Suppress("unused")
    val isLoading = _isLoading.asStateFlow()

    init {
        viewModelScope.launch {
            activePlaylist.filterNotNull().collectLatest { playlist ->
                val oneWeekInMs = 7L * 24 * 60 * 60 * 1000
                val now = System.currentTimeMillis()

                if (now - playlist.lastUpdated > oneWeekInMs) {
                    try {
                        if (playlist.epgUrl.isNotBlank()) {
                            repository.loadEpg(playlist.epgUrl, playlist.id)
                        }
                        repository.dao.insertPlaylist(playlist.copy(lastUpdated = now))
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }
        }

        // FIX: Listen for playlists sent from the phone web server
        viewModelScope.launch {
            LocalWebServer.playlistFlow.collect { playlist ->
                addManualPlaylist(playlist.name, playlist.url, playlist.epgUrl)
            }
        }
    }

    fun onChannelFocused(channel: ChannelEntity) {
        viewModelScope.launch {
            _focusedEpg.value = repository.getCurrentProgram(channel)
        }
    }

    fun toggleFavorite(channel: ChannelEntity) {
        viewModelScope.launch(Dispatchers.IO) {
            repository.dao.setFavorite(channel.id, !channel.isFavorite)
        }
    }

    suspend fun getProgramsForChannel(channel: ChannelEntity, currentTime: Long): List<EpgProgramEntity> {
        val startTime = currentTime - (24 * 60 * 60 * 1000)
        return repository.getFuturePrograms(channel, startTime)
    }

    suspend fun getFullDayProgramsForChannel(channel: ChannelEntity, dayStartMs: Long, dayEndMs: Long): List<EpgProgramEntity> {
        return repository.getFullDayPrograms(channel, dayStartMs, dayEndMs)
    }

    /**
     * Builds a catch-up URL from a channel's base stream URL and a program's start time.
     * Supports Xtream Codes format: appends `?utc={epoch}&lutc={current_epoch}`
     * Also supports timeshift format in the URL path if detected.
     */
    fun buildCatchupUrl(channelUrl: String, programStartTime: Long): String {
        val utcSeconds = programStartTime / 1000
        val nowSeconds = System.currentTimeMillis() / 1000
        // Xtream Codes standard catch-up format
        val separator = if (channelUrl.contains("?")) "&" else "?"
        return "${channelUrl}${separator}utc=${utcSeconds}&lutc=${nowSeconds}"
    }

    fun getNextChannelUrl(currentUrl: String): String? {
        val currentList = channels.value
        val idx = currentList.indexOfFirst { currentUrl.startsWith(it.streamUrl) }
        return if (idx != -1 && idx < currentList.size - 1) {
            currentList[idx + 1].streamUrl
        } else {
            currentList.firstOrNull()?.streamUrl
        }
    }

    fun getPrevChannelUrl(currentUrl: String): String? {
        val currentList = channels.value
        val idx = currentList.indexOfFirst { currentUrl.startsWith(it.streamUrl) }
        return if (idx > 0) {
            currentList[idx - 1].streamUrl
        } else {
            currentList.lastOrNull()?.streamUrl
        }
    }

    fun openQrSetup() {
        _showQrScreen.value = true // Show screen immediately
        viewModelScope.launch(Dispatchers.IO) {
            val ip = getLocalIpAddress() // Off main thread — can block 100–500ms on some TV boxes
            _ipAddress.value = if (ip == "ERROR") "Network Error: Connect to Wi-Fi/LAN" else "http://$ip:8080"
            if (ip != "ERROR") LocalWebServer.start(8080)
        }
    }

    fun closeQrSetup() {
        _showQrScreen.value = false
        // FIX: Stop the server when exiting the menu to free up the port
        LocalWebServer.stop()
    }

    fun addManualPlaylist(name: String, url: String, epgUrl: String) {
        if (url.isBlank() || _isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val newPlaylistId = repository.loadAndSavePlaylist(
                    name = name.ifBlank { "My Playlist" },
                    url = url.trim(),
                    epgUrl = epgUrl.trim()
                )
                if (epgUrl.isNotBlank()) {
                    repository.loadEpg(epgUrl.trim(), newPlaylistId)
                }
                closeQrSetup()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updatePlaylist(name: String, url: String, epgUrl: String) {
        val current = activePlaylist.value ?: return
        if (_isLoading.value) return
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val trimmedUrl = url.trim()
                val trimmedEpg = epgUrl.trim()
                val urlChanged = trimmedUrl != current.url
                val epgChanged = trimmedEpg != current.epgUrl

                // Always update the entity with new name/urls
                val updated = current.copy(
                    name = name.ifBlank { current.name },
                    url = trimmedUrl.ifBlank { current.url },
                    epgUrl = trimmedEpg,
                    lastUpdated = System.currentTimeMillis()
                )
                repository.dao.insertPlaylist(updated)

                // Re-fetch channels if M3U changed
                if (urlChanged && trimmedUrl.isNotBlank()) {
                    repository.dao.clearChannels(current.id)
                    M3uParser.parseStreaming(current.id, trimmedUrl) { batch ->
                        repository.dao.insertChannels(batch)
                    }
                }

                // Re-fetch EPG if changed
                if (epgChanged && trimmedEpg.isNotBlank()) {
                    repository.loadEpg(trimmedEpg, current.id)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deletePlaylist() {
        val current = activePlaylist.value ?: return
        viewModelScope.launch {
            repository.dao.clearChannels(current.id)
            repository.dao.clearAllEpg()
            repository.dao.deactivateAllPlaylists()
        }
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return "ERROR"
            for (ni in interfaces) {
                if (ni.isLoopback || !ni.isUp || ni.isVirtual) continue
                for (addr in ni.inetAddresses) {
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        val ip = addr.hostAddress ?: ""
                        if (ip.startsWith("192.168.") || ip.startsWith("10.") || ip.startsWith("172.")) {
                            return ip
                        }
                    }
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return "ERROR"
    }
}