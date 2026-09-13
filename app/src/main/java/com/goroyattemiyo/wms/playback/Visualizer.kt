package com.goroyattemiyo.wms.playback

import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class VisualizerMode(val id: String) {
    EMBLEM("emblem"),
    PULSE("pulse"),
    ORBIT("orbit"),
    BARS("bars"),
    WAVE("wave"),
    MINIMAL("minimal"),
}

data class AudioAnalysisFrame(
    val normalizedLevel: Float,
    val phase: Float,
)

/** Boundary for a future player/audio-effect backed analyzer. */
interface AudioAnalysisDataSource {
    val frames: Flow<AudioAnalysisFrame>
}

data class VisualizerRenderState(
    val values: List<Float>,
    val emphasis: Float,
)

fun interface VisualizerRenderer {
    fun render(mode: VisualizerMode, frame: AudioAnalysisFrame): VisualizerRenderState
}

object LightweightVisualizerRenderer : VisualizerRenderer {
    override fun render(mode: VisualizerMode, frame: AudioAnalysisFrame): VisualizerRenderState {
        val level = frame.normalizedLevel.coerceIn(0f, 1f)
        val phase = frame.phase.coerceIn(0f, 1f)
        val count = when (mode) {
            VisualizerMode.BARS -> 12
            VisualizerMode.WAVE -> 20
            VisualizerMode.ORBIT -> 8
            else -> 1
        }
        val values = List(count) { index ->
            val angle = phase * 2f * PI.toFloat() + index * PI.toFloat() / count
            (0.2f + abs(sin(angle)) * 0.8f * level).coerceIn(0f, 1f)
        }
        val emphasis = when (mode) {
            VisualizerMode.MINIMAL -> 0.15f
            VisualizerMode.EMBLEM -> 0.55f
            VisualizerMode.PULSE -> 0.65f + 0.25f * sin(phase * 2f * PI.toFloat())
            else -> 0.75f
        }.coerceIn(0f, 1f)
        return VisualizerRenderState(values, emphasis)
    }
}
