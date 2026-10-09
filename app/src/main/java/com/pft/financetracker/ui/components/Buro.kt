package com.pft.financetracker.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.LabelCaps
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.motion
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.surfaces

/*
 * The building blocks of the "Quiet ledger" language. Depth comes from four surface tiers: cards sit on the page with
 * a hairline, raised things (sheets, the Add button, urgent cards) float on a soft shadow, and sunken wells hold
 * whatever you type or pick. Everything here is presentation only; no screen changes behaviour by using it.
 */

/** Page gutter: 24dp on both edges. */
val Gutter = 24.dp

/** Inner padding for cards. */
val CardPadding = 20.dp

/** The spacing scale. Every gap, padding and spacer in a screen comes from here (4dp grid). */
object Space {
    val xs = 4.dp
    val sm = 8.dp
    val md = 12.dp
    val lg = 16.dp
    val xl = 24.dp
    val xxl = 32.dp
}

/** Card and panel corners. */
val CardRadius = 20.dp
val CardShape = RoundedCornerShape(CardRadius)

/** Controls (segments, inputs, tiles in a grid). */
val ControlShape = RoundedCornerShape(14.dp)

/** Height of every button; tall enough for a 48dp touch target with room for a larger font. */
val ButtonHeight = 52.dp

/** What a text action does: the usual accent, or red for something that removes data. */
enum class Tone { Accent, Danger }

/**
 * Height of the floating nav pill (plus the system navigation bar) on the five tab screens, and zero on every other
 * screen. Tab content scrolls underneath the pill, so lists pad their end by this much and the Add button lifts itself.
 */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/** Bottom content padding for a scrolling screen: clears the nav pill (if any) plus some breathing room. */
@Composable
fun bottomPadding(extra: Dp = 24.dp): Dp = LocalBottomBarPadding.current + extra

/** A soft shadow for raised surfaces, the same everywhere. */
fun Modifier.raised(shape: Shape, elevation: Dp = 10.dp, color: Color): Modifier =
    shadow(elevation, shape, clip = false, ambientColor = color, spotColor = color)

/**
 * A card: white (or the dark card tier) on a hairline. [raised] lifts it onto a shadow instead, for the one or two
 * cards on a screen that need you first.
 */
@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(CardPadding),
    raised: Boolean = false,
    spacing: Dp = Space.lg,
    content: @Composable ColumnScope.() -> Unit,
) {
    val s = surfaces
    Column(
        modifier
            .fillMaxWidth()
            .then(if (raised) Modifier.raised(CardShape, 8.dp, s.shadow) else Modifier)
            .clip(CardShape)
            .background(if (raised) s.raised else s.card)
            .then(if (raised) Modifier else Modifier.border(1.dp, s.hairline, CardShape))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** A sunken well: groups secondary content (the sum under a figure, an original SMS) without a border. */
@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = CardPadding, vertical = Space.lg),
    spacing: Dp = Space.md,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(surfaces.sunken)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content,
    )
}

/** Hairline separator, inset so it lines up with the text rather than the avatar. */
@Composable
fun Hairline(startInset: Dp = 0.dp, endInset: Dp = 0.dp) {
    Box(
        Modifier
            .fillMaxWidth()
            .padding(start = startInset, end = endInset)
            .height(1.dp)
            .background(surfaces.hairline)
    )
}

/** A small uppercase label sitting above a figure or opening a group in a form. Read by TalkBack as a heading. */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier.semantics { heading() }, style = LabelCaps, color = color)
}

/**
 * A chip. Selected chips fill with the accent tint; unselected ones keep a visible outline so they read as controls.
 * 36dp to look at, 48dp to touch. Gives a light tick on press.
 */
@Composable
fun PillChip(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    trailingIcon: ImageVector? = null,
    trailingLabel: String? = null,
    onClick: () -> Unit,
) {
    val s = surfaces
    val haptics = rememberHaptics()
    val bg by animateColorAsState(if (selected) s.accentSoft else Color.Transparent, motion(Motion.effects()), label = "chipBg")
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Row(
        modifier
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(bg)
            .border(1.dp, if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else s.outline.copy(alpha = 0.6f), CircleShape)
            .clickable(role = Role.Checkbox) { haptics.tick(); onClick() }
            .semantics { this.selected = selected }
            .padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium, maxLines = 1, softWrap = false, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, trailingLabel, Modifier.size(16.dp), tint = fg)
        }
    }
}

/**
 * A horizontally scrolling row of chips that runs to the screen edges, its first chip lined up with the content above.
 * Only for short rows of a secondary choice; anything a person must see in full uses [ChipFlow].
 */
@Composable
fun ChipRow(modifier: Modifier = Modifier, inset: Dp = Gutter, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = inset),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** Chips that wrap onto as many lines as they need, so none is ever cut at a card or sheet edge. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ChipFlow(modifier: Modifier = Modifier, content: @Composable FlowRowScope.() -> Unit) {
    FlowRow(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp), content = content)
}

/** The primary action: solid accent, 16dp corners. [fill] stretches it across its container (one main action per screen). */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Boolean = true,
    icon: ImageVector? = null,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).heightIn(min = ButtonHeight),
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = surfaces.sunken,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.sm),
    ) { ButtonContent(text, icon, MaterialTheme.typography.titleSmall) }
}

/** A supporting action next to or instead of a primary one: outlined, accent text. */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    fill: Boolean = false,
    icon: ImageVector? = null,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.then(if (fill) Modifier.fillMaxWidth() else Modifier).heightIn(min = ButtonHeight),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, if (enabled) surfaces.outline else surfaces.hairline),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = Space.lg + Space.xs, vertical = Space.sm),
    ) { ButtonContent(text, icon, MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold)) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, style: TextStyle) {
    if (icon != null) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
    }
    Text(text, style = style, textAlign = TextAlign.Center, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
}

/**
 * A text-only action inside a card or row ("Edit", "See all"). [alignStart] drops the leading padding so the label
 * lines up with the card's text edge when it is the first thing on its line. Always a 48dp touch target.
 */
@Composable
fun TextAction(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tone: Tone = Tone.Accent,
    alignStart: Boolean = false,
) {
    TextButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        contentPadding = PaddingValues(start = if (alignStart) 0.dp else Space.md, end = Space.md),
        colors = ButtonDefaults.textButtonColors(
            contentColor = if (tone == Tone.Danger) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
        ),
    ) { Text(text, style = MaterialTheme.typography.labelLarge.copy(fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) }
}

/**
 * A rounded-square avatar holding one letter: the tint as a 16% wash behind the same hue pushed toward the theme's ink,
 * so the initial stays legible in both themes (the colour is decoration; the letter carries the meaning).
 */
@Composable
fun LetterAvatar(letter: String, tint: Color, size: Dp = 40.dp) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.32f)).background(tint.copy(alpha = 0.16f)), contentAlignment = Alignment.Center) {
        Text(letter.trim().take(1).uppercase(), color = avatarInk(tint), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** A category tint pushed toward the theme's ink until it reads on its own 16% wash (≥ 4.5:1, see tokens.md). */
@Composable
fun avatarInk(tint: Color): Color {
    val dark = MaterialTheme.colorScheme.background.luminanceApprox() < 0.5f
    return if (dark) lerpColor(tint, Color.White, 0.45f) else lerpColor(tint, Color.Black, 0.42f)
}

private fun Color.luminanceApprox(): Float = 0.2126f * red + 0.7152f * green + 0.0722f * blue
private fun lerpColor(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, 1f)

/** An outline icon in a small tinted square: the leading element of settings rows, tool tiles and card headers. */
@Composable
fun TintedSquare(icon: ImageVector, tint: Color = MaterialTheme.colorScheme.primary, size: Dp = 36.dp, background: Color = tint.copy(alpha = 0.13f)) {
    Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(background), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.52f), tint = tint)
    }
}

/** An outline icon centred in a soft circle. */
@Composable
fun IconCircle(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp,
    background: Color = surfaces.sunken,
) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

/** Card title with a small leading icon in a tinted square, so long pages can be scanned. */
@Composable
fun CardTitle(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier.semantics { heading() }, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            TintedSquare(icon, size = 32.dp)
            Spacer(Modifier.width(Space.md))
        }
        Text(title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
    }
}

/** A tappable row: icon in a tinted square, label (and an optional quiet second line), chevron. 56dp tall at least. */
@Composable
fun ActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
    trailing: String? = null,
) {
    val danger = tint == MaterialTheme.colorScheme.error
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedSquare(icon, tint = tint, size = 36.dp)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = if (danger) tint else MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
        }
        if (trailing != null) {
            Spacer(Modifier.width(Space.sm))
            Text(trailing, Modifier.widthIn(max = 140.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/**
 * Lets text grow with the system font size only up to [max] times its design size. For labels in a fixed-size frame
 * (the nav bar, chart axes); body text always scales fully.
 */
@Composable
fun TextStyle.cappedScale(max: Float = 1.3f): TextStyle {
    val scale = LocalDensity.current.fontScale
    if (scale <= max) return this
    val k = max / scale
    return copy(fontSize = fontSize * k, lineHeight = if (lineHeight.isSp) lineHeight * k else lineHeight)
}

/** Section heading with an optional trailing action, used between cards. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f).semantics { heading() })
        action?.invoke(this)
    }
}
