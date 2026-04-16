package com.luminastreams.tv.presentation.iptv

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.data.repository.IptvRepository
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

    // הנתונים שמוצגים ב-UI
    val channels: StateFlow<List<ChannelEntity>> = combine(activePlaylist, _selectedGroup) { playlist, group ->
        if (playlist == null) flowOf(emptyList())
        else when (group) {
            "All" -> repository.getChannels(playlist.id)
            "Favorites" -> repository.getFavoriteChannels()
            else -> flow { emit(repository.dao.getChannelsByGroup(playlist.id, group)) }
        }
    }.flatMapLatest { it }.stateIn(viewModelScope, SharingStarted.Lazily, emptyList())

    val groups: StateFlow<List<String>> = activePlaylist.flatMapLatest { playlist ->
        if (playlist != null) repository.getGroups(playlist.id).map { listOf("All", "Favorites") + it }
        else flowOf(listOf("All", "Favorites"))
    }.stateIn(viewModelScope, SharingStarted.Lazily, listOf("All", "Favorites"))

    // EPG לערוץ שמוצג כרגע
    private val _focusedEpg = MutableStateFlow<EpgProgramEntity?>(null)
    val focusedEpg = _focusedEpg.asStateFlow()

    private val _showQrScreen = MutableStateFlow(false)
    val showQrScreen = _showQrScreen.asStateFlow()

    private val _ipAddress = MutableStateFlow("")
    val ipAddress = _ipAddress.asStateFlow()

    init {
        viewModelScope.launch {
            LocalWebServer.playlistFlow.collect { playlist ->
                _showQrScreen.value = false
                LocalWebServer.stop()
                repository.loadAndSavePlaylist(playlist.name, playlist.url, playlist.epgUrl)
                if (playlist.epgUrl.isNotBlank()) repository.loadEpg(playlist.epgUrl)
            }
        }
    }

    // -- פונקציות ה-EPG והזאפינג המתוקנות --

    fun onChannelFocused(channelId: String) {
        viewModelScope.launch {
            _focusedEpg.value = repository.getCurrentProgram(channelId)
        }
    }

    fun getNextChannelUrl(currentUrl: String): String? {
        val currentList = channels.value // גישה לערך הנוכחי של ה-StateFlow
        val idx = currentList.indexOfFirst { it.streamUrl == currentUrl }
        return if (idx != -1 && idx < currentList.size - 1) {
            currentList[idx + 1].streamUrl
        } else {
            currentList.firstOrNull()?.streamUrl
        }
    }

    fun getPrevChannelUrl(currentUrl: String): String? {
        val currentList = channels.value
        val idx = currentList.indexOfFirst { it.streamUrl == currentUrl }
        return if (idx > 0) {
            currentList[idx - 1].streamUrl
        } else {
            currentList.lastOrNull()?.streamUrl
        }
    }

    fun selectGroup(group: String) { _selectedGroup.value = group }

    fun openQrSetup() {
        _ipAddress.value = "http://${getLocalIpAddress()}:8080"
        _showQrScreen.value = true
        viewModelScope.launch { LocalWebServer.start(8080) }
    }

    fun closeQrSetup() {
        _showQrScreen.value = false
        LocalWebServer.stop()
    }

    private fun getLocalIpAddress(): String {
        return try {
            val interfaces = NetworkInterface.getNetworkInterfaces() ?: return ""
            val addresses = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val ni = interfaces.nextElement()
                if (ni.isLoopback || !ni.isUp) continue
                val inetAddresses = ni.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val addr = inetAddresses.nextElement()
                    if (!addr.isLoopbackAddress && addr is Inet4Address) {
                        addresses.add(addr.hostAddress ?: "")
                    }
                }
            }
            addresses.firstOrNull { it.startsWith("192.168.") }
                ?: addresses.firstOrNull { it.startsWith("10.") }
                ?: addresses.firstOrNull() ?: ""
        } catch (_: Exception) { "" }
    }
}