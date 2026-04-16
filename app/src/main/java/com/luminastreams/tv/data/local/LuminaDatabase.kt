package com.luminastreams.tv.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.data.local.iptv.IptvDao
import com.luminastreams.tv.data.local.iptv.PlaylistEntity

@Database(
    entities = [PlaylistEntity::class, ChannelEntity::class, EpgProgramEntity::class],
    version = 1,
    exportSchema = false
)
abstract class LuminaDatabase : RoomDatabase() {
    abstract fun iptvDao(): IptvDao
}