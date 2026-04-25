package com.luminastreams.tv.presentation.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.Constants
import com.luminastreams.tv.core.LuminaApp
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.MediaRepository
import com.luminastreams.tv.domain.usecase.RealDebridManager
import com.luminastreams.tv.data.remote.FuzerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Headers
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.luminastreams.tv.data.local.WatchProgressManager
import androidx.core.content.edit
import java.util.concurrent.CopyOnWriteArrayList

data class TorrentioResponse(val streams: List<TorrentioStream>? = null)
data class TorrentioStream(val name: String? = null, val title: String? = null, val url: String? = null, val infoHash: String? = null)

interface DynamicTorrentioApi {
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
    @GET
    suspend fun getStreamsDynamic(@retrofit2.http.Url url: String): retrofit2.Response<TorrentioResponse>
}

class DetailsViewModel(private val repository: MediaRepository, context: Context) : ViewModel() {
    private val appContext: Context = context.applicationContext
    private val _state = MutableStateFlow(DetailsScreenState())
    val state: StateFlow<DetailsScreenState> = _state.asStateFlow()

    private val rdManager = RealDebridManager()

    // OPTIMIZATION: Switched to the fast SQLite Room Repository
    private val watchlistRepository = (appContext as LuminaApp).watchlistRepository
    private val progressManager  = WatchProgressManager(appContext)

    private val isHebrew: Boolean get() = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE).getString("app_lang", "he") == "he"

    private val okHttpClient = OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).readTimeout(30, TimeUnit.SECONDS).build()
    private val dynamicTorrentio: DynamicTorrentioApi = Retrofit.Builder().baseUrl("https://torrentio.strem.fun/").client(okHttpClient).addConverterFactory(GsonConverterFactory.create()).build().create(DynamicTorrentioApi::class.java)

    private val streamCache = ConcurrentHashMap<String, List<AdvancedStreamSource>>()
    private var scrapingJob: Job? = null
    private var resolveJob: Job? = null
    
    companion object {
        /** Track all torrent IDs we've added to RD so we can delete them on cleanup across the app session */
        private val activeTorrentIds = CopyOnWriteArrayList<String>()
    }

    private fun getRdToken(): String = appContext.getSharedPreferences(Constants.PREFS_SETTINGS, Context.MODE_PRIVATE).getString(Constants.KEY_RD_TOKEN, "")?.trim() ?: ""
    private fun backdropUrl(path: String?): String = Constants.backdropUrl(path)
    private fun posterUrl(path: String?): String = Constants.posterUrl(path)

    override fun onCleared() {
        super.onCleared()
        scrapingJob?.cancel()
        resolveJob?.cancel()
        // Best-effort cleanup of any active torrents on RD
        val token = getRdToken()
        if (token.isNotBlank() && activeTorrentIds.isNotEmpty()) {
            val ids = activeTorrentIds.toList()
            activeTorrentIds.clear()
            // Fire-and-forget cleanup using a non-viewModelScope dispatcher
            kotlinx.coroutines.GlobalScope.launch(Dispatchers.IO) {
                ids.forEach { id ->
                    try {
                        val request = okhttp3.Request.Builder()
                            .url("https://api.real-debrid.com/rest/1.0/torrents/delete/$id")
                            .header("Authorization", "Bearer $token")
                            .delete()
                            .build()
                        okHttpClient.newCall(request).execute().close()
                    } catch (_: Exception) {}
                }
            }
        }
    }

    fun onEvent(event: DetailsEvent) {
        when (event) {
            is DetailsEvent.LoadInitialData       -> loadData(event.fullId)
            is DetailsEvent.SelectSeason          -> fetchEpisodesForSeason(event.seasonNumber)
            is DetailsEvent.InitiateScraping      -> startScrapingEngine(event.imdbId, event.season, event.episode)
            is DetailsEvent.ResolveAndPlayStream  -> processRealDebridLink(event.stream)
            is DetailsEvent.ToggleFavorite        -> handleToggleFavorite()
            is DetailsEvent.ClearPlayUrl          -> _state.update { it.copy(readyToPlayUrl = null) }
            is DetailsEvent.CancelScraping        -> cancelActiveScraping()
            is DetailsEvent.RefreshProgress       -> refreshProgress()
        }
    }

    private fun loadData(fullId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoadingData = true, errorData = null) }
            try {
                val decodedId = URLDecoder.decode(fullId, "UTF-8")
                if (decodedId.startsWith("http")) {
                    _state.update {
                        it.copy(
                            isLoadingData = false, isFuzerDirect = true, bestSourceHint = "Fuzer Direct • RD+",
                            mediaInfo = MediaDetailsInfo(id = decodedId, imdbId = decodedId, title = "Fuzer Release",
                                overview = if (isHebrew) "מוריד ומפעיל אוטומטית דרך Real-Debrid..." else "Downloading and auto-playing via Real-Debrid...",
                                isSeries = false, ageRating = "IL", studios = listOf("Fuzer Israel"), genres = listOf("Direct Download"))
                        )
                    }
                    playFuzerDirect(decodedId)
                    return@launch
                }
            } catch (_: Exception) {}

            val type   = fullId.substringBefore("_")
            val realId = fullId.substringAfter("_")
            try {
                if (type == "tv") loadTvShow(realId) else loadMovie(realId)
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingData = false, errorData = "Network error: ${e.message}") }
            }
        }
    }

    private fun playFuzerDirect(torrentUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = getRdToken()
            if (token.isEmpty()) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error(if (isHebrew) "טוקן Real-Debrid חסר — עבור להגדרות" else "Real-Debrid token missing — Go to Settings")) }
                return@launch
            }
            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid(if (isHebrew) "מוריד קובץ טורנט..." else "Downloading torrent file...")) }
            val torrentBytes = FuzerEngine.downloadTorrentFile(torrentUrl).getOrElse { e ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error((if (isHebrew) "שגיאה בהורדת הטורנט: " else "Torrent download error: ") + e.message)) }
                return@launch
            }
            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid(if (isHebrew) "מעלה ל-Real-Debrid..." else "Uploading to Real-Debrid...")) }
            rdManager.resolveTorrentFileToStream(
                torrentBytes = torrentBytes,
                apiToken = token,
                onTorrentAdded = { activeTorrentIds.add(it) },
                onProgress = { progress ->
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid((if (isHebrew) "ממיר ב-RD: " else "Resolving in RD: ") + "${progress.toInt()}%")) }
                }
            ).fold(
                onSuccess = { url -> _state.update { it.copy(readyToPlayUrl = url, scrapingStatus = ScrapingStatus.Idle) } },
                onFailure = { e ->
                    if (e.message == "RD_CONFLICT") {
                        viewModelScope.launch(Dispatchers.IO) {
                            cleanupActiveTorrents(token)
                            delay(1000)
                            playFuzerDirect(torrentUrl) // Recursive retry after cleanup
                        }
                    } else {
                        _state.update { it.copy(scrapingStatus = ScrapingStatus.Error((if (isHebrew) "שגיאת RD: " else "RD Error: ") + e.message)) }
                    }
                }
            )
        }
    }

    private suspend fun loadMovie(id: String) {
        repository.getMovieFullDetails(id).fold(
            onSuccess = { dto ->
                val rawImdb  = dto.external_ids?.imdbId
                val scrapeId = if (!rawImdb.isNullOrBlank()) rawImdb else "tmdb:${dto.id}"
                val genres   = dto.genres?.map { it.name }?.ifEmpty { listOf("Drama") } ?: listOf("Drama")
                val studios  = dto.productionCompanies?.map { it.name }?.take(3)?.ifEmpty { listOf("Independent") } ?: listOf("Independent")
                val castList = dto.credits?.cast?.take(15)?.mapNotNull {
                    if (it.profilePath != null) CastMember(it.id.toString(), it.name, it.character, "${Constants.IMAGE_W300}${it.profilePath}") else null
                } ?: emptyList()
                val directorName   = dto.credits?.crew?.find { it.job == "Director" }?.name ?: ""
                val trailerUrl     = fetchRealTrailer(scrapeId, "movie")

                // OPTIMIZATION: Uses suspend function to query SQLite DB
                val isSaved        = watchlistRepository.isInWatchlist("movie_$id")

                val collectionId   = dto.belongsToCollection?.id
                val collectionName = dto.belongsToCollection?.name
                val collectionItems = if (collectionId != null) fetchCollectionViaHttp(collectionId) else emptyList()
                val primaryActorId   = dto.credits?.cast?.firstOrNull()?.id
                val primaryActorName = dto.credits?.cast?.firstOrNull()?.name
                val starringItems    = if (primaryActorId != null) fetchActorWorksViaHttp(primaryActorId) else emptyList()
                val qualityHint      = if (dto.voteAverage >= 7.0f) "4K HDR • RD+" else "1080p • RD+"
                val movieProg        = progressManager.getMovie(scrapeId)
                val logoPath         = dto.images?.logos?.firstOrNull { it.lang == "en" || it.lang == null }?.filePath
                val fullLogoUrl      = if (logoPath != null) "https://image.tmdb.org/t/p/original$logoPath" else ""

                _state.update {
                    it.copy(
                        isLoadingData     = false,
                        bestSourceHint    = qualityHint,
                        contentProgress   = movieProg?.fraction,
                        contentIsFinished = movieProg?.isFinished ?: false,
                        mediaInfo = MediaDetailsInfo(
                            id = "movie_${dto.id}", imdbId = scrapeId, title = dto.title, overview = dto.overview ?: "",
                            posterUrl = posterUrl(dto.posterPath), backdropUrl = backdropUrl(dto.backdropPath), logoUrl = fullLogoUrl,
                            isSeries = false, releaseDate = dto.releaseDate?.take(4) ?: "", runtimeMinutes = dto.runtime ?: 0,
                            tmdbRating = dto.voteAverage.toDouble(), imdbRating = dto.voteAverage.toDouble(), ageRating = "R",
                            studios = studios, genres = genres, director = directorName, cast = castList,
                            trailerUrl = trailerUrl, isFavorite = isSaved, collectionName = collectionName,
                            collectionItems = collectionItems, starringActorName = primaryActorName, starringItems = starringItems
                        )
                    )
                }
            },
            onFailure = { err -> _state.update { it.copy(isLoadingData = false, errorData = err.message) } }
        )
    }

    private suspend fun loadTvShow(id: String) {
        repository.getTvFullDetails(id).fold(
            onSuccess = { dto ->
                val rawImdb  = dto.external_ids?.imdbId
                val scrapeId = if (!rawImdb.isNullOrBlank()) rawImdb else "tmdb:${dto.id}"
                val genres   = dto.genres?.map { it.name }?.ifEmpty { listOf("Drama") } ?: listOf("Drama")
                val studios  = dto.networks?.map { it.name }?.take(3)?.ifEmpty { listOf("Independent") } ?: listOf("Independent")
                val castList = dto.credits?.cast?.take(15)?.mapNotNull {
                    if (it.profilePath != null) CastMember(it.id.toString(), it.name, it.character, "${Constants.IMAGE_W300}${it.profilePath}") else null
                } ?: emptyList()
                val creatorName      = dto.credits?.crew?.find { it.job == "Creator" || it.department == "Writing" }?.name ?: ""
                val trailerUrl       = fetchRealTrailer(scrapeId, "series")

                // OPTIMIZATION: Uses suspend function to query SQLite DB
                val isSaved          = watchlistRepository.isInWatchlist("tv_$id")

                val primaryActorId   = dto.credits?.cast?.firstOrNull()?.id
                val primaryActorName = dto.credits?.cast?.firstOrNull()?.name
                val starringItems    = if (primaryActorId != null) fetchActorWorksViaHttp(primaryActorId) else emptyList()
                val logoPath         = dto.images?.logos?.firstOrNull { it.lang == "en" || it.lang == null }?.filePath
                val fullLogoUrl      = if (logoPath != null) "https://image.tmdb.org/t/p/original$logoPath" else ""
                val latestEp         = progressManager.getLatestEpisodeProgress(scrapeId)

                _state.update {
                    it.copy(
                        isLoadingData      = false,
                        bestSourceHint     = "1080p • RD+",
                        contentProgress    = latestEp?.third?.fraction,
                        contentIsFinished  = latestEp?.third?.isFinished ?: false,
                        lastWatchedSeason  = latestEp?.first,
                        lastWatchedEpisode = latestEp?.second,
                        mediaInfo = MediaDetailsInfo(
                            id = "tv_${dto.id}", imdbId = scrapeId, title = dto.name, overview = dto.overview ?: "",
                            posterUrl = posterUrl(dto.posterPath), backdropUrl = backdropUrl(dto.backdropPath), logoUrl = fullLogoUrl,
                            isSeries = true, releaseDate = dto.firstAirDate?.take(4) ?: "", tmdbRating = dto.voteAverage.toDouble(),
                            imdbRating = dto.voteAverage.toDouble(), ageRating = "TV-MA", studios = studios, genres = genres,
                            director = creatorName, cast = castList, totalSeasons = dto.numberOfSeasons,
                            trailerUrl = trailerUrl, isFavorite = isSaved, starringActorName = primaryActorName, starringItems = starringItems
                        )
                    )
                }
                if (dto.numberOfSeasons > 0) onEvent(DetailsEvent.SelectSeason(1))
            },
            onFailure = { err -> _state.update { it.copy(isLoadingData = false, errorData = err.message) } }
        )
    }

    private suspend fun fetchRealTrailer(imdbId: String, type: String): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(1_500) {
                try {
                    val conn = (URL("https://v3-cinemeta.strem.io/meta/$type/$imdbId.json").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"; connectTimeout = 1_000; readTimeout = 1_000
                    }
                    if (conn.responseCode == 200) {
                        val id = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            .optJSONObject("meta")?.optString("trailer", "")
                        if (!id.isNullOrEmpty()) return@withTimeoutOrNull id
                    }
                } catch (_: Exception) {}
                null
            }
        }

    private fun fetchEpisodesForSeason(seasonNum: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isEpisodesLoading = true, selectedSeason = seasonNum) }
            val imdbId = _state.value.mediaInfo.imdbId
            val real   = fetchCinemetaEpisodes(imdbId, seasonNum)
            val episodes = if (real.isNotEmpty()) real else {
                (1..10).map { ep -> Episode(id = "s${seasonNum}e$ep", episodeNumber = ep, seasonNumber = seasonNum,
                    title = "Episode $ep", overview = "", stillUrl = _state.value.mediaInfo.backdropUrl, progress = 0f) }
            }
            _state.update { it.copy(isEpisodesLoading = false, episodes = episodes) }

            appContext.getSharedPreferences("player_context", Context.MODE_PRIVATE).edit {
                putInt("total_episodes_in_season", episodes.size)
                putInt("total_seasons", _state.value.mediaInfo.totalSeasons)
            }
        }
    }

    private suspend fun fetchCinemetaEpisodes(imdbId: String, targetSeason: Int): List<Episode> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<Episode>()
            withTimeoutOrNull(2_000) {
                try {
                    val conn = (URL("https://v3-cinemeta.strem.io/meta/series/$imdbId.json").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"; connectTimeout = 1_500; readTimeout = 1_500
                    }
                    if (conn.responseCode == 200) {
                        val videos = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            .optJSONObject("meta")?.optJSONArray("videos")
                        videos?.let {
                            for (i in 0 until it.length()) {
                                val v = it.getJSONObject(i)
                                if (v.optInt("season", 0) != targetSeason) continue
                                val ep = v.optInt("episode", 0)
                                list += Episode(id = v.optString("id", "s${targetSeason}e$ep"), episodeNumber = ep,
                                    seasonNumber = targetSeason, title = v.optString("title", "Episode $ep"),
                                    overview = v.optString("overview", ""),
                                    stillUrl = v.optString("thumbnail", "").replace("http://", "https://"), progress = 0f)
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            list.map { ep ->
                val prog = progressManager.getEpisode(imdbId, ep.seasonNumber, ep.episodeNumber)
                ep.copy(progress = prog?.fraction ?: 0f, hasWatched = prog?.isFinished ?: false)
            }.sortedBy { it.episodeNumber }
        }

    private suspend fun resolveImdbId(id: String, tmdbType: String): String {
        if (id.startsWith("tt")) return id
        val tmdbId = id.replace("tmdb:", "")
        return withContext(Dispatchers.IO) {
            try {
                val url  = URL("https://api.themoviedb.org/3/$tmdbType/$tmdbId/external_ids?api_key=${Constants.TMDB_API_KEY}")
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 5000; conn.readTimeout = 5000
                if (conn.responseCode == 200) {
                    val imdb = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optString("imdb_id", "")
                    if (imdb.startsWith("tt")) return@withContext imdb
                }
            } catch (_: Exception) {}
            id
        }
    }

    private fun startScrapingEngine(scrapeId: String, season: Int?, episode: Int?) {
        cancelActiveScraping()
        val token = getRdToken()
        if (token.isEmpty()) {
            _state.update { it.copy(scrapingStatus = ScrapingStatus.Error(if (isHebrew) "נדרש חשבון Real-Debrid. אנא התחבר בהגדרות." else "Real-Debrid account required. Please connect in Settings.")) }
            return
        }
        scrapingJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(scrapingStatus = ScrapingStatus.Searching, availableStreams = emptyList()) }
            val queryType    = if (season != null && episode != null) "series" else "movie"
            val actualScrapeId = resolveImdbId(scrapeId, if (queryType == "series") "tv" else "movie")
            val queryId      = (if (season != null && episode != null) "$actualScrapeId:$season:$episode" else actualScrapeId).trim()
            val cacheKey     = queryId

            if (season != null && episode != null) {
                appContext.getSharedPreferences("player_context", Context.MODE_PRIVATE).edit {
                    putInt("total_seasons", _state.value.mediaInfo.totalSeasons)
                }
            }

            streamCache[cacheKey]?.let { cached ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = cached) }
                return@launch
            }

            try {
                val response = dynamicTorrentio.getStreamsDynamic("https://torrentio.strem.fun/realdebrid=$token/stream/$queryType/$queryId.json")
                if (!response.isSuccessful || response.body()?.streams.isNullOrEmpty()) {
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Error(if (isHebrew) "לא נמצאו מקורות עבור תוכן זה." else "No sources found for this content.")) }
                    return@launch
                }
                val mapped = response.body()!!.streams!!.mapIndexedNotNull { idx, s ->
                    val titleSafe = s.title ?: return@mapIndexedNotNull null
                    val nameSafe  = s.name ?: "Unknown"
                    val upper     = titleSafe.uppercase()
                    val sizeBytes = Regex("([0-9.]+)\\s*(GB|MB)").find(upper)?.let {
                        val v = it.groupValues[1].toDoubleOrNull() ?: 0.0
                        if (it.groupValues[2] == "GB") (v * 1_073_741_824).toLong() else (v * 1_048_576).toLong()
                    } ?: 0L
                    AdvancedStreamSource(id = "str_$idx", releaseGroup = nameSafe.replace("\n", " "),
                        filename = titleSafe.substringBefore("\n"), infoHash = s.infoHash, directUrl = s.url,
                        sizeBytes = sizeBytes, isCachedRd = nameSafe.contains("RD+"),
                        quality = StreamQuality.fromString(upper), videoCodec = VideoCodec.fromString(upper))
                }.sortedByDescending { it.sortScore }
                    .let { list ->
                        val prefs = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                        if (prefs.getBoolean("force_hdr", false)) {
                            val hdr = list.filter { src ->
                                val u = "${src.filename} ${src.releaseGroup}".uppercase()
                                u.contains("HDR") || u.contains("DV") || u.contains(".DV.") ||
                                        u.contains("DOLBY VISION") || u.contains("HDR10") || u.contains("HLG")
                            }
                            hdr + (list - hdr.toSet())
                        } else list
                    }.let { list ->
                        val prefs = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                        when (prefs.getString("max_quality", "4K") ?: "4K") {
                            "1080p" -> list.filter { it.quality != StreamQuality.UHD_4K }
                            "720p"  -> list.filter { it.quality.priority <= 6 }
                            else    -> list
                        }
                    }
                streamCache[cacheKey] = mapped
                if (mapped.isEmpty()) _state.update { it.copy(scrapingStatus = ScrapingStatus.Error(if (isHebrew) "לא נמצאו מקורות התואמים להגדרות האיכות שלך." else "No sources matching your quality settings.")) }
                else _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = mapped) }
            } catch (e: Exception) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error((if (isHebrew) "שגיאה: " else "Error: ") + e.message)) }
            }
        }
    }

    private fun cancelActiveScraping() {
        scrapingJob?.cancel()
        resolveJob?.cancel()
        _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle) }
    }

    /** Delete a torrent from RD account to prevent 2000/2004 conflicts */
    private suspend fun deleteRdTorrent(torrentId: String, token: String) {
        withContext(Dispatchers.IO) {
            try {
                val request = okhttp3.Request.Builder()
                    .url("https://api.real-debrid.com/rest/1.0/torrents/delete/$torrentId")
                    .header("Authorization", "Bearer $token")
                    .delete()
                    .build()
                okHttpClient.newCall(request).execute().close()
            } catch (_: Exception) { /* best-effort cleanup */ }
        }
    }

    /** Cleanup all tracked RD torrents — called before resolving a new source */
    private suspend fun cleanupActiveTorrents(token: String) {
        val ids = activeTorrentIds.toList()
        activeTorrentIds.clear()
        ids.forEach { id -> deleteRdTorrent(id, token) }
    }

    private fun processRealDebridLink(stream: AdvancedStreamSource) {
        if (stream.directUrl?.startsWith("http") == true) {
            resolveJob?.cancel()
            resolveJob = viewModelScope.launch(Dispatchers.IO) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid(stream.id)) }
                try {
                    // Fast pre-flight check to prevent ExoPlayer ParserException on dead links
                    val fastClient = okHttpClient.newBuilder()
                        .connectTimeout(2, TimeUnit.SECONDS)
                        .readTimeout(2, TimeUnit.SECONDS)
                        .build()
                        
                    val request = okhttp3.Request.Builder().url(stream.directUrl).head().build()
                    val response = fastClient.newCall(request).execute()
                    val contentType = response.header("Content-Type") ?: ""
                    val isHtml = contentType.contains("text/html", ignoreCase = true)
                    response.close()
                    
                    if (isHtml) {
                        _state.update { it.copy(scrapingStatus = ScrapingStatus.Error(if (isHebrew) "הקישור פג תוקף או נמחק משרתי RD. נסה מקור אחר." else "Link expired or deleted from RD. Try another source.")) }
                    } else {
                        _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle, readyToPlayUrl = stream.directUrl) }
                    }
                } catch (e: Exception) {
                    // Fallback to trying to play it anyway if the fast network check fails or times out
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle, readyToPlayUrl = stream.directUrl) }
                }
            }
            return
        }
        if (stream.infoHash.isNullOrBlank()) return

        // Cancel any previous resolve job to prevent races
        resolveJob?.cancel()

        resolveJob = viewModelScope.launch(Dispatchers.IO) {
            val token = getRdToken()
            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid(stream.id)) }

            // Clean up previous torrents before adding a new one
            cleanupActiveTorrents(token)

            try {
                val magnetUri = "magnet:?xt=urn:btih:${stream.infoHash}"
                val result = rdManager.resolveMagnetToStreamTracked(magnetUri, token) { torrentId ->
                    activeTorrentIds.add(torrentId)
                }
                result.fold(
                    onSuccess = { url ->
                        _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle, readyToPlayUrl = url) }
                    },
                    onFailure = { e ->
                        val msg = e.message ?: ""
                        // If error 2000/2004 (RD_CONFLICT), cleanup and retry once
                        if (msg == "RD_CONFLICT" || msg.contains("2000") || msg.contains("2004")) {
                            cleanupActiveTorrents(token)
                            // Small delay before retry to let RD process the deletion
                            kotlinx.coroutines.delay(1200)
                            val retry = rdManager.resolveMagnetToStreamTracked(magnetUri, token) { torrentId ->
                                activeTorrentIds.add(torrentId)
                            }
                            retry.fold(
                                onSuccess = { url -> _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle, readyToPlayUrl = url) } },
                                onFailure = { e2 -> 
                                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Error(if (isHebrew) "שגיאת כפל ב-RD: ${e2.message}" else "RD Conflict: ${e2.message}")) }
                                }
                            )
                        } else {
                            _state.update { it.copy(scrapingStatus = ScrapingStatus.Error(if (isHebrew) "נכשל בפענוח קישור מאובטח" else "Failed to resolve secure link")) }
                        }
                    }
                )
            } catch (e: Exception) {
                if (e is kotlinx.coroutines.CancellationException) throw e
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error((if (isHebrew) "שגיאת רשת: " else "Network error: ") + e.message)) }
            }
        }
    }

    private fun refreshProgress() {
        val imdbId = _state.value.mediaInfo.imdbId
        if (imdbId.isBlank() || _state.value.isLoadingData) return

        viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.mediaInfo.isSeries) {
                val latest = progressManager.getLatestEpisodeProgress(imdbId)
                _state.update { s ->
                    s.copy(
                        contentProgress    = latest?.third?.fraction,
                        contentIsFinished  = latest?.third?.isFinished ?: false,
                        lastWatchedSeason  = latest?.first,
                        lastWatchedEpisode = latest?.second
                    )
                }
            } else {
                val prog = progressManager.getMovie(imdbId)
                _state.update { s ->
                    s.copy(contentProgress = prog?.fraction, contentIsFinished = prog?.isFinished ?: false)
                }
            }

            val eps = _state.value.episodes
            if (eps.isNotEmpty()) {
                val refreshed = eps.map { ep ->
                    val prog = progressManager.getEpisode(imdbId, ep.seasonNumber, ep.episodeNumber)
                    ep.copy(progress = prog?.fraction ?: 0f, hasWatched = prog?.isFinished ?: false)
                }
                _state.update { it.copy(episodes = refreshed) }
            }
        }
    }

    private fun handleToggleFavorite() {
        val info  = _state.value.mediaInfo
        val movie = Movie(id = info.id, title = info.title, posterUrl = info.posterUrl, backdropUrl = info.backdropUrl,
            rating = info.tmdbRating.toFloat(), mediaType = if (info.isSeries) "tv" else "movie",
            overview = info.overview, year = info.releaseDate.toIntOrNull() ?: 0, genre = info.genres.firstOrNull() ?: "")

        // OPTIMIZATION: Push database writing to background thread
        viewModelScope.launch(Dispatchers.IO) {
            val isNowAdded = watchlistRepository.toggleWatchlist(movie)
            _state.update { it.copy(mediaInfo = info.copy(isFavorite = isNowAdded)) }
        }
    }

    private suspend fun fetchCollectionViaHttp(collectionId: Int): List<Recommendation> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Recommendation>()
        withTimeoutOrNull(2000) {
            try {
                val conn = URL("https://api.themoviedb.org/3/collection/$collectionId?api_key=${Constants.TMDB_API_KEY}&language=en-US").openConnection() as HttpURLConnection
                if (conn.apply { requestMethod = "GET" }.responseCode == 200) {
                    val parts = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("parts")
                    if (parts != null) {
                        for (i in 0 until parts.length()) {
                            val item   = parts.getJSONObject(i)
                            val poster = item.optString("poster_path", "")
                            if (poster.isNotEmpty() && poster != "null")
                                list += Recommendation(id = "movie_${item.optInt("id")}",
                                    title = item.optString("title", item.optString("name", "Unknown")),
                                    posterUrl = "https://image.tmdb.org/t/p/w300$poster")
                        }
                    }
                }
            } catch (_: Exception) {}
        }
        list.distinctBy { it.id }
    }

    private suspend fun fetchActorWorksViaHttp(personId: Int): List<Recommendation> = withContext(Dispatchers.IO) {
        val list = mutableListOf<Recommendation>()
        withTimeoutOrNull(2000) {
            try {
                val conn = URL("https://api.themoviedb.org/3/person/$personId/combined_credits?api_key=${Constants.TMDB_API_KEY}&language=en-US").openConnection() as HttpURLConnection
                if (conn.apply { requestMethod = "GET" }.responseCode == 200) {
                    val cast = JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).optJSONArray("cast")
                    if (cast != null) {
                        val tempList = mutableListOf<Pair<Recommendation, Double>>()
                        for (i in 0 until cast.length()) {
                            val item   = cast.getJSONObject(i)
                            val poster = item.optString("poster_path", "")
                            if (poster.isNotEmpty() && poster != "null")
                                tempList.add(Pair(Recommendation(
                                    id = "${item.optString("media_type", "movie")}_${item.optInt("id")}",
                                    title = item.optString("title", item.optString("name", "Unknown")),
                                    posterUrl = "https://image.tmdb.org/t/p/w300$poster"),
                                    item.optDouble("popularity", 0.0)))
                        }
                        list.addAll(tempList.distinctBy { it.first.id }.sortedByDescending { it.second }.take(15).map { it.first })
                    }
                }
            } catch (_: Exception) {}
        }
        list
    }
}