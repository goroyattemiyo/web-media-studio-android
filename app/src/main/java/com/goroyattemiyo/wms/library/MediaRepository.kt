package com.goroyattemiyo.wms.library

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import com.goroyattemiyo.wms.acquisition.AcquisitionResult
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal data class LocalMediaMetadata(
    val title: String?,
    val durationMs: Long,
    val mimeType: String,
)

internal fun interface LocalMediaMetadataReader {
    fun read(file: File): LocalMediaMetadata
}

internal class AndroidLocalMediaMetadataReader : LocalMediaMetadataReader {
    override fun read(file: File): LocalMediaMetadata {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(file.absolutePath)
            LocalMediaMetadata(
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE),
                durationMs = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
                    ?.toLongOrNull()
                    ?.coerceAtLeast(0L)
                    ?: 0L,
                mimeType = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_MIMETYPE)
                    ?.takeIf(String::isNotBlank)
                    ?: "audio/mpeg",
            )
        } finally {
            retriever.release()
        }
    }
}

class MediaRepository internal constructor(
    private val mediaDao: MediaDao,
    private val acquiredDirectory: File,
    private val metadataReader: LocalMediaMetadataReader,
) {
    val allMedia: Flow<List<MediaEntity>> = mediaDao.observeAll()

    suspend fun registerAcquisition(
        acquisition: AcquisitionResult,
        originalUrl: String,
    ): MediaEntity = withContext(Dispatchers.IO) {
        val file = acquisition.file
        requireManagedFile(file)
        require(file.exists() && file.isFile && file.length() > 0L) {
            "Completed media file is missing or empty"
        }
        val metadata = runCatching { metadataReader.read(file) }
            .getOrElse { LocalMediaMetadata(null, 0L, acquisition.preset.mimeType) }
        val entity = MediaEntity(
            id = file.nameWithoutExtension,
            title = acquisition.title.ifBlank { metadata.title ?: file.nameWithoutExtension },
            provider = acquisition.provider.ifBlank { "Local" },
            author = acquisition.author,
            originalUrl = originalUrl,
            localPath = file.absolutePath,
            mimeType = metadata.mimeType,
            mediaType = acquisition.preset.mediaType,
            durationMs = metadata.durationMs,
            fileSize = file.length(),
            artworkUrl = acquisition.artworkUrl,
            createdAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
            lastPositionMs = 0L,
        )
        mediaDao.upsert(entity)
        entity
    }

    suspend fun importExistingFiles() = withContext(Dispatchers.IO) {
        acquiredDirectory.mkdirs()
        val knownPaths = mediaDao.getAll().mapTo(mutableSetOf()) { File(it.localPath).absolutePath }
        acquiredDirectory.listFiles()
            .orEmpty()
            .asSequence()
            .filter {
                it.isFile &&
                    it.extension.lowercase() in SUPPORTED_EXTENSIONS &&
                    it.length() > 0L
            }
            .filterNot { it.absolutePath in knownPaths }
            .forEach { file ->
                val metadata = runCatching { metadataReader.read(file) }
                    .getOrElse {
                        LocalMediaMetadata(null, 0L, mimeTypeForExtension(file.extension))
                    }
                mediaDao.upsert(
                    MediaEntity(
                        id = file.nameWithoutExtension,
                        title = metadata.title ?: file.nameWithoutExtension,
                        provider = "Local",
                        author = null,
                        originalUrl = "",
                        localPath = file.absolutePath,
                        mimeType = metadata.mimeType,
                        mediaType = if (file.extension.equals("mp4", ignoreCase = true)) "VIDEO" else "AUDIO",
                        durationMs = metadata.durationMs,
                        fileSize = file.length(),
                        artworkUrl = null,
                        createdAt = file.lastModified().takeIf { it > 0L } ?: System.currentTimeMillis(),
                        lastPositionMs = 0L,
                    ),
                )
            }
    }

    suspend fun importExternal(context: Context, uri: Uri): MediaEntity = withContext(Dispatchers.IO) {
        val extension = context.contentResolver.getType(uri)
            ?.substringAfterLast('/', "")
            ?.takeIf { it in SUPPORTED_EXTENSIONS }
            ?: "mp3"
        acquiredDirectory.mkdirs()
        val destination = File(acquiredDirectory, "wms-${UUID.randomUUID()}.$extension")
        context.contentResolver.openInputStream(uri)?.use { input ->
            destination.outputStream().use(input::copyTo)
        } ?: error("選択したファイルを開けません")
        require(destination.length() > 0L) { "選択したファイルが空です" }
        val metadata = runCatching { metadataReader.read(destination) }
            .getOrElse { LocalMediaMetadata(null, 0L, mimeTypeForExtension(extension)) }
        val entity = MediaEntity(
            id = destination.nameWithoutExtension,
            title = metadata.title ?: destination.nameWithoutExtension,
            provider = "端末から追加",
            author = null,
            originalUrl = "",
            localPath = destination.absolutePath,
            mimeType = metadata.mimeType,
            mediaType = if (metadata.mimeType.startsWith("video/")) "VIDEO" else "AUDIO",
            durationMs = metadata.durationMs,
            fileSize = destination.length(),
            artworkUrl = null,
            createdAt = System.currentTimeMillis(),
            lastPositionMs = 0L,
        )
        mediaDao.upsert(entity)
        entity
    }

    suspend fun delete(media: MediaEntity): Result<Unit> = withContext(Dispatchers.IO) {
        runCatching {
            val file = File(media.localPath)
            requireManagedFile(file)
            if (file.exists() && !file.delete()) {
                error("メディアファイルを削除できませんでした")
            }
            mediaDao.deleteById(media.id)
        }
    }

    suspend fun updateLastPosition(id: String, positionMs: Long) {
        mediaDao.updateLastPosition(id, positionMs.coerceAtLeast(0L))
    }

    suspend fun rollbackRegistration(file: File) = withContext(Dispatchers.IO) {
        requireManagedFile(file)
        mediaDao.deleteByLocalPath(file.absolutePath)
        file.delete()
    }

    private fun requireManagedFile(file: File) {
        val rootPath = acquiredDirectory.canonicalFile.toPath()
        require(file.canonicalFile.toPath().startsWith(rootPath)) {
            "WMS管理外のファイルは操作できません"
        }
    }

    companion object {
        private val SUPPORTED_EXTENSIONS = setOf("mp3", "m4a", "mp4", "wav", "ogg", "opus", "aac")

        private fun mimeTypeForExtension(extension: String): String = when (extension.lowercase()) {
            "m4a" -> "audio/mp4"
            "wav" -> "audio/wav"
            "ogg", "opus" -> "audio/ogg"
            "aac" -> "audio/aac"
            "mp4" -> "video/mp4"
            else -> "audio/mpeg"
        }

        fun create(context: Context, mediaDao: MediaDao): MediaRepository {
            val directory = File(
                context.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: context.filesDir,
                "wms/acquired",
            )
            return MediaRepository(mediaDao, directory, AndroidLocalMediaMetadataReader())
        }
    }
}
