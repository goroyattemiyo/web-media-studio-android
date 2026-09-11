package com.goroyattemiyo.wms.search

data class SearchMediaItem(
    val provider: String,
    val sourceId: String,
    val url: String,
    val title: String,
    val author: String,
    val thumbnailUrl: String?,
    val canDownload: Boolean,
)

interface SearchProvider {
    suspend fun search(query: String, maxResults: Int = 8): Result<List<SearchMediaItem>>
}
