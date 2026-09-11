package com.goroyattemiyo.wms.search

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import java.net.URI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

class LocalYoutubeSearchProvider(context: Context) : SearchProvider {
    private val appContext = context.applicationContext

    override suspend fun search(query: String, maxResults: Int): Result<List<SearchMediaItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                require(trimmed.length >= 2) { "2文字以上で検索してください。" }

                val safeMax = maxResults.coerceIn(1, 12)
                YoutubeDL.getInstance().init(appContext)

                val request = YoutubeDLRequest("ytsearch${safeMax}:$trimmed").apply {
                    addOption("--flat-playlist")
                    addOption("--dump-json")
                    addOption("--ignore-errors")
                    addOption("--no-warnings")
                    addOption("--skip-download")
                    addOption("--playlist-end", safeMax.toString())
                }

                val response = YoutubeDL.getInstance().execute(request, SEARCH_PROCESS_ID)
                val results = response.out
                    .lineSequence()
                    .map(String::trim)
                    .filter { it.startsWith("{") && it.endsWith("}") }
                    .mapNotNull(::parseItem)
                    .distinctBy { it.sourceId.ifBlank { it.url } }
                    .take(safeMax)
                    .toList()

                if (results.isEmpty() && response.err.isNotBlank()) {
                    throw IllegalStateException(
                        "端末内YouTube検索で結果を取得できませんでした。診断: ${safeDiagnostic(response.err)}",
                    )
                }

                results
            }
        }

    private fun parseItem(raw: String): SearchMediaItem? {
        val item = runCatching { JSONObject(raw) }.getOrNull() ?: return null
        val id = item.optString("id").cleanJsonString()
        val title = item.optString("title").cleanJsonString()
        if (id.isBlank() || title.isBlank()) return null

        val webpageUrl = item.optString("webpage_url").cleanJsonString()
        val rawUrl = item.optString("url").cleanJsonString()
        val canonicalUrl = when {
            isSafeHttpUrl(webpageUrl) -> webpageUrl
            isSafeYoutubeUrl(rawUrl) -> rawUrl
            else -> "https://www.youtube.com/watch?v=$id"
        }

        val author = sequenceOf(
            item.optString("channel"),
            item.optString("uploader"),
            item.optString("channel_id"),
            item.optString("uploader_id"),
        )
            .map { it.cleanJsonString() }
            .firstOrNull { it.isNotBlank() }
            ?: "YouTube"

        val thumbnail = item.optString("thumbnail")
            .cleanJsonString()
            .takeIf(::isSafeHttpUrl)
            ?: thumbnailFrom(item.optJSONArray("thumbnails"))

        val durationSeconds = item.optDouble("duration", Double.NaN)
            .takeIf { it.isFinite() && it > 0.0 }
            ?.toInt()

        return SearchMediaItem(
            provider = "youtube",
            sourceId = id,
            url = canonicalUrl,
            title = title,
            author = author,
            thumbnailUrl = thumbnail,
            durationSeconds = durationSeconds,
            canDownload = true,
        )
    }

    private fun thumbnailFrom(items: JSONArray?): String? {
        if (items == null) return null
        for (index in items.length() - 1 downTo 0) {
            val url = items.optJSONObject(index)
                ?.optString("url")
                ?.cleanJsonString()
                .orEmpty()
            if (isSafeHttpUrl(url)) return url
        }
        return null
    }

    private fun isSafeYoutubeUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        val host = uri.host?.lowercase().orEmpty()
        uri.scheme in setOf("http", "https") &&
            uri.rawUserInfo == null &&
            (host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com"))
    }.getOrDefault(false)

    private fun isSafeHttpUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    private fun safeDiagnostic(raw: String): String = raw
        .lineSequence()
        .map(String::trim)
        .firstOrNull { it.isNotBlank() }
        .orEmpty()
        .replace(Regex("https?://\\S+", RegexOption.IGNORE_CASE), "[URL]")
        .replace(Regex("/data/\\S+", RegexOption.IGNORE_CASE), "[APP_PATH]")
        .replace(Regex("/storage/\\S+", RegexOption.IGNORE_CASE), "[STORAGE_PATH]")
        .replace(Regex("\\s+"), " ")
        .take(220)
        .ifBlank { "詳細なし" }

    private fun String.cleanJsonString(): String = trim().takeUnless { it == "null" } ?: ""

    private companion object {
        const val SEARCH_PROCESS_ID = "wms-youtube-search"
    }
}
