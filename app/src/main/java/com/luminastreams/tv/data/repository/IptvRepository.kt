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

    suspend fun loadAndSavePlaylist(name: String, url: String, epgUrl: String): String = withContext(Dispatchers.IO) {
        val playlistId = "pl_${System.currentTimeMillis()}"
        dao.deactivateAllPlaylists()
        dao.insertPlaylist(PlaylistEntity(playlistId, name, url, epgUrl, true, System.currentTimeMillis()))
        dao.clearChannels(playlistId)

        M3uParser.parseStreaming(playlistId, url) { batch ->
            dao.insertChannels(batch)
        }
        return@withContext playlistId
    }

    suspend fun loadEpg(epgUrl: String, playlistId: String) = withContext(Dispatchers.IO) {
        if (epgUrl.isBlank()) return@withContext
        dao.clearAllEpg()

        EpgParser.parseStreaming(
            epgUrl = epgUrl,
            onLogosFound = { logosMap ->
                logosMap.forEach { (epgId, logoUrl) ->
                    dao.updateChannelLogo(playlistId, epgId, logoUrl)
                }
            },
            onBatchParsed = { batch ->
                dao.insertEpgPrograms(batch)
            }
        )
    }

    suspend fun getCurrentProgram(channel: ChannelEntity): EpgProgramEntity? {
        val now = System.currentTimeMillis()
        val cleanName = channel.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()

        return dao.getCurrentEpgForChannel(
            id = channel.id,
            tvgId = channel.tvgId,
            tvgName = channel.tvgName,
            name = channel.name,
            cleanName = cleanName,
            currentTime = now
        )
    }

    suspend fun getFuturePrograms(channel: ChannelEntity, currentTime: Long): List<EpgProgramEntity> {
        val cleanName = channel.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return dao.getFutureEpgForChannel(
            id = channel.id,
            tvgId = channel.tvgId,
            tvgName = channel.tvgName,
            name = channel.name,
            cleanName = cleanName,
            currentTime = currentTime
        )
    }

    suspend fun getFullDayPrograms(channel: ChannelEntity, startFrom: Long, endBefore: Long): List<EpgProgramEntity> {
        val cleanName = channel.name.replace(Regex("[^a-zA-Z0-9]"), "").lowercase()
        return dao.getEpgForChannelInRange(
            id = channel.id,
            tvgId = channel.tvgId,
            tvgName = channel.tvgName,
            name = channel.name,
            cleanName = cleanName,
            startFrom = startFrom,
            endBefore = endBefore
        )
    }
}