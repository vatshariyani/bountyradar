package com.bountyradar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.data.NewsItem
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.EmptyState
import com.bountyradar.app.ui.components.RadarChip
import com.bountyradar.app.ui.components.Tag
import com.bountyradar.app.ui.components.pressScale
import com.bountyradar.app.ui.theme.Radar

private val KINDS = listOf(
    "all" to "All",
    "exploited" to "Exploited",
    "advisory" to "Advisories",
    "research" to "Research",
    "writeup" to "Write-ups",
    "news" to "News",
)

@Composable
fun NewsScreen(vm: RadarViewModel) {
    val items by vm.news.collectAsStateWithLifecycle()
    val kind by vm.newsKind.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val open = remember(context) {
        { url: String ->
            if (url.isNotBlank()) {
                runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
            }
            Unit
        }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "header") {
            Column(Modifier.padding(start = Radar.ScreenPadding, end = Radar.ScreenPadding, top = 14.dp)) {
                Text("Learn", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "What is being exploited, disclosed and written up right now.",
                    style = MaterialTheme.typography.bodyMedium, color = Radar.Muted,
                )
            }
        }
        item(key = "kinds") {
            Row(
                Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Radar.ScreenPadding, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                KINDS.forEach { (key, label) -> RadarChip(label, kind == key) { vm.newsKind.value = key } }
            }
        }
        if (items.isEmpty()) {
            item(key = "empty") {
                EmptyState("Nothing here yet", "This feed refreshes every hour. Try another category or check back soon.")
            }
        }
        items(items, key = { it.id }, contentType = { "news" }) { n -> NewsCard(n, open) }
    }
}

@Composable
private fun NewsCard(item: NewsItem, onOpen: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) Radar.SurfaceHi else Radar.Surface, label = "newsBg")
    val severe = item.kind == "exploited" || item.severity == "critical"

    Column(
        Modifier
            .padding(horizontal = Radar.ScreenPadding)
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(Radar.CardShape)
            .background(bg)
            .border(1.dp, Radar.Line, Radar.CardShape)
            .clickable(interaction, indication = null, role = Role.Button) { onOpen(item.url) }
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Tag(
                when {
                    item.kind == "exploited" -> "Exploited"
                    item.severity.isNotBlank() -> item.severity.replaceFirstChar { it.uppercase() }
                    else -> KINDS.firstOrNull { it.first == item.kind }?.second ?: "News"
                },
                color = when {
                    severe -> Radar.Danger
                    item.severity == "high" -> Radar.Warn
                    else -> Radar.Muted
                },
            )
            Spacer(Modifier.weight(1f))
            Text(
                listOf(item.source, item.date.take(10)).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.labelSmall, color = Radar.Muted, maxLines = 1,
            )
        }
        Spacer(Modifier.height(10.dp))
        Text(
            item.title, style = MaterialTheme.typography.titleMedium,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        if (item.summary.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                item.summary, style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                maxLines = 3, overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
