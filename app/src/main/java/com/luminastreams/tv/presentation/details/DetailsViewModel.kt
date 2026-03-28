package com.luminastreams.tv.presentation.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.Constants
import com.luminastreams.tv.data.local.WatchlistManager
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.MediaRepository
import com.luminastreams.tv.domain.usecase.RealDebridManager
import com.luminastreams.tv.data.remote.FuzerEngine
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

data class TorrentioResponse(val streams: List<TorrentioStream>? = null)
data class TorrentioStream(
    val name    : String? = null,
    val title   : String? = null,
    val url     : String? = null,
    val infoHash: String? = null
)

interface DynamicTorrentioApi {
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
    @GET
    suspend fun getStreamsDynamic(@retrofit2.http.Url url: String): retrofit2.Response<TorrentioResponse>
}

class DetailsViewModel(
    private val repository: MediaRepository,
    context: Context
) : ViewModel() {

    private val appContext: Context = context.applicationContext

    private val _state = MutableStateFlow(DetailsScreenState())
    val state: StateFlow<DetailsScreenState> = _state.asStateFlow()

    private val rdManager        = RealDebridManager()
    private val watchlistManager = WatchlistManager(appContext)

    private val dynamicTorrentio: DynamicTorrentioApi = Retrofit.Builder()
        .baseUrl("https://torrentio.strem.fun/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DynamicTorrentioApi::class.java)

    private val streamCache = ConcurrentHashMap<String, List<AdvancedStreamSource>>()
    private var scrapingJob: Job? = null

    private fun getRdToken(): String =
        appContext.getSharedPreferences(Constants.PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(Constants.KEY_RD_TOKEN, "")?.trim() ?: ""

    private fun backdropUrl(path: String?): String = Constants.backdropUrl(path)
    private fun posterUrl(path: String?): String   = Constants.posterUrl(path)

    fun onEvent(event: DetailsEvent) {
        when (event) {
            is DetailsEvent.LoadInitialData      -> loadData(event.fullId)
            is DetailsEvent.SelectSeason         -> fetchEpisodesForSeason(event.seasonNumber)
            is DetailsEvent.InitiateScraping     -> startScrapingEngine(event.imdbId, event.season, event.episode)
            is DetailsEvent.ResolveAndPlayStream -> processRealDebridLink(event.stream)
            is DetailsEvent.ToggleFavorite       -> handleToggleFavorite()
            is DetailsEvent.ClearPlayUrl         -> _state.update { it.copy(readyToPlayUrl = null) }
            is DetailsEvent.CancelScraping       -> cancelActiveScraping()
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
                            isLoadingData  = false,
                            isFuzerDirect  = true,
                            bestSourceHint = "Fuzer Direct • RD+",
                            mediaInfo = MediaDetailsInfo(
                                id        = decodedId,
                                imdbId    = decodedId,
                                title     = "Fuzer Release",
                                overview  = "מוריד ומפעיל אוטומטית דרך Real-Debrid...",
                                isSeries  = false,
                                ageRating = "IL",
                                studios   = listOf("Fuzer Israel"),
                                genres    = listOf("Direct Download")
                            )
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
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("טוקן Real-Debrid חסר — עבור להגדרות")) }
                return@launch
            }
            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("מוריד קובץ טורנט...")) }
            val torrentBytes = FuzerEngine.downloadTorrentFile(torrentUrl).getOrElse { e ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("שגיאה בהורדת הטורנט: ${e.message}")) }
                return@launch
            }
            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("מעלה ל-Real-Debrid...")) }
            rdManager.resolveTorrentFileToStream(torrentBytes, token) { progress ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("ממיר ב-RD: ${progress.toInt()}%")) }
            }.fold(
                onSuccess = { url -> _state.update { it.copy(readyToPlayUrl = url, scrapingStatus = ScrapingStatus.Idle) } },
                onFailure = { e  -> _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("שגיאת RD: ${e.message}")) } }
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
                val directorName    = dto.credits?.crew?.find { it.job == "Director" }?.name ?: ""
                val recommendations = fetchGenreRecommendations("movie", genres.first())
                val trailerUrl      = fetchRealTrailer(scrapeId, "movie")
                val isSaved         = watchlistManager.isInWatchlist("movie_$id")
                val qualityHint     = if (dto.voteAverage >= 7.0f) "4K HDR • RD+" else "1080p • RD+"

                _state.update {
                    it.copy(
                        isLoadingData  = false,
                        bestSourceHint = qualityHint,
                        mediaInfo = MediaDetailsInfo(
                            id             = "movie_${dto.id}",
                            imdbId         = scrapeId,
                            title          = dto.title,
                            overview       = dto.overview ?: "",
                            posterUrl      = posterUrl(dto.posterPath),
                            backdropUrl    = backdropUrl(dto.backdropPath),
                            logoUrl        = null,
                            isSeries       = false,
                            releaseDate    = dto.releaseDate?.take(4) ?: "",
                            runtimeMinutes = dto.runtime ?: 0,
                            tmdbRating     = dto.voteAverage.toDouble(),
                            imdbRating     = dto.voteAverage.toDouble(),
                            ageRating      = "R",
                            studios        = studios,
                            genres         = genres,
                            director       = directorName,
                            cast           = castList,
                            recommendations = recommendations,
                            trailerUrl     = trailerUrl,
                            isFavorite     = isSaved
                        )
                    )
                }
            },
            onFailure = { err ->
                _state.update { it.copy(isLoadingData = false, errorData = err.message) }
            }
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
                val creatorName     = dto.credits?.crew?.find { it.job == "Creator" || it.department == "Writing" }?.name ?: ""
                val recommendations = fetchGenreRecommendations("series", genres.first())
                val trailerUrl      = fetchRealTrailer(scrapeId, "series")
                val isSaved         = watchlistManager.isInWatchlist("tv_$id")

                _state.update {
                    it.copy(
                        isLoadingData  = false,
                        bestSourceHint = "1080p • RD+",
                        mediaInfo = MediaDetailsInfo(
                            id             = "tv_${dto.id}",
                            imdbId         = scrapeId,
                            title          = dto.name,
                            overview       = dto.overview ?: "",
                            posterUrl      = posterUrl(dto.posterPath),
                            backdropUrl    = backdropUrl(dto.backdropPath),
                            logoUrl        = null,
                            isSeries       = true,
                            releaseDate    = dto.firstAirDate?.take(4) ?: "",
                            tmdbRating     = dto.voteAverage.toDouble(),
                            imdbRating     = dto.voteAverage.toDouble(),
                            ageRating      = "TV-MA",
                            studios        = studios,
                            genres         = genres,
                            director       = creatorName,
                            cast           = castList,
                            recommendations = recommendations,
                            totalSeasons   = dto.numberOfSeasons,
                            trailerUrl     = trailerUrl,
                            isFavorite     = isSaved
                        )
                    )
                }
                if (dto.numberOfSeasons > 0) onEvent(DetailsEvent.SelectSeason(1))
            },
            onFailure = { err ->
                _state.update { it.copy(isLoadingData = false, errorData = err.message) }
            }
        )
    }

    private suspend fun fetchRealTrailer(imdbId: String, type: String): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(1_500) {
                try {
                    val conn = (URL("https://v3-cinemeta.strem.io/meta/$type/$imdbId.json")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod  = "GET"; connectTimeout = 1_000; readTimeout = 1_000
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

    private suspend fun fetchGenreRecommendations(type: String, genre: String): List<Recommendation> =
        withContext(Dispatchers.IO) {
            val recs = mutableListOf<Recommendation>()
            withTimeoutOrNull(1_500) {
                try {
                    val conn = (URL("https://v3-cinemeta.strem.io/catalog/$type/top/genre=$genre.json")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod  = "GET"; connectTimeout = 1_000; readTimeout = 1_000
                    }
                    if (conn.responseCode == 200) {
                        val metas = JSONObject(
                            conn.inputStream.bufferedReader().use { it.readText() }
                        ).optJSONArray("metas")
                        if (metas != null) {
                            for (i in 0 until minOf(metas.length(), 8)) {
                                val m      = metas.getJSONObject(i)
                                val poster = m.optString("poster", "")
                                if (poster.isNotEmpty()) {
                                    recs += Recommendation(
                                        id        = m.optString("id", ""),
                                        title     = m.optString("name", "Unknown"),
                                        posterUrl = poster.replace("http://", "https://")
                                    )
                                }
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            recs
        }

    private fun fetchEpisodesForSeason(seasonNum: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isEpisodesLoading = true, selectedSeason = seasonNum) }
            val imdbId = _state.value.mediaInfo.imdbId
            val real   = fetchCinemetaEpisodes(imdbId, seasonNum)

            if (real.isNotEmpty()) {
                _state.update { it.copy(isEpisodesLoading = false, episodes = real) }
            } else {
                val fallback = (1..10).map { ep ->
                    Episode(
                        id            = "s${seasonNum}e$ep",
                        episodeNumber = ep,
                        seasonNumber  = seasonNum,
                        title         = "Episode $ep",
                        overview      = "",
                        stillUrl      = _state.value.mediaInfo.backdropUrl,
                        progress      = 0f
                    )
                }
                _state.update { it.copy(isEpisodesLoading = false, episodes = fallback) }
            }
        }
    }

    private suspend fun fetchCinemetaEpisodes(imdbId: String, targetSeason: Int): List<Episode> =
        withContext(Dispatchers.IO) {
            val list = mutableListOf<Episode>()
            withTimeoutOrNull(2_000) {
                try {
                    val conn = (URL("https://v3-cinemeta.strem.io/meta/series/$imdbId.json")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod  = "GET"; connectTimeout = 1_500; readTimeout = 1_500
                    }
                    if (conn.responseCode == 200) {
                        val videos = JSONObject(
                            conn.inputStream.bufferedReader().use { it.readText() }
                        ).optJSONObject("meta")?.optJSONArray("videos")

                        videos?.let {
                            for (i in 0 until it.length()) {
                                val v  = it.getJSONObject(i)
                                if (v.optInt("season", 0) != targetSeason) continue
                                val ep = v.optInt("episode", 0)
                                list += Episode(
                                    id            = v.optString("id", "s${targetSeason}e$ep"),
                                    episodeNumber = ep,
                                    seasonNumber  = targetSeason,
                                    title         = v.optString("title", "Episode $ep"),
                                    overview      = v.optString("overview", ""),
                                    stillUrl      = v.optString("thumbnail", "").replace("http://", "https://"),
                                    progress      = 0f
                                )
                            }
                        }
                    }
                } catch (_: Exception) {}
            }
            list.sortedBy { it.episodeNumber }
        }

    private suspend fun resolveImdbId(id: String, tmdbType: String): String {
        if (id.startsWith("tt")) return id
        val tmdbId = id.replace("tmdb:", "")
        return withContext(Dispatchers.IO) {
            try {
                val url  = URL("https://api.themoviedb.org/3/$tmdbType/$tmdbId/external_ids?api_key=${Constants.TMDB_API_KEY}")
                val conn = url.openConnection() as HttpURLConnection
                if (conn.responseCode == 200) {
                    val imdb = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                        .optString("imdb_id", "")
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
            _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Real-Debrid account required. Please connect in Settings.")) }
            return
        }

        scrapingJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(scrapingStatus = ScrapingStatus.Searching, availableStreams = emptyList()) }

            val queryType       = if (season != null && episode != null) "series" else "movie"
            val actualScrapeId  = resolveImdbId(scrapeId, if (queryType == "series") "tv" else "movie")
            val queryId         = (if (season != null && episode != null) "$actualScrapeId:$season:$episode" else actualScrapeId).trim()
            val cacheKey        = queryId

            streamCache[cacheKey]?.let { cached ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = cached) }
                return@launch
            }

            try {
                val configStr = "realdebrid=$token"
                val fullUrl   = "https://torrentio.strem.fun/$configStr/stream/$queryType/$queryId.json"
                val response  = dynamicTorrentio.getStreamsDynamic(fullUrl)

                if (!response.isSuccessful || response.body()?.streams.isNullOrEmpty()) {
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("לא נמצאו מקורות עבור תוכן זה.")) }
                    return@launch
                }

                val mapped = response.body()!!.streams!!.mapIndexedNotNull { idx, s ->
                    val titleSafe = s.title ?: return@mapIndexedNotNull null
                    val nameSafe  = s.name  ?: "Unknown"
                    val upper     = titleSafe.uppercase()

                    val sizeBytes = Regex("([0-9.]+)\\s*(GB|MB)").find(upper)?.let {
                        val v = it.groupValues[1].toDoubleOrNull() ?: 0.0
                        if (it.groupValues[2] == "GB") (v * 1_073_741_824).toLong()
                        else (v * 1_048_576).toLong()
                    } ?: 0L

                    AdvancedStreamSource(
                        id           = "str_$idx",
                        releaseGroup = nameSafe.replace("\n", " "),
                        filename     = titleSafe.substringBefore("\n"),
                        infoHash     = s.infoHash,
                        directUrl    = s.url,
                        sizeBytes    = sizeBytes,
                        isCachedRd   = nameSafe.contains("RD+"),
                        quality      = StreamQuality.fromString(upper),
                        videoCodec   = VideoCodec.fromString(upper)
                    )
                }
                    .sortedByDescending { it.sortScore }
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
                    }
                    .let { list ->
                        val prefs = appContext.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE)
                        when (prefs.getString("max_quality", "4K") ?: "4K") {
                            "1080p" -> list.filter { it.quality != StreamQuality.UHD_4K }
                            "720p"  -> list.filter { it.quality.priority <= 6 }
                            else    -> list
                        }
                    }

                streamCache[cacheKey] = mapped

                if (mapped.isEmpty()) {
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("לא נמצאו מקורות התואמים להגדרות האיכות שלך.")) }
                } else {
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = mapped) }
                }

            } catch (e: Exception) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Error: ${e.message}")) }
            }
        }
    }

    private fun cancelActiveScraping() {
        scrapingJob?.cancel()
        _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle) }
    }

    private fun processRealDebridLink(stream: AdvancedStreamSource) {
        if (stream.directUrl?.startsWith("http") == true) {
            _state.update { it.copy(readyToPlayUrl = stream.directUrl) }
            return
        }
        if (stream.infoHash.isNullOrBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val token = getRdToken()
            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid(stream.id)) }
            try {
                rdManager.resolveMagnetToStream("magnet:?xt=urn:btih:${stream.infoHash}", token).fold(
                    onSuccess = { url -> _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle, readyToPlayUrl = url) } },
                    onFailure = { _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Failed to resolve secure link")) } }
                )
            } catch (e: Exception) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Network error: ${e.message}")) }
            }
        }
    }

    private fun handleToggleFavorite() {
        val info = _state.value.mediaInfo
        val movie = Movie(
            id          = info.id,
            title       = info.title,
            posterUrl   = info.posterUrl,
            backdropUrl = info.backdropUrl,
            rating      = info.tmdbRating.toFloat(),
            mediaType   = if (info.isSeries) "tv" else "movie",
            overview    = info.overview,
            year        = info.releaseDate.toIntOrNull() ?: 0,
            genre       = info.genres.firstOrNull() ?: ""
        )
        val isNowAdded = watchlistManager.toggleWatchlist(movie)
        _state.update { it.copy(mediaInfo = info.copy(isFavorite = isNowAdded)) }
    }
}