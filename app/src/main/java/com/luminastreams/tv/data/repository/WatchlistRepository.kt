package com.luminastreams.tv.data.repository

import com.luminastreams.tv.data.local.watchlist.WatchlistDao
import com.luminastreams.tv.data.local.watchlist.toEntity
import com.luminastreams.tv.domain.model.Movie
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

@Suppress("unused")
class WatchlistRepository(private val dao: WatchlistDao) {

    fun getWatchlistFlow(): Flow<List<Movie>> =
        dao.getWatchlistFlow().map { list -> list.map { it.toMovie() }.reversed() }

    suspend fun getWatchlistSync(): List<Movie> =
        dao.getWatchlistSync().map { it.toMovie() }.reversed()

    suspend fun isInWatchlist(id: String): Boolean =
        dao.isInWatchlist(id)

    fun isInWatchlistFlow(id: String): Flow<Boolean> =
        dao.isInWatchlistFlow(id)

    suspend fun toggleWatchlist(movie: Movie): Boolean {
        val exists = dao.isInWatchlist(movie.id)
        if (exists) {
            dao.removeFromWatchlist(movie.id)
        } else {
            dao.addToWatchlist(movie.toEntity())
        }
        return !exists // Returns true if added, false if removed
    }

    suspend fun clearWatchlist() {
        dao.clearWatchlist()
    }

    suspend fun addToWatchlist(movie: Movie) {
        dao.addToWatchlist(movie.toEntity())
    }

    suspend fun replaceWatchlist(movies: List<Movie>) {
        dao.replaceWatchlist(movies.map { it.toEntity() })
    }
}