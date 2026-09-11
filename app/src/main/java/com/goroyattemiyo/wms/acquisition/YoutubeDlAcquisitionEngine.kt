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

            val outputRoot = File(
                appContext.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: appContext.filesDir,
                "wms/acquired",
            ).apply { mkdirs() }

            val jobsRoot = File(appContext.cacheDir, "wms-acquisition-jobs").apply { mkdirs() }
            val jobDir = File(jobsRoot, "job-${UUID.randomUUID()}").apply { mkdirs() }

            try {
                val request = YoutubeDLRequest(sourceUrl.trim()).apply {
                    addOption("--no-playlist")
                    addOption("--match-filter", "duration <= 1800")
                    addOption("--max-filesize", "250M")
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "192K")
                    addOption("--no-write-thumbnail")
                    addOption("--no-write-info-json")
                    addOption("--output", "${jobDir.absolutePath}/wms-a0-%(id)s.%(ext)s")
                }

                onProgress(AcquisitionProgress(0f, "取得を開始しています"))

                try {
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
                    ?: throw AcquisitionEngineException(
                        code = "OUTPUT_NOT_FOUND",
                        message = "MP3生成後のファイルを確認できませんでした。",
                    )

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

                val title = runCatching {
                    YoutubeDL.getInstance().getInfo(sourceUrl.trim()).title?.trim().orEmpty()
                }.getOrDefault("").ifBlank { "保存済み音声" }

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

        val text = buildString {
            append(error.message.orEmpty())
            append(' ')
            append(error.cause?.message.orEmpty())
        }.lowercase()

        return when {
            "unsupported version of python" in text || "python versions 3.10" in text ->
                AcquisitionEngineException(
                    "PYTHON_RUNTIME_TOO_OLD",
                    "内蔵Pythonと現在のyt-dlp要件が一致していません。取得エンジンの差し替えが必要です。",
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

            "duration" in text && "1800" in text ->
                AcquisitionEngineException(
                    "DURATION_LIMIT",
                    "Gate A0では30分以内のメディアだけを対象にします。",
                    error,
                )

            else -> AcquisitionEngineException(
                fallbackCode,
                "取得エンジンで処理できませんでした。コード: $fallbackCode",
                error,
            )
        }
    }

    private companion object {
        const val PROCESS_ID = "wms-gate-a0"
    }
}
