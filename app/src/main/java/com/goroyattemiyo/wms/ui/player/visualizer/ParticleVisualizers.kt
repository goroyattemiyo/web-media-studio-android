package com.goroyattemiyo.wms.ui.player.visualizer

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun FlowFieldRenderer(
    frame: AudioAnalysisFrame,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val state = remember { FlowFieldState(FLOW_PARTICLE_COUNT) }
    val currentFrame by rememberUpdatedState(frame)
    var drawTick by remember { mutableIntStateOf(0) }
    LaunchedEffect(state) {
        var previousFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos == 0L) previousFrameNanos = frameNanos
                val elapsed = frameNanos - previousFrameNanos
                if (elapsed >= RENDER_INTERVAL_NANOS) {
                    state.advance(currentFrame, (elapsed / 1_000_000_000f).coerceAtMost(0.05f))
                    previousFrameNanos = frameNanos
                    drawTick++
                }
            }
        }
    }

    Canvas(modifier) {
        drawTick
        repeat(state.count) { index ->
            val x = state.x[index] * size.width
            val y = state.y[index] * size.height
            val oldX = state.previousX[index] * size.width
            val oldY = state.previousY[index] * size.height
            if (kotlin.math.abs(x - oldX) < size.width * 0.3f && kotlin.math.abs(y - oldY) < size.height * 0.3f) {
                val band = frame.fftBins.getOrElse(index % frame.fftBins.size.coerceAtLeast(1)) { 0f }
                drawLine(
                    color = lerpColor(primary, secondary, index.toFloat() / state.count)
                        .copy(alpha = 0.2f + frame.high * 0.45f + band * 0.25f),
                    start = Offset(oldX, oldY),
                    end = Offset(x, y),
                    strokeWidth = 1f + frame.bass * 2.4f,
                )
            }
        }
    }
}

private class FlowFieldState(val count: Int) {
    val x = FloatArray(count) { index -> fract(sin(index * 12.9898f) * 43_758.547f) }
    val y = FloatArray(count) { index -> fract(sin(index * 78.233f + 4.7f) * 12_345.679f) }
    val previousX = x.copyOf()
    val previousY = y.copyOf()
    private var time = 0f

    fun advance(frame: AudioAnalysisFrame, deltaSeconds: Float) {
        time += deltaSeconds
        val centroid = (frame.spectralCentroid / 12_000f).coerceIn(0f, 1f)
        repeat(count) { index ->
            previousX[index] = x[index]
            previousY[index] = y[index]
            val curl = sin(y[index] * 9f + time * (0.7f + centroid)) +
                cos(x[index] * 11f - time * (0.5f + frame.mid))
            val angle = curl * PI.toFloat() + frame.mid * 2.4f
            var velocity = 0.035f + frame.bass * 0.12f + frame.high * 0.035f
            if (frame.onsetStrength > 0.08f) velocity += frame.onsetStrength * 0.18f
            var dx = cos(angle) * velocity * deltaSeconds
            var dy = sin(angle) * velocity * deltaSeconds
            if (frame.onsetStrength > 0.08f) {
                val centerX = x[index] - 0.5f
                val centerY = y[index] - 0.5f
                dx += centerX * frame.onsetStrength * deltaSeconds * 0.3f
                dy += centerY * frame.onsetStrength * deltaSeconds * 0.3f
            }
            x[index] = wrap(x[index] + dx)
            y[index] = wrap(y[index] + dy)
        }
    }
}

@Composable
internal fun GlyphRainRenderer(
    frame: AudioAnalysisFrame,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val state = remember { GlyphRainState(GLYPH_COLUMNS) }
    val currentFrame by rememberUpdatedState(frame)
    var drawTick by remember { mutableIntStateOf(0) }
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    LaunchedEffect(state) {
        var previousFrameNanos = 0L
        while (true) {
            withFrameNanos { frameNanos ->
                if (previousFrameNanos == 0L) previousFrameNanos = frameNanos
                val elapsed = frameNanos - previousFrameNanos
                if (elapsed >= RENDER_INTERVAL_NANOS) {
                    state.advance(currentFrame, (elapsed / 1_000_000_000f).coerceAtMost(0.05f))
                    previousFrameNanos = frameNanos
                    drawTick++
                }
            }
        }
    }

    Canvas(modifier) {
        drawTick
        val nativeCanvas = drawContext.canvas.nativeCanvas
        val activeTrail = (3 + frame.mid * 7f).toInt().coerceIn(3, 10)
        val cellWidth = size.width / state.count
        paint.textSize = (cellWidth * (0.65f + frame.high * 0.18f)).coerceAtLeast(12f)
        repeat(state.count) { column ->
            repeat(activeTrail) { trail ->
                val y = (state.y[column] - trail * 0.065f).let(::wrap) * size.height
                val glyphIndex = (state.glyph[column] + trail + (frame.high * 4f).toInt()) % GLYPHS.size
                val color = lerpColor(primary, secondary, column.toFloat() / state.count)
                paint.color = color.copy(alpha = ((activeTrail - trail).toFloat() / activeTrail) * (0.25f + frame.high * 0.7f)).toArgb()
                nativeCanvas.drawText(GLYPHS[glyphIndex], (column + 0.5f) * cellWidth, y, paint)
            }
        }
        if (frame.onsetStrength > 0.08f) {
            drawCircle(
                color = secondary.copy(alpha = frame.onsetStrength * 0.55f),
                radius = size.minDimension * (0.12f + frame.onsetStrength * 0.34f),
                style = Stroke(2f + frame.onsetStrength * 6f),
            )
        }
    }
}

private class GlyphRainState(val count: Int) {
    val y = FloatArray(count) { index -> fract(index * 0.173f + 0.11f) }
    val glyph = IntArray(count) { index -> index % GLYPHS.size }
    private val speedBias = FloatArray(count) { index -> 0.72f + fract(sin(index * 31.7f) * 99.31f) * 0.8f }
    private var mutationAccumulator = 0f

    fun advance(frame: AudioAnalysisFrame, deltaSeconds: Float) {
        mutationAccumulator += deltaSeconds * (1f + frame.high * 10f)
        repeat(count) { index ->
            y[index] = wrap(y[index] + deltaSeconds * speedBias[index] * (0.14f + frame.bass * 0.42f))
            if (mutationAccumulator >= 1f || frame.onsetStrength > 0.45f) {
                glyph[index] = (glyph[index] + 1 + index % 3) % GLYPHS.size
            }
        }
        if (mutationAccumulator >= 1f) mutationAccumulator -= 1f
    }
}

internal fun lerpColor(first: Color, second: Color, amount: Float): Color = Color(
    red = first.red + (second.red - first.red) * amount.coerceIn(0f, 1f),
    green = first.green + (second.green - first.green) * amount.coerceIn(0f, 1f),
    blue = first.blue + (second.blue - first.blue) * amount.coerceIn(0f, 1f),
    alpha = first.alpha + (second.alpha - first.alpha) * amount.coerceIn(0f, 1f),
)

private fun wrap(value: Float): Float = when {
    value < 0f -> value + 1f
    value >= 1f -> value - 1f
    else -> value
}

private fun fract(value: Float): Float = value - kotlin.math.floor(value)

private val GLYPHS = arrayOf("W", "M", "S", "0", "1", "+", "◇", "・")
private const val FLOW_PARTICLE_COUNT = 144
private const val GLYPH_COLUMNS = 18
private const val RENDER_INTERVAL_NANOS = 33_333_333L
