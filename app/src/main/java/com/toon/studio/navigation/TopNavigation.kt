package com.toon.studio.navigation

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToonTopBar(
    title: String,
    onMenuClick: () -> Unit,
    onSearchClick: () -> Unit,
    onNotificationsClick: () -> Unit,
    onSettingsClick: () -> Unit
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(Icons.Filled.Menu, contentDescription = "Open menu")
            }
        },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "TOON",
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1
                )
            }
        },
        actions = {
            IconButton(onClick = onSearchClick) {
                Icon(Icons.Filled.Search, contentDescription = "Search")
            }
            IconButton(onClick = onNotificationsClick) {
                Icon(Icons.Filled.Notifications, contentDescription = "Notifications")
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Filled.Settings, contentDescription = "Settings")
            }
        }
    )
}
