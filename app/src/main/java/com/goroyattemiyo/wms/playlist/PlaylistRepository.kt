package com.goroyattemiyo.wms.playlist

import java.util.UUID
import kotlinx.coroutines.flow.Flow

class PlaylistRepository internal constructor(private val dao: PlaylistDao) {
    val playlists: Flow<List<PlaylistSummary>> = dao.observePlaylists()

    fun observeItems(playlistId: String): Flow<List<PlaylistMediaItem>> = dao.observeItems(playlistId)

    suspend fun create(name: String): PlaylistEntity {
        val cleanName = requireName(name)
        val now = System.currentTimeMillis()
        return PlaylistEntity(UUID.randomUUID().toString(), cleanName, now, now).also {
            dao.insertPlaylist(it)
        }
    }

    suspend fun rename(playlistId: String, name: String) {
        dao.renamePlaylist(playlistId, requireName(name), System.currentTimeMillis())
    }

    suspend fun delete(playlistId: String) {
        dao.deletePlaylist(playlistId)
    }

    suspend fun addMedia(playlistId: String, mediaId: String): Boolean =
        dao.appendMedia(playlistId, mediaId, System.currentTimeMillis())

    suspend fun removeMedia(playlistId: String, mediaId: String): Boolean =
        dao.removeMedia(playlistId, mediaId, System.currentTimeMillis())

    suspend fun moveMedia(playlistId: String, mediaId: String, delta: Int): Boolean {
        require(delta == -1 || delta == 1)
        return dao.moveMedia(playlistId, mediaId, delta, System.currentTimeMillis())
    }

    private fun requireName(name: String): String = name.trim().also {
        require(it.isNotEmpty()) { "プレイリスト名を入力してください" }
        require(it.length <= 80) { "プレイリスト名は80文字以内にしてください" }
    }
}
