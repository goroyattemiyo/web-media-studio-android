package com.goroyattemiyo.wms.ui.components

import org.junit.Assert.assertEquals
import org.junit.Test

class FormattersTest {
    @Test
    fun `duration formatting preserves compact and hour forms`() {
        assertEquals("0:00", formatDuration(-1))
        assertEquals("4:18", formatDuration(258))
        assertEquals("1:02:03", formatDuration(3_723))
    }

    @Test
    fun `playback time clamps negative values`() {
        assertEquals("0:00", formatPlaybackTime(-5_000L))
        assertEquals("1:01", formatPlaybackTime(61_999L))
    }

    @Test
    fun `file size uses non-negative mebibytes`() {
        assertEquals("0.0 MB", formatFileSize(-1L))
        assertEquals("1.5 MB", formatFileSize(1_572_864L))
    }
}
