package com.goroyattemiyo.wms.ui.importer

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.GateA0UiState
import com.goroyattemiyo.wms.acquisition.AcquisitionPreset
import com.goroyattemiyo.wms.playlist.PlaylistSummary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportSheet(
    state: GateA0UiState,
    playlists: List<PlaylistSummary>,
    selectedPlaylistId: String?,
    onDismiss: () -> Unit,
    onPlaylistSelected: (String?) -> Unit,
    onRightsChanged: (Boolean) -> Unit,
    onSave: (AcquisitionPreset) -> Unit,
    onCancel: () -> Unit,
    onUpdateYoutubeDl: () -> Unit,
    onDiagnostics: () -> Unit,
    onCloseAndPlay: () -> Unit,
) {
    var showAdvanced by remember(state.url) { mutableStateOf(false) }
    var selectedPreset by remember(state.url) { mutableStateOf(AcquisitionPreset.MP3_192) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("WMSに追加", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                state.url,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )

            if (state.probing) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("メディア情報を確認しています…")
            }

            if (state.detectedTitle != null || state.detectedProvider != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("取得候補", fontWeight = FontWeight.Bold)
                        state.detectedTitle?.let { Text(it) }
                        state.detectedProvider?.let {
                            Text(it, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(selectedPreset.displayName, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            Text("保存形式", fontWeight = FontWeight.Bold)
            Card(modifier = Modifier.fillMaxWidth()) {
                Text("標準: 音声 MP3 192 kbps", modifier = Modifier.padding(14.dp), color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = { showAdvanced = !showAdvanced }, enabled = !state.acquiring) {
                Text(if (showAdvanced) "詳細オプションを閉じる" else "詳細オプション")
            }
            if (showAdvanced) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("Audio container", fontWeight = FontWeight.Bold)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            FormatChoice("音声", "MP3 / M4A", selectedPreset.mediaType == "AUDIO", !state.acquiring, Modifier.weight(1f)) {
                                selectedPreset = AcquisitionPreset.MP3_192
                            }
                            FormatChoice("動画", "MP4", selectedPreset == AcquisitionPreset.VIDEO_MP4, !state.acquiring, Modifier.weight(1f)) {
                                selectedPreset = AcquisitionPreset.VIDEO_MP4
                            }
                        }
                        AcquisitionPreset.entries
                            .filter { it.mediaType == "AUDIO" }
                            .forEach { preset ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    RadioButton(
                                        selected = selectedPreset == preset,
                                        onClick = { selectedPreset = preset },
                                        enabled = !state.acquiring,
                                    )
                                    Text(preset.displayName)
                                }
                            }
                        Text(
                            "形式はWMS管理の固定プリセットです。配信元に形式がない場合は明確に失敗します。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top,
            ) {
                Checkbox(
                    checked = state.rightsConfirmed,
                    onCheckedChange = onRightsChanged,
                    enabled = !state.acquiring && !state.updatingYtdlp && !state.diagnosing,
                )
                Text(
                    "このメディアを保存する権利・許可を確認しました",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            if (playlists.isNotEmpty()) {
                Text("追加先プレイリスト（任意）", fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(onClick = { onPlaylistSelected(null) }) {
                        Text(if (selectedPlaylistId == null) "✓ 指定なし" else "指定なし")
                    }
                    playlists.forEach { playlist ->
                        OutlinedButton(onClick = { onPlaylistSelected(playlist.id) }) {
                            Text(if (selectedPlaylistId == playlist.id) "✓ ${playlist.name}" else playlist.name)
                        }
                    }
                }
                val destinationName = playlists
                    .firstOrNull { it.id == selectedPlaylistId }
                    ?.name
                    ?: "Libraryのみ"
                Text(
                    "保存先: $destinationName",
                    color = MaterialTheme.colorScheme.primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                )
            }

            Button(
                onClick = { onSave(selectedPreset) },
                modifier = Modifier.fillMaxWidth(),
                enabled = state.engineReady &&
                    state.detectedTitle != null &&
                    state.rightsConfirmed &&
                    !state.updatingYtdlp &&
                    !state.diagnosing &&
                    !state.probing &&
                    !state.acquiring,
            ) {
                Text(if (state.acquiring) "保存中…" else "保存 / ${selectedPreset.displayName}")
            }

            if (state.acquiring) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("${state.progressPercent.toInt()}%  ${state.progressMessage}")
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("キャンセル")
                }
            } else if (state.progressMessage.isNotBlank()) {
                Text(state.progressMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            state.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(acquisitionErrorTitle(state.errorCode), fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.error)
                        Text(message)
                        Text(
                            "コード: ${state.errorCode ?: "UNKNOWN"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                    }
                }
            }

            if (state.savedPath != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("保存完了", fontWeight = FontWeight.Bold)
                        Text(state.savedTitle ?: "保存済みメディア")
                        Button(onClick = onCloseAndPlay, modifier = Modifier.fillMaxWidth()) {
                            Text("閉じて再生")
                        }
                    }
                }
            }


            Spacer(Modifier.height(20.dp))
        }
    }
}
@Composable
private fun FormatChoice(
    label: String,
    detail: String,
    selected: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = modifier) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(if (selected) "✓ $label" else label, fontWeight = FontWeight.Bold)
            Text(detail, style = MaterialTheme.typography.labelSmall)
        }
    }
}

private fun acquisitionErrorTitle(code: String?): String = when (code) {
    "LOGIN_REQUIRED" -> "ログインが必要なため保存できません"
    "DRM_OR_PROTECTED" -> "保護されたメディアは保存できません"
    "UNSUPPORTED_SOURCE" -> "この配信元にはまだ対応していません"
    "UNAVAILABLE" -> "非公開または利用できないメディアです"
    "FORMAT_UNAVAILABLE" -> "選択した保存形式を利用できません"
    "STORAGE_FULL" -> "端末の空き容量が不足しています"
    else -> "保存できませんでした"
}
