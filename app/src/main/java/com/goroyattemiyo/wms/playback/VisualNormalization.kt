package com.goroyattemiyo.wms.playback

import kotlin.math.log10
import kotlin.math.pow

/**
 * Converts bounded analysis magnitudes into a renderer-friendly perceptual range.
 * This is deliberately stateless: it lifts quiet material without AGC pumping and leaves
 * the analysis worker's feature extraction based on the unmodified FFT values.
 */
internal object VisualNormalization {
    fun magnitude(raw: Float): Float = shaped(raw, MAGNITUDE_FLOOR_DB, MAGNITUDE_GAIN)

    fun level(raw: Float): Float = shaped(raw, LEVEL_FLOOR_DB, LEVEL_GAIN)

    fun transient(raw: Float): Float = ((raw - TRANSIENT_FLOOR) / (1f - TRANSIENT_FLOOR))
        .coerceIn(0f, 1f)
        .let { (it * TRANSIENT_GAIN).coerceAtMost(1f) }
        .pow(TRANSIENT_GAMMA)

    private fun shaped(raw: Float, floorDb: Float, gain: Float): Float {
        val db = (20f * log10(raw.coerceAtLeast(MIN_MAGNITUDE))).coerceAtMost(0f)
        val normalized = ((db - floorDb) / -floorDb).coerceIn(0f, 1f)
        // Soft limiting keeps loud masters from pinning the surface at 100%.
        val gained = (normalized * gain) / (1f + normalized * (gain - 1f))
        return gained.pow(RESPONSE_GAMMA)
    }

    private const val MIN_MAGNITUDE = 0.000001f
    private const val MAGNITUDE_FLOOR_DB = -48f
    private const val LEVEL_FLOOR_DB = -54f
    private const val MAGNITUDE_GAIN = 1.22f
    private const val LEVEL_GAIN = 1.12f
    private const val RESPONSE_GAMMA = 0.58f
    private const val TRANSIENT_FLOOR = 0.012f
    private const val TRANSIENT_GAIN = 2.4f
    private const val TRANSIENT_GAMMA = 0.5f
}
