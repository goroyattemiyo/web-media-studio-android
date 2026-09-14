package com.goroyattemiyo.wms.ui.media

import com.goroyattemiyo.wms.library.MediaEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MediaPresentationTest {
    @Test
    fun sourceThumbnailPrecedesEmbeddedAudioAndFallback() {
        val presentation = media(
            author = "Artist",
            artworkUrl = "https://example.com/cover.jpg",
        ).toMediaPresentation()

        assertEquals("Artist", presentation.secondaryLabel)
        assertFalse(presentation.isVideo)
        assertEquals(
            listOf(
                MediaArtworkCandidate.Persisted("https://example.com/cover.jpg"),
                MediaArtworkCandidate.EmbeddedAudio("C:/media/song.mp3"),
                MediaArtworkCandidate.WmsFallback,
            ),
            presentation.artworkCandidates,
        )
    }

    @Test
    fun videoUsesFrameBeforeFallbackWhenPersistedArtworkIsUnsafe() {
        val presentation = media(
            mediaType = "VIDEO",
            localPath = "C:/media/video.mp4",
            artworkUrl = "http://insecure.example.com/cover.jpg",
            author = null,
        ).toMediaPresentation()

        assertEquals("Provider", presentation.secondaryLabel)
        assertTrue(presentation.isVideo)
        assertEquals(
            listOf(
                MediaArtworkCandidate.VideoFrame("C:/media/video.mp4"),
                MediaArtworkCandidate.WmsFallback,
            ),
            presentation.artworkCandidates,
        )
    }

    private fun media(
        mediaType: String = "AUDIO",
        localPath: String = "C:/media/song.mp3",
        artworkUrl: String? = null,
        author: String? = null,
    ) = MediaEntity(
        id = "media-1",
        title = "Title",
        provider = "Provider",
        author = author,
        originalUrl = "https://example.com/source",
        localPath = localPath,
        mimeType = if (mediaType == "VIDEO") "video/mp4" else "audio/mpeg",
        mediaType = mediaType,
        durationMs = 42_000L,
        fileSize = 100L,
        artworkUrl = artworkUrl,
        createdAt = 1L,
        lastPositionMs = 0L,
    )
}
