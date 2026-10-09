package com.pft.financetracker.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Label
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.motion
import com.pft.financetracker.ui.theme.reducedMotion
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.surfaces

/* ---------------------------------------------------------------- Skeletons ---- */

/**
 * The loading look: sunken shapes where content will be, with a slow light sweep (still when animations are off).
 * Replaces spinners, so a screen never flashes ₹0 or "nothing yet" before the database answers. Read as "Loading".
 */
@Composable
fun Modifier.shimmer(): Modifier {
    val base = surfaces.sunken
    if (reducedMotion) return this.background(base)
    val t = rememberInfiniteTransition(label = "shimmer")
    val x by t.animateFloat(-1f, 2f, infiniteRepeatable(tween(1400, easing = LinearEasing), RepeatMode.Restart), label = "x")
    val hi = surfaces.card.copy(alpha = 0.55f)
    return this.drawWithContent {
        drawRect(base)
        val w = size.width
        drawRect(Brush.linearGradient(listOf(Color.Transparent, hi, Color.Transparent), Offset(w * x - w * 0.4f, 0f), Offset(w * x + w * 0.4f, 0f)))
    }
}

@Composable
fun SkeletonBlock(width: Dp?, height: Dp, modifier: Modifier = Modifier, radius: Dp = 8.dp) {
    Box(modifier.then(if (width != null) Modifier.width(width) else Modifier.fillMaxWidth()).height(height).clip(RoundedCornerShape(radius)).shimmer())
}

/** [count] list rows in the shape of a ledger: icon, two lines, an amount. */
@Composable
fun SkeletonRows(count: Int = 6, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().semantics { contentDescription = "Loading" }) {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                SkeletonBlock(RowIconSize, RowIconSize, radius = 12.dp)
                Spacer(Modifier.width(RowIconGap))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    SkeletonBlock(140.dp, 14.dp)
                    SkeletonBlock(96.dp, 11.dp)
                }
                SkeletonBlock(64.dp, 16.dp)
            }
        }
    }
}

/** A hero figure and its line, then a block of cards: the loading shape of Home and every tool screen. */
@Composable
fun SkeletonHero(modifier: Modifier = Modifier, cards: Int = 2) {
    Column(modifier.fillMaxWidth().padding(horizontal = Gutter).semantics { contentDescription = "Loading" }, verticalArrangement = Arrangement.spacedBy(Space.md)) {
        SkeletonBlock(110.dp, 11.dp)
        SkeletonBlock(200.dp, 52.dp, radius = 12.dp)
        SkeletonBlock(240.dp, 13.dp)
        Spacer(Modifier.height(Space.sm))
        repeat(cards) { SkeletonBlock(null, 72.dp, radius = CardRadius) }
    }
}

/** A single card's loading shape. */
@Composable
fun SkeletonCard(modifier: Modifier = Modifier, height: Dp = 120.dp) {
    Box(modifier.fillMaxWidth().semantics { contentDescription = "Loading" }) { SkeletonBlock(null, height, radius = CardRadius) }
}

/* ---------------------------------------------------------------- Empty / error ---- */

/**
 * An empty state that says what this place is for, why it is empty, and how to start, with the one action that starts
 * it. Icon in a tinted square on a soft disc.
 */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
) {
    Column(
        modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = Space.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Box(Modifier.size(88.dp).clip(RoundedCornerShape(28.dp)).background(surfaces.accentSoft), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(36.dp), tint = MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(Space.xs))
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center, modifier = Modifier.semantics { heading() })
        if (body != null) Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (action != null) {
            Spacer(Modifier.height(Space.xs))
            action()
        }
    }
}

/** Something went wrong: say what, and offer the way back ("Try again"). */
@Composable
fun ErrorState(title: String, body: String, modifier: Modifier = Modifier, icon: ImageVector = Icons.Outlined.CloudOff, onRetry: (() -> Unit)? = null) {
    EmptyState(icon, title, body, modifier) { if (onRetry != null) SecondaryButton("Try again", onRetry) }
}

/* ---------------------------------------------------------------- Expandable card ---- */

/**
 * A card that holds a whole section behind its title and a one-line [summary]: tap to open, the chevron turns, the body
 * springs open. Urgent cards are [raised] and may start [initiallyExpanded]. Remembers its state across scrolling.
 */
@Composable
fun ExpandableCard(
    title: String,
    summary: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.primary,
    raised: Boolean = false,
    initiallyExpanded: Boolean = false,
    stateKey: String = title,
    summaryColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    content: @Composable ColumnScope.() -> Unit,
) {
    var open by rememberSaveable(stateKey) { mutableStateOf(initiallyExpanded) }
    val haptics = rememberHaptics()
    val turn by animateFloatAsState(if (open) 180f else 0f, motion(Motion.spatialFast()), label = "chevron")
    FinCard(modifier, padding = PaddingValues(0.dp), raised = raised, spacing = 0.dp) {
        Row(
            Modifier.fillMaxWidth().heightIn(min = 64.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Collapse" else "Expand") { haptics.tick(); open = !open }
                .semantics(mergeDescendants = true) { stateDescription = if (open) "Expanded" else "Collapsed" }
                .padding(horizontal = Space.lg, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TintedSquare(icon, tint, 36.dp)
            Spacer(Modifier.width(Space.md + Space.xs))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() }, maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                Text(summary, style = MaterialTheme.typography.bodySmall, color = summaryColor, maxLines = 2, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.sm))
            Icon(Icons.Outlined.ExpandMore, null, Modifier.size(24.dp).rotate(turn), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        AnimatedVisibility(
            open,
            enter = expandVertically(motion(Motion.spatial())) + fadeIn(motion(Motion.effects())),
            exit = shrinkVertically(motion(Motion.spatial())) + fadeOut(motion(Motion.effects())),
        ) {
            Column(Modifier.fillMaxWidth().padding(start = CardPadding, end = CardPadding, bottom = CardPadding), verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Hairline()
                content()
            }
        }
    }
}

/* ---------------------------------------------------------------- Swipe actions ---- */

/**
 * A row you can swipe: towards the end (→) to [onStartAction] (categorise, the row springs back), towards the start
 * (←) to [onEndAction] (delete, the row leaves and an Undo snackbar follows). Both are also TalkBack actions, so
 * nothing depends on the gesture.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeActions(
    startLabel: String,
    startIcon: ImageVector,
    onStartAction: () -> Unit,
    endLabel: String,
    endIcon: ImageVector = Icons.Outlined.Delete,
    onEndAction: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val haptics = rememberHaptics()
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { v ->
            when (v) {
                SwipeToDismissBoxValue.StartToEnd -> { haptics.tick(); onStartAction(); false }
                SwipeToDismissBoxValue.EndToStart -> { haptics.confirm(); onEndAction(); true }
                SwipeToDismissBoxValue.Settled -> true
            }
        },
        positionalThreshold = { it * 0.35f },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier.semantics {
            customActions = listOf(
                CustomAccessibilityAction(startLabel) { onStartAction(); true },
                CustomAccessibilityAction(endLabel) { onEndAction(); true },
            )
        },
        backgroundContent = {
            val dir = state.dismissDirection
            val toEnd = dir == SwipeToDismissBoxValue.StartToEnd
            val bg = when (dir) {
                SwipeToDismissBoxValue.StartToEnd -> MaterialTheme.colorScheme.primary
                SwipeToDismissBoxValue.EndToStart -> Expense
                else -> Color.Transparent
            }
            val fg = if (toEnd) MaterialTheme.colorScheme.onPrimary else Color.White
            Row(
                Modifier.fillMaxSize().background(bg).padding(horizontal = Gutter),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = if (toEnd) Arrangement.Start else Arrangement.End,
            ) {
                if (dir != SwipeToDismissBoxValue.Settled) {
                    Icon(if (toEnd) startIcon else endIcon, null, tint = fg)
                    Spacer(Modifier.width(Space.sm))
                    Text(if (toEnd) startLabel else endLabel, color = fg, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                }
            }
        },
    ) { Box(Modifier.background(surfaces.page)) { content() } }
}

/** Default icon for the categorise swipe. */
val CategoriseIcon: ImageVector = Icons.Outlined.Label

/* ---------------------------------------------------------------- Snackbar ---- */

/** The app's snackbar: ink on page (inverted), rounded, the action in the soft accent, an undo glyph when it is Undo. */
@Composable
fun FinSnackbarHost(state: SnackbarHostState, modifier: Modifier = Modifier) {
    SnackbarHost(state, modifier) { data -> FinSnackbar(data) }
}

@Composable
private fun FinSnackbar(data: SnackbarData) {
    val label = data.visuals.actionLabel
    Snackbar(
        modifier = Modifier.padding(horizontal = Space.lg, vertical = Space.sm),
        shape = RoundedCornerShape(16.dp),
        containerColor = MaterialTheme.colorScheme.inverseSurface,
        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
        action = if (label != null) ({
            Row(
                Modifier.heightIn(min = 48.dp).clip(RoundedCornerShape(10.dp)).clickable(role = Role.Button) { data.performAction() }.padding(horizontal = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (label == "Undo") {
                    Icon(Icons.AutoMirrored.Outlined.Undo, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.inversePrimary)
                    Spacer(Modifier.width(6.dp))
                }
                Text(label, color = MaterialTheme.colorScheme.inversePrimary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
            }
        }) else null,
    ) { Text(data.visuals.message, style = MaterialTheme.typography.bodyMedium) }
}
