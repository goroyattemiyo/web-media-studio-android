package com.goroyattemiyo.wms.appearance

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WmsSkinCatalogTest {
    @Test
    fun exposesEveryCanonicalSkinIdExactlyOnce() {
        val expected = setOf(
            "midnight-neon", "obsidian", "studio-light", "analog-warm", "cyber-blue",
            "aurora-purple", "emerald-night", "crimson-noir", "sunset-glow", "sakura",
            "pixel-arcade", "led-marquee", "retro-terminal", "cassette-deck",
        )
        assertEquals(expected, WmsSkinCatalog.skins.map { it.id }.toSet())
        assertEquals(expected.size, WmsSkinCatalog.skins.size)
    }

    @Test
    fun unknownSkinFallsBackToMidnightNeon() {
        assertEquals(WmsSkinCatalog.DEFAULT_ID, WmsSkinCatalog.byId("unknown").id)
    }

    @Test
    fun everySkinHasReadableDistinctSurfaceTokens() {
        WmsSkinCatalog.skins.forEach { skin ->
            assertNotEquals(skin.background, skin.surface)
            assertNotEquals(skin.surface, skin.primary)
            assertTrue(skin.displayName.isNotBlank())
            assertTrue(skin.description.isNotBlank())
        }
    }
}
