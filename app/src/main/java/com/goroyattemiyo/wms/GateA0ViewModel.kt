package com.goroyattemiyo.wms

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goroyattemiyo.wms.acquisition.AcquisitionEngineException
import com.goroyattemiyo.wms.acquisition.YoutubeDlAcquisitionEngine
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class GateA0UiState(
    val url: String = "",
    val engineReady: Boolean = false,
    val engineCode: String = "STARTING",
    val engineMessage: String = "取得エンジンを準備しています",
    val probing: Boolean = false,
    val detectedTitle: String? = null,
    val detectedProvider: String? = null,
    val rightsConfirmed: Boolean = false,
    val acquiring: Boolean = false,
    val progressPercent: Float = 0f,
    val progressMessage: String = "",
    val savedPath: String? = null,
    val savedTitle: String? = null,
    val errorCode: String? = null,
    val errorMessage: String? = null,
)

class GateA0ViewModel(application: Application) : AndroidViewModel(application) {
    private val engine = YoutubeDlAcquisitionEngine(application)
    private val _uiState = MutableStateFlow(GateA0UiState())
    val uiState = _uiState.asStateFlow()

    private var acquireJob: Job? = null

    init {
        viewModelScope.launch {
            val result = engine.initialize()
            val engineMessage = if (result.ready) {
                updateYoutubeDlAndDescribe(application)
            } else {
                result.message
            }

            _uiState.update {
                it.copy(
                    engineReady = result.ready,
                    engineCode = result.code,
                    engineMessage = engineMessage,
                    errorCode = if (result.ready) null else result.code,
                    errorMessage = if (result.ready) null else result.message,
                )
            }
        }
    }

    private suspend fun updateYoutubeDlAndDescribe(application: Application): String =
        withContext(Dispatchers.IO) {
            val youtubeDl = YoutubeDL.getInstance()
            val updateSucceeded = runCatching {
                youtubeDl.updateYoutubeDL(application, YoutubeDL.UpdateChannel.STABLE)
            }.isSuccess

            val version = youtubeDl.version(application)
                ?: youtubeDl.versionName(application)
                ?: "組み込み版"

            if (updateSucceeded) {
                "取得エンジン準備完了 / yt-dlp $version"
            } else {
                "取得エンジン準備完了 / yt-dlp更新確認に失敗。$version で継続"
            }
        }

    fun onUrlChanged(value: String) {
        _uiState.update {
            it.copy(
                url = value,
                detectedTitle = null,
                detectedProvider = null,
                savedPath = null,
                savedTitle = null,
                errorCode = null,
                errorMessage = null,
            )
        }
    }

    fun consumeSharedText(sharedText: String?) {
        val url = sharedText
            ?.let(URL_PATTERN::find)
            ?.value
            ?.trimEnd('.', ',', ';', ')', ']', '}', '>', '"', '\'')
            .orEmpty()

        if (url.isNotBlank()) {
            onUrlChanged(url)
        }
    }

    fun setRightsConfirmed(confirmed: Boolean) {
        _uiState.update { it.copy(rightsConfirmed = confirmed) }
    }

    fun probe() {
        val sourceUrl = _uiState.value.url.trim()
        if (sourceUrl.isBlank() || _uiState.value.probing || _uiState.value.acquiring) return

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    probing = true,
                    errorCode = null,
                    errorMessage = null,
                    detectedTitle = null,
                    detectedProvider = null,
                )
            }

            engine.probe(sourceUrl)
                .onSuccess { probe ->
                    _uiState.update {
                        it.copy(
                            probing = false,
                            detectedTitle = probe.title,
                            detectedProvider = probe.provider,
                        )
                    }
                }
                .onFailure(::showFailure)
        }
    }

    fun acquireMp3() {
        val snapshot = _uiState.value
        if (
            snapshot.url.isBlank() ||
            !snapshot.engineReady ||
            !snapshot.rightsConfirmed ||
            snapshot.acquiring ||
            snapshot.probing
        ) {
            return
        }

        acquireJob = viewModelScope.launch {
            _uiState.update {
                it.copy(
                    acquiring = true,
                    progressPercent = 0f,
                    progressMessage = "取得を開始しています",
                    savedPath = null,
                    savedTitle = null,
                    errorCode = null,
                    errorMessage = null,
                )
            }

            engine.acquireMp3(snapshot.url) { progress ->
                _uiState.update {
                    it.copy(
                        progressPercent = progress.percent,
                        progressMessage = progress.message,
                    )
                }
            }
                .onSuccess { result ->
                    _uiState.update {
                        it.copy(
                            acquiring = false,
                            progressPercent = 100f,
                            progressMessage = "保存完了",
                            savedPath = result.file.absolutePath,
                            savedTitle = result.title,
                            detectedTitle = it.detectedTitle ?: result.title,
                            detectedProvider = it.detectedProvider ?: result.provider,
                            rightsConfirmed = false,
                        )
                    }
                }
                .onFailure(::showFailure)
        }
    }

    fun cancelAcquisition() {
        engine.cancel()
        acquireJob?.cancel()
        acquireJob = null
        _uiState.update {
            it.copy(
                acquiring = false,
                progressMessage = "キャンセルしました",
            )
        }
    }

    private fun showFailure(error: Throwable) {
        val engineError = error as? AcquisitionEngineException
        val code = engineError?.code ?: "UNEXPECTED"
        val message = engineError?.message ?: "処理に失敗しました。コード: $code"

        _uiState.update {
            it.copy(
                probing = false,
                acquiring = false,
                errorCode = code,
                errorMessage = message,
                progressMessage = "",
            )
        }
    }

    override fun onCleared() {
        engine.cancel()
        super.onCleared()
    }

    private companion object {
        val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
