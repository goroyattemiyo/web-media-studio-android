package com.goroyattemiyo.wms.ui.appearance

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.appearance.AppearanceSettings
import com.goroyattemiyo.wms.appearance.WmsBackgroundStyle
import com.goroyattemiyo.wms.appearance.WmsSkin
import com.goroyattemiyo.wms.appearance.WmsSkinCatalog
import com.goroyattemiyo.wms.playback.VisualizerMode

@Composable
fun AppearanceScreen(
    settings: AppearanceSettings,
    onBack: () -> Unit,
    onSkinSelected: (String) -> Unit,
    onBackgroundSelected: (String) -> Unit,
    onVisualizerSelected: (String) -> Unit,
    onReducedMotionChanged: (Boolean) -> Unit,
    onChooseBackgroundImage: () -> Unit,
    onBackgroundBlurChanged: (Float) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onBack) { Text("戻る") }
            Text("Appearance", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(64.dp))
        }
        OutlinedButton(onClick = onChooseBackgroundImage, modifier = Modifier.fillMaxWidth()) {
            Text(if (settings.backgroundImagePath == null) "端末から背景画像を選ぶ" else "背景画像を変更")
        }
        if (settings.backgroundImagePath != null) {
            Text("背景のぼかし", fontWeight = FontWeight.Bold)
            Slider(value = settings.backgroundBlur, onValueChange = onBackgroundBlurChanged, valueRange = 0f..24f)
        }

        Text("Skin", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("見た目を選べます。選択はこの端末に保存されます。", color = MaterialTheme.colorScheme.onSurfaceVariant)
        WmsSkinCatalog.skins.chunked(2).forEach { skins ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                skins.forEach { skin ->
                    SkinPreviewTile(
                        skin = skin,
                        selected = skin.id == settings.skinId,
                        onClick = { onSkinSelected(skin.id) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (skins.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Text("Background", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(
            "ホームやライブラリの背景パターンを選べます。",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            WmsBackgroundStyle.entries.forEach { style ->
                OutlinedButton(onClick = { onBackgroundSelected(style.id) }) {
                    Text(if (style.id == settings.backgroundId) "✓ ${style.displayName}" else style.displayName)
                }
            }
        }

        Text("Player Visualizer", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        VisualizerMode.entries.chunked(2).forEach { modes ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                modes.forEach { mode ->
                    OutlinedButton(
                        onClick = { onVisualizerSelected(mode.id) },
                        enabled = !settings.reducedMotion,
                        modifier = Modifier.weight(1f),
                    ) { Text(if (mode.id == settings.visualizerId) "✓ ${visualizerLabel(mode)}" else visualizerLabel(mode)) }
                }
                if (modes.size == 1) Spacer(Modifier.weight(1f))
            }
        }

        Card(modifier = Modifier.fillMaxWidth()) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("モーションを軽減", fontWeight = FontWeight.Bold)
                    Text(
                        "動きの少ない表示にします。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = settings.reducedMotion, onCheckedChange = onReducedMotionChanged)
            }
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun SkinPreviewTile(
    skin: WmsSkin,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(onClick = onClick, modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(
                modifier = Modifier
                .size(46.dp)
                    .background(Color(skin.background), RoundedCornerShape(14.dp))
                    .padding(7.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            Brush.linearGradient(listOf(Color(skin.primary), Color(skin.secondary))),
                            RoundedCornerShape(10.dp),
                        ),
                )
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    if (selected) "✓ ${skin.displayName}" else skin.displayName,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

private fun visualizerLabel(mode: VisualizerMode): String = mode.displayName
