package com.luminastreams.tv.data.local.iptv

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Index

@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val url: String,
    val epgUrl: String,
    val isActive: Boolean,
    val lastUpdated: Long
)

@Entity(
    tableName = "channels",
    indices = [Index("playlistId"), Index("groupTitle"), Index("name")]
)
data class ChannelEntity(
    @PrimaryKey val id: String,
    val playlistId: String,
    val name: String,
    val logoUrl: String,
    val streamUrl: String,
    val groupTitle: String,
    val tvgId: String,
    val tvgName: String,
    val isAdult: Boolean,
    val number: Int,
    val resolution: String,
    var isFavorite: Boolean = false,
    var lastWatched: Long = 0L
)

@Entity(
    tableName = "epg_programs",
    indices = [Index("channelId"), Index("startTime"), Index("endTime")]
)
data class EpgProgramEntity(
    @PrimaryKey(autoGenerate = true) val uid: Int = 0,
    val channelId: String,
    val title: String,
    val description: String,
    val startTime: Long,
    val endTime: Long,
    val posterUrl: String,
    val category: String
)