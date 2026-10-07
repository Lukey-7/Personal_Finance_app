package com.pft.financetracker.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.motion
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.surfaces

/**
 * A one-of-several choice on a sunken track, the chosen segment raised on a thumb that springs across. Replaces pairs
 * and triples of chips (period, monthly/weekly, expense/income). Each segment is a 48dp target; a tick on change.
 * At a large font the labels wrap inside their segment instead of being cut. [trailing] adds an icon-only segment
 * (Home's custom-range calendar) that is selected when [trailingSelected].
 */
@Composable
fun SegmentedControl(
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
    trailing: ImageVector? = null,
    trailingLabel: String? = null,
    trailingSelected: Boolean = false,
    onTrailing: (() -> Unit)? = null,
) {
    val s = surfaces
    val haptics = rememberHaptics()
    val trailingWidth = if (trailing != null) 52.dp else 0.dp
    BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .clip(ControlShape)
            .background(s.sunken)
            .padding(3.dp)
    ) {
        val segWidth = (maxWidth - trailingWidth) / options.size
        val thumbX by animateDpAsState(
            if (trailingSelected) maxWidth - trailingWidth else segWidth * selected.coerceAtLeast(0),
            motion(Motion.spatialFast()), label = "thumb",
        )
        val thumbW by animateDpAsState(if (trailingSelected) trailingWidth else segWidth, motion(Motion.spatialFast()), label = "thumbW")
        if (selected >= 0 || trailingSelected) Box(Modifier.matchParentSize()) {
            Box(
                Modifier.offset(x = thumbX).width(thumbW).fillMaxHeight()
                    .raised(RoundedCornerShape(11.dp), 2.dp, s.shadow)
                    .clip(RoundedCornerShape(11.dp))
                    .background(s.raised)
            )
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            options.forEachIndexed { i, label ->
                val on = i == selected && !trailingSelected
                Box(
                    Modifier.width(segWidth).heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp))
                        .clickable(role = Role.Tab) { if (!on) { haptics.tick(); onSelect(i) } }
                        .semantics { this.selected = on }
                        .padding(horizontal = 6.dp, vertical = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        label,
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                        color = if (on) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (trailing != null && onTrailing != null) Box(
                Modifier.width(trailingWidth).heightIn(min = 44.dp).clip(RoundedCornerShape(11.dp))
                    .clickable(role = Role.Tab) { haptics.tick(); onTrailing() }
                    .semantics { this.selected = trailingSelected; contentDescription = trailingLabel ?: "" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(trailing, null, Modifier.size(20.dp), tint = if (trailingSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** "Filter" with a count badge when something is chosen; opens a [FilterSheet]. */
@Composable
fun FilterButton(count: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = surfaces
    val active = count > 0
    Row(
        modifier
            .heightIn(min = 48.dp)
            .clip(ControlShape)
            .background(if (active) s.accentSoft else s.card)
            .border(1.dp, if (active) MaterialTheme.colorScheme.primary.copy(alpha = 0.5f) else s.hairline, ControlShape)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = if (active) "Filters, $count chosen" else "Filters" }
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Outlined.Tune, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
        if (active) {
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(20.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary), contentAlignment = Alignment.Center) {
                Text(count.toString(), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimary, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** A chosen filter shown under the search, with a cross to drop it. */
@Composable
fun AppliedTag(label: String, onRemove: () -> Unit) {
    Row(
        Modifier.heightIn(min = 36.dp).clip(RoundedCornerShape(10.dp)).background(surfaces.accentSoft)
            .clickable(onClickLabel = "Remove", role = Role.Button, onClick = onRemove)
            .semantics(mergeDescendants = true) { contentDescription = "$label, remove filter" }
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 200.dp))
        Spacer(Modifier.width(4.dp))
        Icon(Icons.Outlined.Close, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.primary)
    }
}

/**
 * The app's bottom sheet: raised surface, 28dp corners, a drag handle, a title, and a body that scrolls on its own
 * when it is taller than the screen. Springs up; snaps when animations are off.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FinSheet(
    onDismiss: () -> Unit,
    title: String? = null,
    state: SheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
    scroll: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = state,
        containerColor = surfaces.raised,
        scrimColor = MaterialTheme.colorScheme.scrim,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        tonalElevation = 0.dp,
        modifier = Modifier.statusBarsPadding(),
    ) {
        Column(
            Modifier.fillMaxWidth()
                .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(start = Gutter, end = Gutter, bottom = Space.xl)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            if (title != null) Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.semantics { heading() })
            content()
        }
    }
}

/** "Learn more": the long explanation, out of the way until asked for. */
@Composable
fun InfoSheet(title: String, body: String, onDismiss: () -> Unit, extra: (@Composable ColumnScope.() -> Unit)? = null) {
    FinSheet(onDismiss, title) {
        Text(body, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        extra?.invoke(this)
        PrimaryButton("Got it", onDismiss)
    }
}

/** A small info button that opens an [InfoSheet]; 48dp target, labelled for TalkBack. */
@Composable
fun InfoButton(title: String, body: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    IconButton(onClick = { open = true }, modifier = modifier) { Icon(Icons.Outlined.Info, "About $title", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    if (open) InfoSheet(title, body, { open = false })
}

/** A one-line description with a "Learn more" link that opens the rest in a sheet. */
@Composable
fun LearnMore(summary: String, title: String, body: String, modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }
    Column(modifier) {
        Text(summary, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        TextAction("Learn more", { open = true }, alignStart = true)
    }
    if (open) InfoSheet(title, body, { open = false })
}

/**
 * A large number pad for amounts: digits, a decimal point and backspace (hold to clear). [onKey] returns false when
 * the key was refused (a third decimal, too many digits), and the pad buzzes. Keys are 56dp tall.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NumberPad(onKey: (Char) -> Boolean, onBackspace: () -> Unit, onClear: () -> Unit, modifier: Modifier = Modifier, keyHeight: Dp = 56.dp) {
    val haptics = rememberHaptics()
    val keys = listOf('1', '2', '3', '4', '5', '6', '7', '8', '9', '.', '0', '<')
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        keys.chunked(3).forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { k ->
                    val label = when (k) { '<' -> "Delete"; '.' -> "Decimal point"; else -> k.toString() }
                    Box(
                        Modifier.weight(1f).heightIn(min = keyHeight).clip(ControlShape).background(surfaces.sunken)
                            .then(
                                if (k == '<') Modifier.combinedClickableCompat(
                                    onClick = { haptics.tick(); onBackspace() },
                                    onLongClick = { haptics.longPress(); onClear() },
                                ) else Modifier.clickable(role = Role.Button) { if (onKey(k)) haptics.tick() else haptics.reject() }
                            )
                            .semantics { contentDescription = label; role = Role.Button },
                        contentAlignment = Alignment.Center,
                    ) {
                        if (k == '<') Icon(Icons.AutoMirrored.Outlined.Backspace, null, Modifier.size(24.dp), tint = MaterialTheme.colorScheme.onSurface)
                        else Text(k.toString(), style = MoneyType.title.copy(fontWeight = FontWeight.Medium), color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
private fun Modifier.combinedClickableCompat(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.then(Modifier.combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "Clear"))

/** Content padding for a sheet's list rows that run edge to edge inside the sheet's gutter. */
val SheetRowPadding = PaddingValues(vertical = Space.sm)
