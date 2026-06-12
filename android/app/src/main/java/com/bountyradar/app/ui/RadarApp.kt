package com.bountyradar.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bountyradar.app.ui.components.RadarBackground
import com.bountyradar.app.ui.screens.FeedScreen
import com.bountyradar.app.ui.screens.PlatformsScreen
import com.bountyradar.app.ui.screens.ProgramDetailScreen
import com.bountyradar.app.ui.screens.SavedScreen
import com.bountyradar.app.ui.screens.SettingsScreen

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("feed", "Feed", Icons.Filled.Radar),
    Tab("platforms", "Platforms", Icons.Filled.GridView),
    Tab("saved", "Saved", Icons.Filled.Bookmark),
    Tab("settings", "Settings", Icons.Filled.Settings),
)

@Composable
fun RadarApp(vm: RadarViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute in tabs.map { it.route }

    RadarBackground {
        Scaffold(
            containerColor = Color.Transparent,
            bottomBar = {
                AnimatedVisibility(
                    visible = showBar,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it },
                ) {
                    NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                        val dest = backStack?.destination
                        tabs.forEach { tab ->
                            val selected = dest?.hierarchy?.any { it.route == tab.route } == true
                            NavigationBarItem(
                                selected = selected,
                                onClick = {
                                    nav.navigate(tab.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(tab.icon, contentDescription = tab.label) },
                                label = { Text(tab.label) },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = MaterialTheme.colorScheme.onPrimary,
                                    indicatorColor = MaterialTheme.colorScheme.primary,
                                ),
                            )
                        }
                    }
                }
            },
        ) { padding ->
            NavHost(
                navController = nav,
                startDestination = "feed",
                modifier = Modifier.fillMaxSize().padding(padding),
            ) {
                composable("feed") {
                    FeedScreen(vm) { docId -> nav.navigate("detail/$docId") }
                }
                composable("platforms") {
                    PlatformsScreen(vm) { platform ->
                        vm.clearFilters()
                        vm.togglePlatform(platform)
                        nav.navigate("feed") {
                            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                        }
                    }
                }
                composable("saved") {
                    SavedScreen(vm) { docId -> nav.navigate("detail/$docId") }
                }
                composable("settings") { SettingsScreen(vm) }
                composable("detail/{docId}") { entry ->
                    ProgramDetailScreen(
                        vm = vm,
                        docId = entry.arguments?.getString("docId").orEmpty(),
                        onBack = { nav.popBackStack() },
                    )
                }
            }
        }
    }
}
