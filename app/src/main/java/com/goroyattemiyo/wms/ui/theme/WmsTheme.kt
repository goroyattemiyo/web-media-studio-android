package com.goroyattemiyo.wms.ui.theme

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.appearance.WmsSkin
import com.goroyattemiyo.wms.appearance.WmsBackgroundStyle
import com.goroyattemiyo.wms.appearance.WmsSurfaceStyle

@Composable
fun WmsTheme(
    skin: WmsSkin,
    backgroundStyle: WmsBackgroundStyle = WmsBackgroundStyle.PLAIN,
    backgroundImagePath: String? = null,
    backgroundBlur: Float = 0f,
    content: @Composable () -> Unit,
) {
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
    val fontFamily = when (skin.surfaceStyle) {
        WmsSurfaceStyle.RETRO -> FontFamily.Monospace
        WmsSurfaceStyle.WARM -> FontFamily.Serif
        else -> FontFamily.SansSerif
    }
    MaterialTheme(
        colorScheme = colors,
        shapes = Shapes(
            small = RoundedCornerShape(corner / 2),
            medium = RoundedCornerShape(corner),
            large = RoundedCornerShape(corner * 1.5f),
        ),
        typography = Typography().let { typography ->
            typography.copy(
                displayLarge = typography.displayLarge.copy(fontFamily = fontFamily),
                displayMedium = typography.displayMedium.copy(fontFamily = fontFamily),
                headlineLarge = typography.headlineLarge.copy(fontFamily = fontFamily),
                headlineMedium = typography.headlineMedium.copy(fontFamily = fontFamily),
                titleLarge = typography.titleLarge.copy(fontFamily = fontFamily),
                titleMedium = typography.titleMedium.copy(fontFamily = fontFamily),
                bodyLarge = typography.bodyLarge.copy(fontFamily = fontFamily),
                bodyMedium = typography.bodyMedium.copy(fontFamily = fontFamily),
                labelLarge = typography.labelLarge.copy(fontFamily = fontFamily),
            )
        },
    ) {
        CompositionLocalProvider(LocalContentColor provides colors.onBackground) {
            Box(modifier = Modifier.fillMaxSize()) {
                StaticBackground(skin, backgroundStyle, backgroundImagePath, backgroundBlur)
                content()
            }
        }
    }
}

@Composable
private fun StaticBackground(skin: WmsSkin, style: WmsBackgroundStyle, imagePath: String?, blur: Float) {
    val base = Color(skin.background)
    val accent = Color(skin.primary).copy(alpha = if (skin.isLight) 0.10f else 0.14f)
    val image = androidx.compose.runtime.remember(imagePath) {
        imagePath?.let { android.graphics.BitmapFactory.decodeFile(it)?.asImageBitmap() }
    }
    image?.let {
        Image(it, null, Modifier.fillMaxSize().blur(blur.dp), contentScale = ContentScale.Crop)
    }
    Canvas(modifier = Modifier.fillMaxSize()) {
        if (image == null) drawRect(base)
        when (style) {
            WmsBackgroundStyle.PLAIN -> Unit
            WmsBackgroundStyle.GRID -> {
                val step = 30.dp.toPx()
                var x = 0f
                while (x <= size.width) {
                    drawLine(accent, start = androidx.compose.ui.geometry.Offset(x, 0f), end = androidx.compose.ui.geometry.Offset(x, size.height), strokeWidth = 1.dp.toPx())
                    x += step
                }
                var y = 0f
                while (y <= size.height) {
                    drawLine(accent, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    y += step
                }
            }
            WmsBackgroundStyle.DOTS -> {
                val step = 24.dp.toPx()
                var x = step / 2
                while (x <= size.width) {
                    var y = step / 2
                    while (y <= size.height) {
                        drawCircle(accent, radius = 1.25.dp.toPx(), center = androidx.compose.ui.geometry.Offset(x, y))
                        y += step
                    }
                    x += step
                }
            }
            WmsBackgroundStyle.SCANLINES -> {
                val step = 5.dp.toPx()
                var y = 0f
                while (y <= size.height) {
                    drawLine(accent, start = androidx.compose.ui.geometry.Offset(0f, y), end = androidx.compose.ui.geometry.Offset(size.width, y), strokeWidth = 1.dp.toPx())
                    y += step
                }
            }
        }
    }
}
