package com.goroyattemiyo.wms.ui.player.visualizer

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.Stroke
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
internal fun LiquidMetaballsRenderer(
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        LiquidMetaballsShader(frame, phase, primary, secondary, modifier)
    } else {
        LiquidMetaballsCanvasFallback(frame, phase, primary, secondary, modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun LiquidMetaballsShader(
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val shader = remember { RuntimeShader(LIQUID_METABALLS_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("time", phase * 2f * PI.toFloat())
        shader.setFloatUniform("bass", frame.bass)
        shader.setFloatUniform("mid", frame.mid)
        shader.setFloatUniform("high", frame.high)
        shader.setFloatUniform("onset", frame.onsetStrength)
        shader.setFloatUniform("colorA", primary.red, primary.green, primary.blue, 1f)
        shader.setFloatUniform("colorB", secondary.red, secondary.green, secondary.blue, 1f)
        drawRect(brush = brush)
    }
}

@Composable
private fun LiquidMetaballsCanvasFallback(
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val time = phase * 2f * PI.toFloat()
        val radius = size.minDimension * (0.16f + frame.bass * 0.09f)
        repeat(7) { index ->
            val angle = index * 2f * PI.toFloat() / 7f + time * (0.25f + index * 0.015f)
            val distance = size.minDimension * (0.08f + index % 3 * 0.045f + frame.onsetStrength * 0.05f)
            val center = Offset(
                center.x + cos(angle) * distance,
                center.y + sin(angle * (1.15f + frame.mid * 0.2f)) * distance,
            )
            drawCircle(
                brush = Brush.radialGradient(
                    listOf(
                        (if (index % 2 == 0) primary else secondary).copy(alpha = 0.75f),
                        primary.copy(alpha = 0.12f),
                        Color.Transparent,
                    ),
                    center = center,
                    radius = radius * 1.4f,
                ),
                radius = radius * 1.4f,
                center = center,
            )
        }
    }
}

@Composable
internal fun VoronoiShardsRenderer(
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        VoronoiShader(frame, phase, primary, secondary, modifier)
    } else {
        VoronoiCanvasFallback(frame, phase, primary, secondary, modifier)
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
@Composable
private fun VoronoiShader(
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    val shader = remember { RuntimeShader(VORONOI_SHADER) }
    val brush = remember(shader) { ShaderBrush(shader) }
    Canvas(modifier) {
        shader.setFloatUniform("resolution", size.width, size.height)
        shader.setFloatUniform("flux", frame.spectralFlux.coerceIn(0f, 1f))
        shader.setFloatUniform("onset", frame.onsetStrength)
        shader.setFloatUniform("colorA", primary.red, primary.green, primary.blue, 1f)
        shader.setFloatUniform("colorB", secondary.red, secondary.green, secondary.blue, 1f)
        repeat(VORONOI_SEED_COUNT) { index ->
            val band = frame.fftBins.getOrElse(index * frame.fftBins.size.coerceAtLeast(1) / VORONOI_SEED_COUNT) { 0f }
            val angle = index * 2f * PI.toFloat() / VORONOI_SEED_COUNT + phase * 2f * PI.toFloat() * (if (index % 2 == 0) 0.08f else -0.06f)
            val radius = 0.18f + (index % 3) * 0.08f + band * 0.1f + frame.onsetStrength * 0.04f
            shader.setFloatUniform(
                "seed$index",
                0.5f + cos(angle) * radius,
                0.5f + sin(angle * 1.17f) * radius,
            )
        }
        drawRect(brush = brush)
    }
}

@Composable
private fun VoronoiCanvasFallback(
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val seeds = List(VORONOI_SEED_COUNT) { index ->
            val angle = index * 2f * PI.toFloat() / VORONOI_SEED_COUNT + phase * 0.4f
            val radius = size.minDimension * (0.18f + frame.fftBins.getOrElse(index) { 0f } * 0.12f)
            Offset(center.x + cos(angle) * radius, center.y + sin(angle * 1.13f) * radius)
        }
        seeds.forEachIndexed { index, seed ->
            seeds.drop(index + 1).sortedBy { (it - seed).getDistanceSquared() }.take(3).forEach { neighbor ->
                val midpoint = Offset((seed.x + neighbor.x) * 0.5f, (seed.y + neighbor.y) * 0.5f)
                val direction = neighbor - seed
                val normal = Offset(-direction.y, direction.x)
                val length = normal.getDistance().coerceAtLeast(1f)
                val extent = size.minDimension * (0.12f + frame.onsetStrength * 0.04f)
                drawLine(
                    color = if (index % 2 == 0) primary else secondary,
                    start = midpoint - normal / length * extent,
                    end = midpoint + normal / length * extent,
                    strokeWidth = 1.5f + frame.spectralFlux * 5f,
                )
            }
            drawCircle(primary.copy(alpha = 0.4f), 5f + frame.onsetStrength * 7f, seed, style = Stroke(2f))
        }
    }
}

private const val VORONOI_SEED_COUNT = 8

private const val LIQUID_METABALLS_SHADER = """
uniform float2 resolution;
uniform float time;
uniform float bass;
uniform float mid;
uniform float high;
uniform float onset;
uniform half4 colorA;
uniform half4 colorB;

float influence(float2 p, float2 c, float radius) {
    float2 d = p - c;
    return radius * radius / max(dot(d, d), 0.002);
}

half4 main(float2 fragCoord) {
    float2 uv = (fragCoord * 2.0 - resolution) / min(resolution.x, resolution.y);
    float wobble = 0.10 + mid * 0.13;
    float split = onset * 0.16;
    float radius = 0.22 + bass * 0.13;
    float field = 0.0;
    field += influence(uv, float2(sin(time * 0.71) * wobble - split, cos(time * 0.53) * wobble), radius);
    field += influence(uv, float2(cos(time * 0.47) * 0.30 + split, sin(time * 0.83) * 0.18), radius * 0.78);
    field += influence(uv, float2(sin(time * 0.39 + 2.2) * 0.32, cos(time * 0.67 + 1.1) * 0.24), radius * 0.70);
    field += influence(uv, float2(cos(time * 0.58 + 3.7) * 0.20, sin(time * 0.44 + 2.8) * 0.32), radius * 0.64);
    float detail = sin((uv.x * 1.3 + uv.y) * 38.0 + time * 2.0) * high * 0.045;
    float body = smoothstep(0.82 + detail, 1.04 + detail, field);
    float edge = smoothstep(0.67, 0.91, field) - smoothstep(1.02, 1.25, field);
    half mixAmount = half(clamp(0.5 + uv.y * 0.35 + mid * 0.2, 0.0, 1.0));
    half3 color = mix(colorA.rgb, colorB.rgb, mixAmount);
    color *= half(0.25 + body * 0.75 + edge * (0.45 + high * 0.35));
    return half4(color, half(clamp(body + edge, 0.0, 1.0)));
}
"""

private const val VORONOI_SHADER = """
uniform float2 resolution;
uniform float2 seed0;
uniform float2 seed1;
uniform float2 seed2;
uniform float2 seed3;
uniform float2 seed4;
uniform float2 seed5;
uniform float2 seed6;
uniform float2 seed7;
uniform float flux;
uniform float onset;
uniform half4 colorA;
uniform half4 colorB;

half4 main(float2 fragCoord) {
    float2 uv = fragCoord / resolution;
    float d0 = distance(uv, seed0); float nearest = d0; float cell = 0.0;
    float d1 = distance(uv, seed1); if (d1 < nearest) { nearest = d1; cell = 1.0; }
    float d2 = distance(uv, seed2); if (d2 < nearest) { nearest = d2; cell = 2.0; }
    float d3 = distance(uv, seed3); if (d3 < nearest) { nearest = d3; cell = 3.0; }
    float d4 = distance(uv, seed4); if (d4 < nearest) { nearest = d4; cell = 4.0; }
    float d5 = distance(uv, seed5); if (d5 < nearest) { nearest = d5; cell = 5.0; }
    float d6 = distance(uv, seed6); if (d6 < nearest) { nearest = d6; cell = 6.0; }
    float d7 = distance(uv, seed7); if (d7 < nearest) { nearest = d7; cell = 7.0; }
    float second = 2.0;
    if (d0 > nearest + 0.0001) second = min(second, d0);
    if (d1 > nearest + 0.0001) second = min(second, d1);
    if (d2 > nearest + 0.0001) second = min(second, d2);
    if (d3 > nearest + 0.0001) second = min(second, d3);
    if (d4 > nearest + 0.0001) second = min(second, d4);
    if (d5 > nearest + 0.0001) second = min(second, d5);
    if (d6 > nearest + 0.0001) second = min(second, d6);
    if (d7 > nearest + 0.0001) second = min(second, d7);
    float boundary = 1.0 - smoothstep(0.006 + flux * 0.01, 0.022 + onset * 0.016, second - nearest);
    float cellTone = fract(sin(cell * 17.17 + 1.3) * 43758.5453);
    half3 base = mix(colorA.rgb, colorB.rgb, half(cellTone));
    float interior = 0.22 + 0.28 * (1.0 - nearest) + cellTone * 0.13;
    half3 color = base * half(interior + boundary * (0.75 + onset * 0.25));
    return half4(color, 1.0);
}
"""
