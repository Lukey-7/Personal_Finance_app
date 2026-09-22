package com.pft.financetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
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

/*
 * FinTrack palette: deep navy for structure, teal for action, warm neutrals for surfaces.
 * Red/green are reserved for money direction so they always mean the same thing.
 * Dynamic (wallpaper) colour is intentionally off so the app looks the same on every phone.
 */
private val Navy900 = Color(0xFF0F1B2D)
private val Navy800 = Color(0xFF16263D)
private val Navy700 = Color(0xFF1F3552)
private val Navy100 = Color(0xFFDCE6F5)
private val Teal600 = Color(0xFF0E7C86)
private val Teal500 = Color(0xFF15919B)
private val Teal100 = Color(0xFFCDEEF1)
private val Teal200 = Color(0xFF7FD3DB)
private val Sand50 = Color(0xFFF7F8FA)
private val Sand100 = Color(0xFFEEF1F5)
private val Sand200 = Color(0xFFE1E6EC)
private val Ink = Color(0xFF111827)
private val InkMuted = Color(0xFF5B6675)
private val Amber = Color(0xFFB7791F)
private val AmberContainer = Color(0xFFFBEFD5)

private val Light = lightColorScheme(
    primary = Navy800,
    onPrimary = Color.White,
    primaryContainer = Navy100,
    onPrimaryContainer = Navy900,
    secondary = Teal600,
    onSecondary = Color.White,
    secondaryContainer = Teal100,
    onSecondaryContainer = Color(0xFF063B40),
    tertiary = Amber,
    onTertiary = Color.White,
    tertiaryContainer = AmberContainer,
    onTertiaryContainer = Color(0xFF4A3000),
    background = Sand50,
    onBackground = Ink,
    surface = Color.White,
    onSurface = Ink,
    surfaceVariant = Sand100,
    onSurfaceVariant = InkMuted,
    surfaceContainer = Sand100,
    surfaceContainerLow = Sand50,
    surfaceContainerHigh = Sand200,
    outline = Color(0xFFC3CBD6),
    outlineVariant = Sand200,
    error = Color(0xFFB3261E),
    onError = Color.White,
    errorContainer = Color(0xFFF9DEDC),
    onErrorContainer = Color(0xFF410E0B),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFB9CCE8),
    onPrimary = Navy900,
    primaryContainer = Navy700,
    onPrimaryContainer = Navy100,
    secondary = Teal200,
    onSecondary = Color(0xFF00363B),
    secondaryContainer = Color(0xFF0B4F55),
    onSecondaryContainer = Teal100,
    tertiary = Color(0xFFE8C078),
    onTertiary = Color(0xFF3F2E00),
    tertiaryContainer = Color(0xFF5B4300),
    onTertiaryContainer = Color(0xFFFFE0A3),
    background = Color(0xFF0B1220),
    onBackground = Color(0xFFE6EAF0),
    surface = Color(0xFF0F172A),
    onSurface = Color(0xFFE6EAF0),
    surfaceVariant = Color(0xFF1B2537),
    onSurfaceVariant = Color(0xFFA7B1C2),
    surfaceContainer = Color(0xFF15203A),
    surfaceContainerLow = Color(0xFF0F172A),
    surfaceContainerHigh = Color(0xFF1C2842),
    outline = Color(0xFF4B5668),
    outlineVariant = Color(0xFF2A3547),
    error = Color(0xFFF2B8B5),
    onError = Color(0xFF601410),
    errorContainer = Color(0xFF8C1D18),
    onErrorContainer = Color(0xFFF9DEDC),
)

/** Stable per-category palette used by charts and avatars. Muted, distinguishable, works on both themes. */
val CategoryColors: List<Color> = listOf(
    Color(0xFFE07A5F), // Food
    Color(0xFF3D5A80), // Shopping
    Color(0xFFF2A541), // Bills
    Color(0xFF6C8EAD), // Transport
    Color(0xFF9B5DE5), // Entertainment
    Color(0xFF2A9D8F), // Health
    Color(0xFF577590), // Education
    Color(0xFF43AA8B), // Investment
    Color(0xFF8D99AE), // ATM
    Color(0xFF7B8CDE), // Transfer
    Color(0xFF3A9B5C), // Income
    Color(0xFF9AA5B1), // Other
)

val Income = Color(0xFF1F8A4C)
val Expense = Color(0xFFC0392B)
val Neutral = Color(0xFF6B7A90)

private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(14.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.SemiBold),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.SemiBold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.Medium),
        labelMedium = TextStyle(fontSize = 12.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.4.sp),
    )
}

@Composable
fun FinTrackTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = AppShapes, typography = AppTypography, content = content)
}
