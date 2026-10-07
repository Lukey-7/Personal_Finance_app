package com.pft.financetracker.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pft.financetracker.R

/*
 * "Quiet ledger": Buro's calm grown into a tactile app.
 *
 * A warm page, white cards on hairlines, and one deep ink-blue that is the only tappable colour. Depth comes from four
 * surface tiers (page, card, raised, sunken) instead of hairlines alone, and money has its own face with tabular
 * figures so columns of amounts line up. Every text pair is at least 4.5:1 and every UI edge 3:1 in both themes;
 * the measured ratios are in docs/redesign/tokens.md.
 *
 * Dynamic (wallpaper) colour is deliberately off so the app looks the same on every phone.
 */

/** The four surface tiers, plus the lines drawn on them. Read with [surfaces]. */
@Immutable
data class Surfaces(
    /** The window behind everything. */
    val page: Color,
    /** Grouped content: cards, list backgrounds. */
    val card: Color,
    /** Things that float: sheets, the Add button, urgent cards, the nav pill. Paired with a soft shadow. */
    val raised: Color,
    /** Things you put something into: inputs, the segment track, the number pad, skeletons. */
    val sunken: Color,
    /** The hairline between rows and around cards (decorative, not a control edge). */
    val hairline: Color,
    /** A control's edge that must be seen (3:1): unselected chip outlines, checkboxes. */
    val outline: Color,
    /** The accent's soft tint: selected chips and the tinted squares behind icons. */
    val accentSoft: Color,
    /** Shadow tint for raised surfaces. */
    val shadow: Color,
)

private val LightSurfaces = Surfaces(
    page = Color(0xFFFDFCFB),
    card = Color(0xFFFFFFFF),
    raised = Color(0xFFFFFFFF),
    sunken = Color(0xFFF3F2EE),
    hairline = Color(0xFFE6E4DF),
    outline = Color(0xFF85888F),
    accentSoft = Color(0xFFEBEEFC),
    shadow = Color(0x29111214),
)

private val DarkSurfaces = Surfaces(
    page = Color(0xFF0B0B0C),
    card = Color(0xFF151618),
    raised = Color(0xFF1D1E21),
    sunken = Color(0xFF101113),
    hairline = Color(0xFF2A2B2F),
    outline = Color(0xFF6E727A),
    accentSoft = Color(0xFF1E2340),
    shadow = Color(0x99000000),
)

val LocalSurfaces = staticCompositionLocalOf { LightSurfaces }

/** The current theme's surface tiers. */
val surfaces: Surfaces @Composable @ReadOnlyComposable get() = LocalSurfaces.current

private val AccentLight = Color(0xFF1F3BD6)   // 7.9:1 on card, 7.1:1 on sunken
private val AccentDark = Color(0xFF9DA8FF)    // 8.2:1 on card
private val InkLight = Color(0xFF111214)
private val InkDark = Color(0xFFF2F2F3)
private val MutedLight = Color(0xFF62666E)    // 5.8:1 on card, 5.1:1 on sunken
private val MutedDark = Color(0xFFA3A7AE)     // 7.5:1 on card
private val DangerLight = Color(0xFFB50000)
private val DangerDark = Color(0xFFFF7B72)

private val Light = lightColorScheme(
    primary = AccentLight,
    onPrimary = Color.White,
    primaryContainer = LightSurfaces.accentSoft,
    onPrimaryContainer = AccentLight,
    inversePrimary = AccentDark,
    secondary = InkLight,
    onSecondary = Color.White,
    secondaryContainer = LightSurfaces.accentSoft,
    onSecondaryContainer = AccentLight,
    tertiary = AccentLight,
    onTertiary = Color.White,
    tertiaryContainer = LightSurfaces.accentSoft,
    onTertiaryContainer = AccentLight,
    background = LightSurfaces.page,
    onBackground = InkLight,
    surface = LightSurfaces.card,
    onSurface = InkLight,
    surfaceVariant = LightSurfaces.sunken,
    onSurfaceVariant = MutedLight,
    surfaceTint = Color.Transparent,
    inverseSurface = InkLight,
    inverseOnSurface = LightSurfaces.page,
    surfaceBright = LightSurfaces.card,
    surfaceDim = LightSurfaces.sunken,
    surfaceContainerLowest = LightSurfaces.card,
    surfaceContainerLow = LightSurfaces.raised,     // bottom sheets
    surfaceContainer = LightSurfaces.sunken,
    surfaceContainerHigh = LightSurfaces.raised,    // dialogs, date pickers, menus
    surfaceContainerHighest = LightSurfaces.sunken, // text field and switch tracks
    outline = LightSurfaces.outline,
    outlineVariant = LightSurfaces.hairline,
    error = DangerLight,
    onError = Color.White,
    errorContainer = Color(0xFFFDECEC),
    onErrorContainer = DangerLight,
    scrim = Color(0x52111214),
)

private val Dark = darkColorScheme(
    primary = AccentDark,
    onPrimary = Color(0xFF0A1170),
    primaryContainer = DarkSurfaces.accentSoft,
    onPrimaryContainer = AccentDark,
    inversePrimary = AccentLight,
    secondary = InkDark,
    onSecondary = Color(0xFF111214),
    secondaryContainer = DarkSurfaces.accentSoft,
    onSecondaryContainer = AccentDark,
    tertiary = AccentDark,
    onTertiary = Color(0xFF0A1170),
    tertiaryContainer = DarkSurfaces.accentSoft,
    onTertiaryContainer = AccentDark,
    background = DarkSurfaces.page,
    onBackground = InkDark,
    surface = DarkSurfaces.card,
    onSurface = InkDark,
    surfaceVariant = DarkSurfaces.sunken,
    onSurfaceVariant = MutedDark,
    surfaceTint = Color.Transparent,
    inverseSurface = InkDark,
    inverseOnSurface = DarkSurfaces.page,
    surfaceBright = DarkSurfaces.raised,
    surfaceDim = DarkSurfaces.page,
    surfaceContainerLowest = DarkSurfaces.page,
    surfaceContainerLow = DarkSurfaces.raised,
    surfaceContainer = DarkSurfaces.sunken,
    surfaceContainerHigh = DarkSurfaces.raised,
    surfaceContainerHighest = Color(0xFF26272B),
    outline = DarkSurfaces.outline,
    outlineVariant = DarkSurfaces.hairline,
    error = DangerDark,
    onError = Color(0xFF4A0000),
    errorContainer = Color(0xFF3A1210),
    onErrorContainer = Color(0xFFFFB4AB),
    scrim = Color(0x8C000000),
)

/**
 * Money direction: money in, money out, and money that only moved. The same three colours everywhere, nowhere else,
 * with a dark-mode set of their own. Every value is at least 4.5:1 on its theme's page, card, raised and sunken tiers.
 */
@Immutable
data class MoneyColors(val income: Color, val expense: Color, val neutral: Color)

private val LightMoney = MoneyColors(income = Color(0xFF007F22), expense = Color(0xFFB50000), neutral = Color(0xFF6A6F77))
private val DarkMoney = MoneyColors(income = Color(0xFF4CD07D), expense = Color(0xFFFF7B72), neutral = Color(0xFF9AA0A8))

val LocalMoneyColors = staticCompositionLocalOf { LightMoney }

val Income: Color @Composable @ReadOnlyComposable get() = LocalMoneyColors.current.income
val Expense: Color @Composable @ReadOnlyComposable get() = LocalMoneyColors.current.expense
val Neutral: Color @Composable @ReadOnlyComposable get() = LocalMoneyColors.current.neutral

/** Colour for a signed balance: green above zero, red below, plain ink at zero (₹0 is neither good nor bad). */
@Composable @ReadOnlyComposable
fun moneyTone(paise: Long): Color = when {
    paise > 0 -> Income
    paise < 0 -> Expense
    else -> MaterialTheme.colorScheme.onSurface
}

/** Category accents for charts and icons: muted, distinguishable, and at least 3:1 (UI) on card in both themes. */
val CategoryColors: List<Color> = listOf(
    Color(0xFFD9694C), // Food
    Color(0xFF4F6FA0), // Shopping
    Color(0xFFC08414), // Bills
    Color(0xFF5E86AA), // Transport
    Color(0xFF9B5DE5), // Entertainment
    Color(0xFF23907F), // Health
    Color(0xFF6A7FA0), // Education
    Color(0xFF3A9C7C), // Investment
    Color(0xFF7F8AA0), // ATM
    Color(0xFF6E7FD8), // Transfer
    Color(0xFF2F9353), // Income
    Color(0xFF8A95A3), // Other
)

/** Generous curvature: 12dp controls, 20dp cards, 28dp sheets. */
private val AppShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(20.dp),
    extraLarge = RoundedCornerShape(28.dp),
)

val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

/** Inter Tight: the display face, for money only. One variable file, pinned to the weights the app uses. */
@OptIn(ExperimentalTextApi::class)
val InterTight = FontFamily(
    Font(R.font.inter_tight, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter_tight, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter_tight, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
)

/** Tabular figures: every digit the same width, so amounts in a column line up. */
const val TabularFigures = "tnum"

/** The type scale: display 56/44/36 (money), title 22/20/17, body 16/14/13, label 12/11. */
private val AppTypography = Typography(
    displayLarge = TextStyle(fontFamily = InterTight, fontSize = 56.sp, lineHeight = 60.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.9).sp, fontFeatureSettings = TabularFigures),
    displayMedium = TextStyle(fontFamily = InterTight, fontSize = 44.sp, lineHeight = 48.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-1.3).sp, fontFeatureSettings = TabularFigures),
    displaySmall = TextStyle(fontFamily = InterTight, fontSize = 36.sp, lineHeight = 40.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.9).sp, fontFeatureSettings = TabularFigures),
    headlineLarge = TextStyle(fontFamily = Inter, fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.5).sp),
    headlineMedium = TextStyle(fontFamily = Inter, fontSize = 24.sp, lineHeight = 30.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp),
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

/**
 * Money styles: Inter Tight with tabular figures. The display sizes are the hero; [row] is a list's amount column;
 * [tile] and [title] sit in tiles and card headers.
 */
object MoneyType {
    val hero: TextStyle get() = AppTypography.displayLarge
    val large: TextStyle get() = AppTypography.displayMedium
    val medium: TextStyle get() = AppTypography.displaySmall
    val title = TextStyle(fontFamily = InterTight, fontSize = 22.sp, lineHeight = 26.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.4).sp, fontFeatureSettings = TabularFigures)
    val tile = TextStyle(fontFamily = InterTight, fontSize = 18.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.2).sp, fontFeatureSettings = TabularFigures)
    val row = TextStyle(fontFamily = InterTight, fontSize = 16.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.1).sp, fontFeatureSettings = TabularFigures)
    val small = TextStyle(fontFamily = InterTight, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularFigures)
    val label = TextStyle(fontFamily = InterTight, fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = TabularFigures)
}

/** Small uppercase label that sits above a figure. */
val LabelCaps = TextStyle(fontFamily = Inter, fontSize = 11.sp, lineHeight = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.9.sp)

@Composable
fun FinTrackTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalMoneyColors provides if (darkTheme) DarkMoney else LightMoney,
        LocalSurfaces provides if (darkTheme) DarkSurfaces else LightSurfaces,
    ) {
        MaterialTheme(colorScheme = if (darkTheme) Dark else Light, shapes = AppShapes, typography = AppTypography) {
            ProvideMotion(content)
        }
    }
}
