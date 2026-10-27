@file:OptIn(kotlinx.coroutines.FlowPreview::class)
package com.luminastreams.tv.presentation.home

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.luminastreams.tv.core.Constants
import com.luminastreams.tv.core.DeviceProfile
import com.luminastreams.tv.data.local.WatchProgressManager
import com.luminastreams.tv.data.remote.FuzerEngine
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import com.luminastreams.tv.core.TrustedHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.Collections
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

class HomeViewModel : ViewModel() {

    private val _state = MutableStateFlow(HomeState())
    val state: StateFlow<HomeState> = _state.asStateFlow()

    private val _uiRows = MutableStateFlow<List<RowDef>>(emptyList())
    val uiRows: StateFlow<List<RowDef>> = _uiRows.asStateFlow()

    private val pageMap = mutableMapOf<String, Int>()
    private val loadingSet: MutableSet<String> = Collections.synchronizedSet(mutableSetOf())
    private var activeJob: Job? = null
    private var currentIsRtl = false

    private val imgBase = "https://image.tmdb.org/t/p"

    override fun onCleared() {
        super.onCleared()
        activeJob?.cancel()
        // evictAll() closes SSL sockets — blocking network op.
        // onCleared() runs on Main Thread → NetworkOnMainThreadException crash.
        Thread {
            runCatching {
                http.dispatcher.cancelAll()
                http.connectionPool.evictAll()
            }
        }.start()
    }

    private val maxConnections = when (DeviceProfile.tier) {
        DeviceProfile.Tier.HIGH -> 8
        DeviceProfile.Tier.MID  -> 5
        DeviceProfile.Tier.LOW  -> 3
    }

    private val http = TrustedHttpClient.builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .connectionPool(okhttp3.ConnectionPool(maxConnections, 5, TimeUnit.MINUTES))
        .protocols(listOf(okhttp3.Protocol.HTTP_2, okhttp3.Protocol.HTTP_1_1))
        .build()

    init {
        viewModelScope.launch(Dispatchers.Default) {
            _state
                .debounce(100L)
                .collectLatest { currentState ->
                    recalculateRows(currentState)
                }
        }
        loadAll()
    }

    fun selectTab(tab: String) {
        _state.update { it.copy(selectedTab = tab) }
        if (tab == "Fuzer" && _state.value.fuzerItems.isEmpty()) {
            loadFuzerContent()
        }
        val currentStudio = _state.value.selectedStudioFilter
        if (currentStudio != null && (tab == "movies" || tab == "series")) {
            loadStudioCategories(currentStudio, isMovies = tab == "movies")
        }
    }

    fun setStudioFilter(studio: String?) {
        _state.update { it.copy(selectedStudioFilter = studio) }
        if (studio != null) {
            loadStudioCategories(studio, isMovies = _state.value.selectedTab == "movies")
        } else {
            studioJob?.cancel()
            _state.update { it.copy(activeStudioCategories = emptyList(), isStudioLoading = false) }
        }
    }

    fun setLanguage(isRtl: Boolean) {
        if (currentIsRtl != isRtl) {
            currentIsRtl = isRtl
            viewModelScope.launch(Dispatchers.Default) {
                recalculateRows(_state.value)
            }
        }
    }

    fun retry() {
        if (_state.value.selectedTab == "Fuzer") loadFuzerContent() else loadAll()
    }

    // ── Studio Dynamic Loading ────────────────────────────────────────
    private var studioJob: Job? = null

    private fun loadStudioCategories(studio: String, isMovies: Boolean) {
        studioJob?.cancel()
        _state.update { it.copy(isStudioLoading = true, activeStudioCategories = emptyList(), activeStudioError = null) }
        
        studioJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val k = Constants.TMDB_API_KEY
                val region = "US"
                val mt = if (isMovies) "movie" else "tv"
                val prefix = if (isMovies) "discover/movie" else "discover/tv"
                
                // Base filter per studio
                val studioFilter = when (studio) {
                    "HBO"       -> if (isMovies) "with_watch_providers=1899|384&watch_region=$region" else "with_networks=49|3186"
                    "AMAZON"    -> if (isMovies) "with_watch_providers=119&watch_region=$region" else "with_networks=1024"
                    "PARAMOUNT" -> if (isMovies) "with_companies=4" else "with_networks=4330|67"
                    "HULU"      -> if (isMovies) "with_watch_providers=15&watch_region=$region" else "with_networks=453"
                    "NETFLIX"   -> if (isMovies) "with_watch_providers=8&watch_region=$region" else "with_networks=213"
                    "APPLE_TV"  -> if (isMovies) "with_watch_providers=350&watch_region=$region" else "with_networks=2552"
                    "DISNEY"    -> if (isMovies) "with_companies=2|3|420" else "with_networks=2739"
                    "SONY"      -> "with_companies=5|34|559"
                    else        -> ""
                }
                
                if (studioFilter.isEmpty()) {
                    _state.update { it.copy(isStudioLoading = false) }
                    return@launch
                }

                // Define categories to fetch
                val queries = listOf(
                    Triple("top", "Most Popular", "הכי פופולרי") to "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&sort_by=popularity.desc",
                    Triple("action", "Action & Adventure", "פעולה והרפתקאות") to "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&with_genres=28|10759&sort_by=popularity.desc",
                    Triple("ani", "Animation", "אנימציה") to "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&with_genres=16&sort_by=popularity.desc",
                    Triple("comedy", "Comedy", "קומדיה") to "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&with_genres=35&sort_by=popularity.desc",
                    Triple("drama", "Drama", "דרמה") to "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&with_genres=18&sort_by=popularity.desc",
                    Triple("scifi", "Sci-Fi & Fantasy", "מדע בדיוני ופנטזיה") to "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&with_genres=878|14|10765&sort_by=popularity.desc",
                    Triple("crime", "Crime & Mystery", "פשע ומסתורין") to "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&with_genres=80|9648&sort_by=popularity.desc"
                )

                val results = coroutineScope {
                    queries.map { (meta, url) ->
                        async { meta to fetch(url, mt) }
                    }.awaitAll()
                }

                val categories = results.mapNotNull { (meta, movies) ->
                    val distinct = movies.distinctBy { it.id }
                    if (distinct.isNotEmpty()) {
                        StudioCategory(id = "studio_${studio}_${meta.first}_${if(isMovies) "m" else "t"}", titleEn = meta.second, titleHe = meta.third, movies = distinct)
                    } else null
                }

                _state.update { it.copy(activeStudioCategories = categories, isStudioLoading = false) }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(activeStudioError = e.message, isStudioLoading = false) }
            }
        }
    }

    // ── Hero Enrichment ───────────────────────────────────────────────
    private val trailerCache = ConcurrentHashMap<String, String>()

    private fun extractTrailerKey(videosArray: JSONArray?): String? {
        if (videosArray == null || videosArray.length() == 0) return null
        var bestKey: String? = null
        var backupKey: String? = null
        for (i in 0 until videosArray.length()) {
            val v = videosArray.optJSONObject(i) ?: continue
            val site = v.optString("site", "")
            if (!site.equals("YouTube", ignoreCase = true)) continue
            val key = v.optString("key", "")
            if (key.isBlank()) continue
            val type = v.optString("type", "")
            val official = v.optBoolean("official", false)
            if (type.equals("Trailer", ignoreCase = true)) {
                if (official) return key
                if (bestKey == null) bestKey = key
            } else if (type.equals("Teaser", ignoreCase = true)) {
                if (backupKey == null) backupKey = key
            } else if (backupKey == null) {
                backupKey = key
            }
        }
        return bestKey ?: backupKey
    }

    private suspend fun fetchCinemetaTrailer(imdbId: String, type: String): String? =
        withContext(Dispatchers.IO) {
            withTimeoutOrNull(1500) {
                var conn: HttpURLConnection? = null
                try {
                    conn = (URL("https://v3-cinemeta.strem.io/meta/$type/$imdbId.json").openConnection() as HttpURLConnection).apply {
                        requestMethod = "GET"; connectTimeout = 1000; readTimeout = 1000
                    }
                    if (conn.responseCode == 200) {
                        val id = JSONObject(conn.inputStream.bufferedReader().use { it.readText() })
                            .optJSONObject("meta")?.optString("trailer", "")
                        if (!id.isNullOrEmpty()) {
                            return@withTimeoutOrNull if (id.contains("v=")) id.substringAfter("v=").substringBefore("&")
                            else if (id.contains("youtu.be/")) id.substringAfter("youtu.be/").substringBefore("?")
                            else id
                        }
                    }
                } catch (e: Exception) { if (e is CancellationException) throw e }
                finally { conn?.disconnect() }
                null
            }
        }

    // Fetches logo, cast, runtime, and trailer for the currently displayed hero movie.
    // Single lightweight API call, debounced by heroEnrichedId.
    private var heroEnrichJob: Job? = null

    fun enrichHeroData(movie: Movie) {
        val movieId = movie.id
        if (movieId == _state.value.heroEnrichedId) return // already fetched

        heroEnrichJob?.cancel()
        heroEnrichJob = viewModelScope.launch(Dispatchers.IO) {
            try {
                val numericId = movieId.substringAfter("_").takeIf { it.isNotBlank() } ?: movieId
                val type = if (movie.mediaType == "tv") "tv" else "movie"
                val lang = if (currentIsRtl) "he-IL" else "en-US"
                val url = "https://api.themoviedb.org/3/$type/$numericId?api_key=${Constants.TMDB_API_KEY}&language=$lang&append_to_response=credits,images,videos&include_image_language=en,null&include_video_language=he,en,null"

                val response = http.newCall(Request.Builder().url(url).build()).execute()
                if (!response.isSuccessful) { response.close(); return@launch }

                val json = JSONObject(response.body.string())
                response.close()

                // Extract logo URL (prefer English logo, fallback to any)
                val logoPath = json.optJSONObject("images")
                    ?.optJSONArray("logos")
                    ?.let { logos ->
                        var best: String? = null
                        for (i in 0 until logos.length()) {
                            val logo = logos.getJSONObject(i)
                            val path = logo.optString("file_path", "")
                            if (path.isNotBlank()) {
                                best = path
                                break // take first available
                            }
                        }
                        best
                    }
                val logoUrl = if (logoPath != null) "${imgBase}/w500$logoPath" else null

                // Extract runtime
                val runtime = json.optInt("runtime", 0).takeIf { it > 0 }
                    ?: json.optJSONArray("episode_run_time")?.let {
                        if (it.length() > 0) it.optInt(0) else null
                    }

                // Extract top 3 cast members with photos
                val castArray = json.optJSONObject("credits")?.optJSONArray("cast")
                val castMembers = mutableListOf<CastMember>()
                if (castArray != null) {
                    for (i in 0 until minOf(castArray.length(), 4)) {
                        val obj = castArray.getJSONObject(i)
                        val name = obj.optString("name", "")
                        val profilePath = obj.optString("profile_path", "")
                        val fullProfileUrl = if (profilePath.isNotBlank() && profilePath != "null") "$imgBase/w185$profilePath" else null
                        if (name.isNotBlank()) castMembers.add(CastMember(name, fullProfileUrl))
                    }
                }

                // Extract localized overview (Hebrew if RTL)
                val overview = json.optString("overview", "").takeIf { it.isNotBlank() }

                // Extract localized genre names (Hebrew if RTL)
                val genresArray = json.optJSONArray("genres")
                val genreNames = mutableListOf<String>()
                if (genresArray != null) {
                    for (i in 0 until minOf(genresArray.length(), 2)) {
                        val name = genresArray.getJSONObject(i).optString("name", "")
                        if (name.isNotBlank()) genreNames.add(name)
                    }
                }
                val genreStr = genreNames.joinToString(" · ").takeIf { it.isNotBlank() }

                // Extract trailer key (YouTube ID)
                var trailerKey = trailerCache[movieId]
                if (trailerKey == null) {
                    trailerKey = extractTrailerKey(json.optJSONObject("videos")?.optJSONArray("results"))

                    // Fallback to TMDB videos query without language filter if missing
                    if (trailerKey == null && numericId.all { it.isDigit() }) {
                        try {
                            val fbUrl = "https://api.themoviedb.org/3/$type/$numericId/videos?api_key=${Constants.TMDB_API_KEY}"
                            val fbResp = http.newCall(Request.Builder().url(fbUrl).build()).execute()
                            if (fbResp.isSuccessful) {
                                val fbJson = JSONObject(fbResp.body.string())
                                fbResp.close()
                                trailerKey = extractTrailerKey(fbJson.optJSONArray("results"))
                            } else {
                                fbResp.close()
                            }
                        } catch (_: Exception) {}
                    }

                    // Fallback to Cinemeta if IMDB id
                    if (trailerKey == null && (movieId.contains("tt") || numericId.contains("tt"))) {
                        val imdbId = if (movieId.startsWith("tt")) movieId else numericId.substringAfter("tt_")
                        val cleanImdb = if (imdbId.startsWith("tt")) imdbId else "tt$imdbId"
                        trailerKey = fetchCinemetaTrailer(cleanImdb, if (movie.mediaType == "tv") "series" else "movie")
                    }

                    if (trailerKey != null) {
                        trailerCache[movieId] = trailerKey
                    }
                }

                _state.update {
                    it.copy(
                        heroLogoUrl = logoUrl,
                        heroRuntime = runtime,
                        heroCast = castMembers,
                        heroOverview = overview,
                        heroGenre = genreStr,
                        heroTrailerKey = trailerKey,
                        heroEnrichedId = movieId
                    )
                }
            } catch (e: CancellationException) { throw e
            } catch (_: Exception) {
                // Silently fail — hero will show text title instead of logo
            }
        }
    }

    /**
     * Scans WatchProgressManager for all in-progress content and builds
     * a "Continue Watching" list sorted by most recently watched.
     * Called from UI layer on resume since HomeViewModel has no Context.
     */
    private var cachedProgressManager: WatchProgressManager? = null

    fun refreshContinueWatching(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            val pm = cachedProgressManager ?: WatchProgressManager(context.applicationContext).also { cachedProgressManager = it }
            val prefs = context.getSharedPreferences("lumina_watch_v2", Context.MODE_PRIVATE)
            val bridge = context.getSharedPreferences("lumina_id_bridge", Context.MODE_PRIVATE)
            val allContent = _state.value.let { s ->
                (s.movieTrending + s.tvTrending + s.moviePremieres + s.movieAction +
                 s.movieAnimation + s.movieDrama + s.movieScifi + s.movieTopRated +
                 s.tvPremieres + s.tvDrama + s.tvCrime + s.tvAnimation + s.tvTopRated +
                 s.movieNetflix + s.movieAppleTV + s.movieDisney + s.movieHBO + s.movieAmazon +
                 s.movieParamount + s.movieHulu + s.tvNetflix + s.tvAppleTV + s.tvDisney +
                 s.tvHBO + s.tvAmazon + s.tvParamount + s.tvHulu +
                 s.fuzerMovies + s.fuzerSeries + s.fuzerMoviesHD + s.fuzerSeriesHD +
                 s.fuzerMovies4K + s.fuzerSeries4K + s.fuzerDubbedMovies + s.fuzerDubbedSeries)
                    .distinctBy { it.id }
            }

            val movieMap = allContent.associateBy { it.id }
            val watchedItems = mutableListOf<Pair<Movie, Long>>()
            val seenIds = mutableSetOf<String>()

            // ── PASS 1: Scan progress keys and try to match to Movies ──
            prefs.all.forEach { (key, _) ->
                val scrapeId = when {
                    key.startsWith("movie_") -> key.removePrefix("movie_")
                    key.startsWith("ep_")    -> key.removePrefix("ep_").substringBefore("_s")
                    else -> null
                } ?: return@forEach

                // Strategy A: Bridge lookup (IMDB → TMDB)
                var movie: Movie? = bridge.getString(scrapeId, null)?.let { movieMap[it] }

                // Strategy B: If scrapeId is "tmdb:12345", construct TMDB display ID directly
                if (movie == null && scrapeId.startsWith("tmdb:")) {
                    val tmdbNum = scrapeId.removePrefix("tmdb:")
                    movie = movieMap["movie_$tmdbNum"] ?: movieMap["tv_$tmdbNum"]
                }

                if (movie == null) return@forEach
                val progress = pm.get(key) ?: return@forEach

                if (progress.hasStarted && !progress.isFinished && seenIds.add(movie.id)) {
                    watchedItems.add(movie.copy(progress = progress.fraction) to progress.lastWatchedAt)
                }
            }

            // ── PASS 2: Reverse scan — for each Movie, check if progress keys exist ──
            // This covers cases where the bridge hasn't been populated yet
            if (watchedItems.size < 20) {
                val allKeys = prefs.all.keys
                allContent.forEach { movie ->
                    if (movie.id in seenIds) return@forEach
                    // Extract numeric TMDB ID from movie.id (e.g., "movie_12345" → "12345")
                    val tmdbNum = movie.id.substringAfter("_")
                    val type = movie.id.substringBefore("_") // "movie" or "tv"

                    // Check for tmdb:-prefixed progress keys
                    val tmdbKey = if (type == "movie") "movie_tmdb:$tmdbNum" else "ep_tmdb:${tmdbNum}_s"
                    val matchKey = if (type == "movie") {
                        allKeys.firstOrNull { it == "movie_tmdb:$tmdbNum" }
                    } else {
                        allKeys.firstOrNull { it.startsWith("ep_tmdb:${tmdbNum}_s") }
                    }

                    if (matchKey != null) {
                        val progress = pm.get(matchKey)
                        if (progress != null && progress.hasStarted && !progress.isFinished && seenIds.add(movie.id)) {
                            watchedItems.add(movie.copy(progress = progress.fraction) to progress.lastWatchedAt)
                        }
                    }
                }
            }

            val sorted = watchedItems
                .sortedByDescending { it.second }
                .take(20)
                .map { it.first }

            _state.update { it.copy(continueWatching = sorted) }
        }
    }

    private fun mergeStudioContent(movies: List<Movie>, series: List<Movie>): List<Movie> {
        return (movies + series).sortedByDescending { it.rating }.distinctBy { it.id }
    }



    private suspend fun recalculateRows(currentState: HomeState) = withContext(Dispatchers.Default) {
        val trFunc = { en: String, he: String -> if (currentIsRtl) he else en }
        val filter = currentState.selectedStudioFilter

        val homeHbo       = mergeStudioContent(currentState.movieHBO, currentState.tvHBO)
        val homeNetflix   = mergeStudioContent(currentState.movieNetflix, currentState.tvNetflix)
        val homeAmazon    = mergeStudioContent(currentState.movieAmazon, currentState.tvAmazon)
        val homeAppleTv   = mergeStudioContent(currentState.movieAppleTV, currentState.tvAppleTV)
        val homeDisney    = mergeStudioContent(currentState.movieDisney, currentState.tvDisney)
        val homeParamount = mergeStudioContent(currentState.movieParamount, currentState.tvParamount)
        val homeHulu      = mergeStudioContent(currentState.movieHulu, currentState.tvHulu)
        val homeSony      = mergeStudioContent(currentState.movieSony, currentState.tvSony)

        val amazonMovies  = currentState.movieAmazon.ifEmpty { currentState.tvAmazon }
        val amazonSeries  = currentState.tvAmazon.ifEmpty { currentState.movieAmazon }

        val newRows = buildList {
            when (currentState.selectedTab) {
                "home" -> {
                    // 🔥 Continue Watching — always FIRST row when content exists
                    if (currentState.continueWatching.isNotEmpty()) add(RowDef.Regular("continueWatching", trFunc("▶  Continue Watching", "▶  המשך צפייה"), currentState.continueWatching))
                    if (currentState.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", trFunc("Trending Movies", "סרטים פופולריים"), currentState.movieTrending))
                    if (homeHbo.isNotEmpty()) add(RowDef.Studio("homeHBO", StudioBrand.HBO, homeHbo))
                    if (homeNetflix.isNotEmpty()) add(RowDef.Studio("homeNetflix", StudioBrand.NETFLIX, homeNetflix))
                    if (currentState.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", trFunc("Popular Shows", "סדרות פופולריות"), currentState.tvTrending))
                    if (homeAmazon.isNotEmpty()) add(RowDef.Studio("homeAmazon", StudioBrand.AMAZON, homeAmazon))
                    if (homeAppleTv.isNotEmpty()) add(RowDef.Studio("homeAppleTv", StudioBrand.APPLE_TV, homeAppleTv))
                    if (homeDisney.isNotEmpty()) add(RowDef.Studio("homeDisney", StudioBrand.DISNEY, homeDisney))
                    if (homeParamount.isNotEmpty()) add(RowDef.Studio("homeParamount", StudioBrand.PARAMOUNT, homeParamount))
                    if (homeHulu.isNotEmpty()) add(RowDef.Studio("homeHulu", StudioBrand.HULU, homeHulu))
                    if (homeSony.isNotEmpty()) add(RowDef.Studio("homeSony", StudioBrand.SONY, homeSony))
                    if (currentState.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres",trFunc("New in Theaters", "בקולנוע"), currentState.moviePremieres))
                }
                "movies" -> {
                    add(RowDef.StudioRibbon)
                    if (filter != null) {
                        currentState.activeStudioCategories.forEach { cat ->
                            add(RowDef.Regular(cat.id, trFunc(cat.titleEn, cat.titleHe), cat.movies))
                        }
                    } else {
                        if (currentState.movieAction.isNotEmpty()) add(RowDef.Regular("movieAction", trFunc("Action & Adventure", "פעולה והרפתקאות"), currentState.movieAction))
                        if (currentState.movieTrending.isNotEmpty()) add(RowDef.Regular("movieTrending", trFunc("Trending Now", "פופולרי עכשיו"), currentState.movieTrending))
                        if (currentState.moviePremieres.isNotEmpty()) add(RowDef.Regular("moviePremieres", trFunc("In Theaters", "בקולנוע"), currentState.moviePremieres))
                        if (currentState.movieAnimation.isNotEmpty()) add(RowDef.Regular("movieAnimation", trFunc("Animations", "אנימציה"), currentState.movieAnimation))
                    }
                }
                "series" -> {
                    add(RowDef.StudioRibbon)
                    if (filter != null) {
                        currentState.activeStudioCategories.forEach { cat ->
                            add(RowDef.Regular(cat.id, trFunc(cat.titleEn, cat.titleHe), cat.movies))
                        }
                    } else {
                        if (currentState.tvDrama.isNotEmpty()) add(RowDef.Regular("tvDrama", trFunc("Drama", "דרמה"), currentState.tvDrama))
                        if (currentState.tvTrending.isNotEmpty()) add(RowDef.Regular("tvTrending", trFunc("Trending Shows", "סדרות פופולריות"), currentState.tvTrending))
                        if (currentState.tvPremieres.isNotEmpty()) add(RowDef.Regular("tvPremieres", trFunc("New Episodes", "פרקים חדשים"), currentState.tvPremieres))
                        if (currentState.tvAnimation.isNotEmpty()) add(RowDef.Regular("tvAnimation", trFunc("Animations", "אנימציה"), currentState.tvAnimation))
                    }
                }
                "Fuzer" -> {
                    val newContent = (currentState.fuzerMovies + currentState.fuzerSeries).sortedByDescending { it.id }.distinctBy { it.id }
                    if (newContent.isNotEmpty()) add(RowDef.Regular("fuzer_new", trFunc("🆕 New Content", "🆕 תוכן חדש"), newContent))
                    if (currentState.fuzerMovies.isNotEmpty()) add(RowDef.Regular("fuzer_m", trFunc("🎬 Movies", "🎬 סרטים"), currentState.fuzerMovies))
                    if (currentState.fuzerMoviesHD.isNotEmpty()) add(RowDef.Regular("fuzer_mhd", trFunc("🎬 Movies HD", "🎬 סרטים HD"), currentState.fuzerMoviesHD))
                    if (currentState.fuzerMovies4K.isNotEmpty()) add(RowDef.Regular("fuzer_m4k", trFunc("✨ Movies 4K", "✨ סרטים 4K"), currentState.fuzerMovies4K))
                    if (currentState.fuzerDubbedMovies.isNotEmpty()) add(RowDef.Regular("fuzer_dm", trFunc("🎤 Dubbed Movies", "🎤 סרטים מדובבים"), currentState.fuzerDubbedMovies))
                    if (currentState.fuzerSeries.isNotEmpty()) add(RowDef.Regular("fuzer_tv", trFunc("📺 TV Shows", "📺 סדרות"), currentState.fuzerSeries))
                    if (currentState.fuzerSeriesHD.isNotEmpty()) add(RowDef.Regular("fuzer_shd", trFunc("📺 TV Shows HD", "📺 סדרות HD"), currentState.fuzerSeriesHD))
                    if (currentState.fuzerSeries4K.isNotEmpty()) add(RowDef.Regular("fuzer_s4k", trFunc("✨ TV Shows 4K", "✨ סדרות 4K"), currentState.fuzerSeries4K))
                    if (currentState.fuzerDubbedSeries.isNotEmpty()) add(RowDef.Regular("fuzer_ds", trFunc("🎤 Dubbed Shows", "🎤 סדרות מדובבות"), currentState.fuzerDubbedSeries))
                }
            }
        }
        val deduplicatedRows = buildList {
            val seenIds = mutableSetOf<String>()
            for (row in newRows) {
                when (row) {
                    is RowDef.Regular -> {
                        if (row.id == "continueWatching") {
                            // Don't deduplicate Continue Watching
                            add(row)
                        } else {
                            val uniqueMovies = row.movies.distinctBy { it.id }.filter { it.id !in seenIds }
                            seenIds.addAll(uniqueMovies.map { it.id })
                            if (uniqueMovies.isNotEmpty()) {
                                add(row.copy(movies = uniqueMovies))
                            }
                        }
                    }
                    is RowDef.Studio -> {
                        val uniqueMovies = row.movies.distinctBy { it.id }.filter { it.id !in seenIds }
                        seenIds.addAll(uniqueMovies.map { it.id })
                        if (uniqueMovies.isNotEmpty()) {
                            add(row.copy(movies = uniqueMovies))
                        }
                    }
                    is RowDef.StudioRibbon -> add(row)
                }
            }
        }
        _uiRows.value = deduplicatedRows
    }

    fun loadMore(id: String) {
        if (id == "ribbon" || id == "fuzer_new" || loadingSet.contains(id)) return
        val nextPage = (pageMap[id] ?: 1) + 1
        if (nextPage > 50) return  // TMDB/Fuzer practical limit
        loadingSet.add(id)

        viewModelScope.launch(Dispatchers.IO) {
            try {
                if (id.startsWith("fuzer_")) {
                    val catId = when (id) {
                        "fuzer_m"   -> com.luminastreams.tv.presentation.home.FuzerCats.MOVIES
                        "fuzer_tv"  -> com.luminastreams.tv.presentation.home.FuzerCats.SERIES
                        "fuzer_mhd" -> com.luminastreams.tv.presentation.home.FuzerCats.MOVIES_HD
                        "fuzer_shd" -> com.luminastreams.tv.presentation.home.FuzerCats.SERIES_HD
                        "fuzer_m4k" -> com.luminastreams.tv.presentation.home.FuzerCats.MOVIES_4K
                        "fuzer_s4k" -> com.luminastreams.tv.presentation.home.FuzerCats.SERIES_4K
                        "fuzer_dm"  -> com.luminastreams.tv.presentation.home.FuzerCats.DUBBED_MOVIES
                        "fuzer_ds"  -> com.luminastreams.tv.presentation.home.FuzerCats.DUBBED_SERIES
                        else -> 0
                    }
                    if (catId != 0) {
                        val newItems = com.luminastreams.tv.data.remote.FuzerEngine.getCategoryPage(catId, nextPage).getOrElse { emptyList() }
                        if (newItems.isEmpty()) { loadingSet.remove(id); return@launch }
                        pageMap[id] = nextPage
                        _state.update { s ->
                            when (id) {
                                "fuzer_m"   -> s.copy(fuzerMovies       = (s.fuzerMovies       + newItems).distinctBy { it.id })
                                "fuzer_tv"  -> s.copy(fuzerSeries       = (s.fuzerSeries       + newItems).distinctBy { it.id })
                                "fuzer_mhd" -> s.copy(fuzerMoviesHD     = (s.fuzerMoviesHD     + newItems).distinctBy { it.id })
                                "fuzer_shd" -> s.copy(fuzerSeriesHD     = (s.fuzerSeriesHD     + newItems).distinctBy { it.id })
                                "fuzer_m4k" -> s.copy(fuzerMovies4K     = (s.fuzerMovies4K     + newItems).distinctBy { it.id })
                                "fuzer_s4k" -> s.copy(fuzerSeries4K     = (s.fuzerSeries4K     + newItems).distinctBy { it.id })
                                "fuzer_dm"  -> s.copy(fuzerDubbedMovies = (s.fuzerDubbedMovies + newItems).distinctBy { it.id })
                                "fuzer_ds"  -> s.copy(fuzerDubbedSeries = (s.fuzerDubbedSeries + newItems).distinctBy { it.id })
                                else -> s
                            }
                        }
                    }
                    return@launch
                }

                val k      = Constants.TMDB_API_KEY
                val region = "US"
                var url    = ""
                var mt     = ""

                if (id.startsWith("studio_")) {
                    val match = Regex("^studio_(.*)_([^_]+)_([a-zA-Z0-9]+)$").find(id)
                    if (match != null) {
                        val studioName = match.groupValues[1]
                        val genreKey = match.groupValues[2]
                        val type = match.groupValues[3]
                        
                        mt = if (type == "m") "movie" else "tv"
                        val prefix = if (type == "m") "discover/movie" else "discover/tv"
                        
                        val studioFilter = when (studioName) {
                            "HBO"       -> if (mt == "movie") "with_watch_providers=1899|384&watch_region=$region" else "with_networks=49|3186"
                            "AMAZON"    -> if (mt == "movie") "with_watch_providers=119&watch_region=$region" else "with_networks=1024"
                            "PARAMOUNT" -> if (mt == "movie") "with_companies=4" else "with_networks=4330|67"
                            "HULU"      -> if (mt == "movie") "with_watch_providers=15&watch_region=$region" else "with_networks=453"
                            "NETFLIX"   -> if (mt == "movie") "with_watch_providers=8&watch_region=$region" else "with_networks=213"
                            "APPLE_TV"  -> if (mt == "movie") "with_watch_providers=350&watch_region=$region" else "with_networks=2552"
                            "DISNEY"    -> if (mt == "movie") "with_companies=2|3|420" else "with_networks=2739"
                            "SONY"      -> "with_companies=5|34|559"
                            else        -> ""
                        }
                        
                        val genreFilter = when (genreKey) {
                            "action" -> "with_genres=28|10759"
                            "ani"    -> "with_genres=16"
                            "comedy" -> "with_genres=35"
                            "drama"  -> "with_genres=18"
                            "scifi"  -> "with_genres=878|14|10765"
                            "crime"  -> "with_genres=80|9648"
                            else     -> "" // "top"
                        }
                        
                        val baseQ = "$BASE/$prefix?api_key=$k&language=en-US&$studioFilter&sort_by=popularity.desc&page=$nextPage"
                        url = if (genreFilter.isNotEmpty()) "$baseQ&$genreFilter" else baseQ
                    }
                } else {
                    when (id) {
                        "movieTrending"  -> { url = "$BASE/trending/movie/week?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                        "moviePremieres" -> { url = "$BASE/movie/now_playing?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                        "movieAnimation" -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieAction"    -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieDrama"     -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieScifi"     -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_genres=878&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieTopRated"  -> { url = "$BASE/movie/top_rated?api_key=$k&language=en-US&page=$nextPage"; mt = "movie" }
                        "movieNetflix"   -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieAppleTV"   -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieDisney"    -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_companies=2|3|420&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieHBO"       -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899|384&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieAmazon"    -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieParamount" -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_companies=4&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieHulu"      -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "movieSony"      -> { url = "$BASE/discover/movie?api_key=$k&language=en-US&with_companies=5|34|559&sort_by=popularity.desc&page=$nextPage"; mt = "movie" }
                        "tvTrending"     -> { url = "$BASE/trending/tv/week?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                        "tvPremieres"    -> { url = "$BASE/tv/on_the_air?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                        "tvAnimation"    -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvDrama"        -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvCrime"        -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvScifi"        -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_genres=10765&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvTopRated"     -> { url = "$BASE/tv/top_rated?api_key=$k&language=en-US&page=$nextPage"; mt = "tv" }
                        "tvNetflix"      -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=213&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvAppleTV"      -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2552&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvDisney"       -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2739&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvHBO"          -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=49|3186&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvAmazon"       -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=1024&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvParamount"    -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=4330|67&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvHulu"         -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_networks=453&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                        "tvSony"         -> { url = "$BASE/discover/tv?api_key=$k&language=en-US&with_companies=5|34|559&sort_by=popularity.desc&page=$nextPage"; mt = "tv" }
                    }
                }

                if (url.isNotEmpty()) {
                    val newItems = fetch(url, mt)
                    if (newItems.isEmpty()) { loadingSet.remove(id); return@launch }  // Stop pagination on empty
                    pageMap[id] = nextPage
                    _state.update { s ->
                    if (id.startsWith("studio_")) {
                        val updatedCategories = s.activeStudioCategories.map { cat ->
                            if (cat.id == id) cat.copy(movies = (cat.movies + newItems).distinctBy { it.id }) else cat
                        }
                        s.copy(activeStudioCategories = updatedCategories)
                    } else {
                        when (id) {
                            "movieTrending"  -> s.copy(movieTrending  = (s.movieTrending  + newItems).distinctBy { it.id })
                            "moviePremieres" -> s.copy(moviePremieres = (s.moviePremieres + newItems).distinctBy { it.id })
                            "movieAnimation" -> s.copy(movieAnimation = (s.movieAnimation + newItems).distinctBy { it.id })
                            "movieAction"    -> s.copy(movieAction    = (s.movieAction + newItems).distinctBy { it.id })
                            "movieDrama"     -> s.copy(movieDrama     = (s.movieDrama + newItems).distinctBy { it.id })
                            "movieScifi"     -> s.copy(movieScifi     = (s.movieScifi + newItems).distinctBy { it.id })
                            "movieTopRated"  -> s.copy(movieTopRated  = (s.movieTopRated + newItems).distinctBy { it.id })
                            "movieNetflix"   -> s.copy(movieNetflix   = (s.movieNetflix + newItems).distinctBy { it.id })
                            "movieAppleTV"   -> s.copy(movieAppleTV   = (s.movieAppleTV + newItems).distinctBy { it.id })
                            "movieDisney"    -> s.copy(movieDisney    = (s.movieDisney + newItems).distinctBy { it.id })
                            "movieHBO"       -> s.copy(movieHBO       = (s.movieHBO + newItems).distinctBy { it.id })
                            "movieAmazon"    -> s.copy(movieAmazon    = (s.movieAmazon + newItems).distinctBy { it.id })
                            "movieParamount" -> s.copy(movieParamount = (s.movieParamount + newItems).distinctBy { it.id })
                            "movieHulu"      -> s.copy(movieHulu      = (s.movieHulu + newItems).distinctBy { it.id })
                            "movieSony"      -> s.copy(movieSony      = (s.movieSony + newItems).distinctBy { it.id })
                            "tvTrending"     -> s.copy(tvTrending     = (s.tvTrending + newItems).distinctBy { it.id })
                            "tvPremieres"    -> s.copy(tvPremieres    = (s.tvPremieres + newItems).distinctBy { it.id })
                            "tvAnimation"    -> s.copy(tvAnimation    = (s.tvAnimation + newItems).distinctBy { it.id })
                            "tvDrama"        -> s.copy(tvDrama        = (s.tvDrama + newItems).distinctBy { it.id })
                            "tvCrime"        -> s.copy(tvCrime        = (s.tvCrime + newItems).distinctBy { it.id })
                            "tvScifi"        -> s.copy(tvScifi        = (s.tvScifi + newItems).distinctBy { it.id })
                            "tvTopRated"     -> s.copy(tvTopRated     = (s.tvTopRated + newItems).distinctBy { it.id })
                            "tvNetflix"      -> s.copy(tvNetflix      = (s.tvNetflix + newItems).distinctBy { it.id })
                            "tvAppleTV"      -> s.copy(tvAppleTV      = (s.tvAppleTV + newItems).distinctBy { it.id })
                            "tvDisney"       -> s.copy(tvDisney       = (s.tvDisney + newItems).distinctBy { it.id })
                            "tvHBO"          -> s.copy(tvHBO          = (s.tvHBO + newItems).distinctBy { it.id })
                            "tvAmazon"       -> s.copy(tvAmazon       = (s.tvAmazon + newItems).distinctBy { it.id })
                            "tvParamount"    -> s.copy(tvParamount    = (s.tvParamount + newItems).distinctBy { it.id })
                            "tvHulu"         -> s.copy(tvHulu         = (s.tvHulu + newItems).distinctBy { it.id })
                            "tvSony"         -> s.copy(tvSony         = (s.tvSony + newItems).distinctBy { it.id })
                            else -> s
                        }
                    }
                    }
                }
            } finally {
                loadingSet.remove(id)
            }
        }
    }

    fun loadFuzerContent() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            if (_state.value.fuzerMovies.isEmpty() && _state.value.fuzerSeries.isEmpty()) {
                _state.update { it.copy(fuzerIsLoading = true, fuzerError = null) }
            }

            try {
                supervisorScope {
                    val moviesR = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES, 1).getOrElse { emptyList() } }
                    val seriesR = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES, 1).getOrElse { emptyList() } }

                    val mRes = moviesR.await()
                    val sRes = seriesR.await()

                    _state.update { s -> s.copy(
                        fuzerIsLoading = false,
                        fuzerItems     = mRes + sRes,
                        fuzerMovies    = mRes,
                        fuzerSeries    = sRes
                    )}

                    val moviesHdDef = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES_HD, 1).getOrElse { emptyList() } }
                    val seriesHdDef = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES_HD, 1).getOrElse { emptyList() } }
                    val movies4kDef = async { FuzerEngine.getCategoryPage(FuzerCats.MOVIES_4K, 1).getOrElse { emptyList() } }
                    val series4kDef = async { FuzerEngine.getCategoryPage(FuzerCats.SERIES_4K, 1).getOrElse { emptyList() } }
                    val dubbedMoviesDef = async { FuzerEngine.getCategoryPage(FuzerCats.DUBBED_MOVIES, 1).getOrElse { emptyList() } }
                    val dubbedSeriesDef = async { FuzerEngine.getCategoryPage(FuzerCats.DUBBED_SERIES, 1).getOrElse { emptyList() } }

                    val mHd = moviesHdDef.await()
                    val sHd = seriesHdDef.await()
                    val m4k = movies4kDef.await()
                    val s4k = series4kDef.await()
                    val dmRes = dubbedMoviesDef.await()
                    val dsRes = dubbedSeriesDef.await()

                    // ⚡ Single batched update instead of 2 separate updates
                    _state.update { s -> s.copy(
                        fuzerMoviesHD = mHd,
                        fuzerSeriesHD = sHd,
                        fuzerMovies4K = m4k,
                        fuzerSeries4K = s4k,
                        fuzerDubbedMovies = dmRes,
                        fuzerDubbedSeries = dsRes
                    )}
                }
            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(fuzerIsLoading = false, fuzerError = "שגיאת טעינה: ${e.message}") }
            }
        }
    }

    private fun loadAll() {
        activeJob?.cancel()
        activeJob = viewModelScope.launch(Dispatchers.IO) {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val k      = Constants.TMDB_API_KEY
                val region = "US"

                coroutineScope {
                    val mTrend  = async { fetch("$BASE/trending/movie/week?api_key=$k&language=en-US", "movie") }
                    val tvTrend = async { fetch("$BASE/trending/tv/week?api_key=$k&language=en-US",   "tv") }
                    val mNow    = async { fetch("$BASE/movie/now_playing?api_key=$k&language=en-US",  "movie") }
                    val mNflx   = async { fetch("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=8&watch_region=$region&sort_by=popularity.desc", "movie") }

                    _state.update { s -> s.copy(
                        isLoading      = false,
                        movieTrending  = mTrend.await(),
                        tvTrending     = tvTrend.await(),
                        moviePremieres = mNow.await(),
                        movieNetflix   = mNflx.await()
                    ) }
                }

                if (DeviceProfile.tier == DeviceProfile.Tier.LOW) {
                    loadWave2Batched(k, region, batchSize = 3, delayMs = 150)
                } else {
                    loadWave2Batched(k, region, batchSize = 6, delayMs = 50)
                }

            } catch (e: CancellationException) { throw e
            } catch (e: Exception) {
                _state.update { it.copy(isLoading = false, error = "Network error: ${e.message}") }
            }
        }
    }

    private suspend fun loadWave2Batched(k: String, region: String, batchSize: Int, delayMs: Long) {
        val requests: List<Pair<String, (HomeState, List<Movie>) -> HomeState>> = listOf(
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=28&sort_by=popularity.desc") { s, v -> s.copy(movieAction = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc") { s, v -> s.copy(movieAnimation = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=16&sort_by=popularity.desc") { s, v -> s.copy(tvAnimation = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc") { s, v -> s.copy(movieDrama = v) },
            Pair("$BASE/movie/top_rated?api_key=$k&language=en-US") { s, v -> s.copy(movieTopRated = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=1899|384&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieHBO = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=119&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieAmazon = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=18&sort_by=popularity.desc") { s, v -> s.copy(tvDrama = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=80&sort_by=popularity.desc") { s, v -> s.copy(tvCrime = v) },
            Pair("$BASE/tv/top_rated?api_key=$k&language=en-US") { s, v -> s.copy(tvTopRated = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=213&sort_by=popularity.desc") { s, v -> s.copy(tvNetflix = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=49|3186&sort_by=popularity.desc") { s, v -> s.copy(tvHBO = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=350&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieAppleTV = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_companies=2|3|420&sort_by=popularity.desc") { s, v -> s.copy(movieDisney = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_companies=4&sort_by=popularity.desc") { s, v -> s.copy(movieParamount = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_watch_providers=15&watch_region=$region&sort_by=popularity.desc") { s, v -> s.copy(movieHulu = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_genres=10765&sort_by=popularity.desc") { s, v -> s.copy(tvScifi = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2552&sort_by=popularity.desc") { s, v -> s.copy(tvAppleTV = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=2739&sort_by=popularity.desc") { s, v -> s.copy(tvDisney = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=1024&sort_by=popularity.desc") { s, v -> s.copy(tvAmazon = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=4330|67&sort_by=popularity.desc") { s, v -> s.copy(tvParamount = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_networks=453&sort_by=popularity.desc") { s, v -> s.copy(tvHulu = v) },
            Pair("$BASE/discover/movie?api_key=$k&language=en-US&with_companies=5|34|559&sort_by=popularity.desc") { s, v -> s.copy(movieSony = v) },
            Pair("$BASE/discover/tv?api_key=$k&language=en-US&with_companies=5|34|559&sort_by=popularity.desc") { s, v -> s.copy(tvSony = v) }
        )

        requests.chunked(batchSize).forEach { batch ->
            val results = coroutineScope {
                batch.map { (url, updater) ->
                    async {
                        val mt = if (url.contains("/movie")) "movie" else "tv"
                        updater to fetch(url, mt)
                    }
                }.awaitAll()
            }

            _state.update { currentState ->
                var nextState = currentState
                results.forEach { (updater, data) ->
                    nextState = updater(nextState, data.distinctBy { it.id })
                }
                nextState
            }

            delay(delayMs)
        }
    }

    // fetch() is always called from an IO-dispatched coroutine — no need for inner withContext
    private fun fetch(url: String, mediaType: String): List<Movie> {
        return try {
            val bodyStr = http.newCall(Request.Builder().url(url).build()).execute().use { it.body.string() }
            val arr = JSONObject(bodyStr).optJSONArray("results") ?: return emptyList()

            val out = mutableListOf<Movie>()
            for (i in 0 until arr.length()) {
                val j  = arr.getJSONObject(i)
                val mt = j.optString("media_type").ifBlank { mediaType }

                val backdropRaw = j.optString("backdrop_path")
                if (backdropRaw.isBlank() || backdropRaw == "null") continue

                val title = if (mt == "tv")
                    j.optString("name").ifBlank { j.optString("original_name") }
                else
                    j.optString("title").ifBlank { j.optString("original_title") }

                val date       = if (mt == "tv") j.optString("first_air_date") else j.optString("release_date")
                val posterPath = j.optString("poster_path")
                if (posterPath.isBlank() || posterPath == "null") continue

                out += Movie(
                    id              = "${mt}_${j.optInt("id")}",
                    title           = title,
                    posterUrl       = "$imgBase/w500$posterPath",
                    backdropUrl     = "$imgBase/w780$backdropRaw",
                    overview        = j.optString("overview"),
                    year            = date.take(4).toIntOrNull() ?: 0,
                    genre           = genreLabel(j.optJSONArray("genre_ids")?.optInt(0, 0) ?: 0, mt),
                    rating          = j.optDouble("vote_average", 0.0).toFloat(),
                    mediaType       = mt,
                    resolutionBadge = ""
                )
            }
            out
        } catch (_: Exception) { emptyList() }
    }

    private fun genreLabel(id: Int, mt: String): String = when (id) {
        28 -> "Action"; 12 -> "Adventure"; 16 -> "Animation"; 35 -> "Comedy"
        80 -> "Crime"; 99 -> "Documentary"; 18 -> "Drama"; 10751 -> "Family"
        14 -> "Fantasy"; 36 -> "History"; 27 -> "Horror"; 10402 -> "Music"
        9648 -> "Mystery"; 10749 -> "Romance"; 878 -> "Sci-Fi"; 10770 -> "TV Movie"
        53 -> "Thriller"; 10752 -> "War"; 37 -> "Western"
        10759 -> "Action & Adventure"; 10762 -> "Kids"; 10763 -> "News"
        10764 -> "Reality"; 10765 -> "Sci-Fi & Fantasy"; 10766 -> "Soap"
        10767 -> "Talk"; 10768 -> "War & Politics"
        else -> if (mt == "tv") "TV Show" else "Movie"
    }

    private companion object {
        private const val BASE = "https://api.themoviedb.org/3"
    }
}
