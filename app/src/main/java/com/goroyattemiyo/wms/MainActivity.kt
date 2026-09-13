package com.goroyattemiyo.wms

import android.content.ComponentName
import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.Canvas
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import androidx.core.content.ContextCompat
import com.goroyattemiyo.wms.playback.PlaybackService
import com.goroyattemiyo.wms.playback.AudioAnalysisFrame
import com.goroyattemiyo.wms.playback.LightweightVisualizerRenderer
import com.goroyattemiyo.wms.playback.PcmAudioAnalysisBus
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.playback.toPlaybackMediaItem
import com.goroyattemiyo.wms.appearance.AppearanceSettings
import com.goroyattemiyo.wms.appearance.AppearanceViewModel
import com.goroyattemiyo.wms.acquisition.AcquisitionPreset
import com.goroyattemiyo.wms.search.SearchMediaItem
import com.goroyattemiyo.wms.library.LibraryViewModel
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playlist.PlaylistMediaItem
import com.goroyattemiyo.wms.playlist.PlaylistSummary
import com.goroyattemiyo.wms.playlist.PlaylistViewModel
import com.goroyattemiyo.wms.ui.appearance.AppearanceScreen
import com.goroyattemiyo.wms.ui.components.formatDuration
import com.goroyattemiyo.wms.ui.components.formatFileSize
import com.goroyattemiyo.wms.ui.components.formatPlaybackTime
import com.goroyattemiyo.wms.ui.navigation.AppTab
import com.goroyattemiyo.wms.ui.navigation.BottomNavigation
import com.goroyattemiyo.wms.ui.playback.PlaybackRequest
import com.goroyattemiyo.wms.ui.theme.WmsTheme
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

@Composable
private fun SearchHome(
    acquisitionState: GateA0UiState,
    searchState: SearchUiState,
    searchText: String,
    onSearchTextChanged: (String) -> Unit,
    onSubmit: () -> Unit,
    onImportResult: (SearchMediaItem) -> Unit,
    onOpenDeveloper: () -> Unit,
    onOpenAppearance: () -> Unit,
    developerOpen: Boolean,
    recentMedia: List<MediaEntity>,
    onPlayRecent: (MediaEntity) -> Unit,
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }

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
                Box {
                    TextButton(onClick = { menuOpen = true }) { Text("⋮") }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("Appearance") },
                            onClick = {
                                menuOpen = false
                                onOpenAppearance()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Developer Tools") },
                            onClick = {
                                menuOpen = false
                                onOpenDeveloper()
                            },
                        )
                    }
                }
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
                Button(onClick = { }) { Text("YouTube · 端末内検索") }
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

            if (recentMedia.isNotEmpty() && searchState.results.isEmpty()) {
                Text("最近追加", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                recentMedia.forEach { media ->
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Image(
                                painter = painterResource(R.drawable.wms_emblem),
                                contentDescription = null,
                                modifier = Modifier.size(46.dp),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${media.provider} · ${media.mediaType.lowercase()}",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    style = MaterialTheme.typography.bodySmall,
                                )
                            }
                            TextButton(onClick = { onPlayRecent(media) }) { Text("再生") }
                        }
                    }
                }
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
                    Text(
                        item.url,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.labelSmall,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
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

@Composable
private fun SavedMiniPlayer(
    controller: MediaController?,
    media: MediaEntity,
    playbackRequest: PlaybackRequest?,
    onPositionChanged: (Long) -> Unit,
    onMediaTransition: (String) -> Unit,
    onOpenNowPlaying: () -> Unit,
    expanded: Boolean,
) {
    val fileAvailable = remember(media.localPath) {
        File(media.localPath).let { it.exists() && it.length() > 0L }
    }
    var isPlaying by remember { mutableStateOf(false) }
    var playbackState by remember { mutableIntStateOf(Player.STATE_IDLE) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var durationMs by remember { mutableLongStateOf(0L) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekFraction by remember { mutableFloatStateOf(0f) }
    var lastPersistedPositionMs by remember(media.id) { mutableLongStateOf(media.lastPositionMs) }

    DisposableEffect(controller) {
        if (controller == null) return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(state: Int) {
                playbackState = state
            }

            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                item?.mediaId?.takeIf(String::isNotBlank)?.let(onMediaTransition)
            }
        }
        controller.addListener(listener)
        isPlaying = controller.isPlaying
        playbackState = controller.playbackState
        controller.currentMediaItem?.mediaId?.takeIf(String::isNotBlank)?.let(onMediaTransition)
        onDispose {
            controller.removeListener(listener)
        }
    }

    LaunchedEffect(controller) {
        while (true) {
            val knownDuration = controller?.duration ?: C.TIME_UNSET
            durationMs = if (knownDuration == C.TIME_UNSET || knownDuration < 0L) {
                0L
            } else {
                knownDuration
            }
            if (!isSeeking) {
                positionMs = (controller?.currentPosition ?: 0L)
                    .coerceIn(0L, durationMs.coerceAtLeast(0L))
            }
            if (controller?.isPlaying == true && kotlin.math.abs(positionMs - lastPersistedPositionMs) >= 5_000L) {
                lastPersistedPositionMs = positionMs
                onPositionChanged(positionMs)
            }
            delay(if (controller?.isPlaying == true) 250L else 750L)
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

    LaunchedEffect(controller, playbackRequest) {
        if (controller != null && playbackRequest != null) {
            val playableQueue = playbackRequest.queue.filter { item ->
                File(item.localPath).let { it.exists() && it.length() > 0L }
            }
            val startIndex = playableQueue.indexOfFirst { it.id == playbackRequest.mediaId }
            if (startIndex >= 0) {
                val requestedMedia = playableQueue[startIndex]
                controller.setMediaItems(
                    playableQueue.map(MediaEntity::toPlaybackMediaItem),
                    startIndex,
                    requestedMedia.lastPositionMs.coerceAtLeast(0L),
                )
                controller.prepare()
                controller.play()
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
                        text = media.title,
                        fontWeight = FontWeight.Bold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        statusText,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    TextButton(onClick = onOpenNowPlaying) { Text("Now Playing") }
                }
                if (!expanded) {
                    TextButton(
                        onClick = {
                            if (controller?.isPlaying == true) controller.pause() else controller?.play()
                        },
                        enabled = controller != null && fileAvailable,
                    ) { Text(if (isPlaying) "停止" else "再生") }
                    TextButton(
                        onClick = { controller?.seekToNextMediaItem() },
                        enabled = controller?.hasNextMediaItem() == true,
                    ) { Text("次") }
                }
            }

            if (expanded) {
                Slider(
                value = displayedFraction,
                onValueChange = {
                    isSeeking = true
                    seekFraction = it
                },
                onValueChangeFinished = {
                    val targetMs = (seekFraction * durationMs).toLong().coerceIn(0L, durationMs)
                    controller?.seekTo(targetMs)
                    positionMs = targetMs
                    lastPersistedPositionMs = targetMs
                    onPositionChanged(targetMs)
                    isSeeking = false
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = controller != null && fileAvailable && durationMs > 0L,
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
                        val targetMs = ((controller?.currentPosition ?: 0L) - 10_000L).coerceAtLeast(0L)
                        controller?.seekTo(targetMs)
                        positionMs = targetMs
                        lastPersistedPositionMs = targetMs
                        onPositionChanged(targetMs)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = controller != null && fileAvailable && durationMs > 0L,
                ) {
                    Text("−10秒")
                }
                Button(
                    onClick = {
                        if (controller?.isPlaying == true) {
                            controller.pause()
                            lastPersistedPositionMs = controller.currentPosition
                            onPositionChanged(controller.currentPosition)
                        } else {
                            if (controller?.playbackState == Player.STATE_ENDED) controller.seekTo(0L)
                            controller?.play()
                        }
                    },
                    modifier = Modifier.weight(1.4f),
                    enabled = controller != null && fileAvailable,
                ) {
                    Text(if (isPlaying) "一時停止" else "再生")
                }
                OutlinedButton(
                    onClick = {
                        val targetMs = ((controller?.currentPosition ?: 0L) + 10_000L).coerceAtMost(durationMs)
                        controller?.seekTo(targetMs)
                        positionMs = targetMs
                        lastPersistedPositionMs = targetMs
                        onPositionChanged(targetMs)
                    },
                    modifier = Modifier.weight(1f),
                    enabled = controller != null && fileAvailable && durationMs > 0L,
                ) {
                    Text("+10秒")
                }
            }
                Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = { controller?.seekToPreviousMediaItem() },
                    modifier = Modifier.weight(1f),
                    enabled = controller?.hasPreviousMediaItem() == true,
                ) {
                    Text("前へ")
                }
                OutlinedButton(
                    onClick = { controller?.seekToNextMediaItem() },
                    modifier = Modifier.weight(1f),
                    enabled = controller?.hasNextMediaItem() == true,
                ) {
                    Text("次へ")
                }
            }
            }
        }
    }
}

@Composable
private fun rememberPlaybackController(): MediaController? {
    val context = LocalContext.current
    var controller by remember { mutableStateOf<MediaController?>(null) }

    DisposableEffect(context) {
        val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        val future = MediaController.Builder(context, token).buildAsync()
        future.addListener(
            { controller = runCatching { future.get() }.getOrNull() },
            ContextCompat.getMainExecutor(context),
        )
        onDispose {
            controller = null
            MediaController.releaseFuture(future)
        }
    }
    return controller
}

@Composable
private fun NowPlayingScreen(
    title: String,
    onBack: () -> Unit,
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    onVisualizerSelected: (String) -> Unit,
    controller: MediaController?,
    isVideo: Boolean,
) {
    val analysisFrame by PcmAudioAnalysisBus.frames.collectAsStateWithLifecycle(
        initialValue = AudioAnalysisFrame(),
    )
    val phase = if (reducedMotion) {
        0f
    } else {
        val transition = rememberInfiniteTransition(label = "wms-visualizer")
        val animatedPhase by transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1_800, easing = LinearEasing)),
            label = "visualizer-phase",
        )
        animatedPhase
    }
    val renderState = LightweightVisualizerRenderer.render(
        visualizerMode,
        analysisFrame.copy(phase = phase),
    )
    val visualPrimary = MaterialTheme.colorScheme.primary
    val visualSecondary = MaterialTheme.colorScheme.secondary
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            TextButton(onClick = onBack) { Text("戻る") }
        }
        Text("Now Playing", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Box(
            modifier = Modifier
                .size(240.dp)
                .background(
                    Brush.radialGradient(
                        listOf(Color(0xAA57D8FF), Color(0x553B55FF), Color.Transparent),
                    ),
                    RoundedCornerShape(48.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (isVideo && controller != null) {
                AndroidView(
                    factory = { context ->
                        PlayerView(context).apply {
                            useController = false
                            player = controller
                        }
                    },
                    update = { it.player = controller },
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(36.dp)),
                )
            } else {
                Canvas(Modifier.fillMaxSize()) {
                    drawCircle(
                        color = visualPrimary.copy(alpha = renderState.emphasis),
                        radius = size.minDimension * (0.32f + renderState.emphasis * 0.08f),
                        style = Stroke(width = 5.dp.toPx()),
                    )
                    val step = size.width / (renderState.values.size + 1)
                    renderState.values.forEachIndexed { index, value ->
                        val x = step * (index + 1)
                        val halfHeight = size.height * value * 0.22f
                        drawLine(
                            color = visualSecondary.copy(alpha = 0.35f + value * 0.6f),
                            start = androidx.compose.ui.geometry.Offset(x, center.y - halfHeight),
                            end = androidx.compose.ui.geometry.Offset(x, center.y + halfHeight),
                            strokeWidth = 4.dp.toPx(),
                        )
                    }
                }
                Image(
                    painter = painterResource(R.drawable.wms_emblem),
                    contentDescription = "WMS",
                    modifier = Modifier.size(150.dp),
                )
            }
        }
        Text(
            title.ifBlank { "WMS Local Player" },
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            "端末内メディア · MediaSession",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(if (isVideo) "Video · MP4" else "Visualizer: ${visualizerMode.id}", fontWeight = FontWeight.Bold)
        if (reducedMotion) {
            Text(
                "モーション軽減中 · minimal を使用",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        if (!isVideo) Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            VisualizerMode.entries.forEach { mode ->
                OutlinedButton(
                    onClick = { onVisualizerSelected(mode.id) },
                    enabled = !reducedMotion,
                ) {
                    Text(if (mode == visualizerMode) "✓ ${mode.id}" else mode.id)
                }
            }
        }
    }
}

private fun isDirectUrl(value: String): Boolean = URL_PATTERN.matches(value.trim())

private val URL_PATTERN = Regex("https?://[^\\s]+", RegexOption.IGNORE_CASE)
