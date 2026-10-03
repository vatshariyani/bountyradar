package com.bountyradar.app.ui.theme

import android.app.Activity
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.bountyradar.app.R

/**
 * Design tokens. One dark theme, one neutral family (a green-tinted off-black),
 * ONE accent. Platform hues exist only as small identity marks.
 */
object Radar {
    val Bg = Color(0xFF0A0C0B)
    val Surface = Color(0xFF111413)
    val SurfaceHi = Color(0xFF191D1B)
    val Line = Color(0xFF252A28)
    val Text = Color(0xFFECEFED)
    val Muted = Color(0xFF8E9893)
    val Faint = Color(0xFF5F6964)
    val Accent = Color(0xFF3DDC97)
    val AccentSoft = Color(0x243DDC97)   // 14% accent wash for selected states
    val OnAccent = Color(0xFF04140D)
    val Warn = Color(0xFFE5B454)
    val Danger = Color(0xFFEF6F6C)

    // One radius system: containers 20, inner blocks 14, tags 8, controls are pills.
    val CardShape = RoundedCornerShape(20.dp)
    val InnerShape = RoundedCornerShape(14.dp)
    val TagShape = RoundedCornerShape(8.dp)
    val PillShape = RoundedCornerShape(50)

    val ScreenPadding = 20.dp
}

private val Colors = darkColorScheme(
    primary = Radar.Accent,
    onPrimary = Radar.OnAccent,
    primaryContainer = Radar.AccentSoft,
    onPrimaryContainer = Radar.Accent,
    secondary = Radar.Text,
    onSecondary = Radar.Bg,
    secondaryContainer = Radar.SurfaceHi,
    onSecondaryContainer = Radar.Text,
    tertiary = Radar.Warn,
    onTertiary = Radar.Bg,
    background = Radar.Bg,
    onBackground = Radar.Text,
    surface = Radar.Surface,
    onSurface = Radar.Text,
    surfaceVariant = Radar.SurfaceHi,
    onSurfaceVariant = Radar.Muted,
    surfaceTint = Radar.Surface,          // no accent-tinted elevation overlays
    surfaceContainer = Radar.Surface,
    surfaceContainerLow = Radar.Surface,
    surfaceContainerHigh = Radar.SurfaceHi,
    surfaceContainerHighest = Radar.SurfaceHi,
    outline = Radar.Line,
    outlineVariant = Radar.Line,
    error = Radar.Danger,
    onError = Radar.Bg,
    scrim = Color(0xCC000000),
)

@OptIn(ExperimentalTextApi::class)
private fun geist(res: Int, weight: Int) =
    Font(res, FontWeight(weight), variationSettings = FontVariation.Settings(FontVariation.weight(weight)))

val Geist = FontFamily(
    geist(R.font.geist, 400), geist(R.font.geist, 500), geist(R.font.geist, 600), geist(R.font.geist, 700),
)

/** Tabular figures for money, counts and scope entries. */
val GeistMono = FontFamily(
    geist(R.font.geist_mono, 400), geist(R.font.geist_mono, 500), geist(R.font.geist_mono, 600),
)

private fun style(size: Int, line: Int, weight: Int, tracking: Double = 0.0) = TextStyle(
    fontFamily = Geist, fontSize = size.sp, lineHeight = line.sp,
    fontWeight = FontWeight(weight), letterSpacing = tracking.em,
)

private val Type = Typography(
    displayLarge = style(44, 46, 600, -0.035),
    displayMedium = style(36, 40, 600, -0.03),
    displaySmall = style(30, 34, 600, -0.03),
    headlineLarge = style(28, 32, 600, -0.025),
    headlineMedium = style(24, 30, 600, -0.02),
    headlineSmall = style(21, 28, 600, -0.02),
    titleLarge = style(19, 26, 600, -0.015),
    titleMedium = style(16, 22, 600, -0.01),
    titleSmall = style(15, 20, 600, -0.005),
    bodyLarge = style(16, 24, 400),
    bodyMedium = style(15, 22, 400),
    bodySmall = style(13, 19, 400),
    labelLarge = style(15, 20, 600),
    labelMedium = style(13, 18, 500),
    labelSmall = style(12, 16, 500, 0.01),
)

private val AppShapes = Shapes(
    extraSmall = Radar.TagShape,
    small = Radar.InnerShape,
    medium = Radar.InnerShape,
    large = Radar.CardShape,
    extraLarge = RoundedCornerShape(28.dp),
)

/** Stable, restrained identity hue per platform (used for the monogram only). */
fun platformColor(platform: String): Color = when (platform.removePrefix("fb:")) {
    "hackerone" -> Color(0xFF8FB4E8)
    "bugcrowd" -> Color(0xFFE8A07E)
    "intigriti" -> Color(0xFFA9A2E6)
    "yeswehack" -> Color(0xFF7FD1BC)
    "immunefi" -> Color(0xFFC3A8E6)
    "sherlock" -> Color(0xFFE59AB8)
    "cantina" -> Color(0xFFE5C37A)
    "federacy" -> Color(0xFF8CCFD8)
    "hackenproof" -> Color(0xFF86D6C4)
    "standoff365" -> Color(0xFFE59A98)
    "independent" -> Color(0xFFB4CF8A)
    else -> Radar.Muted
}

fun platformName(key: String): String = when (key.removePrefix("fb:")) {
    "hackerone" -> "HackerOne"
    "bugcrowd" -> "Bugcrowd"
    "intigriti" -> "Intigriti"
    "yeswehack" -> "YesWeHack"
    "immunefi" -> "Immunefi"
    "sherlock" -> "Sherlock"
    "cantina" -> "Cantina"
    "federacy" -> "Federacy"
    "hackenproof" -> "HackenProof"
    "standoff365" -> "Standoff 365"
    "independent" -> "Self-hosted"
    else -> key.replaceFirstChar { it.uppercase() }
}

@Composable
fun BountyRadarTheme(content: @Composable () -> Unit) {
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = false
                isAppearanceLightNavigationBars = false
            }
        }
    }
    MaterialTheme(colorScheme = Colors, typography = Type, shapes = AppShapes, content = content)
}
