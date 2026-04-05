package com.luminastreams.tv.presentation.iptv

import android.app.Application
import android.content.Context
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

class IptvViewModel(application: Application) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("lumina_iptv", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(IptvState())
    val state: StateFlow<IptvState> = _state.asStateFlow()

    private var epgRefreshJob: Job? = null

    init {
        loadSavedPlaylists()
        loadFavorites()
        loadRecent()
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
            is IptvEvent.ShowAddPlaylist -> _state.update { it.copy(showAddPlaylist = true, addPlaylistName = "", addPlaylistUrl = "", addPlaylistEpgUrl = "") }
            is IptvEvent.ShowEditPlaylist -> _state.update { it.copy(showAddPlaylist = true, addPlaylistName = event.playlist.name, addPlaylistUrl = event.playlist.url, addPlaylistEpgUrl = event.playlist.epgUrl) }
            is IptvEvent.HideAddPlaylist -> _state.update { it.copy(showAddPlaylist = false, addPlaylistName = "", addPlaylistUrl = "", addPlaylistEpgUrl = "") }
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
                val channels = M3uParser.parse(url).getOrThrow()
                // שומר על הסדר האמיתי של הקובץ בדיוק כמו שביקשת!
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

                if (epgUrl.isNotBlank()) loadEpg(epgUrl)
            } catch (e: Exception) {
                _state.update { it.copy(loadState = IptvLoadState.Error("Failed: ${e.message?.take(60)}")) }
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
        val epg = _state.value.epgData[channel.tvgId] ?: _state.value.epgData[channel.id]
        val now = System.currentTimeMillis()
        val recentIds = (listOf(channel.id) + _state.value.recentChannelIds).distinct().take(20)
        saveRecentToPrefs(recentIds)
        _state.update { it.copy(currentChannel = channel, currentProgram = epg?.firstOrNull { p -> p.isLive }, nextProgram = epg?.firstOrNull { p -> p.startTime > now }, recentChannelIds = recentIds) }
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
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            _state.update { it.copy(epgLoadState = IptvLoadState.Loading) }
            try {
                val epgMap = EpgParser.parse(epgUrl).getOrThrow()
                _state.update { it.copy(epgData = epgMap, epgLoadState = IptvLoadState.Success) }
                _state.value.currentChannel?.let { selectChannel(it) }
            } catch (e: Exception) {
                _state.update { it.copy(epgLoadState = IptvLoadState.Error("EPG: ${e.message?.take(50)}")) }
            }
        }
    }

    private fun refreshEpg() {
        val activePlaylist = _state.value.playlists.find { it.id == _state.value.activePlaylistId }
        if (activePlaylist?.epgUrl?.isNotBlank() == true) loadEpg(activePlaylist.epgUrl)
    }

    fun getEpgForChannel(channel: IptvChannel): List<EpgProgram> {
        val epgData = _state.value.epgData
        return epgData[channel.tvgId] ?: epgData[channel.id] ?: epgData[channel.tvgName] ?: emptyList()
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