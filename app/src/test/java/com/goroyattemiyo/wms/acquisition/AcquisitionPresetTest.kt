package com.goroyattemiyo.wms.acquisition

import org.junit.Assert.assertEquals
import org.junit.Test

class AcquisitionPresetTest {
    @Test
    fun presetIdsAreStableAndUnknownFallsBackToMp3() {
        assertEquals(
            listOf("mp3-192", "m4a-192", "video-mp4"),
            AcquisitionPreset.entries.map { it.id },
        )
        assertEquals(AcquisitionPreset.MP3_192, AcquisitionPreset.fromId("unexpected"))
    }

    @Test
    fun presetsCarryRegistrationMetadata() {
        assertEquals("AUDIO", AcquisitionPreset.M4A_192.mediaType)
        assertEquals("audio/mp4", AcquisitionPreset.M4A_192.mimeType)
        assertEquals("VIDEO", AcquisitionPreset.VIDEO_MP4.mediaType)
        assertEquals("video/mp4", AcquisitionPreset.VIDEO_MP4.mimeType)
    }
}
