package com.goroyattemiyo.wms.acquisition

import android.content.Context
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.URI
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AcquisitionEngineException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

data class EngineDiagnostics(
    val executionSucceeded: Boolean,
    val lines: List<String>,
)

class YoutubeDlAcquisitionEngine(context: Context) : MediaAcquisitionEngine {
    private val appContext = context.applicationContext
    private val initMutex = Mutex()
    private val cancelRequested = AtomicBoolean(false)

    @Volatile
    private var initialized = false

    override suspend fun initialize(): EngineState = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (initialized) {
                return@withLock EngineState(true, "READY", "取得エンジン準備完了")
            }

            try {
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                initialized = true
                EngineState(true, "READY", "取得エンジン準備完了")
            } catch (error: Throwable) {
                val classified = classifyError(error, "RUNTIME_INIT")
                EngineState(false, classified.code, classified.message)
            }
        }
    }

    override suspend fun probe(sourceUrl: String): Result<ProbeResult> = withContext(Dispatchers.IO) {
        runCatching {
            validateUrl(sourceUrl)
            ensureReady()
            val info = try {
                YoutubeDL.getInstance().getInfo(sourceUrl.trim())
            } catch (error: Throwable) {
                throw classifyError(error, "PROBE_FAILED")
            }
            ProbeResult(
                title = info.title?.trim().orEmpty().ifBlank { "タイトル不明" },
                provider = providerFor(sourceUrl),
                author = info.uploader?.trim()?.takeIf(String::isNotBlank),
                artworkUrl = info.thumbnail?.trim()?.takeIf(::isSafeArtworkUrl),
            )
        }
    }

    suspend fun diagnose(sourceUrl: String): Result<EngineDiagnostics> = withContext(Dispatchers.IO) {
        runCatching {
            validateUrl(sourceUrl)
            ensureReady()
            val request = YoutubeDLRequest(sourceUrl.trim()).apply {
                addOption("--verbose")
                addOption("--simulate")
                addOption("--no-playlist")
            }

            try {
                val response = YoutubeDL.getInstance().execute(request)
                EngineDiagnostics(
                    executionSucceeded = true,
                    lines = diagnosticLines("${response.err}\n${response.out}"),
                )
            } catch (error: Throwable) {
                val raw = buildString {
                    append(error.message.orEmpty())
                    append('\n')
                    append(error.cause?.message.orEmpty())
                }
                val lines = diagnosticLines(raw)
                if (lines.isEmpty()) throw classifyError(error, "DIAGNOSTIC_FAILED")
                EngineDiagnostics(false, lines)
            }
        }
    }

    fun prepareForAcquisition() {
        cancelRequested.set(false)
    }

    override suspend fun acquire(
        sourceUrl: String,
        preset: AcquisitionPreset,
        onProgress: (AcquisitionProgress) -> Unit,
    ): Result<AcquisitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            validateUrl(sourceUrl)
            ensureReady()
            ensureNotCanceled()

            val source = sourceUrl.trim()
            val info = try {
                YoutubeDL.getInstance().getInfo(source)
            } catch (error: Throwable) {
                throw classifyError(error, "ACQUIRE_FAILED")
            }
            ensureNotCanceled()

            val outputRoot = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir,
                "wms/acquired",
            ).apply { mkdirs() }
            val jobsRoot = File(appContext.cacheDir, "wms-acquisition-jobs").apply { mkdirs() }
            val jobDir = File(jobsRoot, "job-${UUID.randomUUID()}").apply { mkdirs() }

            try {
                val request = YoutubeDLRequest(source).apply {
                    addOption("--no-playlist")
                    when (preset) {
                        AcquisitionPreset.MP3_192 -> {
                            addOption("--format", "bestaudio/best")
                            addOption("--extract-audio")
                            addOption("--audio-format", "mp3")
                            addOption("--audio-quality", "192K")
                        }
                        AcquisitionPreset.M4A_192 -> {
                            addOption("--format", "bestaudio/best")
                            addOption("--extract-audio")
                            addOption("--audio-format", "m4a")
                            addOption("--audio-quality", "192K")
                        }
                        AcquisitionPreset.VIDEO_MP4 -> {
                            addOption("--format", "bestvideo[ext=mp4]+bestaudio[ext=m4a]/best[ext=mp4]")
                            addOption("--merge-output-format", "mp4")
                        }
                    }
                    addOption("--no-write-thumbnail")
                    addOption("--no-write-info-json")
                    addOption("--output", "${jobDir.absolutePath}/wms-%(id)s.%(ext)s")
                }

                onProgress(AcquisitionProgress(0f, "取得を開始しています"))
                ensureNotCanceled()

                val response = try {
                    YoutubeDL.getInstance().execute(request, PROCESS_ID) { progress: Float, _: Long, line: String ->
                        if (!cancelRequested.get()) {
                            onProgress(
                                AcquisitionProgress(
                                    percent = progress.coerceIn(0f, 100f),
                                    message = safeProgressMessage(line, preset),
                                ),
                            )
                        }
                    }
                } catch (error: Throwable) {
                    if (cancelRequested.get()) throw CancellationException("Acquisition canceled")
                    throw classifyError(error, "ACQUIRE_FAILED")
                }
                ensureNotCanceled()

                val produced = jobDir.listFiles()
                    ?.filter { it.isFile && it.extension.equals(preset.extension, ignoreCase = true) }
                    ?.maxByOrNull { it.lastModified() }
                    ?: throw AcquisitionEngineException(
                        "OUTPUT_NOT_FOUND",
                        "${preset.displayName}生成後のファイルを確認できませんでした。診断: ${safeDiagnostic("${response.err}\n${response.out}")}",
                    )

                val destinationName = "wms-${UUID.randomUUID()}.${preset.extension}"
                val destination = File(outputRoot, destinationName)
                val staging = File(outputRoot, "$destinationName.part")

                try {
                    ensureNotCanceled()
                    produced.copyTo(staging, overwrite = false)
                    ensureNotCanceled()

                    if (!staging.exists() || staging.length() <= 0L) {
                        throw AcquisitionEngineException(
                            "OUTPUT_INVALID",
                            "生成ファイルが空か、保存を確認できませんでした。",
                        )
                    }

                    currentCoroutineContext().ensureActive()
                    ensureNotCanceled()

                    if (!staging.renameTo(destination)) {
                        throw AcquisitionEngineException(
                            "FINALIZE_FAILED",
                            "保存ファイルの確定に失敗しました。",
                        )
                    }

                    currentCoroutineContext().ensureActive()
                    ensureNotCanceled()

                    if (!destination.exists() || destination.length() <= 0L) {
                        throw AcquisitionEngineException(
                            "OUTPUT_INVALID",
                            "生成ファイルが空か、保存を確認できませんでした。",
                        )
                    }

                    AcquisitionResult(
                        file = destination,
                        title = info.title?.trim().orEmpty().ifBlank { "保存済みメディア" },
                        provider = providerFor(sourceUrl),
                        preset = preset,
                        author = info.uploader?.trim()?.takeIf(String::isNotBlank),
                        artworkUrl = info.thumbnail?.trim()?.takeIf(::isSafeArtworkUrl),
                    )
                } catch (error: Throwable) {
                    staging.delete()
                    destination.delete()
                    if (error is CancellationException) throw error
                    throw classifyError(error, "SAVE_FAILED")
                } finally {
                    staging.delete()
                }
            } finally {
                jobDir.deleteRecursively()
            }
        }
    }

    override fun cancel() {
        cancelRequested.set(true)
        runCatching { YoutubeDL.getInstance().destroyProcessById(PROCESS_ID) }
    }

    private fun isSafeArtworkUrl(value: String): Boolean = runCatching {
        val uri = URI(value)
        uri.scheme.equals("https", ignoreCase = true) && !uri.host.isNullOrBlank()
    }.getOrDefault(false)

    private fun ensureNotCanceled() {
        if (cancelRequested.get()) {
            throw CancellationException("Acquisition canceled")
        }
    }

    private suspend fun ensureReady() {
        if (initialized) return
        val state = initialize()
        if (!state.ready) throw AcquisitionEngineException(state.code, state.message)
    }

    private fun validateUrl(raw: String) {
        val uri = try {
            URI(raw.trim())
        } catch (error: Exception) {
            throw AcquisitionEngineException("INVALID_URL", "HTTP(S) URLを確認してください。", error)
        }
        val scheme = uri.scheme?.lowercase()
        if (scheme !in setOf("http", "https") || uri.host.isNullOrBlank()) {
            throw AcquisitionEngineException("INVALID_URL", "HTTP(S) URLを確認してください。")
        }
        if (uri.rawUserInfo != null) {
            throw AcquisitionEngineException("URL_CREDENTIALS", "認証情報を含むURLは受け付けません。")
        }
    }

    private fun providerFor(raw: String): String {
        return SourceProviderCatalog.identify(raw).displayName
    }

    private fun safeProgressMessage(line: String, preset: AcquisitionPreset): String {
        val lower = line.lowercase()
        return when {
            "ffmpeg" in lower || "destination" in lower -> if (preset.mediaType == "VIDEO") {
                "映像と音声を仕上げています"
            } else {
                "音声を変換しています"
            }
            "100%" in lower -> "保存を仕上げています"
            "download" in lower -> "メディアを取得しています"
            else -> "処理中"
        }
    }

    private fun diagnosticLines(raw: String): List<String> {
        val keys = listOf(
            "yt-dlp version",
            "python ",
            "js runtimes:",
            "po token providers:",
            "po token cache providers:",
            "js challenge providers:",
            "player client",
            "player_client",
            "po token",
            "http error 403",
            "forbidden",
            "no supported javascript runtime",
            "no space left on device",
        )
        return raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .filter { line -> keys.any { key -> key in line.lowercase() } }
            .map(::sanitizeDiagnosticLine)
            .distinct()
            .take(MAX_DIAGNOSTIC_LINES)
            .toList()
            .ifEmpty { listOf("対象となる診断行は取得できませんでした") }
    }

    private fun sanitizeDiagnosticLine(raw: String): String = raw
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[URL]")
        .replace(Regex("/data/\\S+", RegexOption.IGNORE_CASE), "[APP_PATH]")
        .replace(Regex("/storage/\\S+", RegexOption.IGNORE_CASE), "[STORAGE_PATH]")
        .replace(Regex("\\s+"), " ")
        .take(320)

    private fun classifyError(error: Throwable, fallbackCode: String): AcquisitionEngineException {
        if (error is AcquisitionEngineException) return error
        val rawText = buildString {
            append(error.message.orEmpty())
            append('\n')
            append(error.cause?.message.orEmpty())
        }
        val text = rawText.lowercase()

        return when {
            "no space left on device" in text || "enospc" in text || "errno 28" in text ->
                AcquisitionEngineException(
                    "STORAGE_FULL",
                    "端末の空き容量が不足しています。不要なファイルを削除して再試行してください。",
                    error,
                )
            "unsupported version of python" in text || "requires-python" in text ->
                AcquisitionEngineException(
                    "PYTHON_RUNTIME_TOO_OLD",
                    "内蔵Pythonと現在のyt-dlp要件が一致していません。取得エンジンの更新が必要です。",
                    error,
                )
            "no supported javascript runtime" in text ||
                ("javascript runtime" in text && "deprecated" in text) ||
                "js challenge" in text ||
                "nsig extraction failed" in text ||
                "signature extraction failed" in text ->
                AcquisitionEngineException(
                    "JS_RUNTIME_REQUIRED",
                    "YouTubeの現在の取得方式にはJavaScriptランタイムが必要です。",
                    error,
                )
            "yt-dlp-ejs" in text || ("ejs" in text && ("missing" in text || "unavailable" in text)) ->
                AcquisitionEngineException(
                    "EJS_COMPONENT_REQUIRED",
                    "YouTubeのJavaScriptチャレンジ用EJSコンポーネントが不足しています。",
                    error,
                )
            "cookie" in text || "login" in text || "sign in" in text ->
                AcquisitionEngineException(
                    "LOGIN_REQUIRED",
                    "このURLはログインやCookieを必要とするため、現在のWMS対象外です。",
                    error,
                )
            "drm" in text ->
                AcquisitionEngineException(
                    "DRM_OR_PROTECTED",
                    "保護されたメディアは現在のWMS対象外です。",
                    error,
                )
            "requested format is not available" in text ||
                "only images are available" in text ||
                "no video formats found" in text ->
                AcquisitionEngineException(
                    "FORMAT_UNAVAILABLE",
                    "このURLでは選択した保存形式を取得できませんでした。別の形式を選択してください。",
                    error,
                )
            "http error 403" in text || "forbidden" in text ->
                AcquisitionEngineException(
                    "SOURCE_FORBIDDEN",
                    "配信元から取得が拒否されました。診断: ${safeDiagnostic(rawText)}",
                    error,
                )
            "ffmpeg" in text && ("error" in text || "failed" in text || "not found" in text) ->
                AcquisitionEngineException(
                    "FFMPEG_FAILED",
                    "音声変換処理で失敗しました。",
                    error,
                )
            "unsupported url" in text || "no suitable extractor" in text ->
                AcquisitionEngineException(
                    "UNSUPPORTED_SOURCE",
                    "この公開URLは現在の取得エンジンで処理できません。",
                    error,
                )
            "private video" in text || "unavailable" in text || "not available" in text ->
                AcquisitionEngineException(
                    "UNAVAILABLE",
                    "このメディアは公開状態または利用可能状態を確認できません。",
                    error,
                )
            else -> AcquisitionEngineException(
                fallbackCode,
                "取得エンジンで処理できませんでした。診断: ${safeDiagnostic(rawText)}",
                error,
            )
        }
    }

    private fun safeDiagnostic(raw: String): String {
        val compact = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .firstOrNull { line ->
                val lower = line.lowercase()
                "error" in lower || "warning" in lower || "failed" in lower || "unable" in lower
            }
            ?: raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()
        return sanitizeDiagnosticLine(compact).take(220).ifBlank { "詳細なし" }
    }

    private companion object {
        const val PROCESS_ID = "wms-acquisition"
        const val MAX_DIAGNOSTIC_LINES = 12
    }
}
