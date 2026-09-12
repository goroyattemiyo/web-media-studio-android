package com.goroyattemiyo.wms.playlist

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query(
        """
        SELECT p.id, p.name, p.createdAt, p.updatedAt, COUNT(e.mediaId) AS itemCount
        FROM playlists p
        LEFT JOIN playlist_entries e ON e.playlistId = p.id
        GROUP BY p.id
        ORDER BY p.updatedAt DESC, p.createdAt DESC
        """,
    )
    fun observePlaylists(): Flow<List<PlaylistSummary>>

    @Query(
        """
        SELECT m.*, e.position AS playlistPosition
        FROM playlist_entries e
        INNER JOIN media m ON m.id = e.mediaId
        WHERE e.playlistId = :playlistId
        ORDER BY e.position ASC
        """,
    )
    fun observeItems(playlistId: String): Flow<List<PlaylistMediaItem>>

    @Insert
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name, updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("SELECT COALESCE(MAX(position), -1) FROM playlist_entries WHERE playlistId = :playlistId")
    suspend fun maxPosition(playlistId: String): Int

    @Query("SELECT COUNT(*) FROM playlist_entries WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun entryCount(playlistId: String, mediaId: String): Int

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEntry(entry: PlaylistEntryEntity): Long

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun getEntry(playlistId: String, mediaId: String): PlaylistEntryEntity?

    @Query("SELECT * FROM playlist_entries WHERE playlistId = :playlistId AND position = :position LIMIT 1")
    suspend fun getEntryAt(playlistId: String, position: Int): PlaylistEntryEntity?

    @Update
    suspend fun updateEntry(entry: PlaylistEntryEntity)

    @Query("DELETE FROM playlist_entries WHERE playlistId = :playlistId AND mediaId = :mediaId")
    suspend fun deleteEntry(playlistId: String, mediaId: String)

    @Query("UPDATE playlist_entries SET position = position - 1 WHERE playlistId = :playlistId AND position > :position")
    suspend fun compactAfter(playlistId: String, position: Int)

    @Query("UPDATE playlists SET updatedAt = :updatedAt WHERE id = :playlistId")
    suspend fun touchPlaylist(playlistId: String, updatedAt: Long)

    @Transaction
    suspend fun appendMedia(playlistId: String, mediaId: String, updatedAt: Long): Boolean {
        if (entryCount(playlistId, mediaId) > 0) return false
        insertEntry(PlaylistEntryEntity(playlistId, mediaId, maxPosition(playlistId) + 1))
        touchPlaylist(playlistId, updatedAt)
        return true
    }

    @Transaction
    suspend fun removeMedia(playlistId: String, mediaId: String, updatedAt: Long): Boolean {
        val entry = getEntry(playlistId, mediaId) ?: return false
        deleteEntry(playlistId, mediaId)
        compactAfter(playlistId, entry.position)
        touchPlaylist(playlistId, updatedAt)
        return true
    }

    @Transaction
    suspend fun moveMedia(playlistId: String, mediaId: String, delta: Int, updatedAt: Long): Boolean {
        val entry = getEntry(playlistId, mediaId) ?: return false
        val swap = getEntryAt(playlistId, entry.position + delta) ?: return false
        updateEntry(entry.copy(position = swap.position))
        updateEntry(swap.copy(position = entry.position))
        touchPlaylist(playlistId, updatedAt)
        return true
    }
}
