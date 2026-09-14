package com.goroyattemiyo.wms.ui.library

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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.playlist.PlaylistMediaItem
import com.goroyattemiyo.wms.playlist.PlaylistSummary
import java.io.File

@Composable
fun PlaylistScreen(
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
