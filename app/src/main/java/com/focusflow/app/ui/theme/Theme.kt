package com.focusflow.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** User-selectable theme preference. Dark is the default. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

private val Accent = Color(0xFF6EA8FF)
private val AccentDark = Color(0xFF2F6FDB)

private val DarkColors: ColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = Color(0xFF06214A),
    primaryContainer = Color(0xFF1B3A6B),
    onPrimaryContainer = Color(0xFFD6E6FF),
    secondary = Color(0xFF7FD6C2),
    onSecondary = Color(0xFF00382E),
    background = Color(0xFF0E1116),
    onBackground = Color(0xFFE6E8EC),
    surface = Color(0xFF151A21),
    onSurface = Color(0xFFE6E8EC),
    surfaceVariant = Color(0xFF1E2530),
    onSurfaceVariant = Color(0xFFA9B1BE),
    outline = Color(0xFF3A4352),
    error = Color(0xFFFF8A80),
)

private val LightColors: ColorScheme = lightColorScheme(
    primary = AccentDark,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E6FF),
    onPrimaryContainer = Color(0xFF0B2A5C),
    secondary = Color(0xFF1B7F6C),
    background = Color(0xFFF6F7FA),
    onBackground = Color(0xFF11151B),
    surface = Color.White,
    onSurface = Color(0xFF11151B),
    surfaceVariant = Color(0xFFE8EBF1),
    onSurfaceVariant = Color(0xFF4A5362),
    outline = Color(0xFFB7BECB),
)

private val AppTypography = Typography(
    headlineMedium = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold),
    titleLarge = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold),
    titleMedium = TextStyle(fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.Medium),
    bodyLarge = TextStyle(fontSize = 16.sp, lineHeight = 24.sp),
    bodyMedium = TextStyle(fontSize = 14.sp, lineHeight = 20.sp),
    labelLarge = TextStyle(fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium),
)

private val AppShapes = Shapes(
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
)

@Composable
fun FocusFlowTheme(
    themeMode: ThemeMode = ThemeMode.DARK,
    content: @Composable () -> Unit,
) {
    val dark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    MaterialTheme(
        colorScheme = if (dark) DarkColors else LightColors,
        typography = AppTypography,
        shapes = AppShapes,
        content = content,
    )
}
