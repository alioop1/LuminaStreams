package com.luminastreams.tv.presentation.details

import android.annotation.SuppressLint
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
import retrofit2.http.Path
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

data class TorrentioResponse(val streams: List<TorrentioStream>? = null)
data class TorrentioStream(
    val name: String?     = null,
    val title: String?    = null,
    val url: String?      = null,
    val infoHash: String? = null
)

interface DynamicTorrentioApi {
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
    @GET("{config}/stream/{type}/{id}.json")
    suspend fun getStreamsDynamic(
        @Path("config", encoded = true) config: String,
        @Path("type") type: String,
        @Path("id")   id:   String
    ): retrofit2.Response<TorrentioResponse>
}

@SuppressLint("StaticFieldLeak")
class DetailsViewModel(
    private val repository: MediaRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsScreenState())
    val state: StateFlow<DetailsScreenState> = _state.asStateFlow()

    private val rdManager        = RealDebridManager()
    private val watchlistManager = WatchlistManager(context)
    private val fuzerEngine      = FuzerEngine()

    private val dynamicTorrentio: DynamicTorrentioApi = Retrofit.Builder()
        .baseUrl("https://torrentio.strem.fun/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DynamicTorrentioApi::class.java)

    private val streamCache = ConcurrentHashMap<String, List<AdvancedStreamSource>>()
    private var scrapingJob: Job? = null

    private fun getRdToken(): String =
        context.getSharedPreferences(Constants.PREFS_SETTINGS, Context.MODE_PRIVATE)
            .getString(Constants.KEY_RD_TOKEN, "")?.trim() ?: ""

    // ── Public API ────────────────────────────────────────────────────────────
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

    // ── Load data ─────────────────────────────────────────────────────────────
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

    // ── Fuzer direct play ─────────────────────────────────────────────────────
    private fun playFuzerDirect(torrentUrl: String) {
        viewModelScope.launch(Dispatchers.IO) {
            val token = getRdToken()
            if (token.isEmpty()) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("טוקן Real-Debrid חסר — עבור להגדרות")) }
                return@launch
            }

            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("מוריד קובץ טורנט...")) }

            val torrentBytes = fuzerEngine.downloadTorrentFile(torrentUrl).getOrElse { e ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("שגיאה בהורדת הטורנט: ${e.message}")) }
                return@launch
            }

            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("מעלה ל-Real-Debrid...")) }

            rdManager.resolveTorrentFileToStream(
                torrentBytes = torrentBytes,
                apiToken     = token,
                season       = null,
                episode      = null
            ) { progress ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("ממיר ב-RD: ${progress.toInt()}%")) }
            }.fold(
                onSuccess = { url -> _state.update { it.copy(readyToPlayUrl = url, scrapingStatus = ScrapingStatus.Idle) } },
                onFailure = { e  -> _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("שגיאת RD: ${e.message}")) } }
            )
        }
    }

    // ── Load Movie ────────────────────────────────────────────────────────────
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
                            posterUrl      = "${Constants.IMAGE_W780}${dto.posterPath}",
                            backdropUrl    = "${Constants.IMAGE_W1280}${dto.backdropPath}",
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

    // ── Load TV Show ──────────────────────────────────────────────────────────
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
                            posterUrl      = "${Constants.IMAGE_W780}${dto.posterPath}",
                            backdropUrl    = "${Constants.IMAGE_W1280}${dto.backdropPath}",
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

    // ── Network helpers ───────────────────────────────────────────────────────
    private suspend fun fetchRealTrailer(imdbId: String, type: String): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(1_500) {
                try {
                    val conn = (URL("https://v3-cinemeta.strem.io/meta/$type/$imdbId.json")
                        .openConnection() as HttpURLConnection).apply {
                        requestMethod  = "GET"
                        connectTimeout = 1_000
                        readTimeout    = 1_000
                    }
                    if (conn.responseCode == 200) {
                        val body = conn.inputStream.bufferedReader().use { it.readText() }
                        val id   = JSONObject(body).optJSONObject("meta")?.optString("trailer", "")
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
                        requestMethod  = "GET"
                        connectTimeout = 1_000
                        readTimeout    = 1_000
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

    // ── Episodes ──────────────────────────────────────────────────────────────
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
                        requestMethod  = "GET"
                        connectTimeout = 1_500
                        readTimeout    = 1_500
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

    // ── Stream scraping ───────────────────────────────────────────────────────
    private fun startScrapingEngine(scrapeId: String, season: Int?, episode: Int?) {
        cancelActiveScraping()
        val token = getRdToken()
        if (token.isEmpty()) {
            _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Real-Debrid account required. Please connect in Settings.")) }
            return
        }

        scrapingJob = viewModelScope.launch(Dispatchers.IO) {
            val cacheKey = if (season != null && episode != null) "$scrapeId:$season:$episode" else scrapeId

            streamCache[cacheKey]?.let { cached ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = cached) }
                return@launch
            }

            _state.update { it.copy(scrapingStatus = ScrapingStatus.Searching, availableStreams = emptyList()) }

            try {
                val queryType = if (season != null && episode != null) "series" else "movie"
                val queryId   = if (season != null && episode != null) "$scrapeId:$season:$episode" else scrapeId

                val response = dynamicTorrentio.getStreamsDynamic(
                    config = "realdebrid=$token",
                    type   = queryType,
                    id     = queryId
                )

                if (!response.isSuccessful || response.body()?.streams.isNullOrEmpty()) {
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("No premium sources found.")) }
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
                        // ✅ REAL: force_hdr — promote HDR/DV/HDR10+ streams to the top
                        if (context.getSharedPreferences("lumina_settings", android.content.Context.MODE_PRIVATE)
                                .getBoolean("force_hdr", false)) {
                            val hdr = list.filter { src ->
                                val u = "${src.filename} ${src.releaseGroup}".uppercase()
                                u.contains("HDR") || u.contains("DV") || u.contains(".DV.") ||
                                        u.contains("DOLBY VISION") || u.contains("HDR10") || u.contains("HLG")
                            }
                            hdr + (list - hdr.toSet())
                        } else list
                    }
                    .let { list ->
                        // ✅ REAL: max_quality — filter out streams above the user's chosen ceiling
                        when (context.getSharedPreferences("lumina_settings", android.content.Context.MODE_PRIVATE)
                            .getString("max_quality", "4K") ?: "4K") {
                            "1080p" -> list.filter { it.quality != StreamQuality.UHD_4K }
                            "720p"  -> list.filter { it.quality.priority <= 6 }
                            else    -> list   // "4K" — no filtering
                        }
                    }

                streamCache[cacheKey] = mapped
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = mapped) }

            } catch (e: Exception) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Error: ${e.message}")) }
            }
        }
    }

    private fun cancelActiveScraping() {
        scrapingJob?.cancel()
        _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle) }
    }

    // ── RealDebrid resolve ────────────────────────────────────────────────────
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
                rdManager.resolveMagnetToStream(
                    "magnet:?xt=urn:btih:${stream.infoHash}", token
                ).fold(
                    onSuccess = { url ->
                        _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle, readyToPlayUrl = url) }
                    },
                    onFailure = {
                        _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Failed to resolve secure link")) }
                    }
                )
            } catch (e: Exception) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("Network error: ${e.message}")) }
            }
        }
    }

    // ── Watchlist ─────────────────────────────────────────────────────────────
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