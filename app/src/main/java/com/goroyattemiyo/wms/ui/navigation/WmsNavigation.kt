package com.goroyattemiyo.wms.ui.navigation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

enum class AppTab {
    SEARCH,
    LIBRARY,
    PLAYLIST,
}

@Composable
fun BottomNavigation(
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
