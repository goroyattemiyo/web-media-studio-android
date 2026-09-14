package com.goroyattemiyo.wms.ui.home

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
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
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import androidx.media3.session.MediaController
import com.goroyattemiyo.wms.GateA0UiState
import com.goroyattemiyo.wms.R
import com.goroyattemiyo.wms.SearchUiState
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playback.VisualizerMode
import com.goroyattemiyo.wms.search.SearchMediaItem
import com.goroyattemiyo.wms.ui.components.formatDuration
import com.goroyattemiyo.wms.ui.components.isDirectUrl
import com.goroyattemiyo.wms.ui.media.MediaArtwork
import com.goroyattemiyo.wms.ui.media.toMediaPresentation
import java.net.URL
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Composable
fun SearchHome(
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
    currentMedia: MediaEntity?,
    playbackController: MediaController?,
    visualizerMode: VisualizerMode,
    reducedMotion: Boolean,
    onOpenPlayer: () -> Unit,
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

            currentMedia?.let { media ->
                CurrentMediaStage(
                    media = media,
                    controller = playbackController,
                    visualizerMode = visualizerMode,
                    reducedMotion = reducedMotion,
                    onOpenPlayer = onOpenPlayer,
                )
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
                    val presentation = remember(media) { media.toMediaPresentation() }
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            MediaArtwork(
                                media = presentation,
                                contentDescription = presentation.title,
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(media.title, maxLines = 2, overflow = TextOverflow.Ellipsis)
                                Text(
                                    "${presentation.secondaryLabel} · ${media.mediaType.lowercase()}",
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
