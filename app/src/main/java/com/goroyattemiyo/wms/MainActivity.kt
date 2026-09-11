package com.goroyattemiyo.wms

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
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
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
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
            WmsTheme {
                WmsRoot(viewModel)
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
private fun WmsRoot(viewModel: GateA0ViewModel) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var importOpen by remember { mutableStateOf(false) }
    var developerOpen by remember { mutableStateOf(false) }
    var searchNotice by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(state.url) {
        if (state.url.isNotBlank()) {
            importOpen = true
            if (!state.probing && state.detectedTitle == null) {
                viewModel.probe()
            }
        }
    }

    SearchHome(
        state = state,
        searchText = searchText,
        onSearchTextChanged = {
            searchText = it
            searchNotice = null
        },
        onSubmit = {
            val value = searchText.trim()
            if (value.isBlank()) return@SearchHome
            if (URL_PATTERN.matches(value)) {
                viewModel.onUrlChanged(value)
                importOpen = true
            } else {
                searchNotice = "キーワード検索は次の実装でYouTube Providerから接続します。"
            }
        },
        searchNotice = searchNotice,
        onOpenDeveloper = { developerOpen = !developerOpen },
        developerOpen = developerOpen,
        onOpenSaved = { importOpen = true },
    )

    if (importOpen && state.url.isNotBlank()) {
        ImportSheet(
            state = state,
            onDismiss = { importOpen = false },
            onRightsChanged = viewModel::setRightsConfirmed,
            onSave = viewModel::acquireMp3,
            onCancel = viewModel::cancelAcquisition,
            onUpdateYoutubeDl = viewModel::updateYoutubeDl,
            onDiagnostics = viewModel::runDiagnostics,
        )
    }
}

@Composable
private fun SearchHome(
    state: GateA0UiState,
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    searchNotice: String?,
    onOpenDeveloper: () -> Unit,
    developerOpen: Boolean,
    onOpenSaved: () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(Color(0x332B8CFF), Color.Transparent),
                            ),
                            shape = RoundedCornerShape(18.dp),
                        )
                        .padding(6.dp),
                ) {
                    Image(
                        painter = painterResource(R.drawable.wms_emblem),
                        contentDescription = "WMS",
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(
                        text = "WMS",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "Web Media Studio",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onOpenDeveloper) {
                    Text("⋮")
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "Find media",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = "曲名・アーティスト・動画名で検索。URLを貼ればそのまま取り込みます。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedTextField(
                        value = searchText,
                        onValueChange = onSearchTextChanged,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("検索 または URL") },
                        placeholder = { Text("曲名・アーティスト・https://...") },
                        singleLine = true,
                    )
                    Button(
                        onClick = onSubmit,
                        modifier = Modifier.fillMaxWidth(),
                        enabled = searchText.trim().isNotEmpty(),
                    ) {
                        Text(if (URL_PATTERN.matches(searchText.trim())) "URLを取り込む" else "検索")
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { }) { Text("すべて") }
                OutlinedButton(onClick = { }) { Text("YouTube") }
                OutlinedButton(onClick = { }, enabled = false) { Text("More soon") }
            }

            searchNotice?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (developerOpen) {
                DeveloperStatusCard(state)
            }

            Text(
                text = "最近の検索",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "検索Provider接続後、ここに最近の検索を表示します。",
                    modifier = Modifier.padding(18.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(
                text = "最近追加したメディア",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (state.savedPath != null) {
                SavedMiniPlayer(
                    path = state.savedPath,
                    title = state.savedTitle ?: "保存済み音声",
                    onOpen = onOpenSaved,
                )
            } else {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = "まだ保存されたメディアはありません。",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            BottomNavigationPreview()
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun DeveloperStatusCard(state: GateA0UiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("Developer status", fontWeight = FontWeight.Bold)
            Text(
                text = "Engine: ${if (state.engineReady) "READY" else state.engineCode}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "yt-dlp: ${state.ytdlpVersion ?: "確認中"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ImportSheet(
    state: GateA0UiState,
    onDismiss: () -> Unit,
    onRightsChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onUpdateYoutubeDl: () -> Unit,
    onDiagnostics: () -> Unit,
) {
    var showDeveloper by remember { mutableStateOf(false) }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "WMSに追加",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = state.url,
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
                        Text(
                            text = "Audio · MP3 192 kbps",
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
                    text = "このメディアを保存する権利・許可を確認しました",
                    modifier = Modifier.padding(top = 12.dp),
                )
            }

            Button(
                onClick = onSave,
                modifier = Modifier.fillMaxWidth(),
                enabled = state.engineReady &&
                    state.detectedTitle != null &&
                    state.rightsConfirmed &&
                    !state.updatingYtdlp &&
                    !state.diagnosing &&
                    !state.probing &&
                    !state.acquiring,
            ) {
                Text(if (state.acquiring) "保存中…" else "保存 / MP3 192 kbps")
            }

            if (state.acquiring) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                Text("${state.progressPercent.toInt()}%  ${state.progressMessage}")
                OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) {
                    Text("キャンセル")
                }
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
                        verticalArrangement = Arrangement.spacedBy(5.dp),
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

            if (state.savedPath != null) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text("保存完了", fontWeight = FontWeight.Bold)
                        Text(state.savedTitle ?: "保存済み音声")
                        Text(
                            text = "シートを閉じるとSearch HomeのMini Playerから再生できます。",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
            }

            TextButton(onClick = { showDeveloper = !showDeveloper }) {
                Text(if (showDeveloper) "Developer toolsを閉じる" else "Developer tools")
            }

            if (showDeveloper) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("Acquisition Engine", fontWeight = FontWeight.Bold)
                        Text("yt-dlp: ${state.ytdlpVersion ?: "確認中"}")
                        OutlinedButton(
                            onClick = onUpdateYoutubeDl,
                            enabled = state.engineReady && !state.updatingYtdlp && !state.diagnosing && !state.probing && !state.acquiring,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.updatingYtdlp) "Updating…" else "Update yt-dlp stable")
                        }
                        OutlinedButton(
                            onClick = onDiagnostics,
                            enabled = state.engineReady && !state.updatingYtdlp && !state.diagnosing && !state.probing && !state.acquiring,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.diagnosing) "Diagnosing…" else "yt-dlp Diagnostics")
                        }
                        state.diagnosticLines.forEach { line ->
                            Text(line, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun SavedMiniPlayer(path: String, title: String, onOpen: () -> Unit) {
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
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Image(
                painter = painterResource(R.drawable.wms_emblem),
                contentDescription = null,
                modifier = Modifier.size(48.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, maxLines = 2)
                Text(
                    text = "Local · MP3",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Button(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
                Text(if (isPlaying) "Ⅱ" else "▶")
            }
            TextButton(onClick = onOpen) { Text("›") }
        }
    }
}

@Composable
private fun BottomNavigationPreview() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("⌕ Search", fontWeight = FontWeight.Bold)
            Text("Library", color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("Playlist", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private val WmsDarkColors = darkColorScheme(
    primary = Color(0xFF3AF2FF),
    onPrimary = Color(0xFF041216),
    secondary = Color(0xFF9B4FFF),
    background = Color(0xFF05070D),
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF111722),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF1B2432),
    onSurfaceVariant = Color(0xFFAEB8C8),
    error = Color(0xFFFFB4AB),
)

@Composable
private fun WmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WmsDarkColors,
        content = content,
    )
}

private val URL_PATTERN = Regex("https?://\\S+", RegexOption.IGNORE_CASE)
