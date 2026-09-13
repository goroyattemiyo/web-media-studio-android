package com.goroyattemiyo.wms.acquisition

import java.net.URI

enum class SourceProvider(
    val id: String,
    val displayName: String,
    val keywordSearchAvailable: Boolean,
) {
    YOUTUBE("youtube", "YouTube", true),
    TIKTOK("tiktok", "TikTok", false),
    INSTAGRAM("instagram", "Instagram", false),
    DIRECT_WEB("direct-web", "Direct web", false),
}

data class SourceIdentity(
    val provider: SourceProvider,
    val host: String,
) {
    val displayName: String
        get() = when (provider) {
            SourceProvider.DIRECT_WEB -> host.ifBlank { provider.displayName }
            else -> provider.displayName
        }
}

object SourceProviderCatalog {
    fun identify(rawUrl: String): SourceIdentity {
        val host = runCatching {
            URI(rawUrl.trim()).host?.lowercase()?.removeSuffix(".").orEmpty()
        }.getOrDefault("")
        val provider = when {
            host == "youtu.be" || host == "youtube.com" || host.endsWith(".youtube.com") ->
                SourceProvider.YOUTUBE
            host == "tiktok.com" || host.endsWith(".tiktok.com") -> SourceProvider.TIKTOK
            host == "instagram.com" || host.endsWith(".instagram.com") -> SourceProvider.INSTAGRAM
            else -> SourceProvider.DIRECT_WEB
        }
        return SourceIdentity(provider = provider, host = host)
    }
}
