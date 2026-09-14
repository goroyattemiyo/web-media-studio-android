package com.goroyattemiyo.wms.ui.media

import com.goroyattemiyo.wms.library.MediaEntity
import java.net.URI

sealed interface MediaArtworkCandidate {
    data class Persisted(val uri: String) : MediaArtworkCandidate
    data class EmbeddedAudio(val localPath: String) : MediaArtworkCandidate
    data class VideoFrame(val localPath: String) : MediaArtworkCandidate
    data object WmsFallback : MediaArtworkCandidate
}

data class MediaPresentation(
    val id: String,
    val title: String,
    val secondaryLabel: String,
    val provider: String,
    val isVideo: Boolean,
    val durationMs: Long,
    val artworkCandidates: List<MediaArtworkCandidate>,
)

data class CurrentMediaPresentation(
    val media: MediaPresentation,
    val positionMs: Long,
    val durationMs: Long,
    val isPlaying: Boolean,
    val hasPrevious: Boolean,
    val hasNext: Boolean,
)

fun MediaEntity.toMediaPresentation(): MediaPresentation {
    val localCandidate = if (mediaType.equals("VIDEO", ignoreCase = true)) {
        MediaArtworkCandidate.VideoFrame(localPath)
    } else {
        MediaArtworkCandidate.EmbeddedAudio(localPath)
    }
    val candidates = buildList {
        artworkUrl?.takeIf(::isSupportedArtworkUri)?.let {
            add(MediaArtworkCandidate.Persisted(it))
        }
        add(localCandidate)
        add(MediaArtworkCandidate.WmsFallback)
    }
    return MediaPresentation(
        id = id,
        title = title,
        secondaryLabel = author?.takeIf(String::isNotBlank) ?: provider,
        provider = provider,
        isVideo = mediaType.equals("VIDEO", ignoreCase = true),
        durationMs = durationMs.coerceAtLeast(0L),
        artworkCandidates = candidates,
    )
}

private fun isSupportedArtworkUri(value: String): Boolean = runCatching {
    val uri = URI(value.trim())
    when (uri.scheme?.lowercase()) {
        "https" -> !uri.host.isNullOrBlank()
        "content", "file", "android.resource" -> true
        else -> false
    }
}.getOrDefault(false)
