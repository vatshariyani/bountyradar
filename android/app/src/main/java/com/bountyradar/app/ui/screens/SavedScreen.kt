package com.bountyradar.app.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.EmptyState
import com.bountyradar.app.ui.components.ProgramCard

@Composable
fun SavedScreen(vm: RadarViewModel, onOpenProgram: (String) -> Unit) {
    val saved by vm.savedPrograms.collectAsStateWithLifecycle()
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()

    if (saved.isEmpty()) {
        EmptyState(
            title = "No saved programs yet",
            subtitle = "Tap the bookmark on any program to keep it here for quick access.",
            icon = {
                Icon(
                    Icons.Outlined.BookmarkBorder,
                    contentDescription = null,
                    modifier = Modifier.padding(8.dp),
                    tint = MaterialTheme.colorScheme.primary,
                )
            },
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Saved (${saved.size})", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
        }
        items(saved, key = { it.docId }) { program ->
            ProgramCard(
                program = program,
                bookmarked = program.docId in bookmarks,
                onClick = { onOpenProgram(program.docId) },
                onBookmark = { vm.toggleBookmark(program.docId) },
            )
        }
    }
}
