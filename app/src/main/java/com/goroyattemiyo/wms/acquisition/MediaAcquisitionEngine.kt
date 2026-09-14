package com.goroyattemiyo.wms.acquisition

import java.io.File

data class EngineState(
    val ready: Boolean,
    val code: String,
    val message: String,
)

data class ProbeResult(
    val title: String,
    val provider: String,
    val author: String? = null,
    val artworkUrl: String? = null,
)

data class AcquisitionProgress(
    val percent: Float,
    val message: String,
)

data class AcquisitionResult(
    val file: File,
    val title: String,
    val provider: String,
    val preset: AcquisitionPreset = AcquisitionPreset.MP3_192,
    val author: String? = null,
    val artworkUrl: String? = null,
)

enum class AcquisitionPreset(
    val id: String,
    val displayName: String,
    val mediaType: String,
    val extension: String,
    val mimeType: String,
) {
    MP3_192("mp3-192", "MP3 · 192 kbps", "AUDIO", "mp3", "audio/mpeg"),
    M4A_192("m4a-192", "M4A · 192 kbps", "AUDIO", "m4a", "audio/mp4"),
    VIDEO_MP4("video-mp4", "Video · MP4", "VIDEO", "mp4", "video/mp4");

    companion object {
        fun fromId(id: String?): AcquisitionPreset = entries.firstOrNull { it.id == id } ?: MP3_192
    }
}

interface MediaAcquisitionEngine {
    suspend fun initialize(): EngineState

    suspend fun probe(sourceUrl: String): Result<ProbeResult>

    suspend fun acquire(
        sourceUrl: String,
        preset: AcquisitionPreset,
        onProgress: (AcquisitionProgress) -> Unit,
    ): Result<AcquisitionResult>

    fun cancel()
}
