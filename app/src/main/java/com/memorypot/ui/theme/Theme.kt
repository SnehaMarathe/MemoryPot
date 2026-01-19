package com.memorypot.ui.theme

import android.app.Activity
import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat

private val LightColors = lightColorScheme(
    // iOS-inspired palette (clean, high-contrast, "premium" neutrals)
    primary = Color(0xFF007AFF),
    onPrimary = Color.White,
    secondary = Color(0xFF34C759),
    onSecondary = Color.White,
    tertiary = Color(0xFFFF9500),
    onTertiary = Color.White,
    background = Color(0xFFF2F2F7),
    surface = Color(0xFFFFFFFF),
    surfaceVariant = Color(0xFFE5E5EA),
    onSurfaceVariant = Color(0xFF3A3A3C)
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFFB1C5FF),
    onPrimary = Color(0xFF0F2A6A),
    secondary = Color(0xFFB6C6E8),
    onSecondary = Color(0xFF1A2A44),
    tertiary = Color(0xFF7DDAC3),
    onTertiary = Color(0xFF00382D),
    background = Color(0xFF0E1118),
    surface = Color(0xFF121622),
    surfaceVariant = Color(0xFF2A2F3B),
    onSurfaceVariant = Color(0xFFC7CAD6)
)

private val AppTypography = Typography(
    titleLarge = Typography().titleLarge,
    titleMedium = Typography().titleMedium,
    titleSmall = Typography().titleSmall,
    bodyLarge = Typography().bodyLarge,
    bodyMedium = Typography().bodyMedium,
    bodySmall = Typography().bodySmall,
    labelLarge = Typography().labelLarge,
    labelMedium = Typography().labelMedium,
    labelSmall = Typography().labelSmall
)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(32.dp)
)

@Composable
fun MemoryPotTheme(
    useDynamicColor: Boolean = true,
    content: @Composable () -> Unit
) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current

    val colorScheme = when {
        useDynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        dark -> DarkColors
        else -> LightColors
    }

    val view = LocalView.current
    SideEffect {
        val window = (view.context as? Activity)?.window ?: return@SideEffect
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = Color.Transparent.toArgb()
        window.navigationBarColor = Color.Transparent.toArgb()
        val controller = WindowInsetsControllerCompat(window, view)
        controller.isAppearanceLightStatusBars = !dark
        controller.isAppearanceLightNavigationBars = !dark
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = AppTypography,
        shapes = AppShapes,
        content = content
    )
}
