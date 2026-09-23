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
    val shape = RoundedCornerShape(22.dp)
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
            .clip(RoundedCornerShape(22.dp))
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
fun PillChip(selected: Boolean, label: String, modifier: Modifier = Modifier, icon: ImageVector? = null, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        modifier
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

/** Full-width solid accent pill: the one primary action on a screen. */
@Composable
fun PrimaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.fillMaxWidth().height(56.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

/** Quiet companion to [PrimaryPill]: tonal panel fill, accent text. */
@Composable
fun SecondaryPill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(52.dp),
        shape = CircleShape,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
            contentColor = MaterialTheme.colorScheme.primary,
            disabledContainerColor = MaterialTheme.colorScheme.surfaceVariant,
            disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
        ),
        elevation = ButtonDefaults.buttonElevation(0.dp, 0.dp, 0.dp, 0.dp, 0.dp),
        contentPadding = PaddingValues(horizontal = 20.dp),
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

/** A tappable settings-style row: icon, label, chevron. Lines up with the card's text edge. */
@Composable
fun ActionRow(label: String, icon: ImageVector, onClick: () -> Unit, tint: Color = MaterialTheme.colorScheme.primary) {
    Row(
        Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = tint)
        Spacer(Modifier.width(12.dp))
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = tint)
        Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
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
