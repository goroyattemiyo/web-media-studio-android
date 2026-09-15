package com.goroyattemiyo.wms.playback

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
}
