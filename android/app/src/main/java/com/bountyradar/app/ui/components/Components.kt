package com.bountyradar.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bountyradar.app.data.ProgramItem
import com.bountyradar.app.ui.theme.GeistMono
import com.bountyradar.app.ui.theme.Radar
import com.bountyradar.app.ui.theme.platformColor
import com.bountyradar.app.ui.theme.platformName

// --------------------------------------------------------------------------- //
// Motion                                                                        //
// --------------------------------------------------------------------------- //

/** Physical press feedback: the element settles in slightly, with a spring. */
fun Modifier.pressScale(interaction: MutableInteractionSource, pressed: Float = 0.97f): Modifier = composed {
    val isPressed by interaction.collectIsPressedAsState()
    val scale = animateFloatAsState(
        if (isPressed) pressed else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "press",
    )
    graphicsLayer { scaleX = scale.value; scaleY = scale.value }
}

/** Loading placeholder pulse, applied in the draw phase only. */
fun Modifier.skeletonPulse(): Modifier = composed {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a = t.animateFloat(0.45f, 1f, infiniteRepeatable(tween(850), RepeatMode.Reverse), label = "a")
    graphicsLayer { alpha = a.value }
}

// --------------------------------------------------------------------------- //
// Brand                                                                         //
// --------------------------------------------------------------------------- //

/** The radar mark: open ring, swept quadrant, and the target it found. */
@Composable
fun RadarLogo(size: Dp, modifier: Modifier = Modifier, pulse: Boolean = false) {
    val blip = if (pulse) {
        rememberInfiniteTransition(label = "logo")
            .animateFloat(0.35f, 1f, infiniteRepeatable(tween(1100), RepeatMode.Reverse), label = "blip")
    } else null
    Canvas(modifier.size(size)) {
        val s = this.size.minDimension
        val r = s * 0.36f
        val c = Offset(this.size.width / 2f, this.size.height / 2f)
        val box = Size(r * 2, r * 2)
        val origin = Offset(c.x - r, c.y - r)
        drawArc(Radar.Accent.copy(alpha = 0.22f), 270f, 90f, useCenter = true, topLeft = origin, size = box)
        drawArc(
            Radar.Accent, 0f, 270f, useCenter = false, topLeft = origin, size = box,
            style = Stroke(width = s * 0.075f, cap = StrokeCap.Round),
        )
        drawCircle(Radar.Accent, s * 0.06f, c)
        drawCircle(
            Radar.Text.copy(alpha = blip?.value ?: 1f), s * 0.072f,
            Offset(c.x + r * 0.707f, c.y - r * 0.707f),
        )
    }
}

// --------------------------------------------------------------------------- //
// Small pieces                                                                  //
// --------------------------------------------------------------------------- //

@Composable
fun PlatformAvatar(platform: String, size: Int = 44) {
    val color = platformColor(platform)
    Box(
        Modifier
            .size(size.dp)
            .clip(Radar.InnerShape)
            .background(Radar.SurfaceHi)
            .border(1.dp, Radar.Line, Radar.InnerShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            platformName(platform).take(1),
            color = color, fontWeight = FontWeight.SemiBold, fontSize = (size * 0.42f).sp,
        )
    }
}

/** Small state tag. Filled = new, outlined = updated / neutral. */
@Composable
fun Tag(text: String, color: Color = Radar.Accent, filled: Boolean = false) {
    Box(
        Modifier
            .clip(Radar.TagShape)
            .background(if (filled) color else Color.Transparent)
            .border(1.dp, if (filled) color else color.copy(alpha = 0.45f), Radar.TagShape)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            color = if (filled) Radar.OnAccent else color,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier = modifier,
        style = MaterialTheme.typography.titleSmall,
        color = Radar.Muted,
    )
}

/** Selectable chip: 40dp tall pill with an animated selected state. */
@Composable
fun RadarChip(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val bg by animateColorAsState(if (selected) Radar.AccentSoft else Color.Transparent, label = "chipBg")
    val line by animateColorAsState(if (selected) Radar.Accent.copy(alpha = 0.55f) else Radar.Line, label = "chipLine")
    val fg by animateColorAsState(if (selected) Radar.Accent else Radar.Text, label = "chipFg")
    Box(
        modifier
            .heightIn(min = 40.dp)
            .clip(Radar.PillShape)
            .background(bg)
            .border(1.dp, line, Radar.PillShape)
            .clickable(role = Role.Checkbox, onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = fg, style = MaterialTheme.typography.labelMedium, maxLines = 1)
    }
}

/** Primary action: full pill, 56dp, optional nested trailing arrow. */
@Composable
fun PrimaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    loading: Boolean = false,
    arrow: Boolean = false,
) {
    val interaction = remember { MutableInteractionSource() }
    val active = enabled && !loading
    val bg by animateColorAsState(if (enabled) Radar.Accent else Radar.SurfaceHi, label = "btnBg")
    val fg = if (enabled) Radar.OnAccent else Radar.Faint
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .pressScale(interaction, 0.98f)
            .clip(Radar.PillShape)
            .background(bg)
            .clickable(interaction, indication = null, enabled = active, role = Role.Button, onClick = onClick),
    ) {
        if (loading) {
            CircularProgressIndicator(
                Modifier.align(Alignment.Center).size(22.dp), color = fg, strokeWidth = 2.dp,
            )
        } else {
            Text(label, Modifier.align(Alignment.Center), color = fg, style = MaterialTheme.typography.labelLarge)
            if (arrow) {
                Box(
                    Modifier
                        .align(Alignment.CenterEnd)
                        .padding(end = 8.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Radar.OnAccent.copy(alpha = 0.14f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowForward, null, tint = fg, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** Secondary action: outlined pill, same height rhythm as the primary. */
@Composable
fun SecondaryButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    color: Color = Radar.Text,
) {
    val interaction = remember { MutableInteractionSource() }
    Box(
        modifier
            .height(48.dp)
            .pressScale(interaction, 0.98f)
            .clip(Radar.PillShape)
            .border(1.dp, Radar.Line, Radar.PillShape)
            .clickable(interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .padding(horizontal = 20.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = if (enabled) color else Radar.Faint, style = MaterialTheme.typography.labelLarge)
    }
}

/** 48dp circular icon button with a visible resting surface. */
@Composable
fun IconCircleButton(
    icon: ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Radar.Text,
    surface: Boolean = true,
) {
    Box(
        modifier
            .size(48.dp)
            .clip(CircleShape)
            .then(if (surface) Modifier.background(Radar.Surface).border(1.dp, Radar.Line, CircleShape) else Modifier)
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(22.dp))
    }
}

/** Top bar for pushed screens. Insets are handled by the app scaffold, not here. */
@Composable
fun ScreenTopBar(title: String, onBack: () -> Unit, action: (@Composable () -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircleButton(Icons.AutoMirrored.Outlined.ArrowBack, "Back", onBack, surface = false)
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 8.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        if (action != null) action() else Spacer(Modifier.width(48.dp))
    }
}

@Composable
fun radarFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = Radar.Accent,
    unfocusedBorderColor = Radar.Line,
    focusedContainerColor = Radar.Surface,
    unfocusedContainerColor = Radar.Surface,
    cursorColor = Radar.Accent,
    focusedLabelColor = Radar.Accent,
    unfocusedLabelColor = Radar.Muted,
    focusedPlaceholderColor = Radar.Faint,
    unfocusedPlaceholderColor = Radar.Faint,
    focusedLeadingIconColor = Radar.Muted,
    unfocusedLeadingIconColor = Radar.Muted,
)

/** Compact search pill: 46dp tall, so it lines up with the chips below it. */
@Composable
fun SearchField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()
    val line by animateColorAsState(if (focused) Radar.Accent else Radar.Line, label = "searchLine")
    val style = MaterialTheme.typography.bodyMedium.copy(color = Radar.Text, fontSize = 14.sp)
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.height(46.dp),
        singleLine = true,
        textStyle = style,
        cursorBrush = SolidColor(Radar.Accent),
        interactionSource = interaction,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxSize()
                    .clip(Radar.PillShape)
                    .background(Radar.Surface)
                    .border(1.dp, line, Radar.PillShape)
                    .padding(start = 14.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Search, null, tint = Radar.Muted, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(10.dp))
                Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) {
                        Text(placeholder, style = style, color = Radar.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    inner()
                }
                if (value.isNotEmpty()) {
                    Box(
                        Modifier.size(38.dp).clip(CircleShape).clickable(role = Role.Button) { onValueChange("") },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Outlined.Close, "Clear search", tint = Radar.Muted, modifier = Modifier.size(18.dp))
                    }
                } else {
                    Spacer(Modifier.width(10.dp))
                }
            }
        },
    )
}

/** Grouped surface: the one container style used across the app. */
@Composable
fun Panel(
    modifier: Modifier = Modifier,
    padding: Dp = 18.dp,
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(Radar.CardShape)
            .background(Radar.Surface)
            .border(1.dp, Radar.Line, Radar.CardShape)
            .padding(padding),
        content = content,
    )
}

/** One metric: large tabular number over a quiet label. */
@Composable
fun Metric(value: String, label: String, modifier: Modifier = Modifier, accent: Boolean = false) {
    Column(modifier) {
        Text(
            value,
            fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 20.sp,
            color = if (accent) Radar.Accent else Radar.Text,
            maxLines = 1, overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(2.dp))
        Text(label, style = MaterialTheme.typography.labelSmall, color = Radar.Muted, maxLines = 1)
    }
}

// --------------------------------------------------------------------------- //
// Program card                                                                  //
// --------------------------------------------------------------------------- //

/**
 * One row of the feed. Built for scrolling: no shadow, no intrinsic measurement,
 * press feedback in the layer phase, every derived value pre-computed.
 */
@Composable
fun ProgramCard(
    item: ProgramItem,
    bookmarked: Boolean,
    onClick: (String) -> Unit,
    onBookmark: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val bg by animateColorAsState(if (pressed) Radar.SurfaceHi else Radar.Surface, label = "cardBg")
    val program = item.program

    Column(
        modifier
            .fillMaxWidth()
            .pressScale(interaction, 0.98f)
            .clip(Radar.CardShape)
            .background(bg)
            .border(1.dp, Radar.Line, Radar.CardShape)
            .clickable(interaction, indication = null, role = Role.Button) { onClick(item.docId) }
            .padding(start = 16.dp, top = 12.dp, end = 6.dp, bottom = 16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PlatformAvatar(item.platformKey, 44)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    program.name,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
                Text(
                    item.subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = Radar.Muted, maxLines = 1, overflow = TextOverflow.Ellipsis,
                )
            }
            BookmarkButton(bookmarked) { onBookmark(item.docId) }
        }
        Spacer(Modifier.height(10.dp))
        Row(
            Modifier.padding(end = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                item.rewardLabel,
                Modifier.weight(1f),
                fontFamily = GeistMono, fontWeight = FontWeight.Medium, fontSize = 14.sp,
                color = if (program.bounty) Radar.Accent else Radar.Muted,
                maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (item.hasWildcard) Tag("Wildcard", Radar.Muted)
            if (item.isNew) Tag("New", filled = true) else if (item.isUpdated) Tag("Updated")
        }
    }
}

/** 48dp bookmark target with a spring pop and a haptic tick. */
@Composable
fun BookmarkButton(bookmarked: Boolean, onToggle: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val scale = animateFloatAsState(
        if (bookmarked) 1.12f else 1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessMedium),
        label = "bookmark",
    )
    val tint by animateColorAsState(if (bookmarked) Radar.Accent else Radar.Faint, label = "bookmarkTint")
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox) {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onToggle()
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (bookmarked) Icons.Filled.Bookmark else Icons.Outlined.BookmarkBorder,
            contentDescription = if (bookmarked) "Remove bookmark" else "Bookmark",
            tint = tint,
            modifier = Modifier.size(22.dp).graphicsLayer { scaleX = scale.value; scaleY = scale.value },
        )
    }
}

/** Placeholder with the same shape as a program card, shown while loading. */
@Composable
fun SkeletonCard() {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(Radar.CardShape)
            .background(Radar.Surface)
            .border(1.dp, Radar.Line, Radar.CardShape)
            .padding(16.dp)
            .skeletonPulse()
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(Radar.InnerShape).background(Radar.SurfaceHi))
            Spacer(Modifier.width(12.dp))
            Column {
                Box(Modifier.width(160.dp).height(14.dp).clip(Radar.TagShape).background(Radar.SurfaceHi))
                Spacer(Modifier.height(8.dp))
                Box(Modifier.width(96.dp).height(10.dp).clip(Radar.TagShape).background(Radar.SurfaceHi))
            }
        }
        Spacer(Modifier.height(18.dp))
        Box(Modifier.width(120.dp).height(12.dp).clip(Radar.TagShape).background(Radar.SurfaceHi))
    }
}

@Composable
fun EmptyState(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = 28.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        RadarLogo(56.dp)
        Spacer(Modifier.height(18.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = Radar.Muted, textAlign = TextAlign.Center)
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(20.dp))
            SecondaryButton(actionLabel, onAction, color = Radar.Accent)
        }
    }
}

@Composable
fun FullScreenLoading() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { RadarLogo(64.dp, pulse = true) }
}
