package com.luminastreams.tv.data.local.iptv

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface IptvDao {

    // --- Playlists ---
    @Query("SELECT * FROM playlists ORDER BY lastUpdated DESC")
    fun getAllPlaylists(): Flow<List<PlaylistEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET isActive = 0")
    suspend fun deactivateAllPlaylists()

    // --- Channels ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertChannels(channels: List<ChannelEntity>)

    @Query("DELETE FROM channels WHERE playlistId = :playlistId")
    suspend fun clearChannels(playlistId: String)

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId ORDER BY number ASC")
    fun getChannelsForPlaylist(playlistId: String): Flow<List<ChannelEntity>>

    @Query("SELECT DISTINCT groupTitle FROM channels WHERE playlistId = :playlistId")
    fun getGroupsForPlaylist(playlistId: String): Flow<List<String>>

    @Query("SELECT * FROM channels WHERE playlistId = :playlistId AND groupTitle = :group ORDER BY number ASC")
    suspend fun getChannelsByGroup(playlistId: String, group: String): List<ChannelEntity>

    @Query("SELECT * FROM channels WHERE isFavorite = 1 ORDER BY number ASC")
    fun getFavoriteChannels(): Flow<List<ChannelEntity>>

    // הזרקת הלוגואים (רק אם הערוץ ללא לוגו)
    @Query("""
        UPDATE channels 
        SET logoUrl = :logoUrl 
        WHERE playlistId = :playlistId 
        AND logoUrl = '' 
        AND (
            tvgId = :epgId 
            OR name = :epgId 
            OR REPLACE(LOWER(name), ' ', '') = REPLACE(LOWER(:epgId), ' ', '')
            OR :epgId LIKE '%' || name || '%'
            OR name LIKE '%' || :epgId || '%'
        )
    """)
    suspend fun updateChannelLogo(playlistId: String, epgId: String, logoUrl: String)

    // --- EPG ---
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertEpgPrograms(programs: List<EpgProgramEntity>)

    @Query("DELETE FROM epg_programs")
    suspend fun clearAllEpg()

    // EPG נוכחי (ל-Header)
    @Query("""
        SELECT * FROM epg_programs 
        WHERE (
            channelId COLLATE NOCASE IN (:id, :tvgId, :tvgName, :name)
            OR channelId COLLATE NOCASE LIKE '%' || :cleanName || '%'
        )
        AND endTime > :currentTime 
        ORDER BY startTime ASC 
        LIMIT 1
    """)
    suspend fun getCurrentEpgForChannel(
        id: String,
        tvgId: String,
        tvgName: String,
        name: String,
        cleanName: String,
        currentTime: Long
    ): EpgProgramEntity?

    // EPG עתידי (עבור ה- TV Guide)
    @Query("""
        SELECT * FROM epg_programs 
        WHERE (
            channelId COLLATE NOCASE IN (:id, :tvgId, :tvgName, :name)
            OR channelId COLLATE NOCASE LIKE '%' || :cleanName || '%'
        )
        AND endTime > :currentTime 
        ORDER BY startTime ASC 
        LIMIT 10
    """)
    suspend fun getFutureEpgForChannel(
        id: String,
        tvgId: String,
        tvgName: String,
        name: String,
        cleanName: String,
        currentTime: Long
    ): List<EpgProgramEntity>
}