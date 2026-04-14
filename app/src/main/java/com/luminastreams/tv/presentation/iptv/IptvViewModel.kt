package com.luminastreams.tv.presentation.iptv

import android.app.Application
import android.content.Context
import android.util.Log
import android.util.JsonReader
import android.util.JsonWriter
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.NetworkInterface
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.File
import java.io.FileReader
import java.io.FileWriter

class IptvViewModel(application: Application) : AndroidViewModel(application) {
    private val TAG = "EPG_DEBUG"
    private val prefs = application.getSharedPreferences("lumina_iptv", Context.MODE_PRIVATE)
    private val _state = MutableStateFlow(IptvState())
    val state: StateFlow<IptvState> = _state.asStateFlow()

    val channelState: StateFlow<ChannelState> = _state
        .map { s -> ChannelState(
            playlists          = s.playlists,
            activePlaylistId   = s.activePlaylistId,
            channels           = s.channels,
            groups             = s.groups,
            selectedGroup      = s.selectedGroup,
            filteredChannels   = s.filteredChannels,
            searchQuery        = s.searchQuery,
            epgData            = s.epgData,
            epgLoadState       = s.epgLoadState,
            channelLogos       = s.channelLogos,
            favoriteChannelIds = s.favoriteChannelIds,
            recentChannelIds   = s.recentChannelIds,
            channelSortMode    = s.channelSortMode,
            loadState          = s.loadState,
            viewMode           = s.viewMode,
        )}
        // Use reference equality (===) for collection fields so we avoid the catastrophically
        // expensive structural comparison of epgData (Map<String, List<EpgProgram>> with
        // potentially millions of entries). _state.update{it.copy()} reuses the same
        // collection references when those fields were not actually modified.
        .distinctUntilChanged { old, new ->
            old.loadState      == new.loadState      &&
                    old.epgLoadState   == new.epgLoadState   &&
                    old.selectedGroup  == new.selectedGroup  &&
                    old.searchQuery    == new.searchQuery    &&
                    old.channelSortMode == new.channelSortMode &&
                    old.viewMode       == new.viewMode       &&
                    old.activePlaylistId == new.activePlaylistId &&
                    old.playlists      === new.playlists     &&
                    old.channels       === new.channels      &&
                    old.filteredChannels === new.filteredChannels &&
                    old.groups         === new.groups        &&
                    old.epgData        === new.epgData       &&
                    old.channelLogos   === new.channelLogos  &&
                    old.favoriteChannelIds === new.favoriteChannelIds &&
                    old.recentChannelIds === new.recentChannelIds
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, ChannelState())

    val playerState: StateFlow<PlayerState> = _state
        .map { s -> PlayerState(
            currentChannel      = s.currentChannel,
            currentProgram      = s.currentProgram,
            nextProgram         = s.nextProgram,
            isRecording         = s.isRecording,
            recordingChannelId  = s.recordingChannelId,
            subtitlesEnabled    = s.subtitlesEnabled,
            audioTrackIndex     = s.audioTrackIndex,
            streamQuality       = s.streamQuality,
            sleepTimer          = s.sleepTimer,
            sleepTimerRemainingMs = s.sleepTimerRemainingMs,
            multiViewChannels   = s.multiViewChannels,
            showMultiView       = s.showMultiView,
            showMiniPlayer      = s.showMiniPlayer,
        )}
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, PlayerState())

    val uiState: StateFlow<UiState> = _state
        .map { s -> UiState(
            showAddPlaylist      = s.showAddPlaylist,
            showEpgGuide         = s.showEpgGuide,
            showQrCode           = s.showQrCode,
            qrCodeChannel        = s.qrCodeChannel,
            showSleepTimerPicker = s.showSleepTimerPicker,
            showSettings         = s.showSettings,
            showParentalPinEntry = s.showParentalPinEntry,
            pendingLockedChannel = s.pendingLockedChannel,
            parentalLockEnabled  = s.parentalLockEnabled,
            parentalPin          = s.parentalPin,
            addPlaylistName      = s.addPlaylistName,
            addPlaylistUrl       = s.addPlaylistUrl,
            addPlaylistEpgUrl    = s.addPlaylistEpgUrl,
            localIpAddress       = s.localIpAddress,
            epgDayOffset         = s.epgDayOffset,
        )}
        .distinctUntilChanged()
        .stateIn(viewModelScope, SharingStarted.Eagerly, UiState())

    private var epgRefreshJob: Job? = null
    private var webServerJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var autoRefreshJob: Job? = null
    private var searchJob: Job? = null
    private val epgIndex = HashMap<String, List<EpgProgram>>(512)
    private val _sleepTimerMs = MutableStateFlow(0L)
    val sleepTimerMs: StateFlow<Long> = _sleepTimerMs.asStateFlow()
    @Volatile private var epgLoadInProgress = false
    private val epgCacheDir by lazy { application.cacheDir.also { it.mkdirs() } }
    private fun epgCacheFile(epgUrl: String) = File(epgCacheDir, "epg_${epgUrl.hashCode()}.json")
    private fun epgCacheTimeKey(epgUrl: String) = "epg_ts_${epgUrl.hashCode()}"

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
            is IptvEvent.HideIptvSettings -> _state.update { it.copy(showSettings = false) }
            is IptvEvent.ShowIptvSettings -> _state.update { it.copy(showSettings = true) }
            is IptvEvent.SetEpgDayOffset -> _state.update { it.copy(epgDayOffset = event.offset) }
            is IptvEvent.ToggleSubtitles -> {
                val enabled = !_state.value.subtitlesEnabled
                _state.update { it.copy(subtitlesEnabled = enabled) }
                prefs.edit { putBoolean("subtitles_enabled", enabled) }
            }
            is IptvEvent.SelectAudioTrack -> {
                _state.update { it.copy(audioTrackIndex = event.index) }
            }
            is IptvEvent.ChannelUp -> {
                val channels = _state.value.filteredChannels
                if (channels.isEmpty()) return
                val nextIndex = (event.currentIndex + 1).coerceAtMost(channels.lastIndex)
                selectChannel(channels[nextIndex])
            }
            is IptvEvent.ChannelDown -> {
                val channels = _state.value.filteredChannels
                if (channels.isEmpty()) return
                val prevIndex = (event.currentIndex - 1).coerceAtLeast(0)
                selectChannel(channels[prevIndex])
            }
        }
    }

    private fun baseChannelsForGroup(s: IptvState, group: String): List<IptvChannel> = when (group) {
        "All"       -> s.channels
        "Favorites" -> s.channels.filter { it.id in s.favoriteChannelIds }
        "Recent"    -> s.recentChannelIds.mapNotNull { id -> s.channels.find { it.id == id } }
        else        -> s.channels.filter { it.groupTitle == group }
    }

    private fun updateSearch(query: String) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(120)
            val filtered = withContext(Dispatchers.Default) {
                val s = _state.value
                val base = baseChannelsForGroup(s, s.selectedGroup)
                if (query.isBlank()) base else base.filter { it.name.contains(query, ignoreCase = true) }
            }
            _state.update { it.copy(searchQuery = query, filteredChannels = sortChannels(filtered, it.channelSortMode)) }
        }
    }

    fun selectGroup(group: String) {
        viewModelScope.launch {
            val filtered = withContext(Dispatchers.Default) {
                val s = _state.value
                val base = baseChannelsForGroup(s, group)
                val q = s.searchQuery
                if (q.isBlank()) base else base.filter { it.name.contains(q, ignoreCase = true) }
            }
            _state.update { it.copy(selectedGroup = group, filteredChannels = sortChannels(filtered, it.channelSortMode)) }
        }
    }

    private fun selectChannel(channel: IptvChannel) {
        if (_state.value.parentalLockEnabled && channel.isAdult) {
            _state.update { it.copy(showParentalPinEntry = true, pendingLockedChannel = channel) }
            return
        }
        val epg = getEpgForChannel(channel)
        val now = System.currentTimeMillis()
        _state.update {
            it.copy(
                currentChannel = channel,
                currentProgram = epg.firstOrNull { p -> p.isLiveNow },
                nextProgram = epg.firstOrNull { p -> p.startTime > now && !p.isLiveNow }
            )
        }
        updateRecent(channel.id)
    }

    private fun updateRecent(id: String) {
        val recent = _state.value.recentChannelIds.toMutableList()
        recent.remove(id)
        recent.add(0, id)
        val trimmed = recent.take(50)
        saveRecentToPrefs(trimmed)
        _state.update { it.copy(recentChannelIds = trimmed) }
        if (_state.value.selectedGroup == "Recent") selectGroup("Recent")
    }

    private fun toggleFavorite(channelId: String) {
        val favs = _state.value.favoriteChannelIds.toMutableSet()
        if (channelId in favs) favs.remove(channelId) else favs.add(channelId)
        saveFavoritesToPrefs(favs)
        _state.update { it.copy(favoriteChannelIds = favs) }
        if (_state.value.selectedGroup == "Favorites") selectGroup("Favorites")
    }

    private fun loadEpgWithCache(epgUrl: String) {
        if (epgLoadInProgress) return
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            epgLoadInProgress = true
            _state.update { it.copy(epgLoadState = IptvLoadState.Loading) }
            try {
                val cacheFile = epgCacheFile(epgUrl)
                val cacheTs = prefs.getLong(epgCacheTimeKey(epgUrl), 0L)
                val isCacheValid = (System.currentTimeMillis() - cacheTs) < 24 * 60 * 60 * 1000L // תוקף ל-24 שעות

                if (cacheFile.exists() && cacheFile.length() > 100) {
                    try {
                        val result = withContext(Dispatchers.IO) { deserializeEpgResult(cacheFile) }
                        if (result != null) {
                            applyEpgResult(result)
                            if (isCacheValid) return@launch // הקאש מעודכן מהיום? עוצרים כאן ולא מעמיסים על האינטרנט.
                            // אם עבר יום - הצגנו את הישן מיד כדי שהממשק לא ייתקע, ועכשיו נוריד חדש ברקע.
                        }
                    } catch (e: Exception) {
                        Log.w(TAG, "EPG cache read failed: ${e.message}")
                    }
                }
                loadEpgFresh(epgUrl)
            } catch (e: Exception) {
                _state.update { it.copy(epgLoadState = IptvLoadState.Error("EPG: ${e.message?.take(40)}")) }
            } finally {
                epgLoadInProgress = false
            }
        }
    }

    private suspend fun loadEpgFresh(epgUrl: String) {
        _state.update { it.copy(epgLoadState = IptvLoadState.Loading) }
        try {
            val allowedIds = buildSet<String> {
                _state.value.channels.forEach { ch ->
                    if (ch.tvgId.isNotEmpty()) add(ch.tvgId.lowercase())
                    if (ch.tvgName.isNotEmpty()) add(ch.tvgName.lowercase())
                    add(ch.name.lowercase())
                }
            }
            val result = withContext(Dispatchers.IO) {
                EpgParser.parse(epgUrl, allowedIds).getOrThrow()
            }
            withContext(Dispatchers.IO) {
                try {
                    val cacheFile = epgCacheFile(epgUrl)
                    serializeEpgResult(result, cacheFile)
                    prefs.edit { putLong(epgCacheTimeKey(epgUrl), System.currentTimeMillis()) }
                } catch (e: Exception) {}
            }
            applyEpgResult(result)
        } catch (e: Exception) {
            _state.update { it.copy(epgLoadState = IptvLoadState.Error("EPG: ${e.message?.take(40)}")) }
        }
    }

    private fun refreshEpg() {
        val activePlaylist = _state.value.playlists.find { it.id == _state.value.activePlaylistId }
        if (activePlaylist?.epgUrl?.isNotBlank() == true) {
            epgLoadInProgress = false
            loadEpgFreshForced(activePlaylist.epgUrl)
        }
    }

    private fun loadEpgFreshForced(epgUrl: String) {
        if (epgLoadInProgress) return
        epgRefreshJob?.cancel()
        epgRefreshJob = viewModelScope.launch {
            epgLoadInProgress = true
            try { loadEpgFresh(epgUrl) } finally { epgLoadInProgress = false }
        }
    }

    private fun resolveEpgLogo(ch: IptvChannel, logoMap: Map<String, String>): String? {
        if (logoMap.isEmpty()) return null
        val keys = listOfNotNull(
            ch.tvgId.lowercase().ifBlank { null },
            ch.tvgName.lowercase().ifBlank { null },
            ch.id.lowercase().ifBlank { null },
            ch.name.lowercase().ifBlank { null }
        )
        keys.forEach { k -> logoMap[k]?.takeIf { it.isNotBlank() }?.let { return it } }
        return logoMap.entries.firstOrNull { (epgKey, logo) ->
            logo.isNotBlank() && keys.any { k ->
                k.length >= 3 && (epgKey.contains(k) || k.contains(epgKey))
            }
        }?.value
    }

    private suspend fun applyEpgResult(result: EpgParser.EpgResult) = withContext(Dispatchers.Default) {
        val epgProgramsForChannel = mutableMapOf<String, List<EpgProgram>>()
        _state.value.channels.forEach { ch ->
            val keys = listOfNotNull(
                ch.tvgId.lowercase().ifBlank { null },
                ch.tvgName.lowercase().ifBlank { null },
                ch.id.lowercase(),
                ch.name.lowercase()
            )
            val programs = keys.firstNotNullOfOrNull { k ->
                result.programs[k]?.takeIf { it.isNotEmpty() }
            } ?: run {
                result.programs.entries.firstOrNull { (epgKey, progs) ->
                    progs.isNotEmpty() && keys.any { k ->
                        epgKey.contains(k) || k.contains(epgKey)
                    }
                }?.value
            }
            if (programs != null) {
                epgProgramsForChannel[ch.id] = programs
            }
        }
        val updatedChannels = _state.value.channels.map { ch ->
            // חיסול פריזים: אם לערוץ כבר יש לוגו תקין, מדלגים עליו לחלוטין (חוסך מיליוני ריצות לולאה)
            if (ch.logoUrl.isNotBlank()) return@map ch

            val epgLogo = resolveEpgLogo(ch, result.channelLogos)
            if (!epgLogo.isNullOrBlank()) ch.copy(logoUrl = epgLogo) else ch
        }
        val updatedById = updatedChannels.associateBy { it.id }
        val updatedFilteredChannels = _state.value.filteredChannels.map { fch ->
            updatedById[fch.id] ?: fch
        }
        val mergedEpgData = result.programs.toMutableMap()
        epgProgramsForChannel.forEach { (chId, progs) ->
            mergedEpgData[chId] = progs
        }
        _state.update {
            it.copy(
                epgData = mergedEpgData,
                channelLogos = result.channelLogos,
                channels = updatedChannels,
                filteredChannels = updatedFilteredChannels,
                epgLoadState = IptvLoadState.Success
            )
        }
        buildEpgIndex(updatedChannels, mergedEpgData)
        _state.value.currentChannel?.let { ch ->
            val updatedCh = updatedById[ch.id] ?: ch
            val epg = getEpgForChannel(updatedCh, mergedEpgData)
            val now = System.currentTimeMillis()
            _state.update {
                it.copy(
                    currentChannel = updatedCh,
                    currentProgram = epg.firstOrNull { p -> p.isLiveNow },
                    nextProgram = epg.firstOrNull { p -> p.startTime > now && !p.isLiveNow }
                )
            }
        }
    }

    private fun serializeEpgResult(result: EpgParser.EpgResult, outFile: File) {
        BufferedWriter(FileWriter(outFile)).use { fw ->
            JsonWriter(fw).use { writer ->
                writer.beginObject()
                writer.name("programs")
                writer.beginObject()
                for ((channelId, progList) in result.programs) {
                    writer.name(channelId)
                    writer.beginArray()
                    for (p in progList) {
                        writer.beginObject()
                        writer.name("channelId").value(p.channelId)
                        writer.name("title").value(p.title)
                        writer.name("desc").value(p.description)
                        writer.name("start").value(p.startTime)
                        writer.name("end").value(p.endTime)
                        writer.name("cat").value(p.category)
                        writer.name("icon").value(p.posterUrl)
                        writer.name("ep").value(p.episodeNum)
                        writer.name("rat").value(p.rating)
                        writer.endObject()
                    }
                    writer.endArray()
                }
                writer.endObject()
                writer.name("logos")
                writer.beginObject()
                for ((k, v) in result.channelLogos) {
                    writer.name(k).value(v)
                }
                writer.endObject()
                writer.endObject()
            }
        }
    }

    private fun deserializeEpgResult(cacheFile: File): EpgParser.EpgResult? {
        return try {
            val programs = mutableMapOf<String, List<EpgProgram>>()
            val logos = mutableMapOf<String, String>()
            BufferedReader(FileReader(cacheFile)).use { fr ->
                JsonReader(fr).use { reader ->
                    reader.beginObject()
                    while (reader.hasNext()) {
                        when (reader.nextName()) {
                            "programs" -> {
                                reader.beginObject()
                                while (reader.hasNext()) {
                                    val channelId = reader.nextName()
                                    val list = mutableListOf<EpgProgram>()
                                    reader.beginArray()
                                    while (reader.hasNext()) {
                                        var cId = ""; var title = ""; var desc = ""
                                        var start = 0L; var end = 0L; var cat = ""
                                        var icon = ""; var ep = ""; var rat = ""
                                        reader.beginObject()
                                        while (reader.hasNext()) {
                                            when (reader.nextName()) {
                                                "channelId" -> cId = reader.nextString()
                                                "title"     -> title = reader.nextString()
                                                "desc"      -> desc = reader.nextString()
                                                "start"     -> start = reader.nextLong()
                                                "end"       -> end = reader.nextLong()
                                                "cat"       -> cat = reader.nextString()
                                                "icon"      -> icon = reader.nextString()
                                                "ep"        -> ep = reader.nextString()
                                                "rat"       -> rat = reader.nextString()
                                                else        -> reader.skipValue()
                                            }
                                        }
                                        reader.endObject()
                                        list.add(EpgProgram(
                                            channelId = cId, title = title, description = desc,
                                            startTime = start, endTime = end, category = cat,
                                            posterUrl = icon, episodeNum = ep, rating = rat
                                        ))
                                    }
                                    reader.endArray()
                                    programs[channelId] = list
                                }
                                reader.endObject()
                            }
                            "logos" -> {
                                reader.beginObject()
                                while (reader.hasNext()) { logos[reader.nextName()] = reader.nextString() }
                                reader.endObject()
                            }
                            else -> reader.skipValue()
                        }
                    }
                    reader.endObject()
                }
            }
            EpgParser.EpgResult(programs = programs, channelLogos = logos, channelDisplayNames = emptyMap())
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun buildEpgIndex(channels: List<IptvChannel>, dataMap: Map<String, List<EpgProgram>>) = withContext(Dispatchers.Default) {
        // Pre-build a reverse lookup: every epgKey → its programs (already in dataMap).
        // Then for each channel, check exact keys first, and only do fuzzy fallback
        // against a pre-lowercased key set — avoiding O(channels × epg_entries) scans.
        val epgKeysLower = dataMap.keys.map { it.lowercase() to it }.toMap()

        val newIndex = HashMap<String, List<EpgProgram>>(channels.size)
        channels.forEach { ch ->
            val keys = buildChannelKeys(ch) // already lowercased
            // 1. Exact match
            val exactPrograms = keys.firstNotNullOfOrNull { k -> dataMap[k]?.takeIf { it.isNotEmpty() } }
            if (exactPrograms != null) {
                newIndex[ch.id] = exactPrograms
                return@forEach
            }
            // 2. Fuzzy match — only scan the key list (strings), not the program lists
            val fuzzyKey = keys.firstOrNull { k ->
                k.length >= 3 && epgKeysLower.keys.any { ek -> ek.contains(k) || k.contains(ek) }
            }
            if (fuzzyKey != null) {
                val originalKey = epgKeysLower.entries
                    .firstOrNull { (ek, _) -> ek.contains(fuzzyKey) || fuzzyKey.contains(ek) }?.value
                val progs = originalKey?.let { dataMap[it]?.takeIf { p -> p.isNotEmpty() } }
                if (progs != null) newIndex[ch.id] = progs
            }
        }
        withContext(Dispatchers.Main) {
            epgIndex.clear()
            epgIndex.putAll(newIndex)
        }
    }

    private fun buildChannelKeys(ch: IptvChannel) = listOfNotNull(
        ch.tvgId.lowercase().ifBlank { null },
        ch.tvgName.lowercase().ifBlank { null },
        ch.id.lowercase(),
        ch.name.lowercase()
    )

    fun getEpgForChannel(channel: IptvChannel, dataMap: Map<String, List<EpgProgram>> = _state.value.epgData): List<EpgProgram> {
        epgIndex[channel.id]?.let { return it }
        val keys = buildChannelKeys(channel)
        return keys.firstNotNullOfOrNull { k -> dataMap[k]?.takeIf { it.isNotEmpty() } }
            ?: dataMap.entries.firstOrNull { (epgKey, progs) ->
                progs.isNotEmpty() && keys.any { k -> epgKey.contains(k) || k.contains(epgKey) }
            }?.value
            ?: emptyList()
    }

    private fun loadPlaylist(url: String, name: String, epgUrl: String, existingId: String? = null) {
        viewModelScope.launch {
            _state.update { it.copy(loadState = IptvLoadState.Loading) }
            try {
                // העברת חישובי המערך הענקי לליבות הרקע של המעבד כדי לא לתקוע את הממשק
                val (channels, groups, sorted) = withContext(Dispatchers.Default) {
                    val rawChannels = M3uParser.parse(url).getOrThrow()
                    val existingLogoMap = _state.value.channelLogos
                    val chs = if (existingLogoMap.isEmpty()) rawChannels else {
                        rawChannels.map { ch ->
                            val epgLogo = resolveEpgLogo(ch, existingLogoMap)
                            if (!epgLogo.isNullOrBlank() && epgLogo != ch.logoUrl) ch.copy(logoUrl = epgLogo) else ch
                        }
                    }
                    val grps = buildGroupList(chs)
                    val srt = sortChannels(chs, _state.value.channelSortMode)
                    Triple(chs, grps, srt)
                }

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
                        selectedGroup = "All", filteredChannels = sorted,
                        loadState = IptvLoadState.Success, showAddPlaylist = false
                    )
                }
                if (epgUrl.isNotBlank()) {
                    loadEpgWithCache(epgUrl)
                }
            } catch (e: Exception) {
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
            ChannelSortMode.NAME_ASC  -> channels.sortedWith(Comparator { a, b -> a.name.compareTo(b.name, ignoreCase = true) })
            ChannelSortMode.NAME_DESC -> channels.sortedWith(Comparator { a, b -> b.name.compareTo(a.name, ignoreCase = true) })
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
        viewModelScope.launch {
            val sorted = withContext(Dispatchers.Default) {
                val s = _state.value
                val base = baseChannelsForGroup(s, s.selectedGroup).let { list ->
                    val q = s.searchQuery
                    if (q.isBlank()) list else list.filter { it.name.contains(q, ignoreCase = true) }
                }
                sortChannels(base, mode)
            }
            _state.update { it.copy(filteredChannels = sorted) }
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

    private fun startSleepTimer(timer: SleepTimer) {
        sleepTimerJob?.cancel()
        if (timer == SleepTimer.OFF) return
        sleepTimerJob = viewModelScope.launch {
            val endTime = System.currentTimeMillis() + timer.minutes * 60_000L
            while (true) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) break
                _sleepTimerMs.update { remaining.coerceAtLeast(0) }
                delay(1000)
            }
            _sleepTimerMs.update { 0L }
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
                    id = obj.optString("id"), name = obj.optString("name"), url = obj.optString("url"),
                    epgUrl = obj.optString("epgUrl"), channelCount = obj.optInt("channelCount"),
                    lastUpdated = obj.optLong("lastUpdated"), isActive = obj.optBoolean("isActive"),
                    userAgent = obj.optString("userAgent", ""), autoRefreshHours = obj.optInt("autoRefreshHours", 0)
                )
            }
            if (playlists.isNotEmpty()) {
                _state.update { it.copy(playlists = playlists) }
                playlists.lastOrNull { it.isActive }?.let { loadPlaylist(it.url, it.name, it.epgUrl, it.id) }
            }
        } catch (e: Exception) {}
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
            } catch (e: Exception) {}
        }
    }

    private fun loadFavorites() {
        _state.update { it.copy(favoriteChannelIds = prefs.getStringSet("favorites", emptySet()) ?: emptySet()) }
    }

    private fun saveFavoritesToPrefs(favs: Set<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit { putStringSet("favorites", favs) }
        }
    }

    private fun loadRecent() {
        val raw = prefs.getString("recent_channels", "") ?: ""
        _state.update { it.copy(recentChannelIds = if (raw.isBlank()) emptyList() else raw.split(",").filter { it.isNotBlank() }) }
    }

    private fun saveRecentToPrefs(ids: List<String>) {
        viewModelScope.launch(Dispatchers.IO) {
            prefs.edit { putString("recent_channels", ids.joinToString(",")) }
        }
    }

    override fun onCleared() {
        sleepTimerJob?.cancel()
        searchJob?.cancel()
        autoRefreshJob?.cancel()
        epgRefreshJob?.cancel()
        webServerJob?.cancel()
        LocalWebServer.stop()
        super.onCleared()
    }
}