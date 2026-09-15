package com.goroyattemiyo.wms.ui.navigation

import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable

enum class AppTab { HOME, LIBRARY, PLAYER }

@Composable
fun BottomNavigation(
    selectedTab: AppTab,
    onSelect: (AppTab) -> Unit,
) {
    NavigationBar {
        NavigationBarItem(
            selected = selectedTab == AppTab.HOME,
            onClick = { onSelect(AppTab.HOME) },
            icon = { Text("⌂") },
            label = { Text("Home") },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.LIBRARY,
            onClick = { onSelect(AppTab.LIBRARY) },
            icon = { Text("▤") },
            label = { Text("Library") },
        )
        NavigationBarItem(
            selected = selectedTab == AppTab.PLAYER,
            onClick = { onSelect(AppTab.PLAYER) },
            icon = { Text("▶") },
            label = { Text("Player") },
        )
    }
}
