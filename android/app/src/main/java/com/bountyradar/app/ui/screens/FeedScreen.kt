package com.bountyradar.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.FilterSortSheet
import com.bountyradar.app.ui.components.ProgramCard

@Composable
fun FeedScreen(vm: RadarViewModel, onOpenProgram: (String) -> Unit) {
    val programs by vm.programs.collectAsStateWithLifecycle()
    val query by vm.query.collectAsStateWithLifecycle()
    val filters by vm.filters.collectAsStateWithLifecycle()
    val total by vm.totalCount.collectAsStateWithLifecycle()
    val newToday by vm.newTodayCount.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    var showSheet by remember { mutableStateOf(false) }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 12.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item { HeroHeader(total = total, newToday = newToday, shown = programs.size) }

        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { vm.query.value = it },
                    placeholder = { Text("Search programs, scope, tags…") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.weight(1f),
                )
                Spacer(Modifier.width(10.dp))
                BadgedBox(badge = {
                    if (filters.activeCount > 0) Badge { Text("${filters.activeCount}") }
                }) {
                    FilledTonalIconButton(
                        onClick = { showSheet = true },
                        modifier = Modifier.size(56.dp),
                    ) { Icon(Icons.Filled.Tune, contentDescription = "Filter & sort") }
                }
            }
        }

        if (programs.isEmpty()) {
            item {
                Box(Modifier.fillMaxWidth().padding(top = 60.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("📡", style = MaterialTheme.typography.displaySmall)
                        Spacer(Modifier.height(8.dp))
                        Text(
                            if (total == 0) "Waiting for programs…" else "No matches for these filters",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            if (total == 0) "Make sure Firestore read rules are set."
                            else "Try clearing filters or search.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        } else {
            items(programs, key = { it.docId }) { program ->
                ProgramCard(
                    program = program,
                    bookmarked = program.docId in bookmarks,
                    onClick = { onOpenProgram(program.docId) },
                    onBookmark = { vm.toggleBookmark(program.docId) },
                )
            }
        }
    }

    if (showSheet) FilterSortSheet(vm) { showSheet = false }
}

@Composable
private fun HeroHeader(total: Int, newToday: Int, shown: Int) {
    val cs = MaterialTheme.colorScheme
    Surface(
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier.fillMaxWidth(),
        color = cs.surface,
    ) {
        Box(
            Modifier
                .clip(RoundedCornerShape(24.dp))
                .background(
                    Brush.linearGradient(
                        listOf(cs.primary.copy(alpha = 0.22f), cs.tertiary.copy(alpha = 0.18f))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                Text("BountyRadar", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Catch new targets first.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant,
                )
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    StatTile("$total", "programs", Modifier.weight(1f))
                    StatTile("$newToday", "new today", Modifier.weight(1f), highlight = true)
                    StatTile("$shown", "shown", Modifier.weight(1f))
                }
            }
        }
    }
}

@Composable
private fun StatTile(value: String, label: String, modifier: Modifier = Modifier, highlight: Boolean = false) {
    val cs = MaterialTheme.colorScheme
    Surface(
        color = if (highlight) cs.primary.copy(alpha = 0.18f) else cs.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier,
    ) {
        Column(Modifier.padding(12.dp)) {
            Text(
                value,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Black,
                color = if (highlight) cs.primary else cs.onSurface,
            )
            Text(label, style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
    }
}
