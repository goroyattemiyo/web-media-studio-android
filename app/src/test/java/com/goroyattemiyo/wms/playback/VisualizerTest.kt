package com.goroyattemiyo.wms.playback

import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class VisualizerTest {
    @Test
    fun everyModeProducesBoundedRenderValues() {
        VisualizerMode.entries.forEach { mode ->
            val state = LightweightVisualizerRenderer.render(
                mode,
                AudioAnalysisFrame(normalizedLevel = 0.8f, phase = 0.35f),
            )
            assertTrue(state.values.isNotEmpty())
            assertTrue(state.values.all { it in 0f..1f })
            assertTrue(state.emphasis in 0f..1f)
        }
    }

    @Test
    fun exposesEveryCanonicalVisualizerId() {
        assertEquals(
            setOf(
                "rainbow-ring", "oscilloscope", "spectrum-city", "neon-tunnel", "kaleido",
                "particles", "pulse", "orbit", "bars", "wave", "emblem", "minimal",
            ),
            VisualizerMode.entries.map { it.id }.toSet(),
        )
    }

    @UnstableApi
    @Test
    fun pcm16AnalysisPublishesBoundedReactiveFrame() = runTest {
        val sink = PcmAnalysisBufferSink()
        sink.flush(48_000, 2, C.ENCODING_PCM_16BIT)
        val pcm = ByteBuffer.allocate(64 * Short.SIZE_BYTES).order(ByteOrder.LITTLE_ENDIAN)
        repeat(64) { index -> pcm.putShort(if (index % 2 == 0) 16_000 else -16_000) }
        pcm.flip()

        sink.handleBuffer(pcm)

        val frame = PcmAudioAnalysisBus.frames.first()
        assertTrue(frame.normalizedLevel in 0f..1f)
        assertTrue(frame.normalizedLevel > 0f)
        assertEquals(32, frame.waveform.size)
        assertEquals(20, frame.spectrum.size)
        assertTrue(frame.waveform.all { it in 0f..1f })
        assertTrue(frame.spectrum.all { it in 0f..1f })
    }
}
