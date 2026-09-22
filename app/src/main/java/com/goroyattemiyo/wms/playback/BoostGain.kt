package com.goroyattemiyo.wms.playback

import kotlin.math.pow

/** PCM amplitude multiplier for a user-requested boost in 0.1 dB units. */
internal object BoostGain {
    fun amplitudeForTenths(dbTenths: Int): Float =
        10.0.pow(dbTenths.coerceIn(0, AppVolumeState.MAX_BOOST_DB_TENTHS) / 200.0).toFloat()
}
