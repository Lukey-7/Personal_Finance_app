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
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pft.financetracker.R

/*
 * Buro-derived design language.
 *
 * High-utility minimalism: a warm off-white page, white cards separated by hairlines instead of
 * shadows, one saturated blue for anything actionable, and numbers treated as the hero element.
 * Colours below were sampled from the reference screens in stitch_buro_fintech_app/.
 *
 * Dynamic (wallpaper) colour is deliberately off so the app looks the same on every phone.
 */

// --- sampled from the reference ---
private val Page = Color(0xFFFDFCFB)        // warm off-white background
private val CardWhite = Color(0xFFFFFFFF)
private val SoftPanel = Color(0xFFF4F5F6)   // tonal grouping, no border
private val Hairline = Color(0xFFE7E6E6)    // the only separator
private val Ink = Color(0xFF000000)
private val InkBody = Color(0xFF101112)
private val Muted = Color(0xFF70757D)
private val MutedSoft = Color(0xFFA8ABB0)
private val Accent = Color(0xFF0000FF)      // Buro blue: buttons, links, selected state
private val AccentSoft = Color(0xFFF0EFFB)  // selected chip / avatar tint
private val Danger = Color(0xFFB50000)
private val DangerSoft = Color(0xFFFDECEC)

private val Light = lightColorScheme(
    primary = Accent,
    onPrimary = Color.White,
    primaryContainer = AccentSoft,
    onPrimaryContainer = Accent,
    secondary = Ink,
    onSecondary = Color.White,
    secondaryContainer = SoftPanel,
    onSecondaryContainer = InkBody,
    tertiary = Accent,
    onTertiary = Color.White,
    tertiaryContainer = AccentSoft,
    onTertiaryContainer = Accent,
    background = Page,
    onBackground = InkBody,
    surface = CardWhite,
    onSurface = InkBody,
    surfaceVariant = SoftPanel,
    onSurfaceVariant = Muted,
    surfaceContainer = SoftPanel,
    surfaceContainerLow = Page,
    surfaceContainerLowest = CardWhite,
    surfaceContainerHigh = SoftPanel,
    surfaceContainerHighest = Hairline,
    outline = MutedSoft,
    outlineVariant = Hairline,
    error = Danger,
    onError = Color.White,
    errorContainer = DangerSoft,
    onErrorContainer = Danger,
    scrim = Color(0x33000000),
)

/* The reference is light-only; this keeps its structure - one accent, hairlines, no shadows. */
private val Dark = darkColorScheme(
    primary = Color(0xFF9DA8FF),
    onPrimary = Color(0xFF00007A),
    primaryContainer = Color(0xFF1B1F3B),
    onPrimaryContainer = Color(0xFFC9CFFF),
    secondary = Color(0xFFE8E8E8),
    onSecondary = Color(0xFF101112),
    secondaryContainer = Color(0xFF1C1D1F),
    onSecondaryContainer = Color(0xFFE8E8E8),
    tertiary = Color(0xFF9DA8FF),
    onTertiary = Color(0xFF00007A),
    tertiaryContainer = Color(0xFF1B1F3B),
    onTertiaryContainer = Color(0xFFC9CFFF),
    background = Color(0xFF0B0B0C),
    onBackground = Color(0xFFF2F2F3),
    surface = Color(0xFF121314),
    onSurface = Color(0xFFF2F2F3),
    surfaceVariant = Color(0xFF1C1D1F),
    onSurfaceVariant = Color(0xFFA8ABB0),
    surfaceContainer = Color(0xFF1C1D1F),
    surfaceContainerLow = Color(0xFF121314),
    surfaceContainerLowest = Color(0xFF0B0B0C),
    surfaceContainerHigh = Color(0xFF232425),
    surfaceContainerHighest = Color(0xFF2B2C2E),
    outline = Color(0xFF5A5D62),
    outlineVariant = Color(0xFF2B2C2E),
    error = Color(0xFFFF8A80),
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF3A1210),
    onErrorContainer = Color(0xFFFFB4AB),
)

/** Money direction. Sampled from the reference; the same two colours everywhere, nowhere else. */
val Income = Color(0xFF00A62D)
val Expense = Color(0xFFB50000)
val Neutral = Color(0xFF70757D)

/** Category accents for charts and avatars: muted, distinguishable, legible on both themes. */
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

/** High-radius curvature: pill controls, 24dp cards. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(10.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/**
 * Numbers are the hero: large balances get tight tracking so they read as a single dense block.
 * Everything else stays quiet so the figures carry the page.
 */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = Inter, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.8).sp),
    displayMedium = TextStyle(fontFamily = Inter, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.2).sp),
    displaySmall = TextStyle(fontFamily = Inter, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp),
    headlineLarge = TextStyle(fontFamily = Inter, fontSize = 30.sp, lineHeight = 36.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontSize = 26.sp, lineHeight = 32.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
    headlineSmall = TextStyle(fontFamily = Inter, fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.3).sp),
    titleLarge = TextStyle(fontFamily = Inter, fontSize = 20.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp),
    titleMedium = TextStyle(fontFamily = Inter, fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp),
    titleSmall = TextStyle(fontFamily = Inter, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold),
    bodyLarge = TextStyle(fontFamily = Inter, fontSize = 16.sp, lineHeight = 24.sp, fontWeight = FontWeight.Normal),
    bodyMedium = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal),
    bodySmall = TextStyle(fontFamily = Inter, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal),
    labelLarge = TextStyle(fontFamily = Inter, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium),
    labelMedium = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
    labelSmall = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.2.sp),
)

/** Small uppercase label that sits above a figure, as in the reference. */
val LabelCaps = TextStyle(fontFamily = Inter, fontSize = 12.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.6.sp)

@Composable
fun FinTrackTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = AppShapes, typography = AppTypography, content = content)
}
