package com.goroyattemiyo.wms.ui.sound

import android.os.Bundle
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionCommand
import com.goroyattemiyo.wms.playback.AppVolumeBus
import com.goroyattemiyo.wms.playback.SoundCommand
import com.goroyattemiyo.wms.playback.SoundEqBus
import com.goroyattemiyo.wms.playback.SoundPresets
import kotlin.math.roundToInt

private enum class PresetDialog { SAVE, RENAME, DELETE }

private fun frequencyLabel(hz: Int): String = if (hz < 1000) "$hz Hz" else
    "${(hz / 100f).roundToInt() / 10f} kHz"

private fun gainLabel(mb: Int): String = "${if (mb > 0) "+" else ""}${mb / 100f} dB"

@Composable
fun SoundScreen(controller: MediaController?) {
    val volume by AppVolumeBus.state.collectAsStateWithLifecycle()
    val eq by SoundEqBus.state.collectAsStateWithLifecycle()
    val connected = controller?.isSessionCommandAvailable(SessionCommand(SoundCommand.VOLUME, Bundle.EMPTY)) == true
    var shownVolume by remember { mutableIntStateOf(volume.percent) }
    var presetMenuOpen by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<PresetDialog?>(null) }
    var nameText by remember { mutableStateOf("") }
    var pendingBands by remember { mutableStateOf<Map<Int, Int>>(emptyMap()) }
    LaunchedEffect(volume.percent) { shownVolume = volume.percent }
    val activeCustom = eq.customPresets.firstOrNull { it.id == eq.selectedPresetId }
    val selectedName = (SoundPresets.builtIns + eq.customPresets)
        .firstOrNull { it.id == eq.selectedPresetId }?.name ?: "カスタム（未保存）"

    fun send(action: String, extras: Bundle = Bundle.EMPTY) {
        if (connected) controller?.sendCustomCommand(SessionCommand(action, Bundle.EMPTY), extras)
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Sound Control", style = MaterialTheme.typography.headlineMedium)
        Text("WMSの音量・音質", style = MaterialTheme.typography.titleMedium)
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("アプリ音量", style = MaterialTheme.typography.titleMedium)
                    Text("$shownVolume%", style = MaterialTheme.typography.titleLarge)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TextButton(
                        onClick = {
                            send(SoundCommand.VOLUME, Bundle().apply {
                                putInt(SoundCommand.PERCENT, volume.muteToggleTarget())
                            })
                        },
                        enabled = connected,
                    ) { Text(if (volume.percent == 0) "解除" else "消音") }
                    Slider(
                        value = shownVolume.toFloat().coerceIn(0f, volume.maximum.toFloat()),
                        valueRange = 0f..volume.maximum.toFloat(),
                        onValueChange = { value ->
                            val percent = value.roundToInt().coerceIn(0, volume.maximum)
                            if (percent != shownVolume) {
                                shownVolume = percent
                                send(SoundCommand.VOLUME, Bundle().apply {
                                    putInt(SoundCommand.PERCENT, percent)
                                })
                            }
                        },
                        enabled = connected,
                        modifier = Modifier.weight(1f),
                    )
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("音量ブースト", style = MaterialTheme.typography.bodyLarge)
                    Switch(
                        checked = volume.boostEnabled,
                        onCheckedChange = { enabled ->
                            send(SoundCommand.BOOST, Bundle().apply { putBoolean(SoundCommand.ENABLED, enabled) })
                        },
                        // Always allow an explicit request while connected. SoundEngine decides whether
                        // boosting is safe and publishes the actual rejection reason instead of a dead switch.
                        enabled = connected,
                    )
                }
                Text(
                    if (volume.boostEnabled) "BOOST ON：100%超はPCM増幅＋リミッター（最大200%は試験上限）。" else
                        "100%＝原音量。ブーストは再生中に明示的にONにしてください。",
                    style = MaterialTheme.typography.bodySmall,
                )
                if (!volume.boostEnabled && connected) {
                    val guidance = when {
                        eq.enabled -> "BOOSTを使うには、下のイコライザーをOFFにしてください。"
                        !volume.boostAvailable -> "BOOST準備待ち：端末内のMP3を再生してください。再生中も使えない場合はPCM出力・接続先の監視を確認します。"
                        else -> "BOOSTをONにすると音量スライダーが200%まで広がります。まず125%から試してください。"
                    }
                    Text(guidance, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = { send(SoundCommand.RESET) }, enabled = connected) {
                    Text("100%に戻す／ブースト解除")
                }
                if (volume.limitedFrames > 0 && volume.boostEnabled)
                    Text("リミッター作動：累計 ${volume.limitedFrames} フレーム", style = MaterialTheme.typography.bodySmall)
                if (volume.message.isNotBlank())
                    Text(volume.message, color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodySmall)
                if (!connected) Text("再生サービスへの接続を確認中です。", style = MaterialTheme.typography.bodySmall)
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically) {
                    Text("イコライザー", style = MaterialTheme.typography.titleMedium)
                    Switch(
                        checked = eq.enabled,
                        onCheckedChange = { enabled ->
                            send(SoundCommand.EQ_ENABLED, Bundle().apply { putBoolean(SoundCommand.ENABLED, enabled) })
                        },
                        enabled = connected && eq.supported && !volume.boostEnabled,
                    )
                }
                Text("ブーストとEQは安全確認まで同時使用できません。", style = MaterialTheme.typography.bodySmall)
                Text(eq.message, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall)
                Box {
                    TextButton(onClick = { presetMenuOpen = true },
                        enabled = connected && eq.supported && !volume.boostEnabled) {
                        Text("プリセット：$selectedName ▾")
                    }
                    DropdownMenu(expanded = presetMenuOpen, onDismissRequest = { presetMenuOpen = false }) {
                        DropdownMenuItem(text = { Text("標準プリセット") }, onClick = {}, enabled = false)
                        SoundPresets.builtIns.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    send(SoundCommand.PRESET, Bundle().apply { putString(SoundCommand.ID, preset.id) })
                                    presetMenuOpen = false
                                },
                            )
                        }
                        DropdownMenuItem(text = { Text("マイプリセット") }, onClick = {}, enabled = false)
                        eq.customPresets.forEach { preset ->
                            DropdownMenuItem(
                                text = { Text(preset.name) },
                                onClick = {
                                    send(SoundCommand.PRESET, Bundle().apply { putString(SoundCommand.ID, preset.id) })
                                    presetMenuOpen = false
                                },
                            )
                        }
                    }
                }
                if (eq.supported && eq.bands.isNotEmpty()) {
                    Text("縦フェーダー：下が低音、右に進むほど高音。中央が0 dB。",
                        style = MaterialTheme.typography.bodySmall)
                    Row(
                        modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        eq.bands.forEachIndexed { index, band ->
                            val displayed = pendingBands[index] ?: band.gainMb
                            Column(
                                modifier = Modifier.width(56.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp),
                            ) {
                                Text(gainLabel(displayed), style = MaterialTheme.typography.labelSmall,
                                    maxLines = 1)
                                Box(Modifier.width(56.dp).height(172.dp), contentAlignment = Alignment.Center) {
                                    Slider(
                                        value = displayed.toFloat().coerceIn(eq.minGainMb.toFloat(), eq.maxGainMb.toFloat()),
                                        onValueChange = { v ->
                                            pendingBands = pendingBands + (index to ((v / 50f).roundToInt() * 50)
                                                .coerceIn(eq.minGainMb, eq.maxGainMb))
                                        },
                                        onValueChangeFinished = {
                                            val next = pendingBands[index] ?: band.gainMb
                                            send(SoundCommand.EQ_BAND, Bundle().apply {
                                                putInt(SoundCommand.INDEX, index)
                                                putInt(SoundCommand.GAIN_MB, next)
                                            })
                                            pendingBands = pendingBands - index
                                        },
                                        valueRange = eq.minGainMb.toFloat()..eq.maxGainMb.toFloat(),
                                        enabled = connected && eq.enabled && !volume.boostEnabled && eq.minGainMb < eq.maxGainMb,
                                        modifier = Modifier.requiredWidth(156.dp).height(42.dp).rotate(-90f),
                                    )
                                }
                                Text(frequencyLabel(band.frequencyHz),
                                    style = MaterialTheme.typography.labelSmall, maxLines = 1)
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Button(onClick = {
                            send(SoundCommand.PRESET, Bundle().apply { putString(SoundCommand.ID, "builtin:flat") })
                        }, enabled = connected && !volume.boostEnabled) { Text("フラット") }
                        TextButton(onClick = { nameText = ""; dialog = PresetDialog.SAVE },
                            enabled = connected && eq.supported) { Text("名前を付けて保存") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(onClick = {
                            nameText = activeCustom?.name.orEmpty()
                            dialog = PresetDialog.RENAME
                        }, enabled = connected && activeCustom != null) { Text("名前変更") }
                        TextButton(onClick = { dialog = PresetDialog.DELETE },
                            enabled = connected && activeCustom != null) { Text("削除") }
                    }
                } else {
                    Text("対応帯域を取得できるまでEQ操作はできません。",
                        style = MaterialTheme.typography.bodySmall)
                }
            }
        }
        Text("Bluetooth／車載機の上限は変更しません。大音量による歪みや聴覚への影響に注意してください。",
            style = MaterialTheme.typography.bodySmall)
    }

    if (dialog != null) {
        val currentDialog = dialog!!
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(when (currentDialog) {
                PresetDialog.SAVE -> "マイプリセットを保存"
                PresetDialog.RENAME -> "プリセット名を変更"
                PresetDialog.DELETE -> "マイプリセットを削除"
            }) },
            text = {
                if (currentDialog == PresetDialog.DELETE) Text("${activeCustom?.name.orEmpty()} を削除しますか？")
                else OutlinedTextField(value = nameText, onValueChange = { nameText = it },
                    label = { Text("プリセット名（1〜40文字）") }, singleLine = true)
            },
            confirmButton = {
                TextButton(onClick = {
                    when (currentDialog) {
                        PresetDialog.SAVE -> send(SoundCommand.SAVE, Bundle().apply { putString(SoundCommand.NAME, nameText) })
                        PresetDialog.RENAME -> send(SoundCommand.RENAME, Bundle().apply {
                            putString(SoundCommand.ID, activeCustom?.id)
                            putString(SoundCommand.NAME, nameText)
                        })
                        PresetDialog.DELETE -> send(SoundCommand.DELETE, Bundle().apply {
                            putString(SoundCommand.ID, activeCustom?.id)
                        })
                    }
                    dialog = null
                }, enabled = connected && (currentDialog == PresetDialog.DELETE ||
                    SoundPresets.validName(nameText) != null)) {
                    Text(if (currentDialog == PresetDialog.DELETE) "削除" else "保存")
                }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("キャンセル") } },
        )
    }
}
