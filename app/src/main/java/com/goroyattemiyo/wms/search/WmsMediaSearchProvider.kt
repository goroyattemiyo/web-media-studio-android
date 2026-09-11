package com.goroyattemiyo.wms.search

import java.net.HttpURLConnection
import java.net.URI
import java.net.URLEncoder
import java.net.URL
import java.nio.charset.StandardCharsets
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

class WmsMediaSearchProvider(
    private val baseUrl: String = DEFAULT_BASE_URL,
) : SearchProvider {
    override suspend fun search(query: String, maxResults: Int): Result<List<SearchMediaItem>> =
        withContext(Dispatchers.IO) {
            runCatching {
                val trimmed = query.trim()
                require(trimmed.length >= 2) { "2文字以上で検索してください。" }

                val safeMax = maxResults.coerceIn(1, 12)
                val encoded = URLEncoder.encode(trimmed, StandardCharsets.UTF_8.toString())
                val endpoint = "$baseUrl/video/search?q=$encoded&provider=youtube&max_results=$safeMax"
                val uri = URI(endpoint)
                require(uri.scheme == "https" && uri.host == EXPECTED_HOST) {
                    "検索先を確認できません。"
                }

                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    requestMethod = "GET"
                    connectTimeout = 8_000
                    readTimeout = 12_000
                    setRequestProperty("Accept", "application/json")
                    useCaches = false
                }

                try {
                    val status = connection.responseCode
                    if (status !in 200..299) {
                        throw IllegalStateException("動画検索に失敗しました。コード: SEARCH_HTTP_$status")
                    }

                    val body = connection.inputStream.bufferedReader().use { it.readText() }
                    val root = JSONObject(body)
                    val items = root.optJSONArray("items") ?: return@runCatching emptyList()

                    buildList {
                        for (index in 0 until items.length()) {
                            val item = items.optJSONObject(index) ?: continue
                            val url = item.optString("url").trim()
                            val title = item.optString("title").trim()
                            if (!isSafeHttpUrl(url) || title.isBlank()) continue

                            add(
                                SearchMediaItem(
                                    provider = item.optString("provider", "youtube").ifBlank { "youtube" },
                                    sourceId = item.optString("source_id").trim(),
                                    url = url,
                                    title = title,
                                    author = item.optString("author").trim(),
                                    thumbnailUrl = item.optString("thumbnail_url")
                                        .takeIf { it.isNotBlank() && it != "null" && isSafeHttpUrl(it) },
                                    canDownload = item.optBoolean("can_download", false),
                                ),
                            )
                        }
                    }
                } finally {
                    connection.disconnect()
                }
            }
        }

    private fun isSafeHttpUrl(raw: String): Boolean = runCatching {
        val uri = URI(raw)
        uri.scheme in setOf("http", "https") && !uri.host.isNullOrBlank() && uri.rawUserInfo == null
    }.getOrDefault(false)

    private companion object {
        const val DEFAULT_BASE_URL = "https://wms-media-worker-pcdbs5armq-an.a.run.app"
        const val EXPECTED_HOST = "wms-media-worker-pcdbs5armq-an.a.run.app"
    }
}
