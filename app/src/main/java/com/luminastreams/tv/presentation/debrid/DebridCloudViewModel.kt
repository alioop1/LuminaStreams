package com.luminastreams.tv.presentation.debrid

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.LuminaApp
import com.luminastreams.tv.domain.repository.MediaRepository
import com.luminastreams.tv.domain.usecase.AllDebridManager
import com.luminastreams.tv.domain.usecase.AllDebridTokenKeeper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class DebridCloudViewModel(
    private val repository: MediaRepository,
    application: Application
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(DebridCloudState())
    val state: StateFlow<DebridCloudState> = _state.asStateFlow()

    private val adManager = AllDebridManager(application)
    private val isHebrew: Boolean = (application as LuminaApp).settingsRepository.current().appLanguage == "he"
    private val tmdbCache = ConcurrentHashMap<String, TmdbMeta>()

    private data class TmdbMeta(
        val posterUrl: String?,
        val backdropUrl: String?,
        val overview: String?,
        val rating: Float,
        val year: String,
        val mediaType: String
    )

    private val videoExtensions = setOf(
        "mkv", "mp4", "avi", "ts", "mov", "m4v", "webm", "flv", "wmv", "mpg", "mpeg"
    )

    init {
        loadTorrents()
    }

    fun onEvent(event: DebridCloudEvent) {
        when (event) {
            is DebridCloudEvent.Refresh -> loadTorrents(isRefresh = true)
            is DebridCloudEvent.OnTorrentClick -> handleTorrentClick(event.item)
            is DebridCloudEvent.OnPlayFile -> playSpecificFile(event.file, event.torrentItem)
            is DebridCloudEvent.DeleteTorrent -> deleteTorrent(event.torrentId)
            is DebridCloudEvent.DismissFilesDialog -> _state.update {
                it.copy(selectedTorrentForFiles = null, filesForSelectedTorrent = emptyList(), isLoadingFiles = false)
            }
            is DebridCloudEvent.ClearPlayTarget -> _state.update { it.copy(readyToPlay = null) }
        }
    }

    private fun getToken(): String = AllDebridTokenKeeper.getValidToken() ?: ""

    fun loadTorrents(isRefresh: Boolean = false) {
        viewModelScope.launch {
            val token = getToken()
            if (token.isBlank()) {
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        isNotAuthenticated = true,
                        error = null,
                        items = emptyList()
                    )
                }
                return@launch
            }

            _state.update {
                if (isRefresh) it.copy(isRefreshing = true, error = null)
                else it.copy(isLoading = true, error = null, isNotAuthenticated = false)
            }

            val result = adManager.getUserMagnets(token)
            result.fold(
                onSuccess = { rawMagnets ->
                    // Sort: Ready (4) first, then by uploadDate descending
                    val sorted = rawMagnets.sortedWith(
                        compareByDescending<AllDebridManager.AdMagnetItem> { it.statusCode == 4 }
                            .thenByDescending { it.uploadDate }
                    )

                    val uiItems = sorted.map { m ->
                        val cleanTitle = cleanTorrentTitle(m.filename)
                        val searchTitle = cleanSearchQuery(cleanTitle)
                        val percent = if (m.statusCode == 4) 100
                        else if (m.size > 0) ((m.downloaded.toDouble() / m.size.toDouble()) * 100).toInt().coerceIn(0, 99)
                        else 0

                        DebridTorrentUiItem(
                            id = m.id,
                            rawFilename = m.filename,
                            displayTitle = cleanTitle,
                            cleanSearchTitle = searchTitle,
                            sizeFormatted = formatBytes(m.size),
                            sizeBytes = m.size,
                            status = m.status,
                            statusCode = m.statusCode,
                            progressPercent = percent,
                            uploadDateFormatted = formatTimestamp(m.uploadDate),
                            nbLinks = m.nbLinks
                        )
                    }

                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = null,
                            isNotAuthenticated = false,
                            items = uiItems
                        )
                    }

                    // Asynchronously enrich with TMDB metadata for posters and backdrops
                    enrichWithTmdb(uiItems)
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = if (isHebrew) "שגיאה בטעינת הענן מ-AllDebrid: ${err.message}"
                            else "Failed to load AllDebrid library: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    private fun enrichWithTmdb(items: List<DebridTorrentUiItem>) {
        viewModelScope.launch(Dispatchers.IO) {
            items.forEach { item ->
                val query = item.cleanSearchTitle
                if (query.isBlank()) return@forEach

                val meta = tmdbCache[query] ?: run {
                    val searchRes = repository.searchMulti(query, 1, isHebrew).getOrNull()?.firstOrNull()
                    if (searchRes != null) {
                        val rawBackdrop = searchRes.backdropUrl?.takeIf { it.isNotBlank() } ?: searchRes.posterUrl.takeIf { it.isNotBlank() }
                        val highResBackdrop = rawBackdrop?.replace(Regex("""/w\d+/"""), "/original/")
                        val highResPoster = searchRes.posterUrl.replace(Regex("""/w\d+/"""), "/w500/").takeIf { it.isNotBlank() }

                        val m = TmdbMeta(
                            posterUrl = highResPoster,
                            backdropUrl = highResBackdrop,
                            overview = searchRes.overview.takeIf { it.isNotBlank() },
                            rating = searchRes.rating,
                            year = searchRes.releaseYear,
                            mediaType = if (searchRes.type.name.contains("TV", true)) "tv" else "movie"
                        )
                        tmdbCache[query] = m
                        m
                    } else null
                }

                if (meta != null) {
                    _state.update { s ->
                        s.copy(items = s.items.map { cur ->
                            if (cur.id == item.id) {
                                cur.copy(
                                    posterUrl = meta.posterUrl,
                                    backdropUrl = meta.backdropUrl,
                                    overview = meta.overview,
                                    rating = meta.rating,
                                    year = meta.year,
                                    mediaType = meta.mediaType
                                )
                            } else cur
                        })
                    }
                }
            }
        }
    }

    private fun handleTorrentClick(item: DebridTorrentUiItem) {
        if (item.statusCode != 4) {
            // Not ready yet
            val msg = if (item.statusCode in 1..3) {
                if (isHebrew) "הטורנט עדיין בהורדה ב-AllDebrid (${item.progressPercent}%)"
                else "Torrent is still downloading on AllDebrid (${item.progressPercent}%)"
            } else {
                if (isHebrew) "הטורנט אינו מוכן לצפייה (${item.status})"
                else "Torrent is not ready (${item.status})"
            }
            _state.update { it.copy(error = msg) }
            return
        }

        viewModelScope.launch {
            val token = getToken()
            if (token.isBlank()) return@launch

            _state.update {
                it.copy(
                    resolvingStream = true,
                    resolvingMessage = if (isHebrew) "בודק קבצים בטורנט..." else "Inspecting files...",
                    error = null
                )
            }

            val filesRes = adManager.getMagnetFiles(token, item.id)
            filesRes.fold(
                onSuccess = { allFiles ->
                    val videoFiles = allFiles.filter { f ->
                        val ext = f.name.substringAfterLast('.', "").lowercase(Locale.ROOT)
                        ext in videoExtensions && !f.name.lowercase(Locale.ROOT).contains("sample")
                    }

                    if (videoFiles.isEmpty()) {
                        _state.update {
                            it.copy(
                                resolvingStream = false,
                                error = if (isHebrew) "לא נמצאו קבצי וידאו בטורנט זה" else "No video files found in this torrent"
                            )
                        }
                    } else if (videoFiles.size == 1) {
                        // Single video file -> unlock and play immediately!
                        unlockAndPlay(videoFiles.first(), item)
                    } else {
                        // Multi-file torrent (e.g. TV series season pack) -> show file picker
                        _state.update {
                            it.copy(
                                resolvingStream = false,
                                selectedTorrentForFiles = item,
                                filesForSelectedTorrent = videoFiles,
                                isLoadingFiles = false
                            )
                        }
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            resolvingStream = false,
                            error = if (isHebrew) "שגיאה בפתיחת הטורנט: ${err.message}" else "Failed to open torrent: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    private fun playSpecificFile(file: AllDebridManager.AdFile, torrentItem: DebridTorrentUiItem) {
        viewModelScope.launch {
            unlockAndPlay(file, torrentItem)
        }
    }

    private suspend fun unlockAndPlay(file: AllDebridManager.AdFile, torrentItem: DebridTorrentUiItem) {
        val token = getToken()
        if (token.isBlank()) return

        _state.update {
            it.copy(
                resolvingStream = true,
                resolvingMessage = if (isHebrew) "פותח קישור להזרמה..." else "Unlocking direct stream...",
                error = null
            )
        }

        val unlockRes = adManager.unlockLinkDirect(token, file.link)
        unlockRes.fold(
            onSuccess = { directUrl ->
                val displayTitle = cleanTorrentTitle(file.name).ifBlank { torrentItem.displayTitle }
                _state.update {
                    it.copy(
                        resolvingStream = false,
                        selectedTorrentForFiles = null,
                        readyToPlay = PlaybackTarget(
                            url = directUrl,
                            title = displayTitle,
                            backdropUrl = torrentItem.backdropUrl ?: "",
                            posterUrl = torrentItem.posterUrl ?: ""
                        )
                    )
                }
            },
            onFailure = { err ->
                _state.update {
                    it.copy(
                        resolvingStream = false,
                        error = if (isHebrew) "שגיאה בפתיחת הקישור: ${err.message}" else "Failed to unlock stream: ${err.message}"
                    )
                }
            }
        )
    }

    private fun deleteTorrent(torrentId: Long) {
        viewModelScope.launch {
            val token = getToken()
            if (token.isBlank()) return@launch

            _state.update { it.copy(resolvingStream = true, resolvingMessage = if (isHebrew) "מוחק מ-AllDebrid..." else "Deleting from AllDebrid...") }
            val delRes = adManager.deleteMagnet(token, torrentId)
            delRes.fold(
                onSuccess = {
                    _state.update { s ->
                        s.copy(
                            resolvingStream = false,
                            selectedTorrentForFiles = null,
                            items = s.items.filter { it.id != torrentId }
                        )
                    }
                },
                onFailure = { err ->
                    _state.update {
                        it.copy(
                            resolvingStream = false,
                            error = if (isHebrew) "שגיאה במחיקת טורנט: ${err.message}" else "Failed to delete torrent: ${err.message}"
                        )
                    }
                }
            )
        }
    }

    companion object {
        fun cleanTorrentTitle(raw: String): String {
            var name = raw
            // Strip leading brackets like [DEVIL-TORRENTS.PL]
            name = name.replace(Regex("""^\s*\[[^\]]+\]\s*"""), "")
            name = name.replace(Regex("""^\s*\([^\)]+\)\s*"""), "")
            // Strip extension
            name = name.substringBeforeLast('.')
            // Replace dots and underscores with spaces
            name = name.replace(Regex("""[._]"""), " ")
            // Cut scene tags
            val cutRegex = Regex("""(?i)\b(2160p|1080p|720p|480p|4k|uhd|bluray|web-dl|webrip|hdrip|dvdrip|h265|x265|h264|x264|hevc|ddp5\.1|atmos|truehd|aac|ac3|flac|multi|heb|hebdub)\b""")
            val match = cutRegex.find(name)
            if (match != null) {
                val before = name.substring(0, match.range.first).trim(' ', '-', '_')
                if (before.isNotBlank()) {
                    return before
                }
            }
            return name.trim(' ', '-', '_')
        }

        fun cleanSearchQuery(title: String): String {
            // Strip season/episode markers so TMDB search gets the show name
            var clean = title.replace(Regex("""(?i)\b(s\d{1,2}e\d{1,2}|s\d{1,2}|season\s*\d{1,2})\b"""), "")
            clean = clean.replace(Regex("""\(\s*s\d{1,2}\s*\)""", RegexOption.IGNORE_CASE), "")
            clean = clean.replace(Regex("""(?i)\b(tvs|pl|ai)\b"""), "")
            return clean.trim(' ', '-', '_')
        }

        fun formatBytes(bytes: Long): String {
            if (bytes <= 0) return "0 MB"
            val gb = bytes.toDouble() / (1024.0 * 1024.0 * 1024.0)
            return if (gb >= 1.0) {
                String.format(Locale.US, "%.2f GB", gb)
            } else {
                val mb = bytes.toDouble() / (1024.0 * 1024.0)
                String.format(Locale.US, "%.0f MB", mb)
            }
        }

        fun formatTimestamp(epochSeconds: Long): String {
            if (epochSeconds <= 0) return ""
            return try {
                val sdf = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())
                sdf.format(Date(epochSeconds * 1000L))
            } catch (_: Exception) {
                ""
            }
        }
    }
}
