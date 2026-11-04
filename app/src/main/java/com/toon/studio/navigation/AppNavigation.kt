package com.toon.studio.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.toon.studio.screens.ScreenContent
import com.toon.studio.screens.screenTitle
import kotlinx.coroutines.launch

private val allRoutes: List<String> =
    (drawerItems.map { it.route } +
        listOf(
            Routes.STUDIO,
            Routes.ACCOUNT,
            Routes.SEARCH,
            Routes.NOTIFICATIONS,
            Routes.SETTINGS
        ))
        .distinct()

@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(DrawerValue.Closed)
    val scope = rememberCoroutineScope()

    val backStackEntry = navController.currentBackStackEntryAsState().value
    val currentRoute = backStackEntry?.destination?.route

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    BackHandler(enabled = drawerState.isOpen) {
        scope.launch { drawerState.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ToonDrawerContent(currentRoute) { route ->
                scope.launch { drawerState.close() }
                navigateTo(route)
            }
        }
    ) {
        Scaffold(
            topBar = {
                ToonTopBar(
                    title = screenTitle(currentRoute),
                    onMenuClick = { scope.launch { drawerState.open() } },
                    onSearchClick = { navigateTo(Routes.SEARCH) },
                    onNotificationsClick = { navigateTo(Routes.NOTIFICATIONS) },
                    onSettingsClick = { navigateTo(Routes.SETTINGS) }
                )
            },
            bottomBar = {
                ToonBottomBar(currentRoute) { route -> navigateTo(route) }
            }
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.HOME,
                modifier = Modifier.padding(padding)
            ) {
                allRoutes.forEach { route ->
                    composable(route) { ScreenContent(route) }
                }
            }
        }
    }
}
