package com.goroyattemiyo.wms.ui.sound

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.goroyattemiyo.wms.playback.AppVolumeBus
import kotlin.math.roundToInt

/** S1: volume is live; the EQ section must not pretend to be an implemented audio effect. */
@Composable
fun SoundScreen(controller: MediaController?) {
    val volume by AppVolumeBus.state.collectAsStateWithLifecycle()
    var sliderPercent by remember { mutableIntStateOf(volume.percent) }
    LaunchedEffect(volume.percent) { sliderPercent = volume.percent }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text("Sound Control", style = MaterialTheme.typography.headlineMedium)
        Text("音量・音質", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("WMS音量", style = MaterialTheme.typography.titleMedium)
                    Text("$sliderPercent%")
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            controller?.volume = volume.muteToggleTarget() / 100f
                        },
                        enabled = controller != null,
                    ) {
                        Text(if (volume.percent == 0) "ミュート解除" else "ミュート")
                    }
                    Slider(
                        value = sliderPercent.toFloat(),
                        onValueChange = { value ->
                            val next = value.roundToInt().coerceIn(0, 100)
                            sliderPercent = next
                            controller?.volume = next / 100f
                        },
                        valueRange = 0f..100f,
                        enabled = controller != null,
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("100%＝原音量。端末やBluetooth機器の最大音量は変更しません。", style = MaterialTheme.typography.bodySmall)
                if (controller == null) {
                    Text("再生サービスに接続すると操作できます。", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("イコライザー", style = MaterialTheme.typography.titleMedium)
                Text("縦型フェーダー・標準8種・マイプリセットは次の実装段階です。", style = MaterialTheme.typography.bodyMedium)
                Text("現在のビルドではEQは無効です。音質を変更したように見えるダミー操作は置きません。", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
