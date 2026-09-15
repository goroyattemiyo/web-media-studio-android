package com.goroyattemiyo.wms.playback

import kotlinx.coroutines.flow.Flow
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class VisualizerRendererType {
    HYPER_TUNNEL,
    PHOSPHOR_LISSAJOUS,
    SPECTRUM_CITY,
    GLYPH_RAIN,
    STRANGE_ATTRACTOR,
    VORONOI_SHARDS,
    EMBLEM_REACTOR,
    LIQUID_METABALLS,
    FLOW_FIELD,
    WIREFRAME_TERRAIN,
    SPECTROGRAM_WATERFALL,
    MINIMAL,
}

enum class VisualizerImplementationStatus { V3_IMPLEMENTED, V2_RETAINED }

enum class VisualizerMode(
    val persistedId: String,
    val displayName: String,
    val rendererType: VisualizerRendererType,
    val implementationStatus: VisualizerImplementationStatus,
) {
    RAINBOW_RING("rainbow-ring", "ハイパートンネル", VisualizerRendererType.HYPER_TUNNEL, VisualizerImplementationStatus.V2_RETAINED),
    OSCILLOSCOPE("oscilloscope", "フォスファー・リサージュ", VisualizerRendererType.PHOSPHOR_LISSAJOUS, VisualizerImplementationStatus.V3_IMPLEMENTED),
    SPECTRUM_CITY("spectrum-city", "スペクトラムシティ", VisualizerRendererType.SPECTRUM_CITY, VisualizerImplementationStatus.V2_RETAINED),
    NEON_TUNNEL("neon-tunnel", "グリフレイン", VisualizerRendererType.GLYPH_RAIN, VisualizerImplementationStatus.V3_IMPLEMENTED),
    KALEIDO("kaleido", "ストレンジアトラクター", VisualizerRendererType.STRANGE_ATTRACTOR, VisualizerImplementationStatus.V2_RETAINED),
    PARTICLES("particles", "ボロノイ・シャーズ", VisualizerRendererType.VORONOI_SHARDS, VisualizerImplementationStatus.V3_IMPLEMENTED),
    EMBLEM("emblem", "エンブレムリアクター", VisualizerRendererType.EMBLEM_REACTOR, VisualizerImplementationStatus.V2_RETAINED),
    PULSE("pulse", "リキッドメタボール", VisualizerRendererType.LIQUID_METABALLS, VisualizerImplementationStatus.V3_IMPLEMENTED),
    ORBIT("orbit", "フローフィールド", VisualizerRendererType.FLOW_FIELD, VisualizerImplementationStatus.V3_IMPLEMENTED),
    BARS("bars", "ワイヤーフレーム地形", VisualizerRendererType.WIREFRAME_TERRAIN, VisualizerImplementationStatus.V3_IMPLEMENTED),
    WAVE("wave", "スペクトログラム滝", VisualizerRendererType.SPECTROGRAM_WATERFALL, VisualizerImplementationStatus.V3_IMPLEMENTED),
    MINIMAL("minimal", "ミニマル", VisualizerRendererType.MINIMAL, VisualizerImplementationStatus.V2_RETAINED),
    ;

    val id: String get() = persistedId

    companion object {
        /** Existing persisted IDs remain valid; no DataStore migration is needed. */
        fun fromPersistedId(id: String?): VisualizerMode = entries.firstOrNull { it.persistedId == id } ?: EMBLEM
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
