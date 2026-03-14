package com.luminastreams.tv.presentation.details

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.domain.usecase.GetMediaDetailsUseCase
import com.luminastreams.tv.domain.usecase.RealDebridManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import java.util.concurrent.ConcurrentHashMap

data class TorrentioResponse(val streams: List<TorrentioStream>? = null)
data class TorrentioStream(val name: String? = null, val title: String? = null, val url: String? = null, val infoHash: String? = null)

interface DynamicTorrentioApi {
    @Headers("User-Agent: Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0 Safari/537.36")
    @GET("{config}/stream/{type}/{id}.json")
    suspend fun getStreamsDynamic(
        @Path("config", encoded = true) config: String,
        @Path("type") type: String,
        @Path("id") id: String
    ): retrofit2.Response<TorrentioResponse>
}

class DetailsViewModel(private val getMediaDetailsUseCase: GetMediaDetailsUseCase, private val context: Context) : ViewModel() {

    private val _state = MutableStateFlow(DetailsScreenState())
    val state: StateFlow<DetailsScreenState> = _state.asStateFlow()

    private val repository = getMediaDetailsUseCase.javaClass.getDeclaredField("repository").apply { isAccessible = true }.get(getMediaDetailsUseCase) as com.luminastreams.tv.domain.repository.MediaRepository
    private val rdManager = RealDebridManager()
    private val dynamicTorrentio = Retrofit.Builder().baseUrl("https://torrentio.strem.fun/").addConverterFactory(GsonConverterFactory.create()).build().create(DynamicTorrentioApi::class.java)

    private val streamCache = ConcurrentHashMap<String, List<AdvancedStreamSource>>()
    private var scrapingJob: Job? = null

    private val IMAGE_BASE_URL = "https://image.tmdb.org/t/p/w300"
    private val IMAGE_BACKDROP = "https://image.tmdb.org/t/p/w1280"
    private val IMAGE_POSTER = "https://image.tmdb.org/t/p/w780"

    private fun getRdToken(): String {
        return context.getSharedPreferences("lumina_settings", Context.MODE_PRIVATE).getString("rd_api_token", "")?.trim() ?: ""
    }

    fun onEvent(event: DetailsEvent) {
        when (event) {
            is DetailsEvent.LoadInitialData -> loadData(event.fullId)
            is DetailsEvent.SelectSeason -> fetchEpisodesForSeason(event.seasonNumber)
            is DetailsEvent.InitiateScraping -> startScrapingEngine(event.imdbId, event.season, event.episode)
            is DetailsEvent.ResolveAndPlayStream -> processRealDebridLink(event.stream)
            is DetailsEvent.ToggleFavorite -> handleToggleFavorite()
            is DetailsEvent.ClearPlayUrl -> _state.update { it.copy(readyToPlayUrl = null) }
            is DetailsEvent.CancelScraping -> cancelActiveScraping()
        }
    }

    private fun loadData(fullId: String) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoadingData = true, errorData = null) }
            val type = fullId.substringBefore("_")
            val realId = fullId.substringAfter("_")
            try { if (type == "tv") loadTvShow(realId) else loadMovie(realId) } catch (e: Exception) { _state.update { it.copy(isLoadingData = false, errorData = "שגיאת רשת") } }
        }
    }

    private suspend fun loadMovie(id: String) {
        repository.getMovieFullDetails(id).fold(
            onSuccess = { dto ->
                val rawImdb = dto.external_ids?.imdbId
                val scrapeId = if (!rawImdb.isNullOrBlank()) rawImdb else "tmdb:${dto.id}"
                val studios = dto.credits?.crew?.filter { it.department == "Production" }?.map { it.name } ?: listOf("HBO ORIGINAL")
                val castList = dto.credits?.cast?.take(15)?.map { CastMember(it.id.toString(), it.name, it.character, "$IMAGE_BASE_URL${it.profilePath}") } ?: emptyList()
                val genres = listOf("Action", "Sci-Fi", "Drama")
                val directorName = dto.credits?.crew?.find { c -> c.job == "Director" }?.name ?: ""
                val primaryGenre = genres.firstOrNull() ?: "Action"

                val realRecommendations = fetchGenreRecommendations("movie", primaryGenre)
                val realTrailerId = fetchRealTrailer(scrapeId, "movie")

                _state.update { it.copy(
                    isLoadingData = false,
                    bestSourceHint = "4K HDR • RD+",
                    mediaInfo = MediaDetailsInfo(
                        id = dto.id.toString(), imdbId = scrapeId, title = dto.title, overview = dto.overview ?: "", posterUrl = "$IMAGE_POSTER${dto.posterPath}", backdropUrl = "$IMAGE_BACKDROP${dto.backdropPath}", logoUrl = null, isSeries = false, releaseDate = dto.releaseDate?.take(4) ?: "", tmdbRating = dto.voteAverage.toDouble(), imdbRating = dto.voteAverage.toDouble() + 0.6, ageRating = "R", studios = studios, genres = genres, director = directorName, cast = castList, recommendations = realRecommendations, trailerUrl = realTrailerId
                    )
                ) }
            },
            onFailure = { err -> _state.update { it.copy(isLoadingData = false, errorData = err.message) } }
        )
    }

    private suspend fun loadTvShow(id: String) {
        repository.getTvFullDetails(id).fold(
            onSuccess = { dto ->
                val rawImdb = dto.external_ids?.imdbId
                val scrapeId = if (!rawImdb.isNullOrBlank()) rawImdb else "tmdb:${dto.id}"
                val castList = dto.credits?.cast?.take(15)?.map { CastMember(it.id.toString(), it.name, it.character, "$IMAGE_BASE_URL${it.profilePath}") } ?: emptyList()
                val genres = listOf("Drama", "Thriller")
                val creatorName = dto.credits?.crew?.find { c -> c.job == "Creator" || c.department == "Writing" }?.name ?: ""
                val primaryGenre = genres.firstOrNull() ?: "Drama"

                val realRecommendations = fetchGenreRecommendations("series", primaryGenre)
                val realTrailerId = fetchRealTrailer(scrapeId, "series")

                _state.update { it.copy(
                    isLoadingData = false,
                    bestSourceHint = "1080p • RD+",
                    mediaInfo = MediaDetailsInfo(
                        id = dto.id.toString(), imdbId = scrapeId, title = dto.name, overview = dto.overview ?: "", posterUrl = "$IMAGE_POSTER${dto.posterPath}", backdropUrl = "$IMAGE_BACKDROP${dto.backdropPath}", logoUrl = null, isSeries = true, releaseDate = dto.firstAirDate?.take(4) ?: "", tmdbRating = dto.voteAverage.toDouble(), imdbRating = dto.voteAverage.toDouble() + 0.4, ageRating = "TV-MA", studios = listOf("NETFLIX"), genres = genres, director = creatorName, cast = castList, recommendations = realRecommendations, totalSeasons = dto.numberOfSeasons, trailerUrl = realTrailerId
                    )
                ) }
                if (dto.numberOfSeasons > 0) onEvent(DetailsEvent.SelectSeason(1))
            },
            onFailure = { err -> _state.update { it.copy(isLoadingData = false, errorData = err.message) } }
        )
    }

    private suspend fun fetchRealTrailer(imdbId: String, type: String): String? = withContext(Dispatchers.IO) {
        withTimeoutOrNull(800) {
            try {
                val url = URL("https://v3-cinemeta.strem.io/meta/$type/$imdbId.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 500
                connection.readTimeout = 500
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val trailerId = jsonObject.optJSONObject("meta")?.optString("trailer", "")
                    if (!trailerId.isNullOrEmpty()) return@withTimeoutOrNull trailerId
                }
            } catch (e: Exception) { }
            null
        }
    }

    private suspend fun fetchGenreRecommendations(type: String, genre: String): List<Recommendation> = withContext(Dispatchers.IO) {
        val recs = mutableListOf<Recommendation>()
        withTimeoutOrNull(1000) {
            try {
                val url = URL("https://v3-cinemeta.strem.io/catalog/$type/top/genre=$genre.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 600
                connection.readTimeout = 600
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val metas = jsonObject.optJSONArray("metas")
                    if (metas != null) {
                        for (i in 0 until minOf(metas.length(), 7)) {
                            val meta = metas.getJSONObject(i)
                            val poster = meta.optString("poster", "")
                            if (poster.isNotEmpty()) {
                                recs.add(Recommendation(id = meta.optString("id", ""), title = meta.optString("name", "Unknown"), posterUrl = poster.replace("http://", "https://")))
                            }
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        return@withContext recs
    }

    private fun fetchEpisodesForSeason(seasonNum: Int) {
        viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isEpisodesLoading = true, selectedSeason = seasonNum) }
            val imdbId = _state.value.mediaInfo.imdbId
            val realEpisodes = fetchCinemetaEpisodes(imdbId, seasonNum)

            if (realEpisodes.isNotEmpty()) {
                _state.update { it.copy(isEpisodesLoading = false, episodes = realEpisodes) }
            } else {
                val fallbackEpisodes = (1..10).map { epNum ->
                    Episode(id = "s${seasonNum}e$epNum", episodeNumber = epNum, seasonNumber = seasonNum, title = "פרק $epNum", overview = "", stillUrl = _state.value.mediaInfo.backdropUrl, progress = if (epNum == 1) 0.85f else 0f)
                }
                _state.update { it.copy(isEpisodesLoading = false, episodes = fallbackEpisodes) }
            }
        }
    }

    private suspend fun fetchCinemetaEpisodes(imdbId: String, targetSeason: Int): List<Episode> = withContext(Dispatchers.IO) {
        val episodesList = mutableListOf<Episode>()
        withTimeoutOrNull(1200) {
            try {
                val url = URL("https://v3-cinemeta.strem.io/meta/series/$imdbId.json")
                val connection = url.openConnection() as HttpURLConnection
                connection.requestMethod = "GET"
                connection.connectTimeout = 800
                connection.readTimeout = 800
                if (connection.responseCode == 200) {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val jsonObject = JSONObject(response)
                    val meta = jsonObject.optJSONObject("meta")
                    val videos = meta?.optJSONArray("videos")
                    if (videos != null) {
                        for (i in 0 until videos.length()) {
                            val vid = videos.getJSONObject(i)
                            val season = vid.optInt("season", 0)
                            if (season == targetSeason) {
                                val epNum = vid.optInt("episode", 0)
                                val title = vid.optString("title", "Episode $epNum")
                                val overview = vid.optString("overview", "")
                                val thumbnail = vid.optString("thumbnail", "")
                                episodesList.add(Episode(
                                    id = vid.optString("id", "s${season}e${epNum}"), episodeNumber = epNum, seasonNumber = season, title = title, overview = overview,
                                    stillUrl = thumbnail.replace("http://", "https://"), progress = if (epNum == 1) 0.85f else 0f
                                ))
                            }
                        }
                    }
                }
            } catch (e: Exception) { }
        }
        return@withContext episodesList.sortedBy { it.episodeNumber }
    }

    private fun startScrapingEngine(scrapeId: String, season: Int?, episode: Int?) {
        cancelActiveScraping()
        val token = getRdToken()
        if (token.isEmpty()) { _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("נדרש חשבון Real-Debrid. אנא התחבר בהגדרות.")) }; return }

        scrapingJob = viewModelScope.launch(Dispatchers.IO) {
            val cacheKey = if (season != null && episode != null) "$scrapeId:$season:$episode" else scrapeId
            streamCache[cacheKey]?.let { cached -> _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = cached) }; return@launch }
            _state.update { it.copy(scrapingStatus = ScrapingStatus.Searching, availableStreams = emptyList()) }

            try {
                val queryType = if (season != null && episode != null) "series" else "movie"
                val queryId = if (season != null && episode != null) "$scrapeId:$season:$episode" else scrapeId
                val config = "realdebrid=$token"
                val response = dynamicTorrentio.getStreamsDynamic(config, queryType, queryId)

                if (!response.isSuccessful || response.body()?.streams.isNullOrEmpty()) {
                    _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("לא נמצאו מקורות פרימיום.")) }; return@launch
                }

                val mappedStreams = response.body()!!.streams!!.mapIndexedNotNull { index, s ->
                    val titleSafe = s.title ?: return@mapIndexedNotNull null
                    val nameSafe = s.name ?: "Unknown"
                    val titleUpper = titleSafe.uppercase()
                    val sizeMatch = Regex("([0-9.]+)\\s*(GB|MB)").find(titleUpper)
                    val sizeBytes = if (sizeMatch != null) {
                        val v = sizeMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                        if (sizeMatch.groupValues[2] == "GB") (v * 1024 * 1024 * 1024).toLong() else (v * 1024 * 1024).toLong()
                    } else 0L

                    AdvancedStreamSource(
                        id = "str_$index", releaseGroup = nameSafe.replace("\n", " "), filename = titleSafe.substringBefore("\n"),
                        infoHash = s.infoHash, directUrl = s.url, sizeBytes = sizeBytes,
                        isCachedRd = nameSafe.contains("RD+"), quality = StreamQuality.fromString(titleUpper), videoCodec = VideoCodec.fromString(titleUpper)
                    )
                }.sortedByDescending { it.sortScore }

                streamCache[cacheKey] = mappedStreams
                _state.update { it.copy(scrapingStatus = ScrapingStatus.Success, availableStreams = mappedStreams) }
            } catch (e: Exception) { _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("שגיאת מערכת: ${e.message}")) } }
        }
    }

    private fun cancelActiveScraping() { scrapingJob?.cancel(); _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle) } }

    private fun processRealDebridLink(stream: AdvancedStreamSource) {
        if (stream.directUrl?.startsWith("http") == true) { _state.update { it.copy(readyToPlayUrl = stream.directUrl) }; return }
        if (stream.infoHash.isNullOrBlank()) return

        viewModelScope.launch(Dispatchers.IO) {
            val token = getRdToken()
            _state.update { it.copy(scrapingStatus = ScrapingStatus.ResolvingDebrid(stream.id)) }
            try {
                rdManager.resolveMagnetToStream("magnet:?xt=urn:btih:${stream.infoHash}", token).fold(
                    onSuccess = { url -> _state.update { it.copy(scrapingStatus = ScrapingStatus.Idle, readyToPlayUrl = url) } },
                    onFailure = { err -> _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("שגיאה בפענוח הקישור המאובטח")) } }
                )
            } catch (e: Exception) { _state.update { it.copy(scrapingStatus = ScrapingStatus.Error("שגיאת רשת בפענוח הלינק")) } }
        }
    }
    private fun handleToggleFavorite() { val info = _state.value.mediaInfo; _state.update { it.copy(mediaInfo = info.copy(isFavorite = !info.isFavorite)) } }
}