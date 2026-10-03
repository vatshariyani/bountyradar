package com.bountyradar.app.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Radar
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.bountyradar.app.ui.screens.AccountsScreen
import com.bountyradar.app.ui.screens.FeedScreen
import com.bountyradar.app.ui.screens.NewsScreen
import com.bountyradar.app.ui.screens.PlatformsScreen
import com.bountyradar.app.ui.screens.ProgramDetailScreen
import com.bountyradar.app.ui.screens.SavedScreen
import com.bountyradar.app.ui.screens.SettingsScreen
import com.bountyradar.app.ui.theme.Radar

private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab("feed", "Feed", Icons.Outlined.Radar),
    Tab("platforms", "Platforms", Icons.Outlined.GridView),
    Tab("news", "Learn", Icons.Outlined.AutoStories),
    Tab("saved", "Saved", Icons.Outlined.BookmarkBorder),
    Tab("settings", "Settings", Icons.Outlined.Tune),
)
private val tabRoutes = tabs.map { it.route }

@Composable
fun RadarApp(vm: RadarViewModel) {
    val nav = rememberNavController()
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route
    val showBar = currentRoute == null || currentRoute in tabRoutes

    // Keep push-topic subscriptions in line with the per-platform mute list.
    val platformKeys by vm.platformKeys.collectAsStateWithLifecycle()
    val muted by vm.mutedPlatforms.collectAsStateWithLifecycle()
    LaunchedEffect(platformKeys, muted) { vm.syncAlertTopics(platformKeys, muted) }

    fun goTab(route: String) {
        nav.navigate(route) {
            popUpTo(nav.graph.findStartDestination().id) { saveState = true }
            launchSingleTop = true
            restoreState = true
        }
    }

    // The scaffold owns ALL system-bar insets: content gets the status-bar inset
    // on top and either the bottom bar or the navigation-bar inset below. Screens
    // must not add their own, which is what caused doubled top spacing before.
    Scaffold(
        containerColor = Radar.Bg,
        bottomBar = {
            AnimatedVisibility(
                visible = showBar,
                enter = slideInVertically(tween(220)) { it } + fadeIn(tween(220)),
                exit = slideOutVertically(tween(180)) { it } + fadeOut(tween(180)),
            ) {
                BottomBar(
                    isSelected = { route -> backStack?.destination?.hierarchy?.any { it.route == route } == true },
                    onSelect = ::goTab,
                )
            }
        },
    ) { padding ->
        NavHost(
            navController = nav,
            startDestination = "feed",
            modifier = Modifier.fillMaxSize().padding(padding).consumeWindowInsets(padding),
            enterTransition = { fadeIn(tween(200, easing = FastOutSlowInEasing)) },
            exitTransition = { fadeOut(tween(120)) },
            popEnterTransition = { fadeIn(tween(200, easing = FastOutSlowInEasing)) },
            popExitTransition = { fadeOut(tween(120)) },
        ) {
            composable("feed") { FeedScreen(vm) { docId -> nav.navigate("detail/$docId") } }
            composable("platforms") {
                PlatformsScreen(vm) { platform ->
                    vm.clearFilters()
                    vm.togglePlatform(platform)
                    goTab("feed")
                }
            }
            composable("news") { NewsScreen(vm) }
            composable("saved") {
                SavedScreen(vm, onOpenProgram = { docId -> nav.navigate("detail/$docId") }, onBrowse = { goTab("feed") })
            }
            composable("settings") { SettingsScreen(vm) { nav.navigate("accounts") } }

            // Pushed screens slide in from the right and back out, like a stack.
            val push = tween<androidx.compose.ui.unit.IntOffset>(280, easing = FastOutSlowInEasing)
            composable(
                "accounts",
                enterTransition = { slideInHorizontally(push) { it / 5 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(push) { it / 5 } + fadeOut(tween(160)) },
            ) { AccountsScreen(vm) { nav.popBackStack() } }
            composable(
                "detail/{docId}",
                enterTransition = { slideInHorizontally(push) { it / 5 } + fadeIn(tween(220)) },
                popExitTransition = { slideOutHorizontally(push) { it / 5 } + fadeOut(tween(160)) },
            ) { entry ->
                ProgramDetailScreen(
                    vm = vm,
                    docId = entry.arguments?.getString("docId").orEmpty(),
                    onBack = { nav.popBackStack() },
                )
            }
        }
    }
}

@Composable
internal fun BottomBar(isSelected: (String) -> Boolean, onSelect: (String) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Radar.Bg)
            .drawBehind { drawLine(Radar.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .navigationBarsPadding()
            .height(68.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        tabs.forEach { tab ->
            val selected = isSelected(tab.route)
            val tint by animateColorAsState(if (selected) Radar.Accent else Radar.Muted, tween(180), label = "tabTint")
            val pill by animateColorAsState(if (selected) Radar.AccentSoft else Color.Transparent, tween(180), label = "tabPill")
            Column(
                Modifier
                    .weight(1f)
                    .height(68.dp)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Tab,
                    ) { onSelect(tab.route) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.Center,
            ) {
                Box(
                    Modifier.size(width = 56.dp, height = 30.dp).clip(Radar.PillShape).background(pill),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(tab.icon, contentDescription = null, tint = tint, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.height(4.dp))
                Text(tab.label, color = tint, style = MaterialTheme.typography.labelSmall, maxLines = 1)
            }
        }
    }
}
