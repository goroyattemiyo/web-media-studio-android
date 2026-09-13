package com.goroyattemiyo.wms

import android.content.Intent
import android.graphics.BitmapFactory
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.goroyattemiyo.wms.search.SearchMediaItem
import com.goroyattemiyo.wms.library.LibraryViewModel
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playlist.PlaylistMediaItem
import com.goroyattemiyo.wms.playlist.PlaylistSummary
import com.goroyattemiyo.wms.playlist.PlaylistViewModel
import java.io.File
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val acquisitionViewModel by viewModels<GateA0ViewModel>()
    private val libraryViewModel by viewModels<LibraryViewModel>()
    private val playlistViewModel by viewModels<PlaylistViewModel>()
    private val searchViewModel by viewModels<SearchViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShareIntent(intent)
        setContent {
            WmsTheme {
                WmsRoot(acquisitionViewModel, searchViewModel, libraryViewModel, playlistViewModel)
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
    libraryViewModel: LibraryViewModel,
    playlistViewModel: PlaylistViewModel,
) {
    val acquisitionState by acquisitionViewModel.uiState.collectAsStateWithLifecycle()
    val searchState by searchViewModel.uiState.collectAsStateWithLifecycle()
    val libraryMedia by libraryViewModel.media.collectAsStateWithLifecycle()
    val selectedMediaId by libraryViewModel.selectedMediaId.collectAsStateWithLifecycle()
    val libraryError by libraryViewModel.errorMessage.collectAsStateWithLifecycle()
    val playlists by playlistViewModel.playlists.collectAsStateWithLifecycle()
    val selectedPlaylistId by playlistViewModel.selectedPlaylistId.collectAsStateWithLifecycle()
    val playlistItems by playlistViewModel.selectedItems.collectAsStateWithLifecycle()
    val playlistError by playlistViewModel.errorMessage.collectAsStateWithLifecycle()
    var searchText by remember { mutableStateOf("") }
    var importOpen by remember { mutableStateOf(false) }
    var developerOpen by remember { mutableStateOf(false) }
    var playRequest by remember { mutableIntStateOf(0) }
    var selectedTab by remember { mutableStateOf(AppTab.SEARCH) }
    var importPlaylistId by remember { mutableStateOf<String?>(null) }
    var initializedImportUrl by remember { mutableStateOf<String?>(null) }

    val selectedMedia = libraryMedia.firstOrNull { it.id == selectedMediaId }
        ?: libraryMedia.firstOrNull()
    val activeQueueIndex = playlistItems.indexOfFirst { it.media.id == selectedMedia?.id }

    LaunchedEffect(playlists, importPlaylistId) {
        if (importPlaylistId != null && playlists.none { it.id == importPlaylistId }) {
            importPlaylistId = null
        }
    }

    LaunchedEffect(acquisitionState.savedPath, libraryMedia) {
        acquisitionState.savedPath?.let { savedPath ->
            libraryViewModel.selectByPath(savedPath)
        }
    }

    LaunchedEffect(acquisitionState.url, selectedPlaylistId) {
        if (acquisitionState.url.isNotBlank()) {
            if (initializedImportUrl != acquisitionState.url) {
                importPlaylistId = selectedPlaylistId
                initializedImportUrl = acquisitionState.url
            }
            importOpen = true
            if (!acquisitionState.probing && acquisitionState.detectedTitle == null) {
                acquisitionViewModel.probe()
            }
        } else {
            initializedImportUrl = null
        }
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.weight(1f)) {
                when (selectedTab) {
                    AppTab.SEARCH -> SearchHome(
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
                    AppTab.LIBRARY -> LibraryScreen(
                        media = libraryMedia,
                        selectedMediaId = selectedMedia?.id,
                        errorMessage = libraryError,
                        onPlay = { media ->
                            playlistViewModel.selectPlaylist(null)
                            libraryViewModel.select(media)
                            playRequest += 1
                        },
                        onDelete = libraryViewModel::delete,
                        onClearError = libraryViewModel::clearError,
                    )
                    AppTab.PLAYLIST -> PlaylistScreen(
                        playlists = playlists,
                        selectedPlaylistId = selectedPlaylistId,
                        items = playlistItems,
                        libraryMedia = libraryMedia,
                        selectedMediaId = selectedMedia?.id,
                        errorMessage = playlistError,
                        onSelectPlaylist = playlistViewModel::selectPlaylist,
                        onCreate = playlistViewModel::create,
                        onRename = playlistViewModel::rename,
                        onDelete = playlistViewModel::delete,
                        onAddMedia = playlistViewModel::addMedia,
                        onRemoveMedia = playlistViewModel::removeMedia,
                        onMoveMedia = playlistViewModel::moveMedia,
                        onPlay = { media ->
                            libraryViewModel.select(media)
                            playRequest += 1
                        },
                        onClearError = playlistViewModel::clearError,
                    )
                }
            }

            selectedMedia?.let { media ->
                SavedMiniPlayer(
                    path = media.localPath,
                    title = media.title,
                    playRequest = playRequest,
                    initialPositionMs = media.lastPositionMs,
                    onPositionChanged = { positionMs ->
                        libraryViewModel.savePosition(media.id, positionMs)
                    },
                    hasPrevious = activeQueueIndex > 0,
                    hasNext = activeQueueIndex >= 0 && activeQueueIndex < playlistItems.lastIndex,
                    onPrevious = {
                        playlistItems.getOrNull(activeQueueIndex - 1)?.media?.let {
                            libraryViewModel.select(it)
                            playRequest += 1
                        }
                    },
                    onNext = {
                        playlistItems.getOrNull(activeQueueIndex + 1)?.media?.let {
                            libraryViewModel.select(it)
                            playRequest += 1
                        }
                    },
                )
            }
            BottomNavigation(
                selectedTab = selectedTab,
                onSelect = { selectedTab = it },
            )
        }
    }

    if (importOpen && acquisitionState.url.isNotBlank()) {
        ImportSheet(
            state = acquisitionState,
            playlists = playlists,
            selectedPlaylistId = importPlaylistId,
            onDismiss = { importOpen = false },
            onPlaylistSelected = { importPlaylistId = it },
            onRightsChanged = acquisitionViewModel::setRightsConfirmed,
            onSave = { acquisitionViewModel.acquireMp3(importPlaylistId) },
            onCancel = acquisitionViewModel::cancelAcquisition,
            onUpdateYoutubeDl = acquisitionViewModel::updateYoutubeDl,
            onDiagnostics = acquisitionViewModel::runDiagnostics,
            onCloseAndPlay = {
                importOpen = false
                playRequest += 1
            },
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
                        Text("WMS", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
                        Text(
                            "Web Media Studio",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                }
                TextButton(onClick = onOpenDeveloper) { Text("⋮") }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("Find media", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "曲名・アーティスト・動画名で検索。URLを貼ればそのまま取り込みます。",
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
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.url)))
                            }
                        },
                    )
                }
            } else if (!searchState.searching) {
                Text(
                    "検索すると、ここにYouTubeの候補が表示されます。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (developerOpen) {
                DeveloperStatusCard(acquisitionState)
            }

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
                SearchThumbnail(item.thumbnailUrl, item.durationSeconds)
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Text(item.title, fontWeight = FontWeight.Bold)
                    if (item.author.isNotBlank()) {
                        Text(
                            item.author,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            item.provider.replaceFirstChar { it.uppercase() },
                            color = Color(0xFF57D8FF),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        item.durationSeconds?.let {
                            Text(
                                formatDuration(it),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.labelMedium,
                            )
                        }
                    }
                }
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onImport, enabled = item.canDownload) { Text("WMSに追加") }
                OutlinedButton(onClick = onOpenSource) { Text("元サイト") }
            }
        }
    }
}

@Composable
private fun SearchThumbnail(url: String?, durationSeconds: Int?) {
    val bitmap by produceState<android.graphics.Bitmap?>(initialValue = null, key1 = url) {
        value = if (url.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) {
                runCatching {
                    val connection = URL(url).openConnection().apply {
                        connectTimeout = 5_000
                        readTimeout = 7_000
                    }
                    connection.getInputStream().use { BitmapFactory.decodeStream(it) }
                }.getOrNull()
            }
        }
    }

    Box(
        modifier = Modifier
            .size(width = 112.dp, height = 64.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(listOf(Color(0xFF11263F), Color(0xFF251B42))),
            ),
        contentAlignment = Alignment.Center,
    ) {
        if (bitmap != null) {
            Image(
                bitmap = bitmap!!.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop,
            )
        } else {
            Text("▶", style = MaterialTheme.typography.headlineSmall, color = Color(0xFF57D8FF))
        }
        durationSeconds?.let {
            Text(
                text = formatDuration(it),
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(5.dp)
                    .background(Color(0xCC000000), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
                color = Color.White,
                style = MaterialTheme.typography.labelSmall,
            )
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
                "Engine: ${if (state.engineReady) "READY" else state.engineCode}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "yt-dlp: ${state.ytdlpVersion ?: "確認中"}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                "検索Provider: YouTube / 端末内 yt-dlp",
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
    playlists: List<PlaylistSummary>,
    selectedPlaylistId: String?,
    onDismiss: () -> Unit,
    onPlaylistSelected: (String?) -> Unit,
    onRightsChanged: (Boolean) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
    onUpdateYoutubeDl: () -> Unit,
    onDiagnostics: () -> Unit,
    onCloseAndPlay: () -> Unit,
) {
    var showDeveloper by remember { mutableStateOf(false) }

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
                        Text(
                            "Audio · MP3 192 kbps",
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
                Text(state.progressMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            state.errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(5.dp),
                    ) {
                        Text(
                            "ERROR ${state.errorCode ?: "UNKNOWN"}",
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
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text("保存完了", fontWeight = FontWeight.Bold)
                        Text(state.savedTitle ?: "保存済み音声")
                        Button(onClick = onCloseAndPlay, modifier = Modifier.fillMaxWidth()) {
                            Text("閉じて再生")
                        }
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

private enum class AppTab {
    SEARCH,
    LIBRARY,
    PLAYLIST,
}

@Composable
private fun LibraryScreen(
    media: List<MediaEntity>,
    selectedMediaId: String?,
    errorMessage: String?,
    onPlay: (MediaEntity) -> Unit,
    onDelete: (MediaEntity) -> Unit,
    onClearError: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<MediaEntity?>(null) }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
            Text(
                "WMSに保存したメディアは端末内で管理されます。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(message, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onClearError) { Text("閉じる") }
                    }
                }
            }

            if (media.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "保存済みメディアはありません。検索からWMSへ追加してください。",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            media.forEach { item ->
                val available = File(item.localPath).let { it.exists() && it.length() > 0L }
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            item.title,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            buildString {
                                append(item.provider)
                                if (item.durationMs > 0L) append(" · ${formatPlaybackTime(item.durationMs)}")
                                append(" · ${formatFileSize(item.fileSize)}")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        when {
                            !available -> Text(
                                "ファイルが見つかりません",
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodySmall,
                            )
                            item.id == selectedMediaId -> Text(
                                "Mini Playerで選択中",
                                color = MaterialTheme.colorScheme.primary,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { onPlay(item) }, enabled = available) { Text("再生") }
                            OutlinedButton(onClick = { pendingDelete = item }) { Text("削除") }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text("Libraryから削除") },
            text = { Text("「${item.title}」の端末内ファイルも削除します。元に戻せません。") },
            confirmButton = {
                Button(
                    onClick = {
                        pendingDelete = null
                        onDelete(item)
                    },
                ) {
                    Text("削除")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text("戻る") }
            },
        )
    }
}

@Composable
private fun PlaylistScreen(
    playlists: List<PlaylistSummary>,
    selectedPlaylistId: String?,
    items: List<PlaylistMediaItem>,
    libraryMedia: List<MediaEntity>,
    selectedMediaId: String?,
    errorMessage: String?,
    onSelectPlaylist: (String) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDelete: (String) -> Unit,
    onAddMedia: (String, String) -> Unit,
    onRemoveMedia: (String, String) -> Unit,
    onMoveMedia: (String, String, Int) -> Unit,
    onPlay: (MediaEntity) -> Unit,
    onClearError: () -> Unit,
) {
    var createDialogOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf<PlaylistSummary?>(null) }
    var deleting by remember { mutableStateOf<PlaylistSummary?>(null) }
    val selectedPlaylist = playlists.firstOrNull { it.id == selectedPlaylistId }
    val mediaIds = items.mapTo(mutableSetOf()) { it.media.id }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Playlist", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Button(onClick = { createDialogOpen = true }) { Text("新規作成") }
            }

            errorMessage?.let { message ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = onClearError) { Text("閉じる") }
                    }
                }
            }

            if (playlists.isEmpty()) {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        "プレイリストを作成すると、保存済みメディアを好きな順序で再生できます。",
                        modifier = Modifier.padding(18.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                Text("プレイリスト", fontWeight = FontWeight.Bold)
                playlists.forEach { playlist ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            TextButton(
                                onClick = { onSelectPlaylist(playlist.id) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    buildString {
                                        if (playlist.id == selectedPlaylistId) append("✓ ")
                                        append(playlist.name)
                                        append(" (${playlist.itemCount})")
                                    },
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                            TextButton(onClick = { renaming = playlist }) { Text("名前") }
                            TextButton(onClick = { deleting = playlist }) { Text("削除") }
                        }
                    }
                }
            }

            selectedPlaylist?.let { playlist ->
                Text("「${playlist.name}」の再生順", fontWeight = FontWeight.Bold)
                if (items.isEmpty()) {
                    Text("まだ項目がありません。Libraryから追加してください。")
                }
                items.forEachIndexed { index, item ->
                    val available = File(item.media.localPath).let { it.exists() && it.length() > 0L }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            Text(
                                "${index + 1}. ${item.media.title}",
                                fontWeight = FontWeight.Bold,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            if (item.media.id == selectedMediaId) {
                                Text(
                                    "Mini Playerで選択中",
                                    color = MaterialTheme.colorScheme.primary,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(6.dp),
                            ) {
                                Button(onClick = { onPlay(item.media) }, enabled = available) { Text("再生") }
                                OutlinedButton(
                                    onClick = { onMoveMedia(playlist.id, item.media.id, -1) },
                                    enabled = index > 0,
                                ) { Text("↑") }
                                OutlinedButton(
                                    onClick = { onMoveMedia(playlist.id, item.media.id, 1) },
                                    enabled = index < items.lastIndex,
                                ) { Text("↓") }
                                TextButton(onClick = { onRemoveMedia(playlist.id, item.media.id) }) {
                                    Text("外す")
                                }
                            }
                        }
                    }
                }

                val availableToAdd = libraryMedia.filterNot { it.id in mediaIds }
                Text("Libraryから追加", fontWeight = FontWeight.Bold)
                if (availableToAdd.isEmpty()) {
                    Text(
                        "追加できる保存済みメディアはありません。",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                availableToAdd.forEach { media ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Text(
                                media.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                            OutlinedButton(onClick = { onAddMedia(playlist.id, media.id) }) {
                                Text("追加")
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }

    if (createDialogOpen) {
        PlaylistNameDialog(
            title = "プレイリストを作成",
            initialName = "",
            onDismiss = { createDialogOpen = false },
            onConfirm = {
                createDialogOpen = false
                onCreate(it)
            },
        )
    }
    renaming?.let { playlist ->
        PlaylistNameDialog(
            title = "名前を変更",
            initialName = playlist.name,
            onDismiss = { renaming = null },
            onConfirm = {
                renaming = null
                onRename(playlist.id, it)
            },
        )
    }
    deleting?.let { playlist ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("プレイリストを削除") },
            text = { Text("「${playlist.name}」を削除します。Library内のメディアは残ります。") },
            confirmButton = {
                Button(onClick = {
                    deleting = null
                    onDelete(playlist.id)
                }) { Text("削除") }
            },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("戻る") } },
        )
    }
}

@Composable
private fun PlaylistNameDialog(
    title: String,
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var name by remember(initialName) { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { if (it.length <= 80) name = it },
                label = { Text("名前") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = {
            Button(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text("保存") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("戻る") } },
    )
}

@Composable
private fun SavedMiniPlayer(
    path: String,
    title: String,
    playRequest: Int,
    initialPositionMs: Long,
    onPositionChanged: (Long) -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    val context = LocalContext.current
    val player = remember { ExoPlayer.Builder(context).build() }
    val fileAvailable = remember(path) { File(path).let { it.exists() && it.length() > 0L } }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableStateOf(0f) }
    var lastPersistedPositionMs by remember(path) { mutableLongStateOf(initialPositionMs) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
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
        playbackState = Player.STATE_IDLE
        positionMs = 0L
        durationMs = 0L
        isSeeking = false
        if (file.exists() && file.length() > 0L) {
            player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
            player.prepare()
            if (initialPositionMs > 0L) player.seekTo(initialPositionMs)
        }
    }

    LaunchedEffect(player, path) {
        while (true) {
            val knownDuration = player.duration
            durationMs = if (knownDuration == C.TIME_UNSET || knownDuration < 0L) {
                0L
            } else {
                knownDuration
            }
            if (!isSeeking) {
                positionMs = player.currentPosition.coerceIn(0L, durationMs.coerceAtLeast(0L))
            }
            if (player.isPlaying && kotlin.math.abs(positionMs - lastPersistedPositionMs) >= 5_000L) {
                lastPersistedPositionMs = positionMs
                onPositionChanged(positionMs)
            }
            delay(if (player.isPlaying) 250L else 750L)
        }
    }

    val displayedPositionMs = if (isSeeking && durationMs > 0L) {
        (seekFraction * durationMs).toLong()
    } else {
        positionMs
    }
    val displayedFraction = when {
        durationMs <= 0L -> 0f
        isSeeking -> seekFraction
        else -> (positionMs.toDouble() / durationMs.toDouble()).toFloat().coerceIn(0f, 1f)
    }
    val statusText = when {
        !fileAvailable -> "Local · ファイルを開けません"
        playbackState == Player.STATE_BUFFERING -> "Local · 読み込み中"
        playbackState == Player.STATE_READY && isPlaying -> "Local · 再生中"
        playbackState == Player.STATE_READY -> "Local · 一時停止"
        playbackState == Player.STATE_ENDED -> "Local · 再生完了"
        else -> "Local · 準備中"
    }

    LaunchedEffect(playRequest, path) {
        if (playRequest > 0) {
            val file = File(path)
            if (file.exists() && file.length() > 0L) {
                if (player.mediaItemCount == 0) {
                    player.setMediaItem(MediaItem.fromUri(Uri.fromFile(file)))
                    player.prepare()
                }
                player.playWhenReady = true
            }
        }
    }

    Card(
        modifier = Modifier
            .padding(horizontal = 12.dp, vertical = 6.dp)
            .fillMaxWidth(),
    ) {
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
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Text(
                        text = title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            Slider(
                value = displayedFraction,
                onValueChange = {
                    isSeeking = true
                    seekFraction = it
                },
                onValueChangeFinished = {
                    val targetMs = (seekFraction * durationMs).toLong().coerceIn(0L, durationMs)
                    player.seekTo(targetMs)
                    positionMs = targetMs
                    lastPersistedPositionMs = targetMs
                    onPositionChanged(targetMs)
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = fileAvailable && durationMs > 0L,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    formatPlaybackTime(displayedPositionMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    formatPlaybackTime(durationMs),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = {
                        val targetMs = (player.currentPosition - 10_000L).coerceAtLeast(0L)
                        player.seekTo(targetMs)
                        positionMs = targetMs
                        lastPersistedPositionMs = targetMs
                        onPositionChanged(targetMs)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = fileAvailable && durationMs > 0L,
                ) {
                    Text("−10秒")
                }
                Button(
                    onClick = {
                        if (player.isPlaying) {
                            player.pause()
                            lastPersistedPositionMs = player.currentPosition
                            onPositionChanged(player.currentPosition)
                        } else {
                            if (player.playbackState == Player.STATE_ENDED) player.seekTo(0L)
                            player.play()
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    enabled = fileAvailable,
                ) {
                    Text(if (isPlaying) "一時停止" else "再生")
                }
                OutlinedButton(
                    onClick = {
                        val targetMs = (player.currentPosition + 10_000L).coerceAtMost(durationMs)
                        player.seekTo(targetMs)
                        positionMs = targetMs
                        lastPersistedPositionMs = targetMs
                        onPositionChanged(targetMs)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = fileAvailable && durationMs > 0L,
                ) {
                    Text("+10秒")
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onPrevious,
                    modifier = Modifier.weight(1f),
                    enabled = hasPrevious,
                ) {
                    Text("前へ")
                }
                OutlinedButton(
                    onClick = onNext,
                    modifier = Modifier.weight(1f),
                    enabled = hasNext,
                ) {
                    Text("次へ")
                }
            }
        }
    }
}

@Composable
private fun BottomNavigation(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(
                onClick = { onSelect(AppTab.SEARCH) },
                enabled = selectedTab != AppTab.SEARCH,
            ) {
                Text("検索")
            }
            TextButton(
                onClick = { onSelect(AppTab.LIBRARY) },
                enabled = selectedTab != AppTab.LIBRARY,
            ) {
                Text("Library")
            }
            TextButton(
                onClick = { onSelect(AppTab.PLAYLIST) },
                enabled = selectedTab != AppTab.PLAYLIST,
            ) {
                Text("Playlist")
            }
        }
    }
}

private fun formatDuration(totalSeconds: Int): String {
    val seconds = totalSeconds.coerceAtLeast(0)
    val hours = seconds / 3600
    val minutes = (seconds % 3600) / 60
    val remainder = seconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, remainder)
    } else {
        "%d:%02d".format(minutes, remainder)
    }
}

private fun formatPlaybackTime(milliseconds: Long): String =
    formatDuration((milliseconds.coerceAtLeast(0L) / 1_000L).coerceAtMost(Int.MAX_VALUE.toLong()).toInt())

private fun formatFileSize(bytes: Long): String = "%.1f MB".format(bytes.coerceAtLeast(0L) / 1_048_576.0)

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
    MaterialTheme(colorScheme = WmsDarkColors, content = content)
}

private fun isDirectUrl(value: String): Boolean = URL_PATTERN.matches(value.trim())

private val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
