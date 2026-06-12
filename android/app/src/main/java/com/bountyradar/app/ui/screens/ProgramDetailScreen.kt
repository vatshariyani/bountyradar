package com.bountyradar.app.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.NewBadge
import com.bountyradar.app.ui.components.PlatformAvatar
import com.bountyradar.app.ui.components.Pill
import com.bountyradar.app.ui.theme.platformColor
import java.time.Duration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProgramDetailScreen(vm: RadarViewModel, docId: String, onBack: () -> Unit) {
    val program = vm.programById(docId)
    val bookmarks by vm.bookmarks.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val cs = MaterialTheme.colorScheme

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Program") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (program != null) {
                        val marked = program.docId in bookmarks
                        IconButton(onClick = { vm.toggleBookmark(program.docId) }) {
                            Icon(
                                if (marked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                                contentDescription = "Bookmark",
                                tint = if (marked) cs.primary else cs.onSurfaceVariant,
                            )
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (program == null) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text("Program not found", color = cs.onSurfaceVariant)
            }
            return@Scaffold
        }
        val accent = platformColor(program.platform)
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Surface(shape = RoundedCornerShape(24.dp), color = cs.surface, modifier = Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(Brush.linearGradient(listOf(accent.copy(alpha = 0.22f), cs.surface)))
                            .padding(20.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            PlatformAvatar(program.platform, 56)
                            Spacer(Modifier.width(14.dp))
                            Column(Modifier.weight(1f)) {
                                Text(program.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Black)
                                Text(program.platformLabel(), color = accent, fontWeight = FontWeight.SemiBold)
                            }
                            if (program.isNewWithin(Duration.ofDays(1))) NewBadge()
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            if (program.bounty) {
                                Pill(if (program.rewardRange.isNotBlank()) program.rewardRange else "Paid bounty", cs.primary, filled = true)
                            } else Pill("VDP", cs.onSurfaceVariant)
                            if (program.isWeb3()) Pill("web3", cs.tertiary)
                        }
                    }
                }
            }

            item {
                Button(
                    onClick = {
                        if (program.url.isNotBlank()) {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(program.url)))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(54.dp),
                ) {
                    Icon(Icons.AutoMirrored.Filled.OpenInNew, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Open program & start hunting", fontWeight = FontWeight.Bold)
                }
            }

            if (program.tags.isNotEmpty()) {
                item { SectionTitle("Tags") }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        program.tags.take(6).forEach { Pill(it, cs.secondary) }
                    }
                }
            }

            item { SectionTitle("In scope (${program.scope.size})") }
            if (program.scope.isEmpty()) {
                item { Text("Scope not listed — open the program for details.", color = cs.onSurfaceVariant) }
            } else {
                items(program.scope) { asset ->
                    Surface(shape = RoundedCornerShape(12.dp), color = cs.surfaceVariant.copy(alpha = 0.5f), modifier = Modifier.fillMaxWidth()) {
                        Text(asset, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(top = 6.dp),
    )
}
