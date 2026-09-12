package com.goroyattemiyo.wms

import android.app.Application
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.goroyattemiyo.wms.acquisition.AcquisitionEngineException
import com.goroyattemiyo.wms.acquisition.AcquisitionJobStatus
import com.goroyattemiyo.wms.acquisition.ManagedAcquisitionBus
import com.goroyattemiyo.wms.acquisition.ManagedAcquisitionService
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
    val ytdlpVersion: String? = null,
    val updatingYtdlp: Boolean = false,
    val diagnosing: Boolean = false,
    val diagnosticSucceeded: Boolean? = null,
    val diagnosticLines: List<String> = emptyList(),
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
    private val app = application
    private val engine = YoutubeDlAcquisitionEngine(application)
    private val preferences = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val _uiState = MutableStateFlow(GateA0UiState())
    val uiState = _uiState.asStateFlow()

    private var probeJob: Job? = null

    init {
        viewModelScope.launch {
            ManagedAcquisitionBus.state.collect { jobState ->
                _uiState.update { current ->
                    when (jobState.status) {
                        AcquisitionJobStatus.IDLE -> current
                        AcquisitionJobStatus.RUNNING -> current.copy(
                            acquiring = true,
                            progressPercent = jobState.progressPercent,
                            progressMessage = jobState.progressMessage,
                            savedPath = null,
                            savedTitle = null,
                            errorCode = null,
                            errorMessage = null,
                        )
                        AcquisitionJobStatus.SUCCEEDED -> {
                            val matchesCurrent = current.url.trim() == jobState.sourceUrl
                            current.copy(
                                acquiring = false,
                                progressPercent = 100f,
                                progressMessage = "保存完了",
                                savedPath = jobState.savedPath,
                                savedTitle = jobState.savedTitle,
                                detectedTitle = if (matchesCurrent) {
                                    current.detectedTitle ?: jobState.savedTitle
                                } else {
                                    current.detectedTitle
                                },
                                detectedProvider = if (matchesCurrent) {
                                    current.detectedProvider ?: jobState.savedProvider
                                } else {
                                    current.detectedProvider
                                },
                                rightsConfirmed = if (matchesCurrent) false else current.rightsConfirmed,
                                errorCode = null,
                                errorMessage = null,
                            )
                        }
                        AcquisitionJobStatus.FAILED -> current.copy(
                            acquiring = false,
                            progressPercent = 0f,
                            progressMessage = "",
                            errorCode = jobState.errorCode,
                            errorMessage = jobState.errorMessage,
                        )
                        AcquisitionJobStatus.CANCELED -> current.copy(
                            acquiring = false,
                            progressPercent = 0f,
                            progressMessage = "キャンセルしました",
                        )
                    }
                }
            }
        }

        viewModelScope.launch {
            val result = engine.initialize()
            val version = if (result.ready) readYoutubeDlVersion() else null

            _uiState.update {
                it.copy(
                    engineReady = result.ready,
                    engineCode = result.code,
                    engineMessage = result.message,
                    ytdlpVersion = version,
                    errorCode = if (result.ready) null else result.code,
                    errorMessage = if (result.ready) null else result.message,
                )
            }

            if (result.ready && shouldAutoUpdateYoutubeDl()) {
                refreshYoutubeDlStable(automatic = true)
            }

            if (result.ready && _uiState.value.url.isNotBlank()) {
                probe()
            }
        }
    }

    fun updateYoutubeDl() {
        val snapshot = _uiState.value
        if (
            !snapshot.engineReady ||
            snapshot.updatingYtdlp ||
            snapshot.diagnosing ||
            snapshot.probing ||
            snapshot.acquiring
        ) {
            return
        }

        viewModelScope.launch {
            refreshYoutubeDlStable(automatic = false)
        }
    }

    private suspend fun refreshYoutubeDlStable(automatic: Boolean) {
        _uiState.update {
            it.copy(
                updatingYtdlp = true,
                engineMessage = if (automatic) {
                    "yt-dlp stableを自動確認しています"
                } else {
                    "yt-dlp stableを確認しています"
                },
                errorCode = null,
                errorMessage = null,
            )
        }

        if (automatic) {
            preferences.edit()
                .putLong(KEY_LAST_AUTO_UPDATE_CHECK_MS, System.currentTimeMillis())
                .apply()
        }

        val result = withContext(Dispatchers.IO) {
            runCatching {
                val youtubeDl = YoutubeDL.getInstance()
                val status = youtubeDl.updateYoutubeDL(app, YoutubeDL.UpdateChannel.STABLE)
                val version = youtubeDl.version(app)
                    ?: youtubeDl.versionName(app)
                    ?: "不明"
                status to version
            }
        }

        result.onSuccess { (status, version) ->
            val message = when (status) {
                YoutubeDL.UpdateStatus.DONE -> if (automatic) {
                    "yt-dlp stableを自動更新しました"
                } else {
                    "yt-dlp stableへ更新しました"
                }
                YoutubeDL.UpdateStatus.ALREADY_UP_TO_DATE -> "yt-dlpはstable最新版です"
                null -> "yt-dlp stable確認が完了しました"
            }
            _uiState.update {
                it.copy(
                    updatingYtdlp = false,
                    engineMessage = message,
                    ytdlpVersion = version,
                )
            }
        }.onFailure { error ->
            val version = readYoutubeDlVersion()
            _uiState.update {
                it.copy(
                    updatingYtdlp = false,
                    engineMessage = if (automatic) {
                        "yt-dlp自動更新に失敗。現在版で継続します"
                    } else {
                        "yt-dlp更新に失敗。現在版で継続します"
                    },
                    ytdlpVersion = version,
                    errorCode = if (automatic) null else "YTDLP_UPDATE_FAILED",
                    errorMessage = if (automatic) null else {
                        "yt-dlp stable更新に失敗しました。現在版は保持されています。診断: ${safeUpdateDiagnostic(error)}"
                    },
                )
            }
        }
    }

    private fun shouldAutoUpdateYoutubeDl(): Boolean {
        val lastCheck = preferences.getLong(KEY_LAST_AUTO_UPDATE_CHECK_MS, 0L)
        return System.currentTimeMillis() - lastCheck >= AUTO_UPDATE_INTERVAL_MS
    }

    fun runDiagnostics() {
        val snapshot = _uiState.value
        val sourceUrl = snapshot.url.trim()
        if (
            sourceUrl.isBlank() ||
            !snapshot.engineReady ||
            snapshot.updatingYtdlp ||
            snapshot.diagnosing ||
            snapshot.probing ||
            snapshot.acquiring
        ) {
            return
        }

        viewModelScope.launch {
            _uiState.update {
                it.copy(
                    diagnosing = true,
                    diagnosticSucceeded = null,
                    diagnosticLines = emptyList(),
                    errorCode = null,
                    errorMessage = null,
                )
            }

            engine.diagnose(sourceUrl)
                .onSuccess { diagnostic ->
                    _uiState.update {
                        it.copy(
                            diagnosing = false,
                            diagnosticSucceeded = diagnostic.executionSucceeded,
                            diagnosticLines = diagnostic.lines,
                        )
                    }
                }
                .onFailure(::showFailure)
        }
    }

    private suspend fun readYoutubeDlVersion(): String = withContext(Dispatchers.IO) {
        val youtubeDl = YoutubeDL.getInstance()
        youtubeDl.version(app)
            ?: youtubeDl.versionName(app)
            ?: "不明"
    }

    private fun safeUpdateDiagnostic(error: Throwable): String {
        val raw = buildString {
            append(error.message.orEmpty())
            append('\n')
            append(error.cause?.message.orEmpty())
        }
        val compact = raw
            .lineSequence()
            .map { it.trim() }
            .firstOrNull { it.isNotBlank() }
            .orEmpty()

        return compact
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[URL]")
            .replace(Regex("/data/\\S+", RegexOption.IGNORE_CASE), "[APP_PATH]")
            .replace(Regex("/storage/\\S+", RegexOption.IGNORE_CASE), "[STORAGE_PATH]")
            .replace(Regex("\\s+"), " ")
            .take(220)
            .ifBlank { "詳細なし" }
    }

    fun onUrlChanged(value: String) {
        probeJob?.cancel()
        probeJob = null

        _uiState.update {
            it.copy(
                url = value,
                probing = false,
                detectedTitle = null,
                detectedProvider = null,
                rightsConfirmed = false,
                diagnosticSucceeded = null,
                diagnosticLines = emptyList(),
                progressPercent = 0f,
                progressMessage = "",
                savedPath = null,
                savedTitle = null,
                errorCode = null,
                errorMessage = null,
            )
        }

        probe()
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
        val snapshot = _uiState.value
        val sourceUrl = snapshot.url.trim()
        if (
            sourceUrl.isBlank() ||
            !snapshot.engineReady ||
            snapshot.updatingYtdlp ||
            snapshot.diagnosing ||
            snapshot.probing ||
            snapshot.acquiring
        ) {
            return
        }

        _uiState.update {
            it.copy(
                probing = true,
                errorCode = null,
                errorMessage = null,
                detectedTitle = null,
                detectedProvider = null,
            )
        }

        probeJob = viewModelScope.launch {
            engine.probe(sourceUrl)
                .onSuccess { probe ->
                    if (_uiState.value.url.trim() == sourceUrl) {
                        _uiState.update {
                            it.copy(
                                probing = false,
                                detectedTitle = probe.title,
                                detectedProvider = probe.provider,
                            )
                        }
                    }
                }
                .onFailure { error ->
                    if (_uiState.value.url.trim() == sourceUrl) {
                        showFailure(error)
                    }
                }
            probeJob = null
        }
    }

    fun acquireMp3() {
        val snapshot = _uiState.value
        if (
            snapshot.url.isBlank() ||
            !snapshot.engineReady ||
            !snapshot.rightsConfirmed ||
            snapshot.updatingYtdlp ||
            snapshot.diagnosing ||
            snapshot.acquiring ||
            snapshot.probing
        ) {
            return
        }

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

        val intent = Intent(app, ManagedAcquisitionService::class.java).apply {
            action = ManagedAcquisitionService.ACTION_START
            putExtra(ManagedAcquisitionService.EXTRA_SOURCE_URL, snapshot.url.trim())
        }

        runCatching {
            app.startForegroundService(intent)
        }.onFailure { error ->
            showFailure(
                AcquisitionEngineException(
                    "SERVICE_START_FAILED",
                    "バックグラウンド保存処理を開始できませんでした。",
                    error,
                ),
            )
        }
    }

    fun cancelAcquisition() {
        val intent = Intent(app, ManagedAcquisitionService::class.java).apply {
            action = ManagedAcquisitionService.ACTION_CANCEL
        }
        runCatching { app.startService(intent) }
            .onFailure { error ->
                showFailure(
                    AcquisitionEngineException(
                        "SERVICE_CANCEL_FAILED",
                        "保存処理のキャンセル要求を送れませんでした。",
                        error,
                    ),
                )
            }
    }

    private fun showFailure(error: Throwable) {
        val engineError = error as? AcquisitionEngineException
        val code = engineError?.code ?: "UNEXPECTED"
        val message = engineError?.message ?: "処理に失敗しました。コード: $code"

        _uiState.update {
            it.copy(
                diagnosing = false,
                probing = false,
                acquiring = false,
                errorCode = code,
                errorMessage = message,
                progressMessage = "",
            )
        }
    }

    override fun onCleared() {
        probeJob?.cancel()
        super.onCleared()
    }

    private companion object {
        const val PREFS_NAME = "wms_runtime_update"
        const val KEY_LAST_AUTO_UPDATE_CHECK_MS = "last_ytdlp_auto_check_ms"
        const val AUTO_UPDATE_INTERVAL_MS = 24L * 60L * 60L * 1000L
        val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
    }
}
