package com.goroyattemiyo.wms.acquisition

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceProviderCatalogTest {
    @Test
    fun `recognizes canonical and subdomain provider hosts without suffix spoofing`() {
        assertProvider(SourceProvider.YOUTUBE, "https://youtu.be/example")
        assertProvider(SourceProvider.YOUTUBE, "https://M.YouTube.com/watch?v=example")
        assertProvider(SourceProvider.TIKTOK, "https://www.tiktok.com/@wms/video/1")
        assertProvider(SourceProvider.INSTAGRAM, "https://www.instagram.com/reel/example/")
        assertProvider(SourceProvider.DIRECT_WEB, "https://youtube.com.example.test/media.mp4")
    }

    @Test
    fun `direct web identity exposes its normalized host`() {
        val identity = SourceProviderCatalog.identify("https://MEDIA.Example.COM./audio/sample.mp3")

        assertEquals(SourceProvider.DIRECT_WEB, identity.provider)
        assertEquals("media.example.com", identity.host)
        assertEquals("media.example.com", identity.displayName)
    }

    @Test
    fun `keyword search capability is independent from acquisition identity`() {
        assertTrue(SourceProvider.YOUTUBE.keywordSearchAvailable)
        assertFalse(SourceProvider.TIKTOK.keywordSearchAvailable)
        assertFalse(SourceProvider.INSTAGRAM.keywordSearchAvailable)
        assertFalse(SourceProvider.DIRECT_WEB.keywordSearchAvailable)
    }

    private fun assertProvider(expected: SourceProvider, url: String) {
        assertEquals(expected, SourceProviderCatalog.identify(url).provider)
    }
}
