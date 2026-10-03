package com.bountyradar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.data.NewsItem
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.Pill

private val KINDS = listOf(
    "all" to "All",
    "exploited" to "Exploited in the wild",
    "advisory" to "Advisories",
    "research" to "Research",
    "writeup" to "Write-ups",
    "news" to "News",
)
private val NewsShape = RoundedCornerShape(18.dp)

@OptIn(ExperimentalMaterial3Api::class)
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
        contentPadding = PaddingValues(16.dp, 16.dp, 16.dp, 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column {
                Text("Learn", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black)
                Text(
                    "Fresh vulnerabilities, advisories and research to learn from.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    KINDS.forEach { (key, label) ->
                        FilterChip(
                            selected = kind == key,
                            onClick = { vm.newsKind.value = key },
                            label = { Text(label) },
                        )
                    }
                }
            }
        }
        if (items.isEmpty()) {
            item {
                Text(
                    "Nothing here yet — the feed refreshes hourly.",
                    Modifier.padding(top = 40.dp).fillMaxWidth(),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(items, key = { it.id }, contentType = { "news" }) { n -> NewsCard(n, open) }
    }
}

@Composable
private fun NewsCard(item: NewsItem, onOpen: (String) -> Unit) {
    val cs = MaterialTheme.colorScheme
    val accent = when {
        item.kind == "exploited" -> cs.error
        item.severity == "critical" -> cs.error
        item.severity == "high" -> Color(0xFFFF9F43)
        item.kind == "research" -> cs.secondary
        item.kind == "writeup" -> cs.primary
        else -> cs.tertiary
    }
    Column(
        Modifier
            .fillMaxWidth()
            .clip(NewsShape)
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.6f), NewsShape)
            .clickable { onOpen(item.url) }
            .padding(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Pill(
                when {
                    item.kind == "exploited" -> "EXPLOITED"
                    item.severity.isNotBlank() -> item.severity.uppercase()
                    else -> item.kind.uppercase()
                },
                accent, filled = true,
            )
            Text(
                item.source,
                style = MaterialTheme.typography.labelMedium,
                color = cs.onSurfaceVariant, fontWeight = FontWeight.SemiBold,
            )
            Spacer(Modifier.weight(1f))
            Text(item.date.take(10), style = MaterialTheme.typography.labelSmall, color = cs.onSurfaceVariant)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            item.title,
            style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold,
            maxLines = 3, overflow = TextOverflow.Ellipsis,
        )
        if (item.summary.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(
                item.summary,
                style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant,
                maxLines = 4, overflow = TextOverflow.Ellipsis,
            )
        }
        if (item.tags.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                item.tags.take(3).forEach { Pill(it.take(24), cs.onSurfaceVariant) }
            }
        }
    }
}
