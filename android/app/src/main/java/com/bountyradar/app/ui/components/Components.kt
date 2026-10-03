package com.bountyradar.app.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bountyradar.app.data.ProgramItem
import com.bountyradar.app.ui.theme.platformColor

private val CardShape = RoundedCornerShape(20.dp)
private val PillShape = RoundedCornerShape(50)
private val AvatarShape = RoundedCornerShape(14.dp)

/**
 * Static aurora gradient behind every screen. (It used to animate, which forced
 * a full-screen redraw every frame and competed with scrolling.)
 */
@Composable
fun RadarBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val brush = remember(cs.primary, cs.tertiary) {
        Brush.linearGradient(
            colors = listOf(
                cs.primary.copy(alpha = 0.10f),
                Color.Transparent,
                cs.tertiary.copy(alpha = 0.10f),
            ),
            start = Offset(0f, 0f),
            end = Offset(900f, 1900f),
        )
    }
    Box(modifier.fillMaxSize().background(cs.background).background(brush)) { content() }
}

@Composable
fun PlatformAvatar(platform: String, size: Int = 44) {
    val color = platformColor(platform)
    val brush = remember(color) {
        Brush.linearGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.55f)))
    }
    Box(
        Modifier.size(size.dp).clip(AvatarShape).background(brush),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            platform.removePrefix("fb:").take(1).uppercase(),
            color = Color.White, fontWeight = FontWeight.Black, fontSize = (size / 2.2).sp,
        )
    }
}

@Composable
fun NewBadge() {
    val t = rememberInfiniteTransition(label = "new")
    // Kept as State and read only in the draw phase -> no recomposition per frame.
    val alpha = t.animateFloat(
        0.55f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a",
    )
    Box(
        Modifier
            .graphicsLayer { this.alpha = alpha.value }
            .clip(PillShape)
            .background(MaterialTheme.colorScheme.primary)
    ) {
        Text(
            "NEW",
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Black, fontSize = 10.sp,
        )
    }
}

@Composable
fun UpdatedBadge() {
    Box(Modifier.clip(PillShape).background(MaterialTheme.colorScheme.tertiary)) {
        Text(
            "UPDATED",
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White, fontWeight = FontWeight.Black, fontSize = 10.sp,
        )
    }
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Box(
        Modifier
            .clip(PillShape)
            .background(if (filled) color.copy(alpha = 0.18f) else Color.Transparent)
            .border(1.dp, color.copy(alpha = 0.5f), PillShape)
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = color, fontSize = 11.sp, fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * One row of the feed. Built for scrolling: no drop shadow, no intrinsic
 * measurement, the accent stripe is drawn (not laid out), the press-scale is
 * applied in the layer phase, and all derived values come pre-computed on
 * [ProgramItem] so nothing is parsed while scrolling.
 */
@Composable
fun ProgramCard(
    item: ProgramItem,
    bookmarked: Boolean,
    onClick: (String) -> Unit,
    onBookmark: (String) -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(if (pressed) 0.97f else 1f, label = "scale")
    val cs = MaterialTheme.colorScheme
    val accent = platformColor(item.platformKey)
    val program = item.program

    Column(
        Modifier
            .fillMaxWidth()
            .graphicsLayer { scaleX = scale.value; scaleY = scale.value }
            .clip(CardShape)
            .background(cs.surface)
            .border(1.dp, cs.outline.copy(alpha = 0.6f), CardShape)
            .drawBehind { drawRect(accent, size = Size(5.dp.toPx(), size.height)) }
            .clickable(interactionSource = interaction, indication = null) { onClick(item.docId) }
            .padding(start = 19.dp, top = 14.dp, end = 14.dp, bottom = 14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformAvatar(item.platformKey, 40)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    program.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                    color = cs.onSurface,
                )
                Text(
                    item.platformLabel,
                    style = MaterialTheme.typography.labelMedium,
                    color = accent, fontWeight = FontWeight.SemiBold,
                )
            }
            if (item.isNew) NewBadge() else if (item.isUpdated) UpdatedBadge()
            Spacer(Modifier.width(6.dp))
            Icon(
                if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                contentDescription = "Bookmark",
                tint = if (bookmarked) cs.primary else cs.onSurfaceVariant,
                modifier = Modifier.size(22.dp).clickable { onBookmark(item.docId) },
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (program.bounty) {
                Pill(program.rewardRange.ifBlank { "💰 Bounty" }, cs.primary, filled = true)
            } else {
                Pill("VDP", cs.onSurfaceVariant)
            }
            if (item.scopeCount > 0) Pill("${item.scopeCount} scope", cs.secondary)
            if (item.isWeb3) Pill("web3", cs.tertiary)
        }
    }
}

@Composable
fun EmptyState(title: String, subtitle: String, icon: @Composable () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        icon()
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(6.dp))
        Text(
            subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}
