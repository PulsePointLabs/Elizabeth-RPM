package com.pulsepointlabs.elizabethlive.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pulsepointlabs.elizabethlive.ThemeSetting

val RpmBlue = Color(0xFF4D8FE8)
val BoostTeal = Color(0xFF36B7A4)
val ThrottleAmber = Color(0xFFF0A84B)
val GoodGreen = Color(0xFF55B68A)
val WarningRed = Color(0xFFE06A6A)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8DB9FF),
    onPrimary = Color(0xFF00315F),
    primaryContainer = Color(0xFF173E65),
    onPrimaryContainer = Color(0xFFD7E8FF),
    secondary = Color(0xFF71D7C6),
    background = Color(0xFF0D131A),
    surface = Color(0xFF141C25),
    surfaceVariant = Color(0xFF202A35),
    onSurface = Color(0xFFE5EDF7),
    onSurfaceVariant = Color(0xFFAAB8C7),
    outline = Color(0xFF3C4A58),
    error = Color(0xFFFFB4AB),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF245F9D),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD5E7FF),
    onPrimaryContainer = Color(0xFF0B365E),
    secondary = Color(0xFF00796B),
    background = Color(0xFFF3F6F9),
    surface = Color(0xFFFCFDFE),
    surfaceVariant = Color(0xFFE7EDF3),
    onSurface = Color(0xFF18212B),
    onSurfaceVariant = Color(0xFF52606D),
    outline = Color(0xFFBCC7D2),
    error = Color(0xFFBA1A1A),
)

@Composable
fun ElizabethTheme(setting: ThemeSetting, content: @Composable () -> Unit) {
    val dark = when (setting) {
        ThemeSetting.SYSTEM -> isSystemInDarkTheme()
        ThemeSetting.LIGHT -> false
        ThemeSetting.DARK -> true
    }
    val colors = if (dark) DarkColors else LightColors
    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            window.statusBarColor = colors.background.toArgb()
            window.navigationBarColor = colors.surface.toArgb()
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography(),
        content = content,
    )
}

