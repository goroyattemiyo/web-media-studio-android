package com.goroyattemiyo.wms.ui.library

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playlist.PlaylistMediaItem
import com.goroyattemiyo.wms.playlist.PlaylistSummary
import com.goroyattemiyo.wms.ui.components.formatFileSize
import com.goroyattemiyo.wms.ui.components.formatPlaybackTime
import com.goroyattemiyo.wms.ui.media.MediaArtwork
import com.goroyattemiyo.wms.ui.media.toMediaPresentation
import java.io.File

private enum class LibrarySection { ALL, PLAYLISTS }

@Composable
fun LibraryScreen(
    media: List<MediaEntity>,
    selectedMediaId: String?,
    errorMessage: String?,
    onPlay: (MediaEntity) -> Unit,
    onDelete: (MediaEntity) -> Unit,
    onClearError: () -> Unit,
    playlists: List<PlaylistSummary>,
    selectedPlaylistId: String?,
    playlistItems: List<PlaylistMediaItem>,
    playlistError: String?,
    onSelectPlaylist: (String?) -> Unit,
    onCreate: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDeletePlaylist: (String) -> Unit,
    onAddMedia: (String, String) -> Unit,
    onRemoveMedia: (String, String) -> Unit,
    onMoveMedia: (String, String, Int) -> Unit,
    onPlayPlaylist: (MediaEntity) -> Unit,
    onClearPlaylistError: () -> Unit,
) {
    var section by remember { mutableStateOf(LibrarySection.ALL) }
    Surface(modifier = Modifier.fillMaxSize(), color = androidx.compose.ui.graphics.Color.Transparent) {
        Column(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Library", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text("保存したメディアとプレイリストをまとめて管理します。", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (section == LibrarySection.ALL) Button(onClick = {}) { Text("All") }
                    else OutlinedButton(onClick = { section = LibrarySection.ALL }) { Text("All") }
                    if (section == LibrarySection.PLAYLISTS) Button(onClick = {}) { Text("Playlists") }
                    else OutlinedButton(onClick = { section = LibrarySection.PLAYLISTS }) { Text("Playlists") }
                }
            }
            Box(modifier = Modifier.weight(1f)) {
                when (section) {
                    LibrarySection.ALL -> AllMediaSection(media, selectedMediaId, errorMessage, onPlay, onDelete, onClearError)
                    LibrarySection.PLAYLISTS -> PlaylistManagementContent(
                        playlists, selectedPlaylistId, playlistItems, media, selectedMediaId, playlistError,
                        onSelectPlaylist, onCreate, onRename, onDeletePlaylist, onAddMedia, onRemoveMedia,
                        onMoveMedia, onPlayPlaylist, onClearPlaylistError,
                    )
                }
            }
        }
    }
}

@Composable
private fun AllMediaSection(
    media: List<MediaEntity>,
    selectedMediaId: String?,
    errorMessage: String?,
    onPlay: (MediaEntity) -> Unit,
    onDelete: (MediaEntity) -> Unit,
    onClearError: () -> Unit,
) {
    var pendingDelete by remember { mutableStateOf<MediaEntity?>(null) }
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Recently added", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
        errorMessage?.let { message ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = onClearError) { Text("閉じる") }
                }
            }
        }
        if (media.isEmpty()) Card(modifier = Modifier.fillMaxWidth()) {
            Text("保存済みメディアはありません。HomeからWMSへ追加してください。", modifier = Modifier.padding(18.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        media.forEach { item ->
            val available = File(item.localPath).let { it.exists() && it.length() > 0L }
            val presentation = remember(item) { item.toMediaPresentation() }
            Card(modifier = Modifier.fillMaxWidth().clickable(enabled = available) { onPlay(item) }) {
                Row(
                    modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MediaArtwork(
                        media = presentation, contentDescription = presentation.title,
                        modifier = Modifier.size(64.dp).clip(RoundedCornerShape(14.dp)),
                    )
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(item.title, fontWeight = FontWeight.Bold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Text(
                            "${presentation.secondaryLabel} · ${if (item.mediaType == "VIDEO") "Video" else "Audio"}",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall,
                        )
                        Text(
                            buildString {
                                if (item.durationMs > 0L) append(formatPlaybackTime(item.durationMs))
                                if (item.fileSize > 0L) append(" · ${formatFileSize(item.fileSize)}")
                            },
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.labelSmall,
                        )
                        when {
                            !available -> Text("ファイルが見つかりません", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            item.id == selectedMediaId -> Text("再生中", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    TextButton(onClick = { pendingDelete = item }) { Text("削除") }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
    }
    pendingDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null }, title = { Text("Libraryから削除") },
            text = { Text("「${item.title}」の端末内ファイルも削除します。元に戻せません。") },
            confirmButton = { Button(onClick = { pendingDelete = null; onDelete(item) }) { Text("削除") } },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("戻る") } },
        )
    }
}
