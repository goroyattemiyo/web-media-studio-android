package com.goroyattemiyo.wms.playback

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackOptionsTest {
    @Test
    fun playbackSpeedOnlyUsesSupportedValues() {
        assertEquals(listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f), PlaybackSpeed.allowed)
        assertEquals(1.5f, PlaybackSpeed.normalize(1.49f))
        assertEquals("1x", PlaybackSpeed.label(1f))
    }

    @Test
    fun repeatModeMapsBothWays() {
        assertEquals(RepeatOption.OFF, RepeatOption.fromMedia3(Player.REPEAT_MODE_OFF))
        assertEquals(RepeatOption.ALL, RepeatOption.fromMedia3(Player.REPEAT_MODE_ALL))
        assertEquals(RepeatOption.ONE, RepeatOption.fromMedia3(Player.REPEAT_MODE_ONE))
        assertEquals(Player.REPEAT_MODE_ONE, RepeatOption.ONE.media3Mode)
    }

    @Test
    fun shuffleStateTogglesWithoutChangingTheQueue() {
        assertTrue(toggledShuffleEnabled(false))
        assertFalse(toggledShuffleEnabled(true))
    }

    @Test
    fun abLoopRequiresStrictlyIncreasingRangeAndCanClear() {
        val withA = AbLoopState().setA(10_000L)
        assertFalse(withA.setB(10_000L).isValid)
        assertFalse(withA.setB(9_999L).isValid)

        val valid = withA.setB(20_000L).toggle()
        assertTrue(valid.enabled)
        assertFalse(valid.shouldLoopAt(19_999L))
        assertTrue(valid.shouldLoopAt(20_000L))

        val cleared = valid.clear()
        assertEquals(null, cleared.pointAMs)
        assertEquals(null, cleared.pointBMs)
        assertFalse(cleared.enabled)
    }

    @Test
    fun settingANewPointClearsExistingLoop() {
        val active = AbLoopState().setA(1_000L).setB(2_000L).toggle()
        val reset = active.setA(3_000L)
        assertEquals(3_000L, reset.pointAMs)
        assertEquals(null, reset.pointBMs)
        assertFalse(reset.enabled)
    }

    @Test
    fun seekingOutsideActiveAbLoopRespectsRequestedPosition() {
        val active = AbLoopState().setA(19_000L).setB(60_000L).toggle()
        assertEquals(active, active.afterManualSeek(19_000L))
        assertEquals(active, active.afterManualSeek(59_999L))
        assertEquals(AbLoopState(), active.afterManualSeek(18_999L))
        assertEquals(AbLoopState(), active.afterManualSeek(60_000L))
        assertEquals(AbLoopState(), active.afterManualSeek(120_000L))
    }

    @Test
    fun manualSeekDoesNotClearInactiveAbRange() {
        val inactive = AbLoopState().setA(19_000L).setB(60_000L)
        assertEquals(inactive, inactive.afterManualSeek(120_000L))
    }
}
