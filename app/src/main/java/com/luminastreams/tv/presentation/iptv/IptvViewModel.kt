package com.luminastreams.tv.presentation.iptv

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val TAG = "EPG_DEBUG"
    private val prefs = application.getSharedPreferences("lumina_iptv", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(IptvState())
    val state: StateFlow<IptvState> = _state.asStateFlow()

    private var epgRefreshJob: Job? = null
    private var webServerJob: Job? = null

    init {
        loadSavedPlaylists()
        loadFavorites()
        loadRecent()
    }

    private fun getLocalIpAddress(): String {
        try {
            val interfaces = NetworkInterface.getNetworkInterfaces()
            val addresses = mutableListOf<String>()
            while (interfaces.hasMoreElements()) {
                val networkInterface = interfaces.nextElement()
                if (networkInterface.isLoopback || !networkInterface.isUp) continue
                val inetAddresses = networkInterface.inetAddresses
                while (inetAddresses.hasMoreElements()) {
                    val address = inetAddresses.nextElement()
                    if (!address.isLoopbackAddress && address is java.net.Inet4Address) {
                        addresses.add(address.hostAddress ?: "")
                    }
                }
            }
            return addresses.firstOrNull { it.startsWith("192.168.") }
                ?: addresses.firstOrNull { it.startsWith("10.") }
                ?: addresses.firstOrNull { it.startsWith("172.") }
                ?: addresses.firstOrNull() ?: ""
        } catch (e: Exception) { e.printStackTrace() }
        return ""
    }

    fun onEvent(event: IptvEvent) {
        when (event) {
            is IptvEvent.LoadPlaylist -> loadPlaylist(event.url, event.name, event.epgUrl)
            is IptvEvent.SelectPlaylist -> selectPlaylist(event.playlistId)
            is IptvEvent.DeletePlaylist -> deletePlaylist(event.playlistId)
            is IptvEvent.SelectChannel -> selectChannel(event.channel)
            is IptvEvent.SelectGroup -> selectGroup(event.group)
            is IptvEvent.UpdateSearch -> updateSearch(event.query)
            is IptvEvent.ToggleFavorite -> toggleFavorite(event.channelId)
            is IptvEvent.ShowQrCode -> _state.update { it.copy(showQrCode = true, qrCodeChannel = event.channel) }
            is IptvEvent.HideQrCode -> _state.update { it.copy(showQrCode = false, qrCodeChannel = null) }

            is IptvEvent.ShowAddPlaylist -> {
                val ip = getLocalIpAddress()
                _state.update { it.copy(showAddPlaylist = true, addPlaylistName = "", addPlaylistUrl = "", addPlaylistEpgUrl = "", localIpAddress = ip) }
                webServerJob?.cancel()
                webServerJob = viewModelScope.launch {
                    LocalWebServer.start(8080) { name, url, epgUrl ->
                        onEvent(IptvEvent.HideAddPlaylist)
                        loadPlaylist(url, name, epgUrl, null)
                    }
                }
            }
            is IptvEvent.ShowEditPlaylist -> {
                val ip = getLocalIpAddress()
                _state.update { it.copy(showAddPlaylist = true, addPlaylistName = event.playlist.name, addPlaylistUrl = event.playlist.url, addPlaylistEpgUrl = event.playlist.epgUrl, localIpAddress = ip) }
                webServerJob?.cancel()
                webServerJob = viewModelScope.launch {
                    LocalWebServer.start(8080) { name, url, epgUrl ->
                        onEvent(IptvEvent.HideAddPlaylist)
                        loadPlaylist(url, name, epgUrl, event.playlist.id)
                    }
                }
            }
            is IptvEvent.HideAddPlaylist -> {
                _state.update { it.copy(showAddPlaylist = false, addPlaylistName = "", addPlaylistUrl = "", addPlaylistEpgUrl = "") }
                LocalWebServer.stop()
            }
            is IptvEvent.ShowEpgGuide -> _state.update { it.copy(showEpgGuide = true) }
            is IptvEvent.HideEpgGuide -> _state.update { it.copy(showEpgGuide = false) }
            is IptvEvent.RefreshEpg -> refreshEpg()
            is IptvEvent.RefreshCurrentPlaylist -> {
                val active = _state.value.playlists.find { it.isActive }
                if (active != null) loadPlaylist(active.url, active.name, active.epgUrl, active.id)
            }
            is IptvEvent.SetViewMode -> _state.update { it.copy(viewMode = event.mode) }
            is IptvEvent.UpdateAddPlaylistName -> _state.update { it.copy(addPlaylistName = event.name) }
            is IptvEvent.UpdateAddPlaylistUrl -> _state.update { it.copy(addPlaylistUrl = event.url) }
            is IptvEvent.UpdateAddPlaylistEpgUrl -> _state.update { it.copy(addPlaylistEpgUrl = event.url) }
            is IptvEvent.ConfirmAddPlaylist -> {
                val s = _state.value
                if (s.addPlaylistUrl.isNotBlank()) {
                    val existingId = s.playlists.find { it.isActive }?.id
                    loadPlaylist(s.addPlaylistUrl, s.addPlaylistName.ifBlank { "My Playlist" }, s.addPlaylistEpgUrl, existingId)
                    onEvent(IptvEvent.HideAddPlaylist)
                }
            }
        }
    }

    private fun loadPlaylist(url: String, name: String, epgUrl: String, existingId: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loadState = IptvLoadState.Loading) }
            try {
                Log.d(TAG, "Loading Playlist M3U from: $url")
                val channels = M3uParser.parse(url).getOrThrow()
                val groups = listOf("All", "Favorites", "Recent") + channels.map { it.groupTitle }.distinct()

                val playlistId = existingId ?: "pl_${System.currentTimeMillis()}"
                val playlist = IptvPlaylist(
                    id = playlistId, name = name, url = url, epgUrl = epgUrl,
                    channelCount = channels.size, lastUpdated = System.currentTimeMillis(), isActive = true
                )

                val existing = _state.value.playlists.filter { it.id != playlistId }.map { it.copy(isActive = false) }
                val newPlaylists = existing + playlist
                savePlaylistsToPrefs(newPlaylists)

                _state.update { s ->
                    s.copy(
                        playlists = newPlaylists, activePlaylistId = playlistId, channels = channels,
                        groups = groups, selectedGroup = "All", filteredChannels = channels,
                        loadState = IptvLoadState.Success, showAddPlaylist = false
                    )
                }

                Log.d(TAG, "Playlist loaded with ${channels.size} channels. Checking for EPG...")
                if (epgUrl.isNotBlank()) {
                    loadEpg(epgUrl)
                } else {
                    Log.d(TAG, "No EPG URL provided for this playlist.")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load playlist", e)
                _state.update { it.copy(loadState = IptvLoadState.Error("Failed to load playlist: ${e.message}")) }
            }
        }
    }

    private fun selectPlaylist(playlistId: String) {
        val playlist = _state.value.playlists.find { it.id == playlistId } ?: return
        loadPlaylist(playlist.url, playlist.name, playlist.epgUrl, playlist.id)
    }

    private fun deletePlaylist(playlistId: String) {
        val updated = _state.value.playlists.filter { it.id != playlistId }
        savePlaylistsToPrefs(updated)
        if (_state.value.activePlaylistId == playlistId) {
            _state.update { it.copy(playlists = updated, activePlaylistId = null, channels = emptyList(), filteredChannels = emptyList(), groups = emptyList()) }
        } else {
            _state.update { it.copy(playlists = updated) }
        }
    }

    private fun selectChannel(channel: IptvChannel) {
        val epg = getEpgForChannel(channel)
        val now = System.currentTimeMillis()
        val recentIds = (listOf(channel.id) + _state.value.recentChannelIds).distinct().take(20)
        saveRecentToPrefs(recentIds)

        _state.update {
            it.copy(
                currentChannel = channel,
                currentProgram = epg.firstOrNull { p -> p.isLive },
                nextProgram = epg.firstOrNull { p -> p.startTime > now },
                recentChannelIds = recentIds
            )
        }

        if (_state.value.selectedGroup == "Recent") {
            selectGroup("Recent")
        }
    }

    private fun selectGroup(group: String) {
        val channels = _state.value.channels
        val favorites = _state.value.favoriteChannelIds
        val recentIds = _state.value.recentChannelIds
        val filtered = when (group) {
            "All" -> channels
            "Favorites" -> channels.filter { it.id in favorites }
            "Recent" -> recentIds.mapNotNull { id -> channels.find { it.id == id } }
            else -> channels.filter { it.groupTitle == group }
        }.let { list -> val q = _state.value.searchQuery; if (q.isBlank()) list else list.filter { it.name.contains(q, true) } }
        _state.update { it.copy(selectedGroup = group, filteredChannels = filtered) }
    }

    private fun updateSearch(query: String) {
        val base = when (val group = _state.value.selectedGroup) {
            "All" -> _state.value.channels
            "Favorites" -> _state.value.channels.filter { it.id in _state.value.favoriteChannelIds }
            "Recent" -> _state.value.recentChannelIds.mapNotNull { id -> _state.value.channels.find { it.id == id } }
            else -> _state.value.channels.filter { it.groupTitle == group }
        }
        val filtered = if (query.isBlank()) base else base.filter { it.name.contains(query, true) }
        _state.update { it.copy(searchQuery = query, filteredChannels = filtered) }
    }

    private fun toggleFavorite(channelId: String) {
        val favs = _state.value.favoriteChannelIds.toMutableSet()
        if (channelId in favs) favs.remove(channelId) else favs.add(channelId)
        saveFavoritesToPrefs(favs)
        _state.update { it.copy(favoriteChannelIds = favs) }
        if (_state.value.selectedGroup == "Favorites") selectGroup("Favorites")
    }

    private fun loadEpg(epgUrl: String) {
        Log.d(TAG, "Calling loadEpg with URL: $epgUrl")
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            _state.update { it.copy(epgLoadState = IptvLoadState.Loading) }
            try {
                val epgMap = EpgParser.parse(epgUrl).getOrThrow()
                Log.d(TAG, "EPG Map successfully saved to ViewModel state. Keys count: ${epgMap.size}")

                val updatedChannels = _state.value.channels.map { ch ->
                    if (ch.logoUrl.isBlank()) {
                        val matchingEpg = getEpgForChannel(ch, epgMap)
                        val epgLogo = matchingEpg.firstOrNull { it.posterUrl.isNotBlank() }?.posterUrl ?: ""
                        if (epgLogo.isNotBlank()) ch.copy(logoUrl = epgLogo) else ch
                    } else ch
                }

                _state.update { it.copy(epgData = epgMap, channels = updatedChannels, epgLoadState = IptvLoadState.Success) }

                selectGroup(_state.value.selectedGroup)

                _state.value.currentChannel?.let { selectChannel(it) }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to load EPG in ViewModel", e)
                _state.update { it.copy(epgLoadState = IptvLoadState.Error("EPG Error: ${e.message?.take(30)}")) }
            }
        }
    }

    private fun refreshEpg() {
        val activePlaylist = _state.value.playlists.find { it.id == _state.value.activePlaylistId }
        if (activePlaylist?.epgUrl?.isNotBlank() == true) loadEpg(activePlaylist.epgUrl)
    }

    fun getEpgForChannel(channel: IptvChannel, dataMap: Map<String, List<EpgProgram>> = _state.value.epgData): List<EpgProgram> {
        if (dataMap.isEmpty()) return emptyList()

        val cId = channel.tvgId.lowercase()
        val cNameOrig = channel.name.lowercase()
        val cTvgName = channel.tvgName.lowercase()

        // 1. התאמה מדויקת (עדיפות עליונה)
        dataMap[cId]?.let { return it }
        dataMap[cTvgName]?.let { return it }
        dataMap[cNameOrig]?.let { return it }

        // 2. ניקוי אגרסיבי להתאמה חכמה ובטוחה
        val normalize = { str: String ->
            str.lowercase()
                .replace(Regex("\\b(hd|fhd|4k|sd|tv|channel)\\b"), "")
                .replace(Regex("[^a-z0-9א-ת]"), "")
                .trim()
        }

        val normName = normalize(cNameOrig)
        val normTvgName = normalize(cTvgName)

        if (normName.isNotEmpty() || normTvgName.isNotEmpty()) {
            val fuzzyMatch = dataMap.entries.firstOrNull {
                val normKey = normalize(it.key)
                // חובה להתנות אורך מינימלי למניעת Match שגוי על ערוצים קצרים (למשל '1')
                normKey.isNotEmpty() && normKey.length > 2 && (
                        normKey == normName ||
                                normKey == normTvgName ||
                                // רק אם השם באמת ארוך מותר לעשות contains פנימי
                                (normKey.length > 5 && (normName.contains(normKey) || normKey.contains(normName)))
                        )
            }
            if (fuzzyMatch != null) {
                return fuzzyMatch.value
            }
        }

        return emptyList()
    }

    private fun loadSavedPlaylists() {
        try {
            val json = prefs.getString("playlists", "[]") ?: "[]"
            val arr = JSONArray(json)
            val playlists = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                IptvPlaylist(
                    id = obj.optString("id"), name = obj.optString("name"), url = obj.optString("url"),
                    epgUrl = obj.optString("epgUrl"), channelCount = obj.optInt("channelCount"),
                    lastUpdated = obj.optLong("lastUpdated"), isActive = obj.optBoolean("isActive")
                )
            }
            if (playlists.isNotEmpty()) {
                _state.update { it.copy(playlists = playlists) }
                playlists.lastOrNull { it.isActive }?.let { loadPlaylist(it.url, it.name, it.epgUrl, it.id) }
            }
        } catch (_: Exception) {}
    }

    private fun savePlaylistsToPrefs(playlists: List<IptvPlaylist>) {
        viewModelScope.launch(Dispatchers.IO) {
            try {
                val arr = JSONArray()
                playlists.forEach { pl ->
                    arr.put(JSONObject().apply {
                        put("id", pl.id); put("name", pl.name); put("url", pl.url)
                        put("epgUrl", pl.epgUrl); put("channelCount", pl.channelCount)
                        put("lastUpdated", pl.lastUpdated); put("isActive", pl.isActive)
                    })
                }
                prefs.edit().putString("playlists", arr.toString()).apply()
            } catch (_: Exception) {}
        }
    }

    private fun loadFavorites() { _state.update { it.copy(favoriteChannelIds = prefs.getStringSet("favorites", emptySet()) ?: emptySet()) } }
    private fun saveFavoritesToPrefs(favs: Set<String>) { prefs.edit().putStringSet("favorites", favs).apply() }
    private fun loadRecent() {
        val raw = prefs.getString("recent_channels", "") ?: ""
        _state.update { it.copy(recentChannelIds = if (raw.isBlank()) emptyList() else raw.split(",").filter { id -> id.isNotBlank() }) }
    }
    private fun saveRecentToPrefs(ids: List<String>) { prefs.edit().putString("recent_channels", ids.joinToString(",")).apply() }
}