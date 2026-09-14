package com.goroyattemiyo.wms

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.appearance.AppearanceSettings
import com.goroyattemiyo.wms.appearance.AppearanceViewModel
import com.goroyattemiyo.wms.library.LibraryViewModel
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playlist.PlaylistViewModel
import com.goroyattemiyo.wms.ui.appearance.AppearanceScreen
import com.goroyattemiyo.wms.ui.components.isDirectUrl
import com.goroyattemiyo.wms.ui.home.SearchHome
import com.goroyattemiyo.wms.ui.importer.ImportSheet
import com.goroyattemiyo.wms.ui.library.LibraryScreen
import com.goroyattemiyo.wms.ui.library.PlaylistScreen
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

    BackHandler(enabled = appearanceOpen || nowPlayingOpen) {
        when {
            appearanceOpen -> appearanceOpen = false
            nowPlayingOpen -> nowPlayingOpen = false
        }
    }

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
                        media = selectedMedia,
                        onBack = { nowPlayingOpen = false },
                        visualizerMode = if (appearance.reducedMotion) {
                            VisualizerMode.MINIMAL
                        } else {
                            appearance.visualizer
                        },
                        reducedMotion = appearance.reducedMotion,
                        onVisualizerSelected = appearanceViewModel::selectVisualizer,
                        controller = playbackController,
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
                        currentMedia = selectedMedia,
                        playbackController = playbackController,
                        visualizerMode = if (appearance.reducedMotion) {
                            VisualizerMode.MINIMAL
                        } else {
                            appearance.visualizer
                        },
                        reducedMotion = appearance.reducedMotion,
                        onOpenPlayer = { nowPlayingOpen = true },
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
