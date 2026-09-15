package com.goroyattemiyo.wms.ui.player.visualizer

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.playback.VisualizerRendererType

@Composable
fun VisualizerSurface(
    mode: VisualizerMode,
    frame: AudioAnalysisFrame,
    phase: Float,
    primary: Color,
    secondary: Color,
    modifier: Modifier = Modifier,
) {
    when (mode.rendererType) {
        VisualizerRendererType.LIQUID_METABALLS -> LiquidMetaballsRenderer(frame, phase, primary, secondary, modifier)
        VisualizerRendererType.FLOW_FIELD -> FlowFieldRenderer(frame, primary, secondary, modifier)
        VisualizerRendererType.VORONOI_SHARDS -> VoronoiShardsRenderer(frame, phase, primary, secondary, modifier)
        VisualizerRendererType.SPECTROGRAM_WATERFALL -> SpectrogramWaterfallRenderer(frame, primary, secondary, modifier)
        VisualizerRendererType.WIREFRAME_TERRAIN -> WireframeTerrainRenderer(frame, primary, secondary, modifier)
        VisualizerRendererType.GLYPH_RAIN -> GlyphRainRenderer(frame, primary, secondary, modifier)
        VisualizerRendererType.PHOSPHOR_LISSAJOUS -> PhosphorLissajousRenderer(frame, primary, secondary, modifier)
        else -> LegacyVisualizerRenderer(mode, frame, phase, primary, secondary, modifier)
    }
}
