package com.goroyattemiyo.wms.acquisition

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class AcquisitionJobStatus {
    IDLE,
    RUNNING,
    SUCCEEDED,
    FAILED,
    CANCELED,
}

data class ManagedAcquisitionState(
    val status: AcquisitionJobStatus = AcquisitionJobStatus.IDLE,
    val sourceUrl: String = "",
    val progressPercent: Float = 0f,
    val progressMessage: String = "",
    val savedPath: String? = null,
    val savedTitle: String? = null,
    val savedProvider: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

object ManagedAcquisitionBus {
    private val mutableState = MutableStateFlow(ManagedAcquisitionState())
    val state = mutableState.asStateFlow()

    fun started(sourceUrl: String) {
        mutableState.value = ManagedAcquisitionState(
            status = AcquisitionJobStatus.RUNNING,
            sourceUrl = sourceUrl,
            progressMessage = "取得を開始しています",
        )
    }

    fun progress(percent: Float, message: String) {
        mutableState.update { current ->
            if (current.status != AcquisitionJobStatus.RUNNING) current else current.copy(
                progressPercent = percent.coerceIn(0f, 100f),
                progressMessage = message,
            )
        }
    }

    fun succeeded(result: AcquisitionResult) {
        mutableState.update { current ->
            current.copy(
                status = AcquisitionJobStatus.SUCCEEDED,
                progressPercent = 100f,
                progressMessage = "保存完了",
                savedPath = result.file.absolutePath,
                savedTitle = result.title,
                savedProvider = result.provider,
                errorCode = null,
                errorMessage = null,
            )
        }
    }

    fun failed(sourceUrl: String, code: String, message: String) {
        mutableState.value = ManagedAcquisitionState(
            status = AcquisitionJobStatus.FAILED,
            sourceUrl = sourceUrl,
            errorCode = code,
            errorMessage = message,
        )
    }

    fun canceled(sourceUrl: String) {
        mutableState.value = ManagedAcquisitionState(
            status = AcquisitionJobStatus.CANCELED,
            sourceUrl = sourceUrl,
            progressMessage = "キャンセルしました",
        )
    }
}
