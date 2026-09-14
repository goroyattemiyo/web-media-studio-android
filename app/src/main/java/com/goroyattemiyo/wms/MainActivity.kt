package com.goroyattemiyo.wms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
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
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.session.MediaController
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.appearance.AppearanceSettings
import com.goroyattemiyo.wms.appearance.AppearanceViewModel
import com.goroyattemiyo.wms.acquisition.AcquisitionPreset
import com.goroyattemiyo.wms.library.LibraryViewModel
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playlist.PlaylistMediaItem
import com.goroyattemiyo.wms.playlist.PlaylistSummary
import com.goroyattemiyo.wms.playlist.PlaylistViewModel
import com.goroyattemiyo.wms.ui.appearance.AppearanceScreen
import com.goroyattemiyo.wms.ui.components.formatFileSize
import com.goroyattemiyo.wms.ui.components.formatPlaybackTime
import com.goroyattemiyo.wms.ui.components.isDirectUrl
import com.goroyattemiyo.wms.ui.home.SearchHome
import com.goroyattemiyo.wms.ui.navigation.AppTab
import com.goroyattemiyo.wms.ui.navigation.BottomNavigation
import com.goroyattemiyo.wms.ui.playback.PlaybackRequest
import com.goroyattemiyo.wms.ui.playback.SavedMiniPlayer
import com.goroyattemiyo.wms.ui.playback.rememberPlaybackController
import com.goroyattemiyo.wms.ui.player.NowPlayingScreen
import com.goroyattemiyo.wms.ui.theme.WmsTheme
import java.io.File

class MainActivity : ComponentActivity() {
    private val acquisitionViewModel by viewModels<GateA0ViewModel>()
    private val libraryViewModel by viewModels<LibraryViewModel>()
    private val playlistViewModel by viewModels<PlaylistViewModel>()
    private val searchViewModel by viewModels<SearchViewModel>()
    private val appearanceViewModel by viewModels<AppearanceViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        consumeShareIntent(intent)
        setContent {
            val appearance by appearanceViewModel.settings.collectAsStateWithLifecycle(
                initialValue = AppearanceSettings(),
            )
            WmsTheme(appearance.skin) {
                WmsRoot(
                    acquisitionViewModel,
                    searchViewModel,
                    libraryViewModel,
                    playlistViewModel,
                    appearance,
                    appearanceViewModel,
                )
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
    appearance: AppearanceSettings,
    appearanceViewModel: AppearanceViewModel,
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
    var nextPlaybackRequestId by remember { mutableIntStateOf(0) }
    var playbackRequest by remember { mutableStateOf<PlaybackRequest?>(null) }
    var selectedTab by remember { mutableStateOf(AppTab.SEARCH) }
    var nowPlayingOpen by remember { mutableStateOf(false) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var importPlaylistId by remember { mutableStateOf<String?>(null) }
    var initializedImportUrl by remember { mutableStateOf<String?>(null) }

    val selectedMedia = libraryMedia.firstOrNull { it.id == selectedMediaId }
        ?: libraryMedia.firstOrNull()
    val playbackController = rememberPlaybackController()

    fun requestPlayback(media: MediaEntity, queue: List<MediaEntity>) {
        nextPlaybackRequestId += 1
        playbackRequest = PlaybackRequest(nextPlaybackRequestId, media.id, queue)
    }

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
                if (appearanceOpen) {
                    AppearanceScreen(
                        settings = appearance,
                        onBack = { appearanceOpen = false },
                        onSkinSelected = appearanceViewModel::selectSkin,
                        onVisualizerSelected = appearanceViewModel::selectVisualizer,
                        onReducedMotionChanged = appearanceViewModel::setReducedMotion,
                    )
                } else if (nowPlayingOpen) {
                    NowPlayingScreen(
                        title = selectedMedia?.title.orEmpty(),
                        onBack = { nowPlayingOpen = false },
                        visualizerMode = if (appearance.reducedMotion) {
                            VisualizerMode.MINIMAL
                        } else {
                            appearance.visualizer
                        },
                        reducedMotion = appearance.reducedMotion,
                        onVisualizerSelected = appearanceViewModel::selectVisualizer,
                        controller = playbackController,
                        isVideo = selectedMedia?.mediaType == "VIDEO",
                    )
                } else when (selectedTab) {
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
                        onOpenAppearance = { appearanceOpen = true },
                        developerOpen = developerOpen,
                        recentMedia = libraryMedia.take(3),
                        onPlayRecent = { media ->
                            playlistViewModel.selectPlaylist(null)
                            libraryViewModel.select(media)
                            requestPlayback(media, listOf(media))
                        },
                    )
                    AppTab.LIBRARY -> LibraryScreen(
                        media = libraryMedia,
                        selectedMediaId = selectedMedia?.id,
                        errorMessage = libraryError,
                        onPlay = { media ->
                            playlistViewModel.selectPlaylist(null)
                            libraryViewModel.select(media)
                            requestPlayback(media, listOf(media))
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
                            requestPlayback(media, playlistItems.map { it.media })
                        },
                        onClearError = playlistViewModel::clearError,
                    )
                }
            }

            if (!appearanceOpen) {
                selectedMedia?.let { media ->
                    SavedMiniPlayer(
                        controller = playbackController,
                        media = media,
                        playbackRequest = playbackRequest,
                        onPositionChanged = { positionMs ->
                            libraryViewModel.savePosition(media.id, positionMs)
                        },
                        onMediaTransition = { mediaId ->
                            libraryMedia.firstOrNull { it.id == mediaId }?.let(libraryViewModel::select)
                        },
                        onOpenNowPlaying = { nowPlayingOpen = true },
                        expanded = nowPlayingOpen,
                    )
                }
                BottomNavigation(
                    selectedTab = selectedTab,
                    onSelect = {
                        nowPlayingOpen = false
                        selectedTab = it
                    },
                )
            }
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
            onSave = { preset -> acquisitionViewModel.acquire(preset, importPlaylistId) },
            onCancel = acquisitionViewModel::cancelAcquisition,
            onUpdateYoutubeDl = acquisitionViewModel::updateYoutubeDl,
            onDiagnostics = acquisitionViewModel::runDiagnostics,
            onCloseAndPlay = {
                importOpen = false
                val savedMedia = acquisitionState.savedPath?.let { savedPath ->
                    libraryMedia.firstOrNull {
                        File(it.localPath).absolutePath == File(savedPath).absolutePath
                    }
                } ?: selectedMedia
                savedMedia?.let { requestPlayback(it, listOf(it)) }
            },
        )
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
    onSave: (AcquisitionPreset) -> Unit,
    onCancel: () -> Unit,
    onUpdateYoutubeDl: () -> Unit,
    onDiagnostics: () -> Unit,
    onCloseAndPlay: () -> Unit,
) {
    var showDeveloper by remember { mutableStateOf(false) }
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FormatChoice(
                    label = "Audio",
                    detail = if (selectedPreset == AcquisitionPreset.M4A_192) "M4A" else "MP3 192",
                    selected = selectedPreset.mediaType == "AUDIO",
                    enabled = !state.acquiring,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPreset = AcquisitionPreset.MP3_192 },
                )
                FormatChoice(
                    label = "Video",
                    detail = "MP4",
                    selected = selectedPreset == AcquisitionPreset.VIDEO_MP4,
                    enabled = !state.acquiring,
                    modifier = Modifier.weight(1f),
                    onClick = { selectedPreset = AcquisitionPreset.VIDEO_MP4 },
                )
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
