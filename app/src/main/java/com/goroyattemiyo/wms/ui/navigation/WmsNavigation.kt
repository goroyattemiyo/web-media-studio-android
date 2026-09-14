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
    HOME,
    LIBRARY,
}

@Composable
fun BottomNavigation(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
    onOpenPlayer: () -> Unit,
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
                onClick = { onSelect(AppTab.HOME) },
                enabled = selectedTab != AppTab.HOME,
            ) {
                Text("Home")
            }
            TextButton(
                onClick = { onSelect(AppTab.LIBRARY) },
                enabled = selectedTab != AppTab.LIBRARY,
            ) {
                Text("Library")
            }
            TextButton(
                onClick = onOpenPlayer,
            ) {
                Text("Player")
            }
        }
    }
}
