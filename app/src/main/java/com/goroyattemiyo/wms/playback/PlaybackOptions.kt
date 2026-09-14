package com.goroyattemiyo.wms.playback

import androidx.media3.common.Player

object PlaybackSpeed {
    val allowed = listOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f)

    fun normalize(value: Float): Float = allowed.minBy { kotlin.math.abs(it - value) }

    fun label(value: Float): String = "${normalize(value).toString().removeSuffix(".0")}x"
}

fun toggledShuffleEnabled(current: Boolean): Boolean = !current

enum class RepeatOption(val media3Mode: Int, val label: String) {
    OFF(Player.REPEAT_MODE_OFF, "リピート オフ"),
    ALL(Player.REPEAT_MODE_ALL, "リピート 全体"),
    ONE(Player.REPEAT_MODE_ONE, "リピート 1曲");

    companion object {
        fun fromMedia3(mode: Int): RepeatOption = entries.firstOrNull { it.media3Mode == mode } ?: OFF
    }
}

data class AbLoopState(
    val pointAMs: Long? = null,
    val pointBMs: Long? = null,
    val enabled: Boolean = false,
) {
    val isValid: Boolean get() = pointAMs != null && pointBMs != null && pointAMs < pointBMs

    fun setA(positionMs: Long): AbLoopState = copy(
        pointAMs = positionMs.coerceAtLeast(0L),
        pointBMs = null,
        enabled = false,
    )

    fun setB(positionMs: Long): AbLoopState =
        if (pointAMs != null && positionMs > pointAMs) copy(pointBMs = positionMs, enabled = false) else this

    fun toggle(): AbLoopState = if (isValid) copy(enabled = !enabled) else copy(enabled = false)

    fun clear(): AbLoopState = AbLoopState()

    fun shouldLoopAt(positionMs: Long): Boolean = enabled && isValid && positionMs >= pointBMs!!
}

object PlaybackCommand {
    const val SET_AB_A = "com.goroyattemiyo.wms.playback.SET_AB_A"
    const val SET_AB_B = "com.goroyattemiyo.wms.playback.SET_AB_B"
    const val TOGGLE_AB = "com.goroyattemiyo.wms.playback.TOGGLE_AB"
    const val CLEAR_AB = "com.goroyattemiyo.wms.playback.CLEAR_AB"
}
