package com.goroyattemiyo.wms.playback

import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class VisualizerMode(val id: String, val displayName: String) {
    RAINBOW_RING("rainbow-ring", "ハイパートンネル"),
    OSCILLOSCOPE("oscilloscope", "フォスファー・リサージュ"),
    SPECTRUM_CITY("spectrum-city", "スペクトラムシティ"),
    NEON_TUNNEL("neon-tunnel", "グリフレイン"),
    KALEIDO("kaleido", "ストレンジアトラクター"),
    PARTICLES("particles", "ボロノイ・シャーズ"),
    EMBLEM("emblem", "エンブレムリアクター"),
    PULSE("pulse", "リキッドメタボール"),
    ORBIT("orbit", "フローフィールド"),
    BARS("bars", "ワイヤーフレーム地形"),
    WAVE("wave", "スペクトログラム滝"),
    MINIMAL("minimal", "ミニマル"),
    ;

    companion object {
        /** Existing persisted IDs remain valid; no DataStore migration is needed. */
        fun fromPersistedId(id: String?): VisualizerMode = entries.firstOrNull { it.id == id } ?: EMBLEM
    }
}

data class AudioAnalysisFrame(
    val rms: Float = 0f,
    val peak: Float = 0f,
    val normalizedLevel: Float = 0f,
    val phase: Float = 0f,
    val waveform: FloatArray = FloatArray(0),
    val leftWaveform: FloatArray = FloatArray(0),
    val rightWaveform: FloatArray = FloatArray(0),
    val fftBins: FloatArray = FloatArray(0),
    val bass: Float = 0f,
    val lowMid: Float = 0f,
    val mid: Float = 0f,
    val high: Float = 0f,
    val spectralCentroid: Float = 0f,
    val spectralFlux: Float = 0f,
    val onsetStrength: Float = 0f,
    val animationTimeSeconds: Float = 0f,
    val sampleRateHz: Int = 0,
    val channelCount: Int = 0,
) {
    val signedWaveform: FloatArray get() = waveform
    val spectrum: FloatArray get() = fftBins
}

/** Boundary between the service-owned PCM analyzer and native visual renderers. */
interface AudioAnalysisDataSource {
    val frames: Flow<AudioAnalysisFrame>
}

data class VisualizerRenderState(
    val values: FloatArray,
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
            VisualizerMode.RAINBOW_RING -> 24
            VisualizerMode.OSCILLOSCOPE -> 32
            VisualizerMode.SPECTRUM_CITY -> 20
            VisualizerMode.NEON_TUNNEL -> 18
            VisualizerMode.KALEIDO -> 16
            VisualizerMode.PARTICLES -> 24
            VisualizerMode.BARS -> 12
            VisualizerMode.WAVE -> 20
            VisualizerMode.ORBIT -> 8
            else -> 1
        }
        val values = FloatArray(count) { index ->
            val angle = phase * 2f * PI.toFloat() + index * PI.toFloat() / count
            val source = when (mode) {
                VisualizerMode.OSCILLOSCOPE -> frame.waveform
                VisualizerMode.WAVE, VisualizerMode.SPECTRUM_CITY, VisualizerMode.BARS -> frame.fftBins
                else -> FloatArray(0)
            }
            val sampleIndex = index * source.size.coerceAtLeast(1) / count
            source.getOrNull(sampleIndex)?.let { sample ->
                if (mode == VisualizerMode.OSCILLOSCOPE) sample * 0.5f + 0.5f else sample
            }
                ?: (0.2f + abs(sin(angle)) * 0.8f * level).coerceIn(0f, 1f)
        }
        val emphasis = when (mode) {
            VisualizerMode.RAINBOW_RING -> 0.55f + level * 0.4f
            VisualizerMode.OSCILLOSCOPE -> 0.45f + level * 0.4f
            VisualizerMode.SPECTRUM_CITY -> 0.5f + level * 0.45f
            VisualizerMode.NEON_TUNNEL -> 0.5f + level * 0.35f
            VisualizerMode.KALEIDO -> 0.55f + level * 0.35f
            VisualizerMode.PARTICLES -> 0.45f + level * 0.5f
            VisualizerMode.MINIMAL -> 0.15f
            VisualizerMode.EMBLEM -> 0.55f
            VisualizerMode.PULSE -> 0.65f + 0.25f * sin(phase * 2f * PI.toFloat())
            else -> 0.75f
        }.coerceIn(0f, 1f)
        return VisualizerRenderState(values, emphasis)
    }
}
