package com.pft.financetracker.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.LabelCaps

/*
 * The Buro building blocks: depth comes from tonal layering and hairlines, never from shadows.
 * Everything here is presentation only - no screen changes behaviour by using it.
 */

/** Standard page gutter from the reference: 24dp safe zone on both edges. */
val Gutter = 24.dp

/** Inner padding for cards; the reference pads its cards by 24dp on every side. */
val CardPadding = 24.dp

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
val CardRadius = 22.dp
val CardShape = RoundedCornerShape(CardRadius)

/** Height of every pill button; tall enough for a 48dp touch target with room for a larger font. */
val ButtonHeight = 52.dp

/** What a text action does: the usual accent, or red for something that removes data. */
enum class Tone { Accent, Danger }

/**
 * Height of the floating nav pill (plus the system navigation bar) on the five tab screens, and zero on
 * every other screen. Tab content scrolls underneath the pill, so lists pad their end by this much and
 * the + button lifts itself by it; nothing is clipped above the bar.
 */
val LocalBottomBarPadding = compositionLocalOf { 0.dp }

/** Bottom content padding for a scrolling screen: clears the nav pill (if any) plus some breathing room. */
@Composable
fun bottomPadding(extra: Dp = 24.dp): Dp = LocalBottomBarPadding.current + extra

/** White surface, 1px hairline, 24dp radius, no elevation. */
@Composable
fun FinCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(CardPadding),
    content: @Composable ColumnScope.() -> Unit,
) {
    val shape = CardShape
    val base = Modifier
        .fillMaxWidth()
        .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    Card(
        modifier = modifier.then(base),
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(Modifier.padding(padding), verticalArrangement = Arrangement.spacedBy(16.dp), content = content)
    }
}

/** Tonal panel used to group secondary content without adding a border. */
@Composable
fun SoftPanel(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(horizontal = CardPadding, vertical = 20.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .fillMaxWidth()
            .clip(CardShape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
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
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}

/** A small uppercase label sitting above a figure. */
@Composable
fun CapsLabel(text: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurfaceVariant) {
    Text(text.uppercase(), modifier = modifier, style = LabelCaps, color = color)
}

/** Selected chips fill with the soft accent tint; unselected ones are plain text. */
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
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
            // The pill stays 38dp to look like the reference; the touch area is the full 48dp.
            .minimumInteractiveComponentSize()
            .clip(CircleShape)
            .background(bg)
            // Unselected pills keep a hairline edge, so a row reads as pills whichever one is selected
            // and its first pill always starts on the content edge.
            .border(1.dp, if (selected) Color.Transparent else MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(18.dp), tint = fg)
            Spacer(Modifier.width(6.dp))
        }
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium)
        if (trailingIcon != null) {
            Spacer(Modifier.width(6.dp))
            Icon(trailingIcon, trailingLabel, Modifier.size(16.dp), tint = fg)
        }
    }
}

/**
 * A horizontally scrolling row of chips. The row runs to the screen edges, but its content is inset so
 * the first chip lines up with everything above it (as the reference aligns its filter pills), and
 * the last one scrolls fully into view instead of being cut at the edge.
 */
@Composable
fun ChipRow(modifier: Modifier = Modifier, inset: Dp = Gutter, content: @Composable RowScope.() -> Unit) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = inset),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
}

/** The primary action: solid accent pill. [fill] stretches it across its container (one main action per screen). */
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
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = Space.xl, vertical = Space.sm),
    ) { ButtonContent(text, icon, MaterialTheme.typography.titleSmall) }
}

/** A supporting action next to or instead of a primary one: hairline pill, accent text. */
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
        shape = CircleShape,
        border = BorderStroke(1.dp, if (enabled) MaterialTheme.colorScheme.outline else MaterialTheme.colorScheme.outlineVariant),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.primary),
        contentPadding = PaddingValues(horizontal = Space.lg + Space.xs, vertical = Space.sm),
    ) { ButtonContent(text, icon, MaterialTheme.typography.labelLarge) }
}

@Composable
private fun ButtonContent(text: String, icon: ImageVector?, style: androidx.compose.ui.text.TextStyle) {
    if (icon != null) {
        Icon(icon, null, Modifier.size(20.dp))
        Spacer(Modifier.width(Space.sm))
    }
    Text(text, style = style, textAlign = TextAlign.Center)
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
    ) { Text(text, style = MaterialTheme.typography.labelLarge) }
}

/** Circular tinted avatar holding a single letter, as used in the reference's asset rows. */
@Composable
fun LetterAvatar(letter: String, tint: Color, size: androidx.compose.ui.unit.Dp = 44.dp) {
    Box(Modifier.size(size).background(tint.copy(alpha = 0.14f), CircleShape), contentAlignment = Alignment.Center) {
        Text(letter.take(1).uppercase(), color = tint, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

/** An outline icon centred in a soft tinted circle: the leading element of a row, 40dp as in the reference. */
@Composable
fun IconCircle(
    icon: ImageVector,
    tint: Color = MaterialTheme.colorScheme.onSurface,
    size: Dp = 40.dp,
    background: Color = MaterialTheme.colorScheme.surfaceVariant,
) {
    Box(Modifier.size(size).background(background, CircleShape), contentAlignment = Alignment.Center) {
        Icon(icon, null, Modifier.size(size * 0.5f), tint = tint)
    }
}

/** Card title with a small leading outline icon, so long pages such as Settings can be scanned. */
@Composable
fun CardTitle(title: String, icon: ImageVector? = null, modifier: Modifier = Modifier) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.width(10.dp))
        }
        Text(title, style = MaterialTheme.typography.titleMedium)
    }
}

/** A tappable settings-style row: icon, label (and an optional quiet second line), chevron. 48dp tall at least. */
@Composable
fun ActionRow(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.primary,
    subtitle: String? = null,
) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 48.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = tint)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** While the database has not answered yet: a small spinner where the content will be, never an empty state. */
@Composable
fun LoadingState(modifier: Modifier = Modifier) {
    Box(
        modifier.fillMaxWidth().padding(vertical = Space.xxl).semantics { contentDescription = "Loading" },
        contentAlignment = Alignment.Center,
    ) { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) }
}

/**
 * Lets text grow with the system font size only up to [max] times its design size. For labels in a fixed-size frame
 * (the nav bar, chart axes); body text always scales fully.
 */
@Composable
fun androidx.compose.ui.text.TextStyle.cappedScale(max: Float = 1.3f): androidx.compose.ui.text.TextStyle {
    val scale = LocalDensity.current.fontScale
    if (scale <= max) return this
    val k = max / scale
    return copy(fontSize = fontSize * k, lineHeight = if (lineHeight.isSp) lineHeight * k else lineHeight)
}

/** Empty state: an outline icon in a soft circle, one line of copy, and optionally the action to take. */
@Composable
fun EmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        IconCircle(icon, tint = MaterialTheme.colorScheme.primary, size = 64.dp, background = MaterialTheme.colorScheme.primaryContainer)
        Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        action?.invoke()
    }
}

/** Section heading with an optional trailing action, used between cards. */
@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: @Composable (RowScope.() -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        action?.invoke(this)
    }
}
