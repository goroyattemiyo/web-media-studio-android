package com.goroyattemiyo.wms.ui.components

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UrlsTest {
    @Test
    fun directHttpUrlsAreRecognizedAfterTrimming() {
        assertTrue(isDirectUrl("  https://example.com/media?id=1  "))
        assertTrue(isDirectUrl("http://example.com/file.mp3"))
    }

    @Test
    fun searchTermsAndEmbeddedUrlsAreNotTreatedAsDirectUrls() {
        assertFalse(isDirectUrl("artist song title"))
        assertFalse(isDirectUrl("open https://example.com"))
        assertFalse(isDirectUrl("ftp://example.com/file"))
    }
}
