package com.toon.studio.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.vector.ImageVector

data class BottomItem(
    val route: String,
    val label: String,
    val icon: ImageVector
)

val bottomItems = listOf(
    BottomItem(Routes.HOME, "Home", Icons.Filled.Home),
    BottomItem(Routes.PROJECTS, "Projects", Icons.Filled.AccountBox),
    BottomItem(Routes.CREATE_FILM, "Create", Icons.Filled.Add),
    BottomItem(Routes.STUDIO, "Studio", Icons.Filled.Build),
    BottomItem(Routes.ACCOUNT, "Account", Icons.Filled.Person)
)

@Composable
fun ToonBottomBar(
    currentRoute: String?,
    onSelect: (String) -> Unit
) {
    NavigationBar {
        bottomItems.forEach { item ->
            NavigationBarItem(
                selected = currentRoute == item.route,
                onClick = { onSelect(item.route) },
                icon = { Icon(item.icon, contentDescription = item.label) },
                label = { Text(item.label) }
            )
        }
    }
}
