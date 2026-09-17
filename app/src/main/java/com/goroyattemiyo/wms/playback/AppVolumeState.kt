package com.goroyattemiyo.wms.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** 100% is unity gain. This never changes the Android system/Bluetooth volume. */
data class AppVolumeState(
    val percent: Int = 100,
    val lastAudiblePercent: Int = 100,
) {
    fun withPercent(value: Int): AppVolumeState {
        val normalized = value.coerceIn(0, 100)
        return copy(
            percent = normalized,
            lastAudiblePercent = if (normalized > 0) normalized else lastAudiblePercent.coerceIn(1, 100),
        )
    }

    fun muteToggleTarget(): Int = if (percent == 0) lastAudiblePercent.coerceIn(1, 100) else 0
}

/** Same-process observable mirror; PlaybackService owns the player and publishes changes. */
object AppVolumeBus {
    private val mutableState = MutableStateFlow(AppVolumeState())
    val state = mutableState.asStateFlow()

    internal fun publish(value: AppVolumeState) {
        mutableState.value = value
    }
}
