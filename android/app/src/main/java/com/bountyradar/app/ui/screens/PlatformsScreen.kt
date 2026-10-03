package com.bountyradar.app.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.bountyradar.app.ui.PlatformStat
import com.bountyradar.app.ui.RadarViewModel
import com.bountyradar.app.ui.components.PlatformAvatar
import com.bountyradar.app.ui.components.pressScale
import com.bountyradar.app.ui.theme.GeistMono
import com.bountyradar.app.ui.theme.Radar
import com.bountyradar.app.ui.theme.platformColor
import com.bountyradar.app.ui.theme.platformName

@Composable
fun PlatformsScreen(vm: RadarViewModel, onSelectPlatform: (String) -> Unit) {
    val stats by vm.platformStats.collectAsStateWithLifecycle()
    val max = stats.maxOfOrNull { it.count } ?: 1

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(Radar.ScreenPadding, 14.dp, Radar.ScreenPadding, 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Platforms", style = MaterialTheme.typography.headlineLarge)
                Spacer(Modifier.height(4.dp))
                Text(
                    "Where programs live. Tap one to see only its programs.",
                    style = MaterialTheme.typography.bodyMedium, color = Radar.Muted,
                )
            }
        }
        items(stats, key = { it.platform }) { stat -> PlatformRow(stat, max, onSelectPlatform) }
    }
}

@Composable
private fun PlatformRow(stat: PlatformStat, max: Int, onSelect: (String) -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val color = platformColor(stat.platform)
    // The bar grows in from the left the first time the row appears.
    val fill = animateFloatAsState(stat.count.toFloat() / max, tween(600), label = "share")

    Column(
        Modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(Radar.CardShape)
            .background(Radar.Surface)
            .border(1.dp, Radar.Line, Radar.CardShape)
            .clickable(interaction, indication = null, role = Role.Button) { onSelect(stat.platform) }
            .padding(start = 16.dp, top = 16.dp, end = 10.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformAvatar(stat.platform, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(platformName(stat.platform), style = MaterialTheme.typography.titleMedium, maxLines = 1)
                Text(
                    "${stat.paid} paying",
                    style = MaterialTheme.typography.bodySmall, color = Radar.Muted,
                )
            }
            Text(
                "%,d".format(stat.count),
                fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 20.sp,
            )
            Spacer(Modifier.width(4.dp))
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = Radar.Faint, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.height(14.dp))
        Box(
            Modifier.fillMaxWidth().padding(end = 6.dp).height(4.dp).clip(Radar.PillShape).background(Radar.SurfaceHi)
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .graphicsLayer { scaleX = fill.value; transformOrigin = TransformOrigin(0f, 0.5f) }
                    .background(color)
            )
        }
    }
}
