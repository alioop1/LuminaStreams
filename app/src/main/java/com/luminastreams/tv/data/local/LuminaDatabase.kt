package com.luminastreams.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.data.local.iptv.IptvDao
import com.luminastreams.tv.data.local.iptv.PlaylistEntity
import com.luminastreams.tv.data.local.watchlist.WatchlistDao
import com.luminastreams.tv.data.local.watchlist.WatchlistEntity
import com.luminastreams.tv.data.local.entities.UserEntity
import com.luminastreams.tv.data.local.dao.UserDao

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        EpgProgramEntity::class,
        WatchlistEntity::class,
        UserEntity::class
    ],
    version = 3, // Bumped for User storage
    exportSchema = false
)
abstract class LuminaDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao
    abstract fun watchlistDao(): WatchlistDao
    abstract fun userDao(): UserDao
}