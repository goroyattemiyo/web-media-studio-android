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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import com.goroyattemiyo.wms.search.SearchMediaItem
import java.io.File

class MainActivity : ComponentActivity() {
    private val acquisitionViewModel by viewModels<GateA0ViewModel>()
    private val searchViewModel by viewModels<SearchViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShareIntent(intent)

        setContent {
            WmsTheme {
                WmsRoot(acquisitionViewModel, searchViewModel)
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
            acquisitionViewModel.consumeSharedText(intent.getStringExtra(Intent.EXTRA_TEXT))
        }
    }
}

@Composable
private fun WmsRoot(
    acquisitionViewModel: GateA0ViewModel,
    searchViewModel: SearchViewModel,
) {
    val acquisitionState by acquisitionViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var importOpen by remember { mutableStateOf(false) }
    var developerOpen by remember { mutableStateOf(false) }

    LaunchedEffect(acquisitionState.url) {
        if (acquisitionState.url.isNotBlank()) {
            importOpen = true
            if (!acquisitionState.probing && acquisitionState.detectedTitle == null) {
                acquisitionViewModel.probe()
            }
        }
    }

    SearchHome(
        acquisitionState = acquisitionState,
        searchState = searchState,
        searchText = searchText,
        onSearchTextChanged = {
            searchText = it
            searchViewModel.clearError()
        },
        onSubmit = {
            val value = searchText.trim()
            if (value.isNotBlank()) {
                if (isDirectUrl(value)) {
                    acquisitionViewModel.onUrlChanged(value)
                    importOpen = true
                } else {
                    searchViewModel.search(value)
                }
            }
        },
        onImportResult = { item ->
            acquisitionViewModel.onUrlChanged(item.url)
            importOpen = true
        },
        onOpenDeveloper = { developerOpen = !developerOpen },
        developerOpen = developerOpen,
    )

    if (importOpen && acquisitionState.url.isNotBlank()) {
        ImportSheet(
            state = acquisitionState,
            onDismiss = { importOpen = false },
            onRightsChanged = acquisitionViewModel::setRightsConfirmed,
            onSave = acquisitionViewModel::acquireMp3,
            onCancel = acquisitionViewModel::cancelAcquisition,
            onUpdateYoutubeDl = acquisitionViewModel::updateYoutubeDl,
            onDiagnostics = acquisitionViewModel::runDiagnostics,
        )
    }
}

@Composable
private fun SearchHome(
    acquisitionState: GateA0UiState,
    searchState: SearchUiState,
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onImportResult: (SearchMediaItem) -> Unit,
    onOpenDeveloper: () -> Unit,
    developerOpen: Boolean,
) {
    val context = LocalContext.current

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
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
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
                }
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
                        enabled = searchText.trim().isNotEmpty() && !searchState.searching,
                    ) {
                        Text(
                            when {
                                searchState.searching -> "検索中…"
                                isDirectUrl(searchText.trim()) -> "URLを取り込む"
                                else -> "検索"
                            },
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(onClick = { }) { Text("YouTube") }
                OutlinedButton(onClick = { }, enabled = false) { Text("TikTok · 今後") }
                OutlinedButton(onClick = { }, enabled = false) { Text("Instagram · 今後") }
                OutlinedButton(onClick = { }, enabled = false) { Text("Web · 今後") }
            }

            searchState.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        text = message,
                        modifier = Modifier.padding(16.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (searchState.results.isNotEmpty()) {
                Text(
                    text = "検索結果 · ${searchState.results.size}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                )
                searchState.results.forEach { item ->
                    SearchResultCard(
                        item = item,
                        onImport = { onImportResult(item) },
                        onOpenSource = {
                            runCatching {
                                context.startActivity(
                                    Intent(Intent.ACTION_VIEW, Uri.parse(item.url)),
                                )
                            }
                        },
                    )
                }
            } else if (!searchState.searching) {
                Text(
                    text = "検索すると、ここにYouTubeの候補が表示されます。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (developerOpen) {
                DeveloperStatusCard(acquisitionState)
            }

            Text(
                text = "最近追加したメディア",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            if (acquisitionState.savedPath != null) {
                SavedMiniPlayer(
                    path = acquisitionState.savedPath,
                    title = acquisitionState.savedTitle ?: "保存済み音声",
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
private fun SearchResultCard(
    item: SearchMediaItem,
    onImport: () -> Unit,
    onOpenSource: () -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(width = 96.dp, height = 58.dp)
                        .background(
                            brush = Brush.linearGradient(
                                listOf(Color(0xFF11263F), Color(0xFF251B42)),
                            ),
                            shape = RoundedCornerShape(12.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "▶",
                        style = MaterialTheme.typography.headlineSmall,
                        color = Color(0xFF57D8FF),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = item.title,
                        fontWeight = FontWeight.Bold,
                    )
                    if (item.author.isNotBlank()) {
                        Text(
                            text = item.author,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Text(
                        text = item.provider.replaceFirstChar { it.uppercase() },
                        color = Color(0xFF57D8FF),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport) {
                    Text("WMSに追加")
                }
                OutlinedButton(onClick = onOpenSource) {
                    Text("元サイト")
                }
            }
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
            Text(
                text = "検索Provider: YouTube / WMS Media Worker",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
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
                            enabled = state.engineReady &&
                                !state.updatingYtdlp &&
                                !state.diagnosing &&
                                !state.probing &&
                                !state.acquiring,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text(if (state.updatingYtdlp) "Updating…" else "Update yt-dlp stable")
                        }
                        OutlinedButton(
                            onClick = onDiagnostics,
                            enabled = state.engineReady &&
                                !state.updatingYtdlp &&
                                !state.diagnosing &&
                                !state.probing &&
                                !state.acquiring,
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
private fun SavedMiniPlayer(path: String, title: String) {
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
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(58.dp)
                        .background(
                            brush = Brush.radialGradient(
                                listOf(Color(0x6657D8FF), Color(0x221D64FF), Color.Transparent),
                            ),
                            shape = RoundedCornerShape(18.dp),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Image(
                        painter = painterResource(R.drawable.wms_emblem),
                        contentDescription = null,
                        modifier = Modifier.size(42.dp),
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(title, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Local · WMS",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
            Button(
                onClick = {
                    if (player.isPlaying) player.pause() else player.play()
                },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (isPlaying) "Pause" else "Play")
            }
        }
    }
}

@Composable
private fun BottomNavigationPreview() {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = { }) { Text("検索") }
            TextButton(onClick = { }, enabled = false) { Text("Library") }
            TextButton(onClick = { }, enabled = false) { Text("Playlist") }
        }
    }
}

private val WmsDarkColors = darkColorScheme(
    primary = Color(0xFF57D8FF),
    onPrimary = Color(0xFF041019),
    background = Color(0xFF05070D),
    onBackground = Color(0xFFF4F7FF),
    surface = Color(0xFF111620),
    onSurface = Color(0xFFF4F7FF),
    surfaceVariant = Color(0xFF1A2230),
    onSurfaceVariant = Color(0xFFAEBBCB),
    secondary = Color(0xFF9B6BFF),
    error = Color(0xFFFFB4AB),
)

@Composable
private fun WmsTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = WmsDarkColors,
        content = content,
    )
}

private fun isDirectUrl(value: String): Boolean = URL_PATTERN.matches(value.trim())

private val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
