package com.goroyattemiyo.wms.ui.player

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.ui.PlayerView
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import com.goroyattemiyo.wms.playback.LightweightVisualizerRenderer
import com.goroyattemiyo.wms.playback.PcmAudioAnalysisBus
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.ui.media.MediaArtwork
import com.goroyattemiyo.wms.ui.media.toMediaPresentation
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

@Composable
fun MediaVisualSurface(
    media: MediaEntity,
    controller: MediaController?,
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    modifier: Modifier = Modifier,
) {
    val presentation = media.toMediaPresentation()
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (presentation.isVideo && controller != null) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        useController = false
                        player = controller
                    }
                },
                update = { it.player = controller },
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            if (visualizerMode == VisualizerMode.EMBLEM) {
                MediaArtwork(
                    media = presentation,
                    contentDescription = presentation.title,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.38f)))
            } else {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.92f)))
            }
            AudioVisualizer(
                visualizerMode = visualizerMode,
                reducedMotion = reducedMotion,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@Composable
private fun AudioVisualizer(
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    modifier: Modifier,
) {
    val analysisFrame by PcmAudioAnalysisBus.frames.collectAsStateWithLifecycle(
        initialValue = AudioAnalysisFrame(),
    )
    val phase = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "wms-visualizer")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
            label = "visualizer-phase",
        )
        animatedPhase
    }
    val renderState = LightweightVisualizerRenderer.render(
        visualizerMode,
        analysisFrame.copy(phase = phase),
    )
    val primary = MaterialTheme.colorScheme.primary
    val secondary = MaterialTheme.colorScheme.secondary
    val history = remember(visualizerMode) { mutableStateListOf<FloatArray>() }
    androidx.compose.runtime.LaunchedEffect(analysisFrame.spectrum) {
        if (visualizerMode == VisualizerMode.WAVE && analysisFrame.spectrum.isNotEmpty()) {
            history.add(analysisFrame.spectrum)
            while (history.size > 18) history.removeAt(0)
        }
    }

    Canvas(modifier) {
        val values = renderState.values
        when (visualizerMode) {
            VisualizerMode.MINIMAL -> drawLine(primary.copy(alpha = 0.7f), androidx.compose.ui.geometry.Offset(size.width * .2f, center.y), androidx.compose.ui.geometry.Offset(size.width * .8f, center.y), 2.dp.toPx())
            VisualizerMode.OSCILLOSCOPE -> {
                val path = Path()
                val source = if (analysisFrame.signedWaveform.isNotEmpty()) {
                    analysisFrame.signedWaveform
                } else {
                    FloatArray(values.size) { values[it] - .5f }
                }
                source.forEachIndexed { index, value ->
                    val t = index.toFloat() / source.lastIndex.coerceAtLeast(1) * PI.toFloat() * 2f
                    val x = center.x + value * size.width * .38f
                    val y = center.y + sin(t + renderState.emphasis * 3f) * size.height * .28f
                    if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
                }
                drawPath(path, secondary.copy(alpha = .2f), style = Stroke(11.dp.toPx()))
                drawPath(path, primary.copy(alpha = 0.95f), style = Stroke(2.dp.toPx()))
            }
            VisualizerMode.WAVE -> history.forEachIndexed { row, spectrum ->
                spectrum.forEachIndexed { column, value ->
                    val hue = (210f + value * 110f) % 360f
                    drawRect(Color.hsv(hue, .7f, .4f + value * .6f), androidx.compose.ui.geometry.Offset(column * size.width / spectrum.size, row * size.height / 18f), androidx.compose.ui.geometry.Size(size.width / spectrum.size + 1f, size.height / 18f + 1f))
                }
            }
            VisualizerMode.SPECTRUM_CITY -> values.forEachIndexed { index, value ->
                val width = size.width / (values.size * 1.25f)
                val left = index * size.width / values.size
                val height = value.coerceAtLeast(.08f) * size.height * .68f
                drawRect(primary.copy(alpha = .35f + value * .65f), androidx.compose.ui.geometry.Offset(left, size.height - height), androidx.compose.ui.geometry.Size(width, height))
                drawLine(secondary, androidx.compose.ui.geometry.Offset(left, size.height-height), androidx.compose.ui.geometry.Offset(left+width, size.height-height), 2.dp.toPx())
            }
            VisualizerMode.BARS -> drawTerrain(values, primary, secondary)
            VisualizerMode.PULSE -> repeat(5) { index ->
                val angle = index * PI * 2 / 5 + renderState.emphasis
                val radius = size.minDimension * (.11f + values.getOrElse(index) { .2f } * .15f)
                drawCircle(if (index % 2 == 0) primary.copy(.55f) else secondary.copy(.55f), radius, androidx.compose.ui.geometry.Offset(center.x + cos(angle).toFloat()*size.width*.16f, center.y + sin(angle).toFloat()*size.height*.16f))
            }
            VisualizerMode.ORBIT -> repeat(24) { index ->
                val x = index * size.width / 24f
                val y = center.y + sin(index * .7f + renderState.emphasis * 7f) * size.height * .25f
                drawLine(primary.copy(.55f), androidx.compose.ui.geometry.Offset(x, y), androidx.compose.ui.geometry.Offset(x + 28.dp.toPx(), y + cos(index + renderState.emphasis).toFloat() * 38.dp.toPx()), 2.dp.toPx())
            }
            VisualizerMode.PARTICLES -> repeat(14) { index ->
                val a = index * PI * 2 / 14 + renderState.emphasis
                val r = size.minDimension * (.2f + values.getOrElse(index % values.size) { .2f }*.22f)
                val p = androidx.compose.ui.geometry.Offset(center.x + cos(a).toFloat()*r, center.y+sin(a).toFloat()*r)
                drawLine(secondary.copy(.8f), p, androidx.compose.ui.geometry.Offset(center.x, center.y), 2.dp.toPx())
                drawCircle(primary.copy(.55f), 7.dp.toPx(), p)
            }
            VisualizerMode.KALEIDO -> {
                val path = Path(); var x = .1f; var y = .1f
                repeat(180) { i ->
                    val nx = sin(1.7f*y + renderState.emphasis*4f) * .45f + .5f
                    val ny = sin(2.3f*x + values.getOrElse(i%values.size){.2f}*5f) * .45f + .5f
                    if (i==0) path.moveTo(x*size.width,y*size.height) else path.lineTo(x*size.width,y*size.height)
                    x=nx;y=ny
                }
                drawPath(path, secondary.copy(.8f), style=Stroke(2.dp.toPx()))
            }
            VisualizerMode.RAINBOW_RING -> {
                val rings = 9
                repeat(rings) { index ->
                    val radius = size.minDimension * (.08f + index*.055f + renderState.emphasis*.02f)
                    drawCircle(Color.hsv((index*32f+renderState.emphasis*100)%360, .7f, 1f).copy(.7f), radius, style=Stroke((rings-index+1).dp.toPx()))
                }
            }
            VisualizerMode.NEON_TUNNEL -> repeat(9) { row ->
                val y = row * size.height / 9f
                repeat(18) { col ->
                    val active = values.getOrElse((row+col)%values.size){0f}
                    if (active > .25f) drawRect(primary.copy(.25f+active*.7f), androidx.compose.ui.geometry.Offset(col*size.width/18f,y), androidx.compose.ui.geometry.Size(5.dp.toPx(), 14.dp.toPx()))
                }
            }
            VisualizerMode.EMBLEM -> {
                drawCircle(primary.copy(.5f), size.minDimension*(.25f+renderState.emphasis*.12f), style=Stroke(4.dp.toPx()))
                drawCircle(secondary.copy(.35f), size.minDimension*(.34f+renderState.emphasis*.08f), style=Stroke(1.dp.toPx()))
            }
            else -> {
                val radius = size.minDimension * 0.27f
                values.forEachIndexed { index, value ->
                    val angle = index * 360f / values.size - 90f
                    val radians = Math.toRadians(angle.toDouble())
                    val start = androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(radians).toFloat() * radius, center.y + kotlin.math.sin(radians).toFloat() * radius)
                    val end = androidx.compose.ui.geometry.Offset(center.x + kotlin.math.cos(radians).toFloat() * (radius + value * size.minDimension * 0.25f), center.y + kotlin.math.sin(radians).toFloat() * (radius + value * size.minDimension * 0.25f))
                    drawLine(Color.hsv((index * 360f / values.size + renderState.emphasis * 90f) % 360f, 0.72f, 1f), start, end, 5.dp.toPx())
                }
            }
        }
    }
}

private fun DrawScope.drawTerrain(values: FloatArray, primary: Color, secondary: Color) {
    repeat(8) { depth ->
        val yBase = size.height * (.2f + depth * .09f)
        val path = Path()
        values.forEachIndexed { index, value ->
            val x = size.width * index / values.lastIndex.coerceAtLeast(1)
            val y = yBase - value * size.height * (.05f + depth*.018f)
            if (index == 0) path.moveTo(x,y) else path.lineTo(x,y)
        }
        drawPath(path, if (depth % 2 == 0) primary.copy(.65f) else secondary.copy(.55f), style=Stroke(1.5.dp.toPx()))
    }
}
