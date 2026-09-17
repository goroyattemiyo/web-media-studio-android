package com.goroyattemiyo.wms.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVolumeStateTest {
    @Test fun defaultsToUnityWithBoostOff() {
        val initial = AppVolumeState()
        assertEquals(100, initial.percent)
        assertEquals(100, initial.maximum)
        assertFalse(initial.boostEnabled)
        assertEquals(0, initial.withPercent(initial.muteToggleTarget()).percent)
    }

    @Test fun muteRestoresLastAudibleLevel() {
        val at35 = AppVolumeState().withPercent(35)
        val muted = at35.withPercent(at35.muteToggleTarget())
        assertEquals(35, muted.lastAudiblePercent)
        assertEquals(35, muted.muteToggleTarget())
    }

    @Test fun boostsOnlyWhenAvailableAndExplicitlyArmed() {
        val unavailable = AppVolumeState().withBoost(true).withPercent(200)
        assertFalse(unavailable.boostEnabled)
        assertEquals(100, unavailable.percent)
        val enabled = AppVolumeState(boostAvailable = true).withBoost(true).withPercent(175)
        assertTrue(enabled.boostEnabled)
        assertEquals(200, enabled.maximum)
        assertEquals(175, enabled.percent)
        assertEquals(175, enabled.withPercent(0).muteToggleTarget())
    }

    @Test fun disarmingAndRouteChangeClampRequestedAndRememberedGain() {
        val boosted = AppVolumeState(boostAvailable = true).withBoost(true).withPercent(190)
        val safe = boosted.withBoost(false)
        assertFalse(safe.boostEnabled)
        assertEquals(100, safe.percent)
        assertEquals(100, safe.lastAudiblePercent)
        assertEquals(100, safe.withPercent(0).muteToggleTarget())
    }

    @Test fun boundsAndSilentInvalidRestoreAreSafe() {
        assertEquals(100, AppVolumeState().withPercent(999).percent)
        assertEquals(0, AppVolumeState().withPercent(-8).percent)
        assertEquals(1, AppVolumeState(0, 0).muteToggleTarget())
        assertEquals(200, AppVolumeState(boostAvailable = true).withBoost(true).withPercent(999).percent)
    }
}
