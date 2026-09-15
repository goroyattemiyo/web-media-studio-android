package com.goroyattemiyo.wms.ui.player.visualizer

import android.graphics.Paint
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.playback.VisualizerRendererType
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Static representative preview: Appearance never starts twelve live render loops. */
@Composable
fun VisualizerStaticPreview(
    mode: VisualizerMode,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    val paint = remember {
        Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = android.graphics.Typeface.MONOSPACE
        }
    }
    Canvas(modifier) {
        when (mode.rendererType) {
            VisualizerRendererType.LIQUID_METABALLS -> repeat(5) { index ->
                val angle = index * 2f * PI.toFloat() / 5f
                drawCircle(
                    color = (if (index % 2 == 0) primary else secondary).copy(alpha = 0.55f),
                    radius = size.minDimension * 0.2f,
                    center = Offset(center.x + cos(angle) * size.width * 0.12f, center.y + sin(angle) * size.height * 0.14f),
                )
            }
            VisualizerRendererType.FLOW_FIELD -> repeat(7) { row ->
                val path = Path().apply {
                    moveTo(0f, size.height * (row + 1f) / 8f)
                    quadraticTo(size.width * 0.45f, size.height * (row % 3 + 1f) / 4f, size.width, size.height * (row + 1f) / 8f)
                }
                drawPath(path, lerpColor(primary, secondary, row / 6f), style = Stroke(1.5f))
            }
            VisualizerRendererType.VORONOI_SHARDS -> {
                repeat(5) { index ->
                    val x = size.width * (0.15f + index * 0.18f)
                    drawLine(primary, Offset(x, 0f), Offset(size.width - x * 0.35f, size.height), 1.5f)
                }
                repeat(3) { index ->
                    val y = size.height * (index + 1f) / 4f
                    drawLine(secondary, Offset(0f, y), Offset(size.width, size.height - y * 0.6f), 1.2f)
                }
            }
            VisualizerRendererType.SPECTROGRAM_WATERFALL -> repeat(6) { row ->
                repeat(10) { column ->
                    val strength = previewFract(sin(row * 7.1f + column * 3.7f) * 91.3f)
                    drawRect(
                        color = lerpColor(Color(0xFF07101A), if (strength > 0.55f) primary else secondary, strength),
                        topLeft = Offset(column * size.width / 10f, row * size.height / 6f),
                        size = Size(size.width / 10f + 1f, size.height / 6f + 1f),
                    )
                }
            }
            VisualizerRendererType.WIREFRAME_TERRAIN -> repeat(6) { depth ->
                val path = Path()
                repeat(10) { column ->
                    val x = column * size.width / 9f
                    val y = size.height * (0.2f + depth * 0.13f) - sin(column * 1.4f + depth) * size.height * 0.08f
                    if (column == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, lerpColor(secondary, primary, depth / 5f), style = Stroke(1.2f))
            }
            VisualizerRendererType.GLYPH_RAIN -> {
                paint.textSize = size.height * 0.25f
                repeat(5) { column ->
                    repeat(3) { row ->
                        paint.color = (if ((column + row) % 2 == 0) primary else secondary).copy(alpha = 0.45f + row * 0.2f).toArgb()
                        drawContext.canvas.nativeCanvas.drawText(
                            PREVIEW_GLYPHS[(column + row) % PREVIEW_GLYPHS.size],
                            size.width * (column + 0.5f) / 5f,
                            size.height * (row + 0.8f) / 3f,
                            paint,
                        )
                    }
                }
            }
            VisualizerRendererType.PHOSPHOR_LISSAJOUS -> {
                val path = Path()
                repeat(80) { index ->
                    val angle = index * 2f * PI.toFloat() / 79f
                    val x = center.x + sin(angle * 3f) * size.width * 0.34f
                    val y = center.y + sin(angle * 2f + 0.6f) * size.height * 0.36f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, primary.copy(alpha = 0.3f), style = Stroke(6f))
                drawPath(path, secondary, style = Stroke(1.5f))
            }
            VisualizerRendererType.HYPER_TUNNEL -> repeat(5) { index ->
                drawCircle(lerpColor(primary, secondary, index / 4f), size.minDimension * (0.08f + index * 0.08f), style = Stroke(2f))
            }
            VisualizerRendererType.SPECTRUM_CITY -> repeat(9) { index ->
                val height = size.height * (0.18f + previewFract(sin(index * 7.2f) * 9.1f) * 0.68f)
                drawRect(if (index % 2 == 0) primary else secondary, Offset(index * size.width / 9f, size.height - height), Size(size.width / 12f, height))
            }
            VisualizerRendererType.STRANGE_ATTRACTOR -> {
                val path = Path()
                var x = 0.2f
                var y = 0.3f
                repeat(90) { index ->
                    val nextX = sin(y * 2.1f) * 0.38f + 0.5f
                    val nextY = sin(x * 3.2f + 1f) * 0.38f + 0.5f
                    if (index == 0) path.moveTo(x * size.width, y * size.height) else path.lineTo(x * size.width, y * size.height)
                    x = nextX
                    y = nextY
                }
                drawPath(path, secondary, style = Stroke(1.2f))
            }
            VisualizerRendererType.EMBLEM_REACTOR -> {
                drawCircle(primary, size.minDimension * 0.22f, style = Stroke(3f))
                drawCircle(secondary.copy(alpha = 0.65f), size.minDimension * 0.36f, style = Stroke(1.5f))
            }
            VisualizerRendererType.MINIMAL -> drawLine(primary, Offset(size.width * 0.2f, center.y), Offset(size.width * 0.8f, center.y), 2f)
        }
    }
}

private fun previewFract(value: Float): Float = value - kotlin.math.floor(value)
private val PREVIEW_GLYPHS = arrayOf("W", "M", "S", "0", "1", "+", "◇", "・")
