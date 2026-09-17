package com.goroyattemiyo.wms.playback

import kotlin.math.ln
import kotlin.math.roundToInt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Gain is stored in millibels (100 mB = 1 dB), never in device-specific band indices. */
data class SoundPoint(val frequencyHz: Int, val gainMb: Int)

data class SoundPreset(
    val id: String,
    val name: String,
    val points: List<SoundPoint>,
    val builtIn: Boolean = false,
)

data class SoundBand(val frequencyHz: Int, val gainMb: Int)

data class SoundEqState(
    val supported: Boolean = false,
    val enabled: Boolean = false,
    val bands: List<SoundBand> = emptyList(),
    val minGainMb: Int = -600,
    val maxGainMb: Int = 600,
    val selectedPresetId: String? = "builtin:flat",
    val customPresets: List<SoundPreset> = emptyList(),
    val message: String = "音声セッションを確認中です。",
) {
    val hasPositiveGain: Boolean get() = enabled && bands.any { it.gainMb > 0 }
}

object SoundEqBus {
    private val mutableState = MutableStateFlow(SoundEqState())
    val state = mutableState.asStateFlow()
    internal fun publish(value: SoundEqState) { mutableState.value = value }
}

object SoundPresets {
    private val centers = listOf(60, 230, 910, 3600, 14000)

    private fun builtIn(id: String, name: String, levelsDb: List<Int>): SoundPreset =
        SoundPreset("builtin:$id", name, centers.zip(levelsDb).map { (hz, db) -> SoundPoint(hz, db * 100) }, true)

    val builtIns = listOf(
        builtIn("flat", "フラット", listOf(0, 0, 0, 0, 0)),
        builtIn("jazz", "ジャズ", listOf(1, 1, 0, 1, 1)),
        builtIn("rock", "ロック", listOf(3, 1, -1, 2, 3)),
        builtIn("pop", "ポップス", listOf(1, 2, 1, 2, 1)),
        builtIn("classical", "クラシック", listOf(1, 0, 0, 1, 2)),
        builtIn("vocal", "ボーカル", listOf(-2, 0, 3, 2, 1)),
        builtIn("bass", "低音重視", listOf(4, 3, 0, -1, -1)),
        builtIn("treble", "高音重視", listOf(-1, -1, 0, 3, 4)),
    )

    fun validName(raw: String): String? = raw.trim().takeIf { it.isNotEmpty() && it.length <= 40 }

    fun gainAt(preset: SoundPreset, hz: Int, minMb: Int, maxMb: Int): Int {
        val points = preset.points.sortedBy { it.frequencyHz }
        if (points.isEmpty()) return 0.coerceIn(minMb, maxMb)
        if (hz <= points.first().frequencyHz) return points.first().gainMb.coerceIn(minMb, maxMb)
        if (hz >= points.last().frequencyHz) return points.last().gainMb.coerceIn(minMb, maxMb)
        val upper = points.indexOfFirst { hz <= it.frequencyHz }
        val left = points[upper - 1]
        val right = points[upper]
        val denominator = ln(right.frequencyHz.toDouble()) - ln(left.frequencyHz.toDouble())
        val proportion = if (denominator > 0.0) (ln(hz.toDouble()) - ln(left.frequencyHz.toDouble())) / denominator else 0.0
        return (left.gainMb + (right.gainMb - left.gainMb) * proportion).roundToInt().coerceIn(minMb, maxMb)
    }

    fun uniqueName(raw: String, existing: List<SoundPreset>, exceptId: String? = null): String? {
        val name = validName(raw) ?: return null
        return name.takeUnless { candidate ->
            (builtIns + existing).any { it.id != exceptId && it.name.equals(candidate, ignoreCase = true) }
        }
    }
}
