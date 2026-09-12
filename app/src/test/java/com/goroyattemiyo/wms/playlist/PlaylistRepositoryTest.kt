package com.goroyattemiyo.wms.playlist

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistRepositoryTest {
    private val dao = FakePlaylistDao()
    private val repository = PlaylistRepository(dao)

    @Test
    fun createTrimsNameAndRejectsBlankName() = runTest {
        val created = repository.create("  Drive mix  ")

        assertEquals("Drive mix", created.name)
        assertEquals(created, dao.playlists.single())
        val failure = runCatching { repository.create("   ") }
        assertTrue(failure.isFailure)
    }

    @Test
    fun addMoveAndRemoveKeepDenseOrder() = runTest {
        val playlist = repository.create("Order")

        assertTrue(repository.addMedia(playlist.id, "a"))
        assertTrue(repository.addMedia(playlist.id, "b"))
        assertTrue(repository.addMedia(playlist.id, "c"))
        assertFalse(repository.addMedia(playlist.id, "b"))
        assertEquals(listOf("a", "b", "c"), dao.orderedMediaIds(playlist.id))

        assertTrue(repository.moveMedia(playlist.id, "c", -1))
        assertEquals(listOf("a", "c", "b"), dao.orderedMediaIds(playlist.id))
        assertFalse(repository.moveMedia(playlist.id, "a", -1))

        assertTrue(repository.removeMedia(playlist.id, "c"))
        assertEquals(listOf("a", "b"), dao.orderedMediaIds(playlist.id))
        assertEquals(listOf(0, 1), dao.orderedEntries(playlist.id).map { it.position })
    }
}

private class FakePlaylistDao : PlaylistDao {
    val playlists = mutableListOf<PlaylistEntity>()
    private val entries = mutableListOf<PlaylistEntryEntity>()
    private val summaries = MutableStateFlow<List<PlaylistSummary>>(emptyList())

    override fun observePlaylists(): Flow<List<PlaylistSummary>> = summaries

    override fun observeItems(playlistId: String): Flow<List<PlaylistMediaItem>> = flowOf(emptyList())

    override suspend fun insertPlaylist(playlist: PlaylistEntity) {
        playlists += playlist
    }

    override suspend fun renamePlaylist(playlistId: String, name: String, updatedAt: Long) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) playlists[index] = playlists[index].copy(name = name, updatedAt = updatedAt)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        playlists.removeAll { it.id == playlistId }
        entries.removeAll { it.playlistId == playlistId }
    }

    override suspend fun maxPosition(playlistId: String): Int =
        entries.filter { it.playlistId == playlistId }.maxOfOrNull { it.position } ?: -1

    override suspend fun entryCount(playlistId: String, mediaId: String): Int =
        entries.count { it.playlistId == playlistId && it.mediaId == mediaId }

    override suspend fun insertEntry(entry: PlaylistEntryEntity): Long {
        if (entryCount(entry.playlistId, entry.mediaId) > 0) return -1L
        entries += entry
        return entries.size.toLong()
    }

    override suspend fun getEntry(playlistId: String, mediaId: String): PlaylistEntryEntity? =
        entries.firstOrNull { it.playlistId == playlistId && it.mediaId == mediaId }

    override suspend fun getEntryAt(playlistId: String, position: Int): PlaylistEntryEntity? =
        entries.firstOrNull { it.playlistId == playlistId && it.position == position }

    override suspend fun updateEntry(entry: PlaylistEntryEntity) {
        val index = entries.indexOfFirst {
            it.playlistId == entry.playlistId && it.mediaId == entry.mediaId
        }
        if (index >= 0) entries[index] = entry
    }

    override suspend fun deleteEntry(playlistId: String, mediaId: String) {
        entries.removeAll { it.playlistId == playlistId && it.mediaId == mediaId }
    }

    override suspend fun compactAfter(playlistId: String, position: Int) {
        entries.indices.forEach { index ->
            val entry = entries[index]
            if (entry.playlistId == playlistId && entry.position > position) {
                entries[index] = entry.copy(position = entry.position - 1)
            }
        }
    }

    override suspend fun touchPlaylist(playlistId: String, updatedAt: Long) {
        val index = playlists.indexOfFirst { it.id == playlistId }
        if (index >= 0) playlists[index] = playlists[index].copy(updatedAt = updatedAt)
    }

    fun orderedEntries(playlistId: String): List<PlaylistEntryEntity> =
        entries.filter { it.playlistId == playlistId }.sortedBy { it.position }

    fun orderedMediaIds(playlistId: String): List<String> =
        orderedEntries(playlistId).map { it.mediaId }
}
