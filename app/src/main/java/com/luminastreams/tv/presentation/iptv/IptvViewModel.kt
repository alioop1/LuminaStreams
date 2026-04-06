// 5. IptvViewModel.kt
package com.luminastreams.tv.presentation.iptv

import android.app.Application
import android.content.Context
import android.util.Log
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
    private var sleepTimerJob: Job? = null
    private var autoRefreshJob: Job? = null

    init {
        loadSavedPlaylists()
        loadFavorites()
        loadRecent()
        loadSettings()
    }

    private fun loadSettings() {
        _state.update {
            it.copy(
                parentalLockEnabled = prefs.getBoolean("parental_lock", false),
                parentalPin = prefs.getString("parental_pin", "") ?: "",
                subtitlesEnabled = prefs.getBoolean("subtitles_enabled", false),
            )
        }
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
                    if (!addr.isLoopbackAddress && addr is java.net.Inet4Address) {
                        addresses.add(addr.hostAddress ?: "")
                    }
                }
            }
            addresses.firstOrNull { it.startsWith("192.168.") }
                ?: addresses.firstOrNull { it.startsWith("10.") }
                ?: addresses.firstOrNull { it.startsWith("172.") }
                ?: addresses.firstOrNull() ?: ""
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
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
                _state.update {
                    it.copy(
                        showAddPlaylist = true,
                        addPlaylistName = "", addPlaylistUrl = "", addPlaylistEpgUrl = "",
                        localIpAddress = ip
                    )
                }
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
                _state.update {
                    it.copy(
                        showAddPlaylist = true,
                        addPlaylistName = event.playlist.name,
                        addPlaylistUrl = event.playlist.url,
                        addPlaylistEpgUrl = event.playlist.epgUrl,
                        localIpAddress = ip
                    )
                }
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
                webServerJob?.cancel()
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

            is IptvEvent.SetSleepTimer -> {
                _state.update { it.copy(sleepTimer = event.timer, showSleepTimerPicker = false) }
                startSleepTimer(event.timer)
            }
            is IptvEvent.DismissSleepTimer -> {
                sleepTimerJob?.cancel()
                _state.update { it.copy(sleepTimer = SleepTimer.OFF, sleepTimerRemainingMs = 0L) }
            }
            is IptvEvent.ShowSleepTimerPicker -> _state.update { it.copy(showSleepTimerPicker = true) }
            is IptvEvent.HideSleepTimerPicker -> _state.update { it.copy(showSleepTimerPicker = false) }

            is IptvEvent.SetChannelSort -> {
                _state.update { it.copy(channelSortMode = event.mode) }
                resortChannels(event.mode)
            }

            is IptvEvent.SetStreamQuality -> {
                _state.update { it.copy(streamQuality = event.quality) }
                prefs.edit { putString("stream_quality", event.quality.name) }
            }

            is IptvEvent.ToggleChannelGrid -> _state.update { it.copy(showChannelGrid = !it.showChannelGrid) }

            is IptvEvent.AddToMultiView -> {
                val current = _state.value.multiViewChannels.toMutableList()
                if (current.size < 4 && event.channel !in current) current.add(event.channel)
                _state.update { it.copy(multiViewChannels = current) }
            }
            is IptvEvent.RemoveFromMultiView -> {
                _state.update { it.copy(multiViewChannels = it.multiViewChannels.filter { ch -> ch.id != event.channel.id }) }
            }
            is IptvEvent.ToggleMultiView -> _state.update { it.copy(showMultiView = !it.showMultiView) }

            is IptvEvent.ToggleRecording -> {
                val ch = _state.value.currentChannel
                if (ch != null) {
                    val recording = !_state.value.isRecording
                    _state.update { it.copy(isRecording = recording, recordingChannelId = if (recording) ch.id else null) }
                }
            }

            is IptvEvent.SetParentalLock -> {
                _state.update { it.copy(parentalLockEnabled = event.enabled, parentalPin = event.pin) }
                prefs.edit {
                    putBoolean("parental_lock", event.enabled)
                    putString("parental_pin", event.pin)
                }
            }
            is IptvEvent.EnterParentalPin -> {
                val correctPin = _state.value.parentalPin
                if (event.pin == correctPin) {
                    val pending = _state.value.pendingLockedChannel
                    _state.update { it.copy(showParentalPinEntry = false, pendingLockedChannel = null) }
                    if (pending != null) selectChannel(pending)
                } else {
                    _state.update { it.copy(showParentalPinEntry = false, pendingLockedChannel = null) }
                }
            }
            is IptvEvent.DismissParentalPin -> _state.update { it.copy(showParentalPinEntry = false, pendingLockedChannel = null) }

            is IptvEvent.SetEpgDayOffset -> _state.update { it.copy(epgDayOffset = event.offset) }

            is IptvEvent.ShowIptvSettings -> _state.update { it.copy(showSettings = true) }
            is IptvEvent.HideIptvSettings -> _state.update { it.copy(showSettings = false) }

            is IptvEvent.ToggleSubtitles -> {
                val enabled = !_state.value.subtitlesEnabled
                _state.update { it.copy(subtitlesEnabled = enabled) }
                prefs.edit { putBoolean("subtitles_enabled", enabled) }
            }

            is IptvEvent.SelectAudioTrack -> _state.update { it.copy(audioTrackIndex = event.index) }

            is IptvEvent.ChannelUp -> {
                val list = _state.value.filteredChannels
                val idx = event.currentIndex
                if (idx > 0) selectChannel(list[idx - 1])
            }
            is IptvEvent.ChannelDown -> {
                val list = _state.value.filteredChannels
                val idx = event.currentIndex
                if (idx < list.size - 1) selectChannel(list[idx + 1])
            }
        }
    }

    private fun loadPlaylist(url: String, name: String, epgUrl: String, existingId: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loadState = IptvLoadState.Loading) }
            try {
                Log.d(TAG, "Loading M3U from: $url")
                val channels = M3uParser.parse(url).getOrThrow()
                val groups = buildGroupList(channels)

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
                        playlists = newPlaylists, activePlaylistId = playlistId,
                        channels = channels, groups = groups,
                        selectedGroup = "All", filteredChannels = sortChannels(channels, s.channelSortMode),
                        loadState = IptvLoadState.Success, showAddPlaylist = false
                    )
                }

                if (epgUrl.isNotBlank()) {
                    loadEpg(epgUrl)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to load playlist", e)
                _state.update { it.copy(loadState = IptvLoadState.Error("Failed: ${e.message?.take(60)}")) }
            }
        }
    }

    private fun buildGroupList(channels: List<IptvChannel>): List<String> {
        val specialGroups = listOf("All", "Favorites", "Recent")
        val channelGroups = channels.map { it.groupTitle }.distinct().sorted()
        return specialGroups + channelGroups
    }

    private fun sortChannels(channels: List<IptvChannel>, mode: ChannelSortMode): List<IptvChannel> {
        return when (mode) {
            ChannelSortMode.NAME_ASC -> channels.sortedBy { it.name.lowercase() }
            ChannelSortMode.NAME_DESC -> channels.sortedByDescending { it.name.lowercase() }
            ChannelSortMode.NUMBER -> channels.sortedBy { it.number }
            ChannelSortMode.RECENTLY_WATCHED -> {
                val recentIds = _state.value.recentChannelIds
                val recentMap = recentIds.mapIndexed { idx, id -> id to idx }.toMap()
                channels.sortedBy { recentMap[it.id] ?: Int.MAX_VALUE }
            }
            ChannelSortMode.DEFAULT -> channels
        }
    }

    private fun resortChannels(mode: ChannelSortMode) {
        val s = _state.value
        val base = when (s.selectedGroup) {
            "All" -> s.channels
            "Favorites" -> s.channels.filter { it.id in s.favoriteChannelIds }
            "Recent" -> s.recentChannelIds.mapNotNull { id -> s.channels.find { it.id == id } }
            else -> s.channels.filter { it.groupTitle == s.selectedGroup }
        }.let { list ->
            val q = s.searchQuery
            if (q.isBlank()) list else list.filter { it.name.contains(q, true) }
        }
        _state.update { it.copy(filteredChannels = sortChannels(base, mode)) }
    }

    private fun selectPlaylist(playlistId: String) {
        val playlist = _state.value.playlists.find { it.id == playlistId } ?: return
        loadPlaylist(playlist.url, playlist.name, playlist.epgUrl, playlist.id)
    }

    private fun deletePlaylist(playlistId: String) {
        val updated = _state.value.playlists.filter { it.id != playlistId }
        savePlaylistsToPrefs(updated)
        if (_state.value.activePlaylistId == playlistId) {
            _state.update {
                it.copy(
                    playlists = updated, activePlaylistId = null,
                    channels = emptyList(), filteredChannels = emptyList(), groups = emptyList()
                )
            }
        } else {
            _state.update { it.copy(playlists = updated) }
        }
    }

    private fun selectChannel(channel: IptvChannel) {
        if (_state.value.parentalLockEnabled && channel.isAdult) {
            _state.update { it.copy(showParentalPinEntry = true, pendingLockedChannel = channel) }
            return
        }

        val epg = getEpgForChannel(channel)
        val now = System.currentTimeMillis()
        val recentIds = (listOf(channel.id) + _state.value.recentChannelIds).distinct().take(30)
        saveRecentToPrefs(recentIds)

        _state.update {
            it.copy(
                currentChannel = channel,
                currentProgram = epg.firstOrNull { p -> p.isLiveNow },
                nextProgram = epg.firstOrNull { p -> p.startTime > now && !p.isLiveNow },
                recentChannelIds = recentIds,
                showMiniPlayer = false
            )
        }

        if (_state.value.selectedGroup == "Recent") selectGroup("Recent")

        if (_state.value.sleepTimer == SleepTimer.END_OF_PROGRAM) {
            val currentProg = _state.value.currentProgram
            if (currentProg != null) {
                sleepTimerJob?.cancel()
                sleepTimerJob = viewModelScope.launch {
                    val remaining = currentProg.endTime - System.currentTimeMillis()
                    if (remaining > 0) {
                        _state.update { it.copy(sleepTimerRemainingMs = remaining) }
                        delay(remaining)
                        _state.update { it.copy(currentChannel = null, sleepTimer = SleepTimer.OFF, sleepTimerRemainingMs = 0L) }
                    }
                }
            }
        }
    }

    private fun selectGroup(group: String) {
        val channels = _state.value.channels
        val favorites = _state.value.favoriteChannelIds
        val recentIds = _state.value.recentChannelIds

        val base = when (group) {
            "All" -> channels
            "Favorites" -> channels.filter { it.id in favorites }
            "Recent" -> recentIds.mapNotNull { id -> channels.find { it.id == id } }
            else -> channels.filter { it.groupTitle == group }
        }

        val query = _state.value.searchQuery
        val filtered = if (query.isBlank()) base else base.filter { it.name.contains(query, true) }
        val sorted = sortChannels(filtered, _state.value.channelSortMode)

        _state.update { it.copy(selectedGroup = group, filteredChannels = sorted) }
    }

    private fun updateSearch(query: String) {
        val s = _state.value
        val base = when (s.selectedGroup) {
            "All" -> s.channels
            "Favorites" -> s.channels.filter { it.id in s.favoriteChannelIds }
            "Recent" -> s.recentChannelIds.mapNotNull { id -> s.channels.find { it.id == id } }
            else -> s.channels.filter { it.groupTitle == s.selectedGroup }
        }
        val filtered = if (query.isBlank()) base else base.filter { ch ->
            ch.name.contains(query, true) ||
                    ch.groupTitle.contains(query, true) ||
                    ch.tvgId.contains(query, true)
        }
        _state.update { it.copy(searchQuery = query, filteredChannels = sortChannels(filtered, it.channelSortMode)) }
    }

    private fun toggleFavorite(channelId: String) {
        val favs = _state.value.favoriteChannelIds.toMutableSet()
        if (channelId in favs) favs.remove(channelId) else favs.add(channelId)
        saveFavoritesToPrefs(favs)
        _state.update { it.copy(favoriteChannelIds = favs) }
        if (_state.value.selectedGroup == "Favorites") selectGroup("Favorites")
    }

    private fun loadEpg(epgUrl: String) {
        Log.d(TAG, "Loading EPG from: $epgUrl")
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            _state.update { it.copy(epgLoadState = IptvLoadState.Loading) }
            try {
                val result = EpgParser.parse(epgUrl).getOrThrow()
                Log.d(TAG, "EPG loaded: ${result.programs.size} channel entries, ${result.channelLogos.size} logos")

                val updatedChannels = _state.value.channels.map { ch ->
                    val epgLogoKey = ch.tvgId.lowercase().ifEmpty { ch.name.lowercase() }
                    val epgLogo = result.channelLogos[epgLogoKey]
                        ?: result.channelLogos[ch.tvgName.lowercase()]
                        ?: result.channelLogos[ch.id.lowercase()]
                        ?: result.channelLogos[ch.name.lowercase()]
                    val mergedLogo = when {
                        !epgLogo.isNullOrBlank() -> epgLogo
                        ch.logoUrl.isNotBlank() -> ch.logoUrl
                        else -> ""
                    }
                    if (mergedLogo != ch.logoUrl) ch.copy(logoUrl = mergedLogo) else ch
                }

                val updatedFilteredChannels = _state.value.filteredChannels.map { fch ->
                    updatedChannels.find { it.id == fch.id } ?: fch
                }
                _state.update {
                    it.copy(
                        epgData = result.programs,
                        channelLogos = result.channelLogos,
                        channels = updatedChannels,
                        filteredChannels = updatedFilteredChannels,
                        epgLoadState = IptvLoadState.Success
                    )
                }

                selectGroup(_state.value.selectedGroup)

                _state.value.currentChannel?.let { ch ->
                    val epg = getEpgForChannel(ch)
                    val now = System.currentTimeMillis()
                    _state.update {
                        it.copy(
                            currentProgram = epg.firstOrNull { p -> p.isLiveNow },
                            nextProgram = epg.firstOrNull { p -> p.startTime > now && !p.isLiveNow }
                        )
                    }
                }

            } catch (e: Exception) {
                Log.e(TAG, "EPG load failed", e)
                _state.update { it.copy(epgLoadState = IptvLoadState.Error("EPG: ${e.message?.take(40)}")) }
            }
        }
    }

    private fun refreshEpg() {
        val activePlaylist = _state.value.playlists.find { it.id == _state.value.activePlaylistId }
        if (activePlaylist?.epgUrl?.isNotBlank() == true) loadEpg(activePlaylist.epgUrl)
    }

    fun getEpgForChannel(channel: IptvChannel, dataMap: Map<String, List<EpgProgram>> = _state.value.epgData): List<EpgProgram> {
        if (dataMap.isEmpty()) return emptyList()

        val lookupKeys = buildList {
            if (channel.tvgId.isNotEmpty()) add(channel.tvgId.lowercase())
            if (channel.tvgName.isNotEmpty()) add(channel.tvgName.lowercase())
            add(channel.id.lowercase())
            add(channel.name.lowercase())
            val cleanName = channel.name.lowercase()
                .replace(Regex("\\b(hd|fhd|4k|sd|uhd)\\b"), "")
                .replace(Regex("\\s+"), " ")
                .trim()
            if (cleanName != channel.name.lowercase()) add(cleanName)
        }.distinct()

        for (key in lookupKeys) {
            dataMap[key]?.let { if (it.isNotEmpty()) return it }
        }

        val normalize = { s: String ->
            s.lowercase()
                .replace(Regex("\\b(hd|fhd|4k|sd|uhd|tv|channel|ch)\\b"), "")
                .replace(Regex("[^a-z0-9\u0590-\u05FF]"), " ")
                .replace(Regex("\\s+"), " ")
                .trim()
        }

        val normalizedKeys = lookupKeys.map { normalize(it) }.filter { it.length > 2 }

        for (normKey in normalizedKeys) {
            val match = dataMap.entries.firstOrNull { (k, _) ->
                val normK = normalize(k)
                normK.isNotEmpty() && normK.length > 2 && (
                        normK == normKey ||
                                (normKey.length > 4 && normK.contains(normKey)) ||
                                (normKey.length > 4 && normKey.contains(normK))
                        )
            }
            if (match != null) return match.value
        }

        return emptyList()
    }

    private fun startSleepTimer(timer: SleepTimer) {
        sleepTimerJob?.cancel()
        if (timer == SleepTimer.OFF) {
            _state.update { it.copy(sleepTimerRemainingMs = 0L) }
            return
        }

        if (timer == SleepTimer.END_OF_PROGRAM) {
            val prog = _state.value.currentProgram
            if (prog != null) {
                val remaining = prog.endTime - System.currentTimeMillis()
                _state.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0)) }
                sleepTimerJob = viewModelScope.launch {
                    if (remaining > 0) delay(remaining)
                    _state.update { it.copy(currentChannel = null, sleepTimer = SleepTimer.OFF, sleepTimerRemainingMs = 0L) }
                }
            }
            return
        }

        val totalMs = timer.minutes * 60_000L
        _state.update { it.copy(sleepTimerRemainingMs = totalMs) }

        sleepTimerJob = viewModelScope.launch {
            var remaining = totalMs
            while (remaining > 0) {
                delay(1_000)
                remaining -= 1_000
                _state.update { it.copy(sleepTimerRemainingMs = remaining.coerceAtLeast(0)) }
            }
            _state.update { it.copy(currentChannel = null, sleepTimer = SleepTimer.OFF, sleepTimerRemainingMs = 0L) }
        }
    }

    private fun loadSavedPlaylists() {
        try {
            val json = prefs.getString("playlists", "[]") ?: "[]"
            val arr = JSONArray(json)
            val playlists = (0 until arr.length()).map { i ->
                val obj = arr.getJSONObject(i)
                IptvPlaylist(
                    id = obj.optString("id"),
                    name = obj.optString("name"),
                    url = obj.optString("url"),
                    epgUrl = obj.optString("epgUrl"),
                    channelCount = obj.optInt("channelCount"),
                    lastUpdated = obj.optLong("lastUpdated"),
                    isActive = obj.optBoolean("isActive"),
                    userAgent = obj.optString("userAgent", ""),
                    autoRefreshHours = obj.optInt("autoRefreshHours", 0),
                )
            }
            if (playlists.isNotEmpty()) {
                _state.update { it.copy(playlists = playlists) }
                playlists.lastOrNull { it.isActive }?.let {
                    loadPlaylist(it.url, it.name, it.epgUrl, it.id)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error loading playlists", e)
        }
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
                        put("userAgent", pl.userAgent); put("autoRefreshHours", pl.autoRefreshHours)
                    })
                }
                prefs.edit { putString("playlists", arr.toString()) }
            } catch (e: Exception) {
                Log.e(TAG, "Error saving playlists", e)
            }
        }
    }

    private fun loadFavorites() {
        _state.update { it.copy(favoriteChannelIds = prefs.getStringSet("favorites", emptySet()) ?: emptySet()) }
    }

    private fun saveFavoritesToPrefs(favs: Set<String>) {
        prefs.edit { putStringSet("favorites", favs) }
    }

    private fun loadRecent() {
        val raw = prefs.getString("recent_channels", "") ?: ""
        _state.update { it.copy(recentChannelIds = if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }) }
    }

    private fun saveRecentToPrefs(ids: List<String>) {
        prefs.edit { putString("recent_channels", ids.joinToString(",")) }
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        autoRefreshJob?.cancel()
        epgRefreshJob?.cancel()
        webServerJob?.cancel()
        LocalWebServer.stop()
        super.onCleared()
    }
}