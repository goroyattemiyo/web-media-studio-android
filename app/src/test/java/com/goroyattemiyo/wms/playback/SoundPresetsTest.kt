package com.goroyattemiyo.wms.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SoundPresetsTest {
    @Test fun eightLogicalProfilesIncludeFlat() {
        assertEquals(8, SoundPresets.builtIns.size)
        assertEquals("builtin:flat", SoundPresets.builtIns.first().id)
        assertEquals(0, SoundPresets.gainAt(SoundPresets.builtIns.first(), 910, -1_500, 1_500))
    }

    @Test fun exactAnchorsAndInterpolationMapByFrequency() {
        val preset = SoundPresets.builtIns.first { it.id == "builtin:bass" }
        assertEquals(400, SoundPresets.gainAt(preset, 60, -1_500, 1_500))
        assertEquals(300, SoundPresets.gainAt(preset, 230, -1_500, 1_500))
        val intermediate = SoundPresets.gainAt(preset, 500, -1_500, 1_500)
        assertTrue(intermediate in 0..300)
    }

    @Test fun deviceLimitsClampAndMalformedNamesAreRejected() {
        val bass = SoundPresets.builtIns.first { it.id == "builtin:bass" }
        assertEquals(150, SoundPresets.gainAt(bass, 60, -150, 150))
        assertNull(SoundPresets.validName("  "))
        assertNull(SoundPresets.validName("x".repeat(41)))
        assertNull(SoundPresets.uniqueName(" ジャズ ", emptyList()))
        assertEquals("私の設定", SoundPresets.uniqueName(" 私の設定 ", emptyList()))
    }
}
