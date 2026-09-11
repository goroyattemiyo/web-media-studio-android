package com.goroyattemiyo.wms.acquisition

import android.content.Context
import android.os.Environment
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.io.File
import java.net.URI
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class AcquisitionEngineException(
    val code: String,
    override val message: String,
    cause: Throwable? = null,
) : Exception(message, cause)

class YoutubeDlAcquisitionEngine(context: Context) : MediaAcquisitionEngine {
    private val appContext = context.applicationContext
    private val initMutex = Mutex()

    @Volatile
    private var initialized = false

    override suspend fun initialize(): EngineState = withContext(Dispatchers.IO) {
        initMutex.withLock {
            if (initialized) {
                return@withLock EngineState(
                    ready = true,
                    code = "READY",
                    message = "取得エンジン準備完了",
                )
            }

            try {
                // Gate A0 intentionally does not call updateYoutubeDL().
                // The packaged Python/yt-dlp compatibility must be proven first.
                YoutubeDL.getInstance().init(appContext)
                FFmpeg.getInstance().init(appContext)
                initialized = true
                EngineState(
                    ready = true,
                    code = "READY",
                    message = "取得エンジン準備完了",
                )
            } catch (error: Throwable) {
                val classified = classifyError(error, "RUNTIME_INIT")
                EngineState(
                    ready = false,
                    code = classified.code,
                    message = classified.message,
                )
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
            )
        }
    }

    override suspend fun acquireMp3(
        sourceUrl: String,
        onProgress: (AcquisitionProgress) -> Unit,
    ): Result<AcquisitionResult> = withContext(Dispatchers.IO) {
        runCatching {
            validateUrl(sourceUrl)
            ensureReady()

            val source = sourceUrl.trim()
            val info = try {
                YoutubeDL.getInstance().getInfo(source)
            } catch (error: Throwable) {
                throw classifyError(error, "ACQUIRE_FAILED")
            }

            if (info.duration > MAX_DURATION_SECONDS) {
                throw AcquisitionEngineException(
                    code = "DURATION_LIMIT",
                    message = "Gate A0では30分以内のメディアだけを対象にします。",
                )
            }

            val outputRoot = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir,
                "wms/acquired",
            ).apply { mkdirs() }

            val jobsRoot = File(appContext.cacheDir, "wms-acquisition-jobs").apply { mkdirs() }
            val jobDir = File(jobsRoot, "job-${UUID.randomUUID()}").apply { mkdirs() }

            try {
                val request = YoutubeDLRequest(source).apply {
                    addOption("--no-playlist")
                    addOption("--match-filter", "duration <= $MAX_DURATION_SECONDS")
                    addOption("--max-filesize", "250M")
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K")
                    addOption("--no-write-thumbnail")
                    addOption("--no-write-info-json")
                    addOption("--output", "${jobDir.absolutePath}/wms-a0-%(id)s.%(ext)s")
                }

                onProgress(AcquisitionProgress(0f, "取得を開始しています"))

                val response = try {
                    YoutubeDL.getInstance().execute(
                        request,
                        PROCESS_ID,
                    ) { progress: Float, _: Long, line: String ->
                        onProgress(
                            AcquisitionProgress(
                                percent = progress.coerceIn(0f, 100f),
                                message = safeProgressMessage(line),
                            ),
                        )
                    }
                } catch (error: Throwable) {
                    throw classifyError(error, "ACQUIRE_FAILED")
                }

                val produced = jobDir
                    .listFiles()
                    ?.filter { it.isFile && it.extension.equals("mp3", ignoreCase = true) }
                    ?.maxByOrNull { it.lastModified() }

                if (produced == null) {
                    val executionText = "${response.out}\n${response.err}".lowercase()
                    if (
                        "does not pass filter" in executionText ||
                        ("duration" in executionText && MAX_DURATION_SECONDS.toString() in executionText)
                    ) {
                        throw AcquisitionEngineException(
                            code = "DURATION_LIMIT",
                            message = "Gate A0では30分以内のメディアだけを対象にします。",
                        )
                    }
                    throw AcquisitionEngineException(
                        code = "OUTPUT_NOT_FOUND",
                        message = "MP3生成後のファイルを確認できませんでした。",
                    )
                }

                val destination = File(outputRoot, "wms-${UUID.randomUUID()}.mp3")
                if (!produced.renameTo(destination)) {
                    produced.copyTo(destination, overwrite = false)
                }

                if (!destination.exists() || destination.length() <= 0L) {
                    throw AcquisitionEngineException(
                        code = "OUTPUT_INVALID",
                        message = "生成ファイルが空か、保存を確認できませんでした。",
                    )
                }

                val title = info.title?.trim().orEmpty().ifBlank { "保存済み音声" }

                AcquisitionResult(
                    file = destination,
                    title = title,
                    provider = providerFor(sourceUrl),
                )
            } finally {
                jobDir.deleteRecursively()
            }
        }
    }

    override fun cancel() {
        runCatching {
            YoutubeDL.getInstance().destroyProcessById(PROCESS_ID)
        }
    }

    private suspend fun ensureReady() {
        if (initialized) return
        val state = initialize()
        if (!state.ready) {
            throw AcquisitionEngineException(state.code, state.message)
        }
    }

    private fun validateUrl(raw: String) {
        val value = raw.trim()
        val uri = try {
            URI(value)
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
        val host = runCatching { URI(raw.trim()).host?.lowercase().orEmpty() }.getOrDefault("")
        return when {
            host == "youtu.be" || host.endsWith(".youtube.com") || host == "youtube.com" -> "YouTube"
            host.endsWith(".tiktok.com") || host == "tiktok.com" -> "TikTok"
            host.endsWith(".instagram.com") || host == "instagram.com" -> "Instagram"
            host.isNotBlank() -> host
            else -> "Web"
        }
    }

    private fun safeProgressMessage(line: String): String {
        val lower = line.lowercase()
        return when {
            "ffmpeg" in lower || "destination" in lower -> "音声を変換しています"
            "100%" in lower -> "保存を仕上げています"
            "download" in lower -> "メディアを取得しています"
            else -> "処理中"
        }
    }

    private fun classifyError(error: Throwable, fallbackCode: String): AcquisitionEngineException {
        if (error is AcquisitionEngineException) return error

        val rawText = buildString {
            append(error.message.orEmpty())
            append('\n')
            append(error.cause?.message.orEmpty())
        }
        val text = rawText.lowercase()

        return when {
            "unsupported version of python" in text ||
                "python versions 3.10" in text ||
                "requires-python" in text ->
                AcquisitionEngineException(
                    "PYTHON_RUNTIME_TOO_OLD",
                    "内蔵Pythonと現在のyt-dlp要件が一致していません。取得エンジンの更新が必要です。",
                    error,
                )

            "no supported javascript runtime" in text ||
                "javascript runtime" in text && "deprecated" in text ||
                "js challenge" in text ||
                "jsc" in text && "unavailable" in text ||
                "nsig extraction failed" in text ||
                "signature extraction failed" in text ->
                AcquisitionEngineException(
                    "JS_RUNTIME_REQUIRED",
                    "YouTubeの現在の取得方式にはJavaScriptランタイムが必要です。Android取得エンジンへQuickJS等を追加する必要があります。",
                    error,
                )

            "yt-dlp-ejs" in text ||
                "ejs" in text && ("not installed" in text || "missing" in text || "unavailable" in text) ->
                AcquisitionEngineException(
                    "EJS_COMPONENT_REQUIRED",
                    "YouTubeのJavaScriptチャレンジ用EJSコンポーネントが不足しています。取得エンジンへの追加が必要です。",
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
                    "このURLでは現在のAndroid取得エンジンが利用可能な音声形式を取得できませんでした。",
                    error,
                )

            "http error 403" in text || "forbidden" in text ->
                AcquisitionEngineException(
                    "SOURCE_FORBIDDEN",
                    "配信元から取得が拒否されました。現在の取得方式ではこのメディアを保存できません。",
                    error,
                )

            "ffmpeg" in text && ("error" in text || "failed" in text || "not found" in text) ->
                AcquisitionEngineException(
                    "FFMPEG_FAILED",
                    "音声変換処理で失敗しました。Android FFmpeg構成を確認する必要があります。",
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

            "duration" in text && MAX_DURATION_SECONDS.toString() in text ->
                AcquisitionEngineException(
                    "DURATION_LIMIT",
                    "Gate A0では30分以内のメディアだけを対象にします。",
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
        val compact = raw
            .lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .firstOrNull { line ->
                val lower = line.lowercase()
                "error" in lower || "warning" in lower || "failed" in lower || "unable" in lower
            }
            ?: raw.lineSequence().map { it.trim() }.firstOrNull { it.isNotBlank() }.orEmpty()

        if (compact.isBlank()) return "詳細なし"

        return compact
            .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[URL]")
            .replace(Regex("/data/\\S+", RegexOption.IGNORE_CASE), "[APP_PATH]")
            .replace(Regex("/storage/\\S+", RegexOption.IGNORE_CASE), "[STORAGE_PATH]")
            .replace(Regex("\\s+"), " ")
            .take(220)
    }

    private companion object {
        const val PROCESS_ID = "wms-gate-a0"
        const val MAX_DURATION_SECONDS = 1800
    }
}
