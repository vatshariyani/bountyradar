package com.bountyradar.app.ui.screens

import androidx.compose.animation.core.animateIntAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SwapVert
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.Recency
import com.bountyradar.app.ui.RewardFilter
import com.bountyradar.app.ui.ScopeType
import com.bountyradar.app.ui.components.EmptyState
import com.bountyradar.app.ui.components.FilterSortSheet
import com.bountyradar.app.ui.components.ProgramCard
import com.bountyradar.app.ui.components.RadarChip
import com.bountyradar.app.ui.components.RadarLogo
import com.bountyradar.app.ui.components.SkeletonCard
import com.bountyradar.app.ui.components.radarFieldColors
import com.bountyradar.app.ui.theme.GeistMono
import com.bountyradar.app.ui.theme.Radar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FeedScreen(vm: RadarViewModel, onOpenProgram: (String) -> Unit) {
    val programs by vm.programs.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val sort by vm.sort.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val newSinceVisit by vm.newSinceVisit.collectAsStateWithLifecycle()
    val platformCount by vm.platformKeys.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val loaded by vm.feedLoaded.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    // Stable callbacks: new lambda instances per row would defeat card skipping.
    val openState = rememberUpdatedState(onOpenProgram)
    val onOpen = remember { { id: String -> openState.value(id) } }
    val onBookmark = remember(vm) { { id: String -> vm.toggleBookmark(id) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header", contentType = "header") {
            FeedHeader(newSinceVisit = newSinceVisit, total = total, platforms = platformCount.size)
        }

        item(key = "search", contentType = "search") {
            Row(
                Modifier.padding(horizontal = Radar.ScreenPadding),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search programs or scope") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            Icon(
                                Icons.Outlined.Close, "Clear search", tint = Radar.Muted,
                                modifier = Modifier.clip(CircleShape).clickable { vm.query.value = "" }.padding(8.dp),
                            )
                        }
                    } else null,
                    singleLine = true,
                    shape = Radar.PillShape,
                    colors = radarFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                FilterButton(filters.activeCount) { showSheet = true }
            }
        }

        item(key = "quick", contentType = "quick") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Radar.ScreenPadding),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                RadarChip("New today", filters.recency == Recency.DAY) {
                    vm.setRecency(if (filters.recency == Recency.DAY) Recency.ALL else Recency.DAY)
                }
                RadarChip("Paid", filters.reward == RewardFilter.PAID) {
                    vm.setReward(if (filters.reward == RewardFilter.PAID) RewardFilter.ANY else RewardFilter.PAID)
                }
                RadarChip("Wildcards", ScopeType.WILDCARD in filters.scopeTypes) { vm.toggleScopeType(ScopeType.WILDCARD) }
                RadarChip("Updated", filters.updatedOnly) { vm.setUpdatedOnly(!filters.updatedOnly) }
                RadarChip("Web3", filters.web3Only) { vm.setWeb3Only(!filters.web3Only) }
            }
        }

        item(key = "sortline", contentType = "sortline") {
            Row(
                Modifier.fillMaxWidth().padding(start = Radar.ScreenPadding, end = 8.dp, top = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    if (loaded) "%,d programs".format(programs.size) else "Loading programs",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.titleSmall, color = Radar.Muted,
                )
                Row(
                    Modifier
                        .height(40.dp)
                        .clip(Radar.PillShape)
                        .clickable(role = Role.Button) { showSheet = true }
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.SwapVert, null, tint = Radar.Accent, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(sort.label, style = MaterialTheme.typography.labelMedium, color = Radar.Accent)
                }
            }
        }

        when {
            !loaded -> items(6, key = { "sk$it" }, contentType = { "skeleton" }) {
                Box(Modifier.padding(horizontal = Radar.ScreenPadding)) { SkeletonCard() }
            }
            programs.isEmpty() -> item(key = "empty") {
                if (total == 0) {
                    EmptyState(
                        "No programs yet",
                        "Nothing has arrived yet. Check your connection; the list fills in as soon as data comes through.",
                    )
                } else {
                    EmptyState(
                        "Nothing matches",
                        "No program fits this search and these filters.",
                        actionLabel = "Clear filters",
                        onAction = { vm.clearFilters(); vm.query.value = "" },
                    )
                }
            }
            else -> items(programs, key = { it.docId }, contentType = { "program" }) { item ->
                ProgramCard(
                    item = item,
                    bookmarked = item.docId in bookmarks,
                    onClick = onOpen,
                    onBookmark = onBookmark,
                    modifier = Modifier.padding(horizontal = Radar.ScreenPadding).animateItemPlacement(),
                )
            }
        }
    }

    if (showSheet) FilterSortSheet(vm) { showSheet = false }
}

/** The reason to open the app: what is new since you last looked. */
@Composable
internal fun FeedHeader(newSinceVisit: Int, total: Int, platforms: Int) {
    val count by animateIntAsState(newSinceVisit, tween(700), label = "newCount")
    Column(Modifier.padding(start = Radar.ScreenPadding, end = Radar.ScreenPadding, top = 14.dp, bottom = 10.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RadarLogo(30.dp, pulse = newSinceVisit > 0)
            Spacer(Modifier.width(8.dp))
            Text("Bounty Radar", style = MaterialTheme.typography.titleMedium)
        }
        Spacer(Modifier.height(22.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "%,d".format(count),
                fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 52.sp, lineHeight = 52.sp,
                color = if (newSinceVisit > 0) Radar.Accent else Radar.Text,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                if (newSinceVisit == 1) "new program since\nyour last visit" else "new programs since\nyour last visit",
                style = MaterialTheme.typography.bodyMedium, color = Radar.Text,
                modifier = Modifier.padding(bottom = 6.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Tracking %,d programs across %d platforms".format(total, platforms),
            style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
        )
    }
}

@Composable
internal fun FilterButton(activeCount: Int, onClick: () -> Unit) {
    Box(Modifier.size(56.dp)) {
        Box(
            Modifier
                .fillMaxSize()
                .clip(CircleShape)
                .background(if (activeCount > 0) Radar.AccentSoft else Radar.Surface)
                .border(1.dp, if (activeCount > 0) Radar.Accent.copy(alpha = 0.55f) else Radar.Line, CircleShape)
                .clickable(role = Role.Button, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Tune, "Filter and sort",
                tint = if (activeCount > 0) Radar.Accent else Radar.Text, modifier = Modifier.size(22.dp),
            )
        }
        if (activeCount > 0) {
            Box(
                Modifier.align(Alignment.TopEnd).size(20.dp).clip(CircleShape).background(Radar.Accent),
                contentAlignment = Alignment.Center,
            ) {
                Text("$activeCount", color = Radar.OnAccent, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}
