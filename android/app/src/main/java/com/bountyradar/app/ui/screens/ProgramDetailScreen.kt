package com.bountyradar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.data.ProgramItem
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.BookmarkButton
import com.bountyradar.app.ui.components.EmptyState
import com.bountyradar.app.ui.components.Metric
import com.bountyradar.app.ui.components.Panel
import com.bountyradar.app.ui.components.PlatformAvatar
import com.bountyradar.app.ui.components.PrimaryButton
import com.bountyradar.app.ui.components.ScreenTopBar
import com.bountyradar.app.ui.components.SecondaryButton
import com.bountyradar.app.ui.components.SectionLabel
import com.bountyradar.app.ui.components.Tag
import com.bountyradar.app.ui.components.radarFieldColors
import com.bountyradar.app.ui.theme.GeistMono
import com.bountyradar.app.ui.theme.Radar
import com.bountyradar.app.ui.theme.platformColor
import com.bountyradar.app.ui.theme.platformName

@Composable
fun ProgramDetailScreen(vm: RadarViewModel, docId: String, onBack: () -> Unit) {
    val itemFlow = remember(docId) { vm.programFlow(docId) }
    val item by itemFlow.collectAsStateWithLifecycle(vm.programById(docId))
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val noteFlow = remember(docId) { vm.note(docId) }
    val note by noteFlow.collectAsStateWithLifecycle("")

    ProgramDetailContent(
        item = item,
        bookmarked = docId in bookmarks,
        note = note,
        onToggleBookmark = { vm.toggleBookmark(docId) },
        onSaveNote = { vm.saveNote(docId, it) },
        onBack = onBack,
    )
}

/** Stateless so it can be rendered in JVM screenshot tests. */
@Composable
internal fun ProgramDetailContent(
    item: ProgramItem?,
    bookmarked: Boolean,
    note: String,
    onToggleBookmark: () -> Unit,
    onSaveNote: (String) -> Unit,
    onBack: () -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        ScreenTopBar(
            title = "Program",
            onBack = onBack,
            action = if (item != null) {
                { BookmarkButton(bookmarked, onToggleBookmark) }
            } else null,
        )
        if (item == null) {
            EmptyState("Program not found", "It may have been removed from its platform.")
        } else {
            ProgramBody(item, note, onSaveNote)
        }
    }
}

@Composable
private fun ColumnScope.ProgramBody(item: ProgramItem, note: String, onSaveNote: (String) -> Unit) {
    val context = LocalContext.current
    val program = item.program

    LazyColumn(
        Modifier.weight(1f).fillMaxWidth(),
        contentPadding = PaddingValues(Radar.ScreenPadding, 8.dp, Radar.ScreenPadding, 24.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        item(key = "hero") {
            Column(Modifier.padding(bottom = 10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformAvatar(item.platformKey, 56)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            platformName(item.platformKey),
                            style = MaterialTheme.typography.labelMedium,
                            color = platformColor(item.platformKey),
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (item.isNew) Tag("New", filled = true) else if (item.isUpdated) Tag("Updated")
                            if (item.isWeb3) Tag("Web3", Radar.Muted)
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                Text(program.name, style = MaterialTheme.typography.headlineLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
            }
        }

        item(key = "metrics") {
            Panel {
                Text(
                    item.rewardLabel,
                    fontFamily = GeistMono, fontSize = 20.sp,
                    color = if (program.bounty) Radar.Accent else Radar.Muted,
                    maxLines = 2, overflow = TextOverflow.Ellipsis,
                )
                Text("Reward", style = MaterialTheme.typography.labelSmall, color = Radar.Muted)
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Metric("%,d".format(item.scopeCount), "Assets in scope", Modifier.weight(1f))
                    Metric("%,d".format(item.wildcardCount), "Wildcards", Modifier.weight(1f))
                }
                if (program.firstSeen.length >= 10) {
                    Spacer(Modifier.height(14.dp))
                    Text(
                        "First seen ${program.firstSeen.take(10)}",
                        style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                    )
                }
            }
        }

        item(key = "notes") {
            var draft by remember(note) { mutableStateOf(note) }
            Column(Modifier.padding(top = 14.dp)) {
                SectionLabel("My notes")
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = draft, onValueChange = { draft = it },
                    placeholder = { Text("Recon ideas, what you tested, leads to revisit") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 8,
                    shape = Radar.InnerShape, colors = radarFieldColors(),
                    textStyle = MaterialTheme.typography.bodyMedium,
                )
                if (draft != note) {
                    Spacer(Modifier.height(8.dp))
                    SecondaryButton("Save note", { onSaveNote(draft) }, color = Radar.Accent)
                }
            }
        }

        item(key = "scopeTitle") {
            SectionLabel("In scope", Modifier.padding(top = 14.dp, bottom = 2.dp))
        }
        if (program.scope.isEmpty()) {
            item(key = "noScope") {
                Text(
                    "This source does not list scope. Open the program for the full rules.",
                    style = MaterialTheme.typography.bodyMedium, color = Radar.Muted,
                )
            }
        } else {
            // Scope entries can repeat, so key by position.
            itemsIndexed(program.scope, key = { i, _ -> "s$i" }, contentType = { _, _ -> "scope" }) { _, asset ->
                SelectionContainer {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(Radar.InnerShape)
                            .background(Radar.Surface)
                            .border(1.dp, Radar.Line, Radar.InnerShape)
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 14.dp, vertical = 12.dp)
                    ) {
                        Text(asset, fontFamily = GeistMono, fontSize = 13.sp, maxLines = 1, softWrap = false)
                    }
                }
            }
        }
    }

    // Primary action stays within thumb reach, pinned under the list (not over it).
    Box(
        Modifier
            .fillMaxWidth()
            .background(Radar.Bg)
            .drawBehind { drawLine(Radar.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
            .padding(horizontal = Radar.ScreenPadding, vertical = 12.dp)
    ) {
        PrimaryButton(
            "Open program",
            arrow = true,
            enabled = program.url.isNotBlank(),
            onClick = {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(program.url))) }
            },
        )
    }
}
