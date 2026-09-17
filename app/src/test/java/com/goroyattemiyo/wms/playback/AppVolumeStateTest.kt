package com.goroyattemiyo.wms.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class AppVolumeStateTest {
    @Test
    fun defaultIsUnityAndMuteRestoresIt() {
        val initial = AppVolumeState()
        assertEquals(100, initial.percent)
        val muted = initial.withPercent(initial.muteToggleTarget())
        assertEquals(0, muted.percent)
        assertEquals(100, muted.muteToggleTarget())
    }

    @Test
    fun remembersLastAudibleValueAcrossMute() {
        val at35 = AppVolumeState().withPercent(35)
        val muted = at35.withPercent(at35.muteToggleTarget())
        assertEquals(35, muted.lastAudiblePercent)
        assertEquals(35, muted.muteToggleTarget())
        assertEquals(35, muted.withPercent(muted.muteToggleTarget()).percent)
    }

    @Test
    fun clampInputAndNeverRestoreSilentValue() {
        assertEquals(100, AppVolumeState().withPercent(999).percent)
        assertEquals(0, AppVolumeState().withPercent(-8).percent)
        assertEquals(1, AppVolumeState(0, 0).muteToggleTarget())
    }
}
