package com.luminastreams.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.data.local.iptv.IptvDao
import com.luminastreams.tv.data.local.iptv.PlaylistEntity
import com.luminastreams.tv.data.local.watchlist.WatchlistDao
import com.luminastreams.tv.data.local.watchlist.WatchlistEntity

@Database(
    entities = [
        PlaylistEntity::class,
        ChannelEntity::class,
        EpgProgramEntity::class,
        WatchlistEntity::class
    ],
    version = 2, // Bumped version to migrate the new watchlist table
    exportSchema = false
)
abstract class LuminaDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao
    abstract fun watchlistDao(): WatchlistDao
}