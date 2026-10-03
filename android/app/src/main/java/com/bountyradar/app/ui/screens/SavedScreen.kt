package com.bountyradar.app.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.EmptyState
import com.bountyradar.app.ui.components.ProgramCard
import com.bountyradar.app.ui.theme.Radar

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SavedScreen(vm: RadarViewModel, onOpenProgram: (String) -> Unit, onBrowse: () -> Unit) {
    val saved by vm.savedPrograms.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val onBookmark = remember(vm) { { id: String -> vm.toggleBookmark(id) } }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Radar.ScreenPadding, 14.dp, Radar.ScreenPadding, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Saved", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    if (saved.isEmpty()) "Your shortlist of targets." else "${saved.size} on your shortlist.",
                    style = MaterialTheme.typography.bodyMedium, color = Radar.Muted,
                )
            }
        }
        if (saved.isEmpty()) {
            item(key = "empty") {
                EmptyState(
                    "Nothing saved yet",
                    "Tap the bookmark on any program to keep it here.",
                    actionLabel = "Browse the feed",
                    onAction = onBrowse,
                )
            }
        } else {
            items(saved, key = { it.docId }, contentType = { "program" }) { item ->
                ProgramCard(
                    item = item,
                    bookmarked = item.docId in bookmarks,
                    onClick = onOpenProgram,
                    onBookmark = onBookmark,
                    modifier = Modifier.animateItemPlacement(),
                )
            }
        }
    }
}
