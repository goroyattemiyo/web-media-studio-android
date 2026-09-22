package com.goroyattemiyo.wms.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class BoostGainTest {
    @Test fun convertsDecibelsToAmplitudeRatherThanPercentage() {
        assertEquals(1f, BoostGain.amplitudeForTenths(0), 0.0001f)
        assertEquals(1.41254f, BoostGain.amplitudeForTenths(30), 0.0001f)
        assertEquals(1.99526f, BoostGain.amplitudeForTenths(60), 0.0001f)
    }

    @Test fun limitsRequestedBoostToExperimentalCeiling() {
        assertEquals(1f, BoostGain.amplitudeForTenths(-100), 0.0001f)
        assertEquals(BoostGain.amplitudeForTenths(60), BoostGain.amplitudeForTenths(1000), 0.0001f)
    }
}
