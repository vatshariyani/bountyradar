package com.bountyradar.app.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.bountyradar.app.data.NewsItem
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.EmptyState
import com.bountyradar.app.ui.components.Panel
import com.bountyradar.app.ui.components.PrimaryButton
import com.bountyradar.app.ui.components.ScreenTopBar
import com.bountyradar.app.ui.components.SecondaryButton
import com.bountyradar.app.ui.components.SectionLabel
import com.bountyradar.app.ui.components.Tag
import com.bountyradar.app.ui.theme.Radar

private val CVE_ID = Regex("CVE-\\d{4}-\\d{4,7}")

@Composable
fun NewsDetailScreen(vm: RadarViewModel, id: String, onBack: () -> Unit) {
    val item = remember(id) { vm.newsById(id) }
    NewsDetailContent(item, onBack)
}

/** Stateless so it can be rendered in JVM screenshot tests. */
@Composable
internal fun NewsDetailContent(item: NewsItem?, onBack: () -> Unit) {
    val context = LocalContext.current
    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Column(Modifier.fillMaxSize()) {
        ScreenTopBar(title = item?.source?.ifBlank { "Article" } ?: "Article", onBack = onBack)

        if (item == null) {
            EmptyState("Article not found", "The feed was refreshed and this entry is no longer in it.")
        } else {
            val cve = remember(item.id) { CVE_ID.find(item.title)?.value }

            Column(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(start = Radar.ScreenPadding, end = Radar.ScreenPadding, top = 8.dp, bottom = 24.dp),
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Tag(newsTagLabel(item), color = newsTagColor(item))
                    NEWS_KINDS.firstOrNull { it.first == item.kind }?.second
                        ?.takeIf { it != newsTagLabel(item) }
                        ?.let { Tag(it, Radar.Muted) }
                }
                Spacer(Modifier.height(14.dp))
                SelectionContainer {
                    Text(item.title, style = MaterialTheme.typography.headlineSmall)
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    listOf(item.source, item.date.take(10)).filter { it.isNotBlank() }.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                )

                Spacer(Modifier.height(18.dp))
                Panel {
                    SectionLabel("Summary")
                    Spacer(Modifier.height(8.dp))
                    SelectionContainer {
                        Text(
                            item.summary.ifBlank { "This source gives no summary. Open the full entry for details." },
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (item.summary.isBlank()) Radar.Muted else Radar.Text,
                        )
                    }
                }

                if (item.tags.isNotEmpty()) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item.tags.forEach { Tag(it, Radar.Muted) }
                    }
                }

                if (cve != null) {
                    Spacer(Modifier.height(18.dp))
                    SectionLabel("Look up $cve")
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        SecondaryButton("NVD", { open("https://nvd.nist.gov/vuln/detail/$cve") }, Modifier.weight(1f))
                        SecondaryButton(
                            "Exploits",
                            { open("https://github.com/search?q=$cve&type=repositories") },
                            Modifier.weight(1f),
                        )
                    }
                }
            }

            // Same pinned action bar as the program screen.
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Radar.Bg)
                    .drawBehind { drawLine(Radar.Line, Offset(0f, 0f), Offset(size.width, 0f), 1.dp.toPx()) }
                    .padding(horizontal = Radar.ScreenPadding, vertical = 12.dp)
            ) {
                PrimaryButton(
                    "Open in browser",
                    arrow = true,
                    enabled = item.url.isNotBlank(),
                    onClick = { open(item.url) },
                )
            }
        }
    }
}
