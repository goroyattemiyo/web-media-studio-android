package com.goroyattemiyo.wms

import android.content.Intent
import android.os.Bundle
import android.os.SystemClock
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.goroyattemiyo.wms.appearance.AppearanceSettings
import com.goroyattemiyo.wms.appearance.AppearanceViewModel
import com.goroyattemiyo.wms.library.LibraryViewModel
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.playback.toPlaybackMediaItem
import com.goroyattemiyo.wms.playlist.PlaylistViewModel
import com.goroyattemiyo.wms.ui.appearance.AppearanceScreen
import com.goroyattemiyo.wms.ui.components.isDirectUrl
import com.goroyattemiyo.wms.ui.home.SearchHome
import com.goroyattemiyo.wms.ui.importer.ImportSheet
import com.goroyattemiyo.wms.ui.library.LibraryScreen
import com.goroyattemiyo.wms.ui.navigation.AppTab
import com.goroyattemiyo.wms.ui.navigation.BottomNavigation
import com.goroyattemiyo.wms.ui.playback.PlaybackRequest
import com.goroyattemiyo.wms.ui.playback.SavedMiniPlayer
import com.goroyattemiyo.wms.ui.playback.rememberPlaybackController
import com.goroyattemiyo.wms.ui.player.NowPlayingScreen
import com.goroyattemiyo.wms.ui.sound.SoundScreen
import com.goroyattemiyo.wms.ui.startup.StartupExperiencePreferences
import com.goroyattemiyo.wms.ui.startup.StartupExperienceScreen
import com.goroyattemiyo.wms.ui.startup.StartupExperienceSession
import com.goroyattemiyo.wms.ui.startup.StartupSonicLogo
import com.goroyattemiyo.wms.ui.theme.WmsTheme
import java.io.File
import kotlinx.coroutines.delay

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
            WmsTheme(appearance.skin, appearance.backgroundStyle, appearance.backgroundImagePath, appearance.backgroundBlur) {
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
    val context = LocalContext.current
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
    var playbackQueue by remember { mutableStateOf<List<MediaEntity>>(emptyList()) }
    var selectedTab by remember { mutableStateOf(AppTab.HOME) }
    var lastContentTab by remember { mutableStateOf(AppTab.HOME) }
    var appearanceOpen by remember { mutableStateOf(false) }
    var importPlaylistId by remember { mutableStateOf<String?>(null) }
    var initializedImportUrl by remember { mutableStateOf<String?>(null) }

    val coldStart = remember { StartupExperienceSession.consumeColdStart() }
    val startupStartedAtMs = remember { SystemClock.elapsedRealtime() }
    var startupAnimationEnabled by remember {
        mutableStateOf(StartupExperiencePreferences.animationEnabled(context))
    }
    var startupSoundEnabled by remember {
        mutableStateOf(StartupExperiencePreferences.soundEnabled(context))
    }
    var startupVisible by remember {
        mutableStateOf(coldStart && startupAnimationEnabled)
    }

    val selectedMedia = libraryMedia.firstOrNull { it.id == selectedMediaId }
        ?: libraryMedia.firstOrNull()
    val playbackController = rememberPlaybackController()
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        libraryViewModel.importUris(uris)
    }
    val backgroundImagePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let(appearanceViewModel::importBackgroundImage)
    }

    LaunchedEffect(coldStart, startupSoundEnabled) {
        if (coldStart && startupSoundEnabled) {
            StartupSonicLogo.playIfAllowed(context)
        }
    }

    LaunchedEffect(startupVisible, acquisitionState.engineCode) {
        if (!startupVisible) return@LaunchedEffect
        if (acquisitionState.engineCode != "STARTING") {
            val elapsedMs = SystemClock.elapsedRealtime() - startupStartedAtMs
            val remainingMs = (MIN_STARTUP_EXPERIENCE_MS - elapsedMs).coerceAtLeast(0L)
            if (remainingMs > 0L) delay(remainingMs)
            startupVisible = false
        }
    }

    LaunchedEffect(startupAnimationEnabled) {
        if (!startupAnimationEnabled) startupVisible = false
    }

    fun requestPlayback(media: MediaEntity, queue: List<MediaEntity>) {
        nextPlaybackRequestId += 1
        playbackQueue = queue
        playbackRequest = PlaybackRequest(nextPlaybackRequestId, media.id, queue)
    }

    fun navigateTo(tab: AppTab) {
        when (tab) {
            AppTab.PLAYER -> {
                if (selectedTab != AppTab.PLAYER) {
                    lastContentTab = selectedTab
                }
                selectedTab = AppTab.PLAYER
            }
            AppTab.HOME, AppTab.LIBRARY, AppTab.SOUND -> {
                lastContentTab = tab
                selectedTab = tab
            }
        }
    }

    BackHandler(enabled = appearanceOpen || selectedTab == AppTab.PLAYER) {
        if (appearanceOpen) {
            appearanceOpen = false
        } else {
            selectedTab = lastContentTab
        }
    }

    LaunchedEffect(playbackController, playbackRequest?.requestId) {
        val controller = playbackController ?: return@LaunchedEffect
        val request = playbackRequest ?: return@LaunchedEffect
        val playableQueue = request.queue.filter { item ->
            File(item.localPath).let { it.exists() && it.length() > 0L }
        }
        val startIndex = playableQueue.indexOfFirst { it.id == request.mediaId }
        if (startIndex >= 0) {
            val requestedMedia = playableQueue[startIndex]
            playbackQueue = playableQueue
            controller.setMediaItems(
                playableQueue.map(MediaEntity::toPlaybackMediaItem),
                startIndex,
                requestedMedia.lastPositionMs.coerceAtLeast(0L),
            )
            controller.prepare()
            controller.play()
        }
    }

    DisposableEffect(playbackController, libraryMedia) {
        val controller = playbackController ?: return@DisposableEffect onDispose {}
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(item: androidx.media3.common.MediaItem?, reason: Int) {
                val mediaId = item?.mediaId?.takeIf(String::isNotBlank) ?: return
                libraryMedia.firstOrNull { it.id == mediaId }?.let(libraryViewModel::select)
            }
        }
        controller.addListener(listener)
        controller.currentMediaItem?.mediaId
            ?.takeIf(String::isNotBlank)
            ?.let { mediaId -> libraryMedia.firstOrNull { it.id == mediaId } }
            ?.let(libraryViewModel::select)
        onDispose { controller.removeListener(listener) }
    }

    LaunchedEffect(Unit) {
        libraryViewModel.importedMedia.collect { imported ->
            if (imported.isNotEmpty()) {
                playlistViewModel.selectPlaylist(null)
                requestPlayback(imported.first(), imported)
            }
        }
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

    val startupStatus = when {
        acquisitionState.engineCode == "STARTING" -> "PREPARING MEDIA ENGINE"
        acquisitionState.updatingYtdlp -> "CHECKING MEDIA ENGINE"
        else -> "OPENING WMS"
    }

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = androidx.compose.ui.graphics.Color.Transparent,
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.weight(1f)) {
                    if (appearanceOpen) {
                        AppearanceScreen(
                            settings = appearance,
                            startupAnimationEnabled = startupAnimationEnabled,
                            startupSoundEnabled = startupSoundEnabled,
                            onBack = { appearanceOpen = false },
                            onSkinSelected = appearanceViewModel::selectSkin,
                            onBackgroundSelected = appearanceViewModel::selectBackground,
                            onVisualizerSelected = appearanceViewModel::selectVisualizer,
                            onReducedMotionChanged = appearanceViewModel::setReducedMotion,
                            onStartupAnimationChanged = { enabled ->
                                startupAnimationEnabled = enabled
                                StartupExperiencePreferences.setAnimationEnabled(context, enabled)
                            },
                            onStartupSoundChanged = { enabled ->
                                startupSoundEnabled = enabled
                                StartupExperiencePreferences.setSoundEnabled(context, enabled)
                            },
                            onChooseBackgroundImage = { backgroundImagePicker.launch(arrayOf("image/*")) },
                            onClearBackgroundImage = appearanceViewModel::clearBackgroundImage,
                            onBackgroundBlurChanged = appearanceViewModel::setBackgroundBlur,
                        )
                    } else when (selectedTab) {
                        AppTab.HOME -> SearchHome(
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
                            libraryCount = libraryMedia.size,
                            onPlayRecent = { media ->
                                playlistViewModel.selectPlaylist(null)
                                libraryViewModel.select(media)
                                requestPlayback(media, libraryMedia)
                            },
                            currentMedia = selectedMedia,
                            skinName = appearance.skin.displayName,
                            appVersion = BuildConfig.VERSION_NAME,
                            playbackController = playbackController,
                            visualizerMode = if (appearance.reducedMotion) {
                                VisualizerMode.MINIMAL
                            } else {
                                appearance.visualizer
                            },
                            reducedMotion = appearance.reducedMotion,
                            onOpenPlayer = { navigateTo(AppTab.PLAYER) },
                            onChooseDeviceMedia = { documentPicker.launch(arrayOf("audio/*", "video/*")) },
                        )
                        AppTab.LIBRARY -> LibraryScreen(
                            media = libraryMedia,
                            selectedMediaId = selectedMedia?.id,
                            errorMessage = libraryError,
                            onPlay = { media ->
                                playlistViewModel.selectPlaylist(null)
                                libraryViewModel.select(media)
                                requestPlayback(media, libraryMedia)
                            },
                            onDelete = libraryViewModel::delete,
                            onClearError = libraryViewModel::clearError,
                            playlists = playlists,
                            selectedPlaylistId = selectedPlaylistId,
                            playlistItems = playlistItems,
                            playlistError = playlistError,
                            onSelectPlaylist = playlistViewModel::selectPlaylist,
                            onCreate = playlistViewModel::create,
                            onRename = playlistViewModel::rename,
                            onDeletePlaylist = playlistViewModel::delete,
                            onAddMedia = playlistViewModel::addMedia,
                            onRemoveMedia = playlistViewModel::removeMedia,
                            onMoveMedia = playlistViewModel::moveMedia,
                            onPlayPlaylist = { media ->
                                libraryViewModel.select(media)
                                requestPlayback(media, playlistItems.map { it.media })
                            },
                            onClearPlaylistError = playlistViewModel::clearError,
                        )
                        AppTab.PLAYER -> NowPlayingScreen(
                            media = selectedMedia,
                            queue = playbackQueue.ifEmpty {
                                if (selectedPlaylistId != null) playlistItems.map { it.media } else libraryMedia
                            },
                            onBack = { selectedTab = lastContentTab },
                            visualizerMode = if (appearance.reducedMotion) {
                                VisualizerMode.MINIMAL
                            } else {
                                appearance.visualizer
                            },
                            reducedMotion = appearance.reducedMotion,
                            onVisualizerSelected = appearanceViewModel::selectVisualizer,
                            controller = playbackController,
                        )
                        AppTab.SOUND -> SoundScreen(controller = playbackController)
                    }
                }

                if (!appearanceOpen) {
                    if (selectedTab != AppTab.PLAYER) {
                        selectedMedia?.let { media ->
                            SavedMiniPlayer(
                                controller = playbackController,
                                media = media,
                                onPositionChanged = { positionMs ->
                                    libraryViewModel.savePosition(media.id, positionMs)
                                },
                                onMediaTransition = { mediaId ->
                                    libraryMedia.firstOrNull { it.id == mediaId }?.let(libraryViewModel::select)
                                },
                                onOpenNowPlaying = { navigateTo(AppTab.PLAYER) },
                            )
                        }
                    }
                    BottomNavigation(
                        selectedTab = selectedTab,
                        onSelect = ::navigateTo,
                    )
                }
            }

            if (startupVisible) {
                StartupExperienceScreen(
                    statusText = startupStatus,
                    modifier = Modifier.fillMaxSize(),
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
                savedMedia?.let { requestPlayback(it, libraryMedia.ifEmpty { listOf(it) }) }
            },
        )
    }
}

private const val MIN_STARTUP_EXPERIENCE_MS = 1_350L
