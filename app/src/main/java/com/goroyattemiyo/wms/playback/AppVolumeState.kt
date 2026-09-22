package com.goroyattemiyo.wms.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Normal WMS volume is 0–100%; boost is a separate explicitly armed PCM gain. */
data class AppVolumeState(
    val percent: Int = 100,
    val lastAudiblePercent: Int = 100,
    val boostEnabled: Boolean = false,
    val boostAvailable: Boolean = false,
    val boostDbTenths: Int = 0,
    /** Measured PCM output/input RMS ratio in dB, before Android/device volume. Null until measured. */
    val actualBoostDb: Float? = null,
    val limitedFrames: Long = 0,
    val message: String = "",
) {
    val maximum: Int get() = 100
    val requestedBoostDb: Float get() = if (boostEnabled && boostAvailable) boostDbTenths / 10f else 0f

    fun withPercent(value: Int): AppVolumeState {
        val normalized = value.coerceIn(0, 100)
        return copy(
            percent = normalized,
            lastAudiblePercent = if (normalized > 0) normalized else lastAudiblePercent.coerceIn(1, 100),
        )
    }

    fun withBoost(enabled: Boolean): AppVolumeState = if (enabled && boostAvailable) {
        copy(boostEnabled = true, boostDbTenths = if (boostEnabled) boostDbTenths else DEFAULT_BOOST_DB_TENTHS,
            actualBoostDb = null)
    } else {
        copy(boostEnabled = false, boostDbTenths = 0, actualBoostDb = null)
    }

    fun withBoostDbTenths(value: Int): AppVolumeState =
        copy(boostDbTenths = if (boostEnabled && boostAvailable) value.coerceIn(0, MAX_BOOST_DB_TENTHS) else 0,
            actualBoostDb = null)

    fun muteToggleTarget(): Int = if (percent == 0) lastAudiblePercent.coerceIn(1, 100) else 0

    companion object {
        const val DEFAULT_BOOST_DB_TENTHS = 30 // +3.0 dB on explicit enable, never restored automatically.
        const val MAX_BOOST_DB_TENTHS = 60 // +6.0 dB experimental ceiling.
    }
}

object AppVolumeBus {
    private val mutableState = MutableStateFlow(AppVolumeState())
    val state = mutableState.asStateFlow()
    internal fun publish(value: AppVolumeState) { mutableState.value = value }
}
