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
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.goroyattemiyo.wms.library.MediaEntity
import com.goroyattemiyo.wms.ui.components.formatFileSize
import com.goroyattemiyo.wms.ui.components.formatPlaybackTime
import java.io.File

@Composable
fun LibraryScreen(
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
