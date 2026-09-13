package com.goroyattemiyo.wms.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.appearance.WmsSkin
import com.goroyattemiyo.wms.appearance.WmsSurfaceStyle

@Composable
fun WmsTheme(skin: WmsSkin, content: @Composable () -> Unit) {
    val colors = if (skin.isLight) {
        lightColorScheme(
            primary = Color(skin.primary),
            secondary = Color(skin.secondary),
            background = Color(skin.background),
            surface = Color(skin.surface),
            surfaceVariant = Color(skin.surfaceVariant),
            onBackground = Color(skin.onBackground),
            onSurface = Color(skin.onBackground),
            onSurfaceVariant = Color(skin.onSurfaceVariant),
        )
    } else {
        darkColorScheme(
            primary = Color(skin.primary),
            onPrimary = Color(skin.background),
            secondary = Color(skin.secondary),
            background = Color(skin.background),
            onBackground = Color(skin.onBackground),
            surface = Color(skin.surface),
            onSurface = Color(skin.onBackground),
            surfaceVariant = Color(skin.surfaceVariant),
            onSurfaceVariant = Color(skin.onSurfaceVariant),
            error = Color(0xFFFFB4AB),
        )
    }
    val corner = when (skin.surfaceStyle) {
        WmsSurfaceStyle.CLEAN -> 10.dp
        WmsSurfaceStyle.WARM -> 20.dp
        WmsSurfaceStyle.RETRO -> 4.dp
        WmsSurfaceStyle.NEON -> 16.dp
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(corner / 2),
            medium = RoundedCornerShape(corner),
            large = RoundedCornerShape(corner * 1.5f),
        ),
        content = content,
    )
}
