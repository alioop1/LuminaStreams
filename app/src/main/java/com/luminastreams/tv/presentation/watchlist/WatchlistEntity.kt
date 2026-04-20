package com.luminastreams.tv.data.local.watchlist

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.luminastreams.tv.domain.model.Movie

@Entity(tableName = "watchlist")
data class WatchlistEntity(
    @PrimaryKey val id: String,
    val title: String,
    val backdropUrl: String,
    val posterUrl: String,
    val overview: String,
    val rating: Float,
    val year: Int,
    val genre: String,
    val mediaType: String
) {
    fun toMovie() = Movie(
        id = id,
        title = title,
        backdropUrl = backdropUrl,
        posterUrl = posterUrl,
        overview = overview,
        rating = rating,
        year = year,
        genre = genre,
        mediaType = mediaType
    )
}

fun Movie.toEntity() = WatchlistEntity(
    id = id,
    title = title,
    backdropUrl = backdropUrl,
    posterUrl = posterUrl,
    overview = overview,
    rating = rating,
    year = year,
    genre = genre,
    mediaType = mediaType
)