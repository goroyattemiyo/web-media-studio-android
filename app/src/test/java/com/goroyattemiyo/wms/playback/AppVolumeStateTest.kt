package com.goroyattemiyo.wms.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AppVolumeStateTest {
    @Test fun defaultsToUnityWithBoostOff() {
        val initial = AppVolumeState()
        assertEquals(100, initial.percent)
        assertEquals(100, initial.maximum)
        assertFalse(initial.boostEnabled)
        assertEquals(0, initial.boostDbTenths)
        assertNull(initial.actualBoostDb)
        assertEquals(0, initial.withPercent(initial.muteToggleTarget()).percent)
    }

    @Test fun muteRestoresLastAudibleLevel() {
        val at35 = AppVolumeState().withPercent(35)
        val muted = at35.withPercent(at35.muteToggleTarget())
        assertEquals(35, muted.lastAudiblePercent)
        assertEquals(35, muted.muteToggleTarget())
    }

    @Test fun boostOnlyWhenAvailableAndExplicitlyArmed() {
        val unavailable = AppVolumeState().withBoost(true)
        assertFalse(unavailable.boostEnabled)
        assertEquals(0, unavailable.boostDbTenths)
        val enabled = AppVolumeState(percent = 45, boostAvailable = true).withBoost(true)
        assertTrue(enabled.boostEnabled)
        assertEquals(45, enabled.percent) // Enabling boost never jumps the normal volume slider.
        assertEquals(30, enabled.boostDbTenths)
        assertEquals(3f, enabled.requestedBoostDb, 0.0001f)
        assertEquals(100, enabled.maximum)
    }

    @Test fun gainBoundariesAndRouteDisarm() {
        val boosted = AppVolumeState(boostAvailable = true).withBoost(true)
            .withBoostDbTenths(999)
        assertEquals(60, boosted.boostDbTenths)
        assertEquals(6f, boosted.requestedBoostDb, 0.0001f)
        val safe = boosted.withBoost(false)
        assertFalse(safe.boostEnabled)
        assertEquals(100, safe.percent)
        assertEquals(0, safe.boostDbTenths)
        assertNull(safe.actualBoostDb)
        assertEquals(0f, safe.requestedBoostDb, 0.0001f)
    }

    @Test fun boundsMuteAndGainCannotArmByItself() {
        assertEquals(100, AppVolumeState().withPercent(999).percent)
        assertEquals(0, AppVolumeState().withPercent(-8).percent)
        assertEquals(1, AppVolumeState(0, 0).muteToggleTarget())
        assertEquals(0, AppVolumeState(boostAvailable = true).withBoostDbTenths(60).boostDbTenths)
        val boosted = AppVolumeState(boostAvailable = true).withBoost(true).withBoostDbTenths(-4)
        assertEquals(0, boosted.boostDbTenths)
        assertTrue(boosted.boostEnabled)
    }
}
