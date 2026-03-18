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
import retrofit2.http.Path
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLDecoder
import java.util.concurrent.ConcurrentHashMap

/**
 * DetailsViewModel — ניהול מצב מסך הפרטים.
 *
 * תיקונים לעומת הגרסה הקודמת:
 * 1. הבנאי מקבל MediaRepository ישירות — אין יותר Reflection שבירי
 * 2. ז'אנרים ממשיים מה-DTO במקום hardcoded
 * 3. סטודיו/רשת ממשיים מה-DTO במקום hardcoded
 * 4. imdbRating = tmdbRating (לא מומצא)
 * 5. קודי ה-API דרך Constants
 * 6. TorrentioResponse/TorrentioStream מוגדרים כאן ולא מוכפלים ב-TmdbApi
 * 7. תמיכה ישירה בהזרמת תוכן מפיוזר דרך Real-Debrid
 *
 * Path: app/src/main/java/com/luminastreams/tv/presentation/details/DetailsViewModel.kt
 */

// ── Torrentio models (הוגדרו כאן בלבד – הוסרו מ-TmdbApi.kt) ────────────────
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

// ── Genre ID → name (ממשית מ-TMDB) ──────────────────────────────────────────
private val GENRE_ID_MAP = mapOf(
    28 to "Action",          12 to "Adventure",    16 to "Animation",
    35 to "Comedy",          80 to "Crime",         99 to "Documentary",
    18 to "Drama",        10751 to "Family",        14 to "Fantasy",
    36 to "History",         27 to "Horror",     10402 to "Music",
    9648 to "Mystery",    10749 to "Romance",      878 to "Sci-Fi",
    10770 to "TV Movie",     53 to "Thriller",   10752 to "War",
    37 to "Western",      10759 to "Action & Adventure",
    10762 to "Kids",      10763 to "News",        10764 to "Reality",
    10765 to "Sci-Fi & Fantasy", 10766 to "Soap", 10767 to "Talk",
    10768 to "War & Politics"
)

// ────────────────────────────────────────────────────────────────────────────
class DetailsViewModel(
    private val repository: MediaRepository,
    private val context: Context
) : ViewModel() {

    private val _state = MutableStateFlow(DetailsScreenState())
    val state: StateFlow<DetailsScreenState> = _state.asStateFlow()

    private val rdManager       = RealDebridManager()
    private val watchlistManager = WatchlistManager(context)
    private val fuzerEngine     = FuzerEngine()

    private val dynamicTorrentio: DynamicTorrentioApi = Retrofit.Builder()
        .baseUrl("https://torrentio.strem.fun/")
        .addConverterFactory(GsonConverterFactory.create())
        .build()
        .create(DynamicTorrentioApi::class.java)

    private val streamCache = ConcurrentHashMap<String, List<AdvancedStreamSource>>()
    private var scrapingJob: Job? = null

    // ── Helpers ───────────────────────────────────────────────────────────────
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

            // --- בדיקה חדשה: האם זה לינק מפיוזר? ---
            try {
                val decodedId = URLDecoder.decode(fullId, "UTF-8")
                if (decodedId.startsWith("http")) {
                    // זה לינק פיוזר! טוענים מסך פרטים מדומה בלי לפנות ל-TMDB
                    _state.update {
                        it.copy(
                            isLoadingData = false,
                            bestSourceHint = "Fuzer Direct • RD+",
                            mediaInfo = MediaDetailsInfo(
                                id              = decodedId,
                                imdbId          = decodedId,
                                title           = "Fuzer Release",
                                overview        = "Press Play to instantly stream this file via Real-Debrid.",
                                posterUrl       = "", // <--- מחקנו את ה-URL שהקריס את ה-UI!
                                backdropUrl     = "",
                                isSeries        = false,
                                tmdbRating      = 10.0,
                                imdbRating      = 10.0,
                                ageRating       = "IL",
                                studios         = listOf("Fuzer Israel"),
                                genres          = listOf("Direct Download")
                            )
                        )
                    }
                    return@launch
                }
            } catch (e: Exception) {
                // מתעלם וממשיך ללוגיקה הרגילה של TMDB
            }

            // --- הלוגיקה הרגילה של TMDB ---
            val type   = fullId.substringBefore("_")
            val realId = fullId.substringAfter("_")
            try {
                if (type == "tv") loadTvShow(realId) else loadMovie(realId)
            } catch (e: Exception) {
                _state.update { it.copy(isLoadingData = false, errorData = "Network error: ${e.message}") }
            }
        }
    }

    private suspend fun loadMovie(id: String) {
        repository.getMovieFullDetails(id).fold(
            onSuccess = { dto ->
                val rawImdb  = dto.external_ids?.imdbId
                val scrapeId = if (!rawImdb.isNullOrBlank()) rawImdb else "tmdb:${dto.id}"

                val genres = dto.genres?.map { it.name }?.ifEmpty { listOf("Drama") }
                    ?: listOf("Drama")

                val studios = dto.productionCompanies
                    ?.map { it.name }?.take(3)?.ifEmpty { listOf("Independent") }
                    ?: listOf("Independent")

                val castList = dto.credits?.cast?.take(15)?.mapNotNull {
                    if (it.profilePath != null) CastMember(
                        it.id.toString(), it.name, it.character,
                        "${Constants.IMAGE_W300}${it.profilePath}"
                    ) else null
                } ?: emptyList()

                val directorName = dto.credits?.crew
                    ?.find { it.job == "Director" }?.name ?: ""

                val recommendations = fetchGenreRecommendations("movie", genres.first())
                val trailerUrl      = fetchRealTrailer(scrapeId, "movie")
                val isSaved         = watchlistManager.isInWatchlist("movie_$id")

                val qualityHint = if (dto.voteAverage >= 7.0f) "4K HDR • RD+" else "1080p • RD+"

                _state.update {
                    it.copy(
                        isLoadingData = false,
                        bestSourceHint = qualityHint,
                        mediaInfo = MediaDetailsInfo(
                            id              = "movie_${dto.id}",
                            imdbId          = scrapeId,
                            title           = dto.title,
                            overview        = dto.overview ?: "",
                            posterUrl       = "${Constants.IMAGE_W780}${dto.posterPath}",
                            backdropUrl     = "${Constants.IMAGE_W1280}${dto.backdropPath}",
                            logoUrl         = null,
                            isSeries        = false,
                            releaseDate     = dto.releaseDate?.take(4) ?: "",
                            runtimeMinutes  = dto.runtime ?: 0,
                            tmdbRating      = dto.voteAverage.toDouble(),
                            imdbRating      = dto.voteAverage.toDouble(),
                            ageRating       = "R",
                            studios         = studios,
                            genres          = genres,
                            director        = directorName,
                            cast            = castList,
                            recommendations = recommendations,
                            trailerUrl      = trailerUrl,
                            isFavorite      = isSaved
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

                val genres = dto.genres?.map { it.name }?.ifEmpty { listOf("Drama") }
                    ?: listOf("Drama")

                val studios = dto.networks
                    ?.map { it.name }?.take(3)?.ifEmpty { listOf("Independent") }
                    ?: listOf("Independent")

                val castList = dto.credits?.cast?.take(15)?.mapNotNull {
                    if (it.profilePath != null) CastMember(
                        it.id.toString(), it.name, it.character,
                        "${Constants.IMAGE_W300}${it.profilePath}"
                    ) else null
                } ?: emptyList()

                val creatorName = dto.credits?.crew
                    ?.find { it.job == "Creator" || it.department == "Writing" }?.name ?: ""

                val recommendations = fetchGenreRecommendations("series", genres.first())
                val trailerUrl      = fetchRealTrailer(scrapeId, "series")
                val isSaved         = watchlistManager.isInWatchlist("tv_$id")

                _state.update {
                    it.copy(
                        isLoadingData = false,
                        bestSourceHint = "1080p • RD+",
                        mediaInfo = MediaDetailsInfo(
                            id              = "tv_${dto.id}",
                            imdbId          = scrapeId,
                            title           = dto.name,
                            overview        = dto.overview ?: "",
                            posterUrl       = "${Constants.IMAGE_W780}${dto.posterPath}",
                            backdropUrl     = "${Constants.IMAGE_W1280}${dto.backdropPath}",
                            logoUrl         = null,
                            isSeries        = true,
                            releaseDate     = dto.firstAirDate?.take(4) ?: "",
                            tmdbRating      = dto.voteAverage.toDouble(),
                            imdbRating      = dto.voteAverage.toDouble(),
                            ageRating       = "TV-MA",
                            studios         = studios,
                            genres          = genres,
                            director        = creatorName,
                            cast            = castList,
                            recommendations = recommendations,
                            totalSeasons    = dto.numberOfSeasons,
                            trailerUrl      = trailerUrl,
                            isFavorite      = isSaved
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

    private suspend fun fetchGenreRecommendations(
        type: String, genre: String
    ): List<Recommendation> = withContext(Dispatchers.IO) {
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
                        stillUrl      = _state.value.mediaInfo.backdropUrl ?: "",
                        progress      = 0f
                    )
                }
                _state.update { it.copy(isEpisodesLoading = false, episodes = fallback) }
            }
        }
    }

    private suspend fun fetchCinemetaEpisodes(
        imdbId: String, targetSeason: Int
    ): List<Episode> = withContext(Dispatchers.IO) {
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
            _state.update {
                it.copy(scrapingStatus = ScrapingStatus.Error(
                    "Real-Debrid account required. Please connect in Settings."
                ))
            }
            return
        }

        scrapingJob = viewModelScope.launch(Dispatchers.IO) {
            // --- המעקף של פיוזר ---
            if (scrapeId.startsWith("http")) {
                _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("מתחיל הורדה לענן...")) }

                try {
                    val torrentBytes = fuzerEngine.downloadTorrentFile(scrapeId).getOrThrow()
                    val rdToken = getRdToken()

                    // התיקון: קוראים לפונקציה המיוחדת של קבצי טורנט, ושולחים את הקובץ עם אחוזים חכמים!
                    val directLink = rdManager.resolveTorrentFileToStream(
                        torrentBytes = torrentBytes,
                        apiToken = rdToken,
                        season = season,
                        episode = episode
                    ) { progress ->
                        _state.update {
                            it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid("מוריד ל-RD: ${progress.toInt()}%"))
                        }
                    }.getOrThrow()

                    _state.update {
                        it.copy(scrapingStatus = ScrapingStatus.Success, readyToPlayUrl = directLink)
                    }
                } catch (e: Exception) {
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("${e.message}")) }
                }
                return@launch
            }
            // ---------------------

            val cacheKey = if (season != null && episode != null)
                "$scrapeId:$season:$episode" else scrapeId

            streamCache[cacheKey]?.let { cached ->
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = cached) }
                return@launch
            }

            _state.update { it.copy(scrapingStatus = ScrapingStatus.Searching, availableStreams = emptyList()) }

            try {
                val queryType = if (season != null && episode != null) "series" else "movie"
                val queryId   = if (season != null && episode != null)
                    "$scrapeId:$season:$episode" else scrapeId

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
                }.sortedByDescending { it.sortScore }

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
            id        = info.id,
            title     = info.title,
            posterUrl = info.posterUrl ?: "",
            backdropUrl = info.backdropUrl ?: "",
            rating    = info.tmdbRating.toFloat(),
            mediaType = if (info.isSeries) "tv" else "movie",
            overview  = info.overview,
            year      = info.releaseDate.toIntOrNull() ?: 0,
            genre     = info.genres.firstOrNull() ?: ""
        )
        val isNowAdded = watchlistManager.toggleWatchlist(movie)
        _state.update { it.copy(mediaInfo = info.copy(isFavorite = isNowAdded)) }
    }
}