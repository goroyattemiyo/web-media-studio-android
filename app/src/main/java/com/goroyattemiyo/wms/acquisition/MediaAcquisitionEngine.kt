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
)

data class AcquisitionProgress(
    val percent: Float,
    val message: String,
)

data class AcquisitionResult(
    val file: File,
    val title: String,
    val provider: String,
)

interface MediaAcquisitionEngine {
    suspend fun initialize(): EngineState

    suspend fun probe(sourceUrl: String): Result<ProbeResult>

    suspend fun acquireMp3(
        sourceUrl: String,
        onProgress: (AcquisitionProgress) -> Unit,
    ): Result<AcquisitionResult>

    fun cancel()
}
