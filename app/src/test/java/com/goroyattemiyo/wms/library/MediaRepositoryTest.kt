package com.goroyattemiyo.wms.library

import com.goroyattemiyo.wms.acquisition.AcquisitionResult
import com.goroyattemiyo.wms.acquisition.AcquisitionPreset
import java.io.File
import java.nio.file.Files
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaRepositoryTest {
    private val root = Files.createTempDirectory("wms-media-repository").toFile()
    private val dao = FakeMediaDao()
    private val metadataReader = LocalMediaMetadataReader {
        LocalMediaMetadata(title = "Embedded title", durationMs = 42_000L, mimeType = "audio/mpeg")
    }
    private val repository = MediaRepository(dao, root, metadataReader)

    @After
    fun tearDown() {
        root.deleteRecursively()
    }

    @Test
    fun registerAcquisitionPersistsRequiredMetadata() = runTest {
        val file = File(root, "wms-123.mp3").apply { writeBytes(byteArrayOf(1, 2, 3)) }

        val entity = repository.registerAcquisition(
            AcquisitionResult(
                file = file,
                title = "Detected title",
                provider = "YouTube",
                author = "Channel name",
                artworkUrl = "https://example.com/thumb.jpg",
            ),
            "https://example.com/watch/123",
        )

        assertEquals("wms-123", entity.id)
        assertEquals("Detected title", entity.title)
        assertEquals("YouTube", entity.provider)
        assertEquals("Channel name", entity.author)
        assertEquals("https://example.com/thumb.jpg", entity.artworkUrl)
        assertEquals("https://example.com/watch/123", entity.originalUrl)
        assertEquals(42_000L, entity.durationMs)
        assertEquals(3L, entity.fileSize)
        assertEquals(entity, dao.rows.value.single())
    }

    @Test
    fun importExistingFilesBackfillsOnlyUnregisteredNonEmptyMp3() = runTest {
        File(root, "existing.mp3").writeBytes(byteArrayOf(1))
        File(root, "empty.mp3").createNewFile()
        File(root, "ignored.txt").writeText("not media")

        repository.importExistingFiles()
        repository.importExistingFiles()

        val imported = dao.rows.value.single()
        assertEquals("existing", imported.id)
        assertEquals("Embedded title", imported.title)
        assertEquals("Local", imported.provider)
    }

    @Test
    fun registerVideoUsesPresetMediaTypeAndMimeFallback() = runTest {
        val file = File(root, "wms-video.mp4").apply { writeBytes(byteArrayOf(1, 2)) }
        val fallbackRepository = MediaRepository(
            dao,
            root,
            LocalMediaMetadataReader { error("unreadable metadata") },
        )

        val entity = fallbackRepository.registerAcquisition(
            AcquisitionResult(file, "Video", "Web", AcquisitionPreset.VIDEO_MP4),
            "https://example.com/video",
        )

        assertEquals("VIDEO", entity.mediaType)
        assertEquals("video/mp4", entity.mimeType)
    }

    @Test
    fun importExistingFilesBackfillsM4aAndMp4() = runTest {
        File(root, "audio.m4a").writeBytes(byteArrayOf(1))
        File(root, "video.mp4").writeBytes(byteArrayOf(2))

        repository.importExistingFiles()

        assertEquals(setOf("audio", "video"), dao.rows.value.map { it.id }.toSet())
        assertEquals("VIDEO", dao.rows.value.single { it.id == "video" }.mediaType)
    }

    @Test
    fun deleteRemovesFileAndDatabaseRow() = runTest {
        val file = File(root, "delete-me.mp3").apply { writeBytes(byteArrayOf(1)) }
        val entity = repository.registerAcquisition(
            AcquisitionResult(file, "Delete me", "Local"),
            "",
        )

        val result = repository.delete(entity)

        assertTrue(result.isSuccess)
        assertFalse(file.exists())
        assertTrue(dao.rows.value.isEmpty())
    }

    @Test
    fun updateLastPositionClampsNegativeValues() = runTest {
        val file = File(root, "position.mp3").apply { writeBytes(byteArrayOf(1)) }
        val entity = repository.registerAcquisition(
            AcquisitionResult(file, "Position", "Local"),
            "",
        )

        repository.updateLastPosition(entity.id, -100L)

        assertEquals(0L, dao.rows.value.single().lastPositionMs)
    }

    @Test
    fun deleteRejectsFilesOutsideManagedDirectory() = runTest {
        val outsideFile = Files.createTempFile("outside-wms", ".mp3").toFile()
        val entity = MediaEntity(
            id = "outside",
            title = "Outside",
            provider = "Local",
            author = null,
            originalUrl = "",
            localPath = outsideFile.absolutePath,
            mimeType = "audio/mpeg",
            mediaType = "AUDIO",
            durationMs = 0L,
            fileSize = outsideFile.length(),
            artworkUrl = null,
            createdAt = 1L,
            lastPositionMs = 0L,
        )
        dao.upsert(entity)

        val result = repository.delete(entity)

        assertTrue(result.isFailure)
        assertTrue(outsideFile.exists())
        assertEquals(entity, dao.rows.value.single())
        outsideFile.delete()
    }
}

private class FakeMediaDao : MediaDao {
    val rows = MutableStateFlow<List<MediaEntity>>(emptyList())

    override fun observeAll(): Flow<List<MediaEntity>> = rows

    override suspend fun getAll(): List<MediaEntity> = rows.value

    override suspend fun getById(id: String): MediaEntity? = rows.value.firstOrNull { it.id == id }

    override suspend fun upsert(media: MediaEntity) {
        rows.value = rows.value.filterNot { it.id == media.id } + media
    }

    override suspend fun deleteById(id: String) {
        rows.value = rows.value.filterNot { it.id == id }
    }

    override suspend fun deleteByLocalPath(localPath: String) {
        rows.value = rows.value.filterNot { it.localPath == localPath }
    }

    override suspend fun updateLastPosition(id: String, positionMs: Long) {
        rows.value = rows.value.map { if (it.id == id) it.copy(lastPositionMs = positionMs) else it }
    }
}
