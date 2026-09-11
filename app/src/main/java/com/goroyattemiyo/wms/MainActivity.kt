package com.goroyattemiyo.wms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import java.io.File

class MainActivity : ComponentActivity() {
    private val viewModel by viewModels<GateA0ViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShareIntent(intent)

        setContent {
            WmsGateA0Theme {
                GateA0Screen(viewModel)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        consumeShareIntent(intent)
    }

    private fun consumeShareIntent(intent: Intent?) {
        if (intent?.action == Intent.ACTION_SEND && intent.type == "text/plain") {
            viewModel.consumeSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}

@Composable
private fun GateA0Screen(viewModel: GateA0ViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "WMS Android",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "Gate A0 / ローカル取得テスト",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = "共有メニューからURLを送るか、公開URLを貼り付けて確認します。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            EngineStatusCard(
                state = state,
                onUpdateYoutubeDl = viewModel::updateYoutubeDl,
            )

            OutlinedTextField(
                value = state.url,
                onValueChange = viewModel::onUrlChanged,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Source URL") },
                placeholder = { Text("https://...") },
                singleLine = false,
                minLines = 2,
                enabled = !state.acquiring && !state.updatingYtdlp && !state.diagnosing,
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = viewModel::probe,
                    enabled = state.url.isNotBlank() &&
                        state.engineReady &&
                        !state.updatingYtdlp &&
                        !state.diagnosing &&
                        !state.probing &&
                        !state.acquiring,
                ) {
                    Text(if (state.probing) "確認中…" else "Probe")
                }

                OutlinedButton(
                    onClick = viewModel::runDiagnostics,
                    enabled = state.url.isNotBlank() &&
                        state.engineReady &&
                        !state.updatingYtdlp &&
                        !state.diagnosing &&
                        !state.probing &&
                        !state.acquiring,
                ) {
                    Text(if (state.diagnosing) "Diagnosing…" else "yt-dlp Diagnostics")
                }

                if (state.acquiring) {
                    OutlinedButton(onClick = viewModel::cancelAcquisition) {
                        Text("Cancel")
                    }
                }
            }

            if (state.diagnosticLines.isNotEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("yt-dlp diagnostics", fontWeight = FontWeight.Bold)
                        Text(
                            text = if (state.diagnosticSucceeded == true) {
                                "SIMULATE PASS"
                            } else {
                                "SIMULATE returned diagnostics"
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        state.diagnosticLines.forEach { line ->
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                }
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
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Checkbox(
                    checked = state.rightsConfirmed,
                    onCheckedChange = viewModel::setRightsConfirmed,
                    enabled = !state.acquiring && !state.updatingYtdlp && !state.diagnosing,
                )
                Text(
                    text = "このメディアを保存する権利・許可を確認しました",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Button(
                onClick = viewModel::acquireMp3,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.url.isNotBlank() &&
                    state.engineReady &&
                    state.rightsConfirmed &&
                    !state.updatingYtdlp &&
                    !state.diagnosing &&
                    !state.probing &&
                    !state.acquiring,
            ) {
                Text(if (state.acquiring) "Saving…" else "Save audio / MP3 192 kbps")
            }

            if (state.acquiring) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text(
                    text = "${state.progressPercent.toInt()}%  ${state.progressMessage}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else if (state.progressMessage.isNotBlank()) {
                Text(
                    text = state.progressMessage,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            state.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(
                            text = "ERROR ${state.errorCode ?: "UNKNOWN"}",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error,
                        )
                        Text(message)
                    }
                }
            }

            val savedPath = state.savedPath
            if (savedPath != null) {
                LocalPreviewCard(
                    path = savedPath,
                    title = state.savedTitle ?: "保存済み音声",
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "A0は実装可否の確認画面です。Library・Playlist・画面OFF再生はA0成功後に追加します。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EngineStatusCard(
    state: GateA0UiState,
    onUpdateYoutubeDl: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Acquisition Engine", fontWeight = FontWeight.Bold)
            Text(
                text = if (state.engineReady) "READY" else state.engineCode,
                color = if (state.engineReady) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
            )
            Text(
                text = state.engineMessage,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "yt-dlp: ${state.ytdlpVersion ?: "確認中"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedButton(
                onClick = onUpdateYoutubeDl,
                enabled = state.engineReady &&
                    !state.updatingYtdlp &&
                    !state.diagnosing &&
                    !state.probing &&
                    !state.acquiring,
            ) {
                Text(if (state.updatingYtdlp) "Updating yt-dlp…" else "Update yt-dlp stable")
            }
        }
    }
}

@Composable
private fun LocalPreviewCard(path: String, title: String) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    var isPlaying by remember { mutableStateOf(false) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }

    LaunchedEffect(path) {
        val file = File(path)
        player.stop()
        player.clearMediaItems()
        if (file.exists()) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
        }
    }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text("Media3 local preview", fontWeight = FontWeight.Bold)
            Text(title)
            Text(
                text = path,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
            Button(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isPlaying) "Pause" else "Play saved file")
            }
        }
    }
}

private val WmsDarkColors = darkColorScheme(
    primary = Color(0xFFF4F4F4),
    onPrimary = Color(0xFF111111),
    background = Color(0xFF0B0D0F),
    onBackground = Color(0xFFF4F4F4),
    surface = Color(0xFF15181C),
    onSurface = Color(0xFFF4F4F4),
    surfaceVariant = Color(0xFF20252A),
    onSurfaceVariant = Color(0xFFB9C0C7),
    error = Color(0xFFFFB4AB),
)

@Composable
private fun WmsGateA0Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WmsDarkColors,
        content = content,
    )
}
