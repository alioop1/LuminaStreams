package com.luminastreams.tv.data.sync

import android.util.Log
import com.luminastreams.tv.data.local.WatchProgressManager
import com.luminastreams.tv.data.repository.WatchlistRepository
import com.luminastreams.tv.domain.model.Movie
import com.luminastreams.tv.domain.repository.AuthRepository
import com.luminastreams.tv.domain.repository.UserSyncRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

class SyncManager(
    private val authRepository: AuthRepository,
    private val syncRepository: UserSyncRepository,
    private val watchlistRepository: WatchlistRepository,
    private val progressManager: WatchProgressManager,
    private val context: android.content.Context
) {
    private val TAG = "SyncManager"
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    // @Volatile: this flag is read from Firestore snapshot listeners (main/IO)
    // and written from push/pull coroutines on Dispatchers.IO. Without @Volatile
    // stale reads can cause feedback loops (push → snapshot → pull → merge → push).
    @Volatile
    private var isSyncingFromCloud = false
    private var syncJob: Job? = null

    // Observable sync status for UI
    private val _syncStatus = MutableStateFlow("")
    val syncStatus: StateFlow<String> = _syncStatus.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    fun stop() {
        syncJob?.cancel()
    }

    private fun Movie.toJsonString(): String {
        return JSONObject().apply {
            put("id", id)
            put("title", title)
            put("backdropUrl", backdropUrl)
            put("posterUrl", posterUrl)
            put("overview", overview)
            put("rating", rating.toDouble())
            put("year", year)
            put("genre", genre)
            put("mediaType", mediaType)
        }.toString()
    }

    private fun String.toMovie(): Movie? {
        return runCatching {
            val j = JSONObject(this)
            Movie(
                id = j.getString("id"),
                title = j.getString("title"),
                backdropUrl = j.optString("backdropUrl", ""),
                posterUrl = j.optString("posterUrl", ""),
                overview = j.optString("overview", ""),
                rating = j.optDouble("rating", 0.0).toFloat(),
                year = j.optInt("year", 0),
                genre = j.optString("genre", ""),
                mediaType = j.optString("mediaType", "movie")
            )
        }.getOrNull()
    }

    /**
     * Pushes a single favorite item to the cloud.
     */
    fun pushFavorites() {
        if (isSyncingFromCloud) return
        scope.launch {
            val user = authRepository.currentUser.firstOrNull() ?: return@launch
            val localFavorites = watchlistRepository.getWatchlistSync().map { it.toJsonString() }
            syncRepository.syncFavorites(user.id, localFavorites)
        }
    }

    /**
     * Pushes a single progress update to the cloud.
     */
    fun pushProgress(key: String, json: String) {
        if (isSyncingFromCloud) return
        scope.launch {
            val user = authRepository.currentUser.firstOrNull() ?: return@launch
            syncRepository.syncProgress(user.id, mapOf(key to json))
        }
    }

    /**
     * Pushes multiple progress updates to the cloud.
     */
    fun pushMultipleProgress(progressMap: Map<String, String>) {
        if (isSyncingFromCloud) return
        scope.launch {
            val user = authRepository.currentUser.firstOrNull() ?: return@launch
            syncRepository.syncProgress(user.id, progressMap)
        }
    }

    /**
     * Pushes local favorites and watch progress to the cloud.
     */
    fun pushToCloud() {
        scope.launch {
            val user = authRepository.currentUser.firstOrNull() ?: return@launch

            // 1. Sync Favorites
            val localFavorites = watchlistRepository.getWatchlistSync().map { it.toJsonString() }
            syncRepository.syncFavorites(user.id, localFavorites)

            // 2. Sync Progress
            val localProgress = progressManager.getAllProgress()
            if (localProgress.isNotEmpty()) {
                syncRepository.syncProgress(user.id, localProgress)
            }
        }
    }

    /**
     * Pulls cloud favorites and watch progress to the local device.
     */
    fun pullFromCloud() {
        scope.launch {
            val user = authRepository.currentUser.firstOrNull() ?: return@launch
            isSyncingFromCloud = true
            try {
                // 1. Fetch Favorites — safe extraction
                val cloudFavorites = syncRepository.getFavorites(user.id).getOrNull()
                if (cloudFavorites != null && cloudFavorites.isNotEmpty()) {
                    val cloudMovies = cloudFavorites.mapNotNull { it.toMovie() }
                    if (cloudMovies.isNotEmpty()) {
                        watchlistRepository.replaceWatchlist(cloudMovies)
                    }
                }

                // 2. Fetch Progress — safe extraction
                val cloudProgress = syncRepository.getProgress(user.id).getOrNull()
                if (cloudProgress != null && cloudProgress.isNotEmpty()) {
                    progressManager.mergeCloudProgress(cloudProgress, context)
                }
            } finally {
                isSyncingFromCloud = false
            }
        }
    }

    /**
     * Full bidirectional sync with status reporting.
     * Pushes local data first, then pulls cloud data.
     */
    fun fullSync() {
        scope.launch {
            val user = authRepository.currentUser.firstOrNull()
            if (user == null) {
                Log.w(TAG, "fullSync: no user logged in")
                _syncStatus.value = "NO_USER"
                delay(3000)
                _syncStatus.value = ""
                return@launch
            }

            _isSyncing.value = true
            _syncStatus.value = "PUSHING"
            Log.d(TAG, "fullSync: START for user uid=${user.id} email=${user.email}")

            try {
                val completed = kotlinx.coroutines.withTimeoutOrNull(15_000L) {
                    // Push favorites
                    val localFavorites = watchlistRepository.getWatchlistSync().map { it.toJsonString() }
                    Log.d(TAG, "fullSync: pushing ${localFavorites.size} favorites")
                    val favResult = syncRepository.syncFavorites(user.id, localFavorites)
                    if (favResult.isFailure) {
                        Log.e(TAG, "fullSync: syncFavorites FAILED", favResult.exceptionOrNull())
                        throw favResult.exceptionOrNull() ?: Exception("syncFavorites failed")
                    }
                    Log.d(TAG, "fullSync: favorites pushed OK")

                    // Push progress
                    val localProgress = progressManager.getAllProgress()
                    Log.d(TAG, "fullSync: pushing ${localProgress.size} progress entries")
                    if (localProgress.isNotEmpty()) {
                        val progResult = syncRepository.syncProgress(user.id, localProgress)
                        if (progResult.isFailure) {
                            Log.e(TAG, "fullSync: syncProgress FAILED", progResult.exceptionOrNull())
                            throw progResult.exceptionOrNull() ?: Exception("syncProgress failed")
                        }
                        Log.d(TAG, "fullSync: progress pushed OK")
                    }

                    _syncStatus.value = "PULLING"
                    Log.d(TAG, "fullSync: pulling from cloud")

                    // Pull favorites
                    isSyncingFromCloud = true
                    val cloudFavResult = syncRepository.getFavorites(user.id)
                    if (cloudFavResult.isFailure) {
                        Log.e(TAG, "fullSync: getFavorites FAILED", cloudFavResult.exceptionOrNull())
                    }
                    val cloudFavorites = cloudFavResult.getOrNull()
                    Log.d(TAG, "fullSync: got ${cloudFavorites?.size ?: 0} cloud favorites")
                    if (cloudFavorites != null && cloudFavorites.isNotEmpty()) {
                        val cloudMovies = cloudFavorites.mapNotNull { it.toMovie() }
                        if (cloudMovies.isNotEmpty()) {
                            watchlistRepository.replaceWatchlist(cloudMovies)
                            Log.d(TAG, "fullSync: replaced local watchlist with ${cloudMovies.size} movies")
                        }
                    }

                    // Pull progress
                    val cloudProgResult = syncRepository.getProgress(user.id)
                    if (cloudProgResult.isFailure) {
                        Log.e(TAG, "fullSync: getProgress FAILED", cloudProgResult.exceptionOrNull())
                    }
                    val cloudProgress = cloudProgResult.getOrNull()
                    Log.d(TAG, "fullSync: got ${cloudProgress?.size ?: 0} cloud progress entries")
                    if (cloudProgress != null && cloudProgress.isNotEmpty()) {
                        progressManager.mergeCloudProgress(cloudProgress, context)
                        Log.d(TAG, "fullSync: merged cloud progress")
                    }
                    true
                }

                if (completed == true) {
                    _syncStatus.value = "SUCCESS"
                    Log.d(TAG, "fullSync: ✓ completed successfully")
                } else {
                    _syncStatus.value = "ERROR"
                    Log.e(TAG, "fullSync: ✗ timed out after 15s")
                }
            } catch (e: Exception) {
                Log.e(TAG, "fullSync: ✗ FAILED with exception", e)
                _syncStatus.value = "ERROR"
            } finally {
                isSyncingFromCloud = false
                _isSyncing.value = false
            }

            // Auto-clear status after 3 seconds so the UI resets
            delay(3000)
            _syncStatus.value = ""
        }
    }

    /**
     * Start real-time sync with Firebase (Snapshot Listener).
     * Also performs an initial pull to load cloud data immediately.
     */
    fun startRealtimeSync() {
        syncJob?.cancel()
        syncJob = scope.launch {
            authRepository.currentUser.collectLatest { user ->
                if (user != null) {
                    Log.d(TAG, "startRealtimeSync: user ${user.id} — performing initial pull")
                    // Initial pull to guarantee data is loaded
                    isSyncingFromCloud = true
                    try {
                        val cloudFavorites = syncRepository.getFavorites(user.id).getOrNull()
                        if (cloudFavorites != null && cloudFavorites.isNotEmpty()) {
                            val cloudMovies = cloudFavorites.mapNotNull { it.toMovie() }
                            if (cloudMovies.isNotEmpty()) {
                                watchlistRepository.replaceWatchlist(cloudMovies)
                            }
                        }
                        val cloudProgress = syncRepository.getProgress(user.id).getOrNull()
                        if (cloudProgress != null && cloudProgress.isNotEmpty()) {
                            progressManager.mergeCloudProgress(cloudProgress, context)
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Initial pull failed", e)
                    } finally {
                        isSyncingFromCloud = false
                    }

                    // Then start listening for real-time changes (debounced to prevent rapid-fire updates)
                    Log.d(TAG, "startRealtimeSync: starting snapshot listener for ${user.id}")
                    @OptIn(FlowPreview::class)
                    syncRepository.observeUserData(user.id).debounce(500).collect { data ->
                        isSyncingFromCloud = true
                        try {
                            // 1. Fetch Favorites — safe extraction (Firestore returns Any types)
                            val rawFavorites = data["favorites"]
                            if (rawFavorites is List<*>) {
                                val cloudMovies = rawFavorites.filterIsInstance<String>().mapNotNull { it.toMovie() }
                                watchlistRepository.replaceWatchlist(cloudMovies)
                            }

                            // 2. Fetch Progress — safe extraction
                            val rawProgress = data["progress"]
                            if (rawProgress is Map<*, *>) {
                                val cloudProgress = rawProgress.entries
                                    .filter { it.key is String && it.value is String }
                                    .associate { (it.key as String) to (it.value as String) }
                                progressManager.mergeCloudProgress(cloudProgress, context)
                            }
                        } finally {
                            isSyncingFromCloud = false
                        }
                    }
                }
            }
        }
    }
}
