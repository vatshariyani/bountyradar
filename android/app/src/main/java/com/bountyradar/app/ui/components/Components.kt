package com.bountyradar.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bountyradar.app.data.Program
import com.bountyradar.app.ui.theme.platformColor
import java.time.Duration

/** Subtle animated aurora background used behind every screen. */
@Composable
fun RadarBackground(modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val transition = rememberInfiniteTransition(label = "bg")
    val shift by transition.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(12000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "shift",
    )
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxSize()
            .background(cs.background)
            .background(
                Brush.linearGradient(
                    colors = listOf(
                        cs.primary.copy(alpha = 0.10f),
                        Color.Transparent,
                        cs.tertiary.copy(alpha = 0.10f),
                    ),
                    start = androidx.compose.ui.geometry.Offset(0f, 300f * shift),
                    end = androidx.compose.ui.geometry.Offset(900f, 1600f * (1 - shift)),
                )
            )
    ) { content() }
}

@Composable
fun PlatformAvatar(platform: String, size: Int = 44) {
    val color = platformColor(platform)
    val label = platform.removePrefix("fb:").take(1).uppercase()
    Box(
        Modifier
            .size(size.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Brush.linearGradient(listOf(color.copy(alpha = 0.9f), color.copy(alpha = 0.55f)))),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = Color.White, fontWeight = FontWeight.Black, fontSize = (size / 2.2).sp)
    }
}

@Composable
fun NewBadge() {
    val t = rememberInfiniteTransition(label = "new")
    val a by t.animateFloat(
        0.5f, 1f,
        infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "a",
    )
    Surface(
        color = MaterialTheme.colorScheme.primary.copy(alpha = a),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            "NEW",
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = MaterialTheme.colorScheme.onPrimary,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
        )
    }
}

@Composable
fun UpdatedBadge() {
    Surface(
        color = MaterialTheme.colorScheme.tertiary.copy(alpha = 0.9f),
        shape = RoundedCornerShape(50),
    ) {
        Text(
            "UPDATED",
            Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            color = Color.White,
            fontWeight = FontWeight.Black,
            fontSize = 10.sp,
        )
    }
}

@Composable
fun Pill(text: String, color: Color, filled: Boolean = false) {
    Surface(
        color = if (filled) color.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(50),
        modifier = Modifier.border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(50)),
    ) {
        Text(
            text,
            Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
            color = color,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
fun ProgramCard(
    program: Program,
    bookmarked: Boolean,
    onClick: () -> Unit,
    onBookmark: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, label = "scale")
    val cs = MaterialTheme.colorScheme
    val accent = platformColor(program.platform)
    val isNew = program.isNewWithin(Duration.ofDays(1))

    Surface(
        color = cs.surface,
        shape = RoundedCornerShape(20.dp),
        tonalElevation = 2.dp,
        shadowElevation = if (pressed) 1.dp else 6.dp,
        modifier = Modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(RoundedCornerShape(20.dp))
            .border(1.dp, cs.outline.copy(alpha = 0.6f), RoundedCornerShape(20.dp))
            .clickable(interaction, null, onClick = onClick),
    ) {
        Row(Modifier.height(intrinsicSize = androidx.compose.foundation.layout.IntrinsicSize.Min)) {
            // accent stripe
            Box(
                Modifier
                    .width(5.dp)
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(accent, accent.copy(alpha = 0.3f))))
            )
            Column(Modifier.padding(14.dp).weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    PlatformAvatar(program.platform, 40)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            program.name,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = cs.onSurface,
                        )
                        Text(
                            program.platformLabel(),
                            style = MaterialTheme.typography.labelMedium,
                            color = accent,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                    if (isNew) NewBadge() else if (program.isRecentlyUpdated()) UpdatedBadge()
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
                        contentDescription = "Bookmark",
                        tint = if (bookmarked) cs.primary else cs.onSurfaceVariant,
                        modifier = Modifier.size(22.dp).clickable(onClick = onBookmark),
                    )
                }
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (program.bounty) {
                        Pill(
                            if (program.rewardRange.isNotBlank()) program.rewardRange else "💰 Bounty",
                            cs.primary, filled = true,
                        )
                    } else {
                        Pill("VDP", cs.onSurfaceVariant)
                    }
                    if (program.scope.isNotEmpty()) {
                        Pill("${program.scope.size} scope", MaterialTheme.colorScheme.secondary)
                    }
                    if (program.isWeb3()) Pill("web3", MaterialTheme.colorScheme.tertiary)
                }
            }
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
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}
