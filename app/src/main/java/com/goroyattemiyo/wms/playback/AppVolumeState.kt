package com.goroyattemiyo.wms.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Percent above 100 is requested DSP gain, never Media3 Player.volume. */
data class AppVolumeState(
    val percent: Int = 100,
    val lastAudiblePercent: Int = 100,
    val boostEnabled: Boolean = false,
    val boostAvailable: Boolean = false,
    val limitedFrames: Long = 0,
    val message: String = "",
) {
    val maximum: Int get() = if (boostEnabled && boostAvailable) 200 else 100

    fun withPercent(value: Int): AppVolumeState {
        val normalized = value.coerceIn(0, maximum)
        return copy(
            percent = normalized,
            lastAudiblePercent = if (normalized > 0) normalized else lastAudiblePercent.coerceIn(1, maximum),
        )
    }

    fun withBoost(enabled: Boolean): AppVolumeState = copy(
        boostEnabled = enabled && boostAvailable,
        percent = if (enabled && boostAvailable) percent else percent.coerceAtMost(100),
        lastAudiblePercent = if (enabled && boostAvailable) lastAudiblePercent else lastAudiblePercent.coerceIn(1, 100),
    )

    fun muteToggleTarget(): Int = if (percent == 0) lastAudiblePercent.coerceIn(1, maximum) else 0
}

object AppVolumeBus {
    private val mutableState = MutableStateFlow(AppVolumeState())
    val state = mutableState.asStateFlow()
    internal fun publish(value: AppVolumeState) { mutableState.value = value }
}
