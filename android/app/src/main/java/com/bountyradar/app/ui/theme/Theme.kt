package com.bountyradar.app.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.bountyradar.app.ui.ThemeMode

// ---- Brand palette --------------------------------------------------------
private val NeonGreen = Color(0xFF1FE0A0)
private val NeonGreenDark = Color(0xFF0FB983)
private val Cyan = Color(0xFF35D0FF)
private val Violet = Color(0xFF8B7BFF)
private val Amber = Color(0xFFFFC24B)

private val DarkColors = darkColorScheme(
    primary = NeonGreen,
    onPrimary = Color(0xFF05130D),
    primaryContainer = Color(0xFF0E3A2C),
    onPrimaryContainer = Color(0xFFA8FBDD),
    secondary = Cyan,
    onSecondary = Color(0xFF03161D),
    tertiary = Violet,
    background = Color(0xFF0A0E13),
    onBackground = Color(0xFFE6EDF3),
    surface = Color(0xFF11161E),
    onSurface = Color(0xFFE6EDF3),
    surfaceVariant = Color(0xFF1C2530),
    onSurfaceVariant = Color(0xFF9FB0C0),
    outline = Color(0xFF2C3946),
    error = Color(0xFFFF6B6B),
)

private val LightColors = lightColorScheme(
    primary = NeonGreenDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFB8F5DF),
    onPrimaryContainer = Color(0xFF00261A),
    secondary = Color(0xFF0E8FBF),
    tertiary = Color(0xFF5B4ED6),
    background = Color(0xFFF6F8FA),
    onBackground = Color(0xFF0C1116),
    surface = Color(0xFFFFFFFF),
    onSurface = Color(0xFF0C1116),
    surfaceVariant = Color(0xFFE7ECF1),
    onSurfaceVariant = Color(0xFF53616E),
    outline = Color(0xFFCBD5DF),
    error = Color(0xFFD3454B),
)

/** Stable accent color per platform for chips, bars and cards. */
fun platformColor(platform: String): Color = when (platform.removePrefix("fb:")) {
    "hackerone" -> Color(0xFF3FA9F5)
    "bugcrowd" -> Color(0xFFFF7A59)
    "intigriti" -> Color(0xFF6C5CE7)
    "yeswehack" -> Color(0xFF18C29C)
    "immunefi" -> Color(0xFFB388FF)
    "sherlock" -> Color(0xFFFF5DA2)
    "cantina" -> Color(0xFFFFC24B)
    "federacy" -> Color(0xFF4DD0E1)
    else -> Color(0xFF8C9AA8)
}

val AccentCyan = Cyan
val AccentViolet = Violet
val AccentAmber = Amber

@Composable
fun BountyRadarTheme(
    themeMode: ThemeMode,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
    }
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = Color.Transparent.toArgb()
            window.navigationBarColor = Color.Transparent.toArgb()
            val light = colors.background.luminance() > 0.5f
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = light
                isAppearanceLightNavigationBars = light
            }
        }
    }

    MaterialTheme(
        colorScheme = colors,
        typography = MaterialTheme.typography,
        content = content,
    )
}
