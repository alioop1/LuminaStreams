package com.luminastreams.tv.data.repository

import com.luminastreams.tv.data.local.iptv.ChannelEntity
import com.luminastreams.tv.data.local.iptv.EpgProgramEntity
import com.luminastreams.tv.data.local.iptv.IptvDao
import com.luminastreams.tv.data.local.iptv.PlaylistEntity
import com.luminastreams.tv.presentation.iptv.EpgParser
import com.luminastreams.tv.presentation.iptv.M3uParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class IptvRepository(val dao: IptvDao) {

    fun getAllPlaylists(): Flow<List<PlaylistEntity>> = dao.getAllPlaylists()

    fun getChannels(playlistId: String): Flow<List<ChannelEntity>> = dao.getChannelsForPlaylist(playlistId)

    fun getFavoriteChannels(): Flow<List<ChannelEntity>> = dao.getFavoriteChannels()

    fun getGroups(playlistId: String): Flow<List<String>> = dao.getGroupsForPlaylist(playlistId)

    suspend fun loadAndSavePlaylist(name: String, url: String, epgUrl: String) = withContext(Dispatchers.IO) {
        val playlistId = "pl_${System.currentTimeMillis()}"
        dao.deactivateAllPlaylists()
        dao.insertPlaylist(PlaylistEntity(playlistId, name, url, epgUrl, true, System.currentTimeMillis()))
        dao.clearChannels(playlistId)

        M3uParser.parseStreaming(playlistId, url) { batch ->
            dao.insertChannels(batch)
        }
    }

    suspend fun loadEpg(epgUrl: String) = withContext(Dispatchers.IO) {
        if (epgUrl.isBlank()) return@withContext
        dao.clearAllEpg()
        EpgParser.parseStreaming(epgUrl) { batch ->
            dao.insertEpgPrograms(batch)
        }
    }

    suspend fun getCurrentProgram(channelId: String): EpgProgramEntity? {
        val now = System.currentTimeMillis()
        return dao.getEpgForChannel(channelId, now).firstOrNull { it.startTime <= now && it.endTime > now }
    }

    suspend fun updateLastWatched(channelId: String) = dao.updateLastWatched(channelId, System.currentTimeMillis())

    suspend fun toggleFavorite(channelId: String, isFavorite: Boolean) = dao.updateFavoriteStatus(channelId, isFavorite)
}