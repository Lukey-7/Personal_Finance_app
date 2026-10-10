package com.pft.financetracker.ui.screens.quickadd

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.NumberPad
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.avatarInk
import com.pft.financetracker.ui.components.categoryColor
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.model.AmountKeys
import com.pft.financetracker.ui.model.QuickAddDraft
import com.pft.financetracker.ui.model.RecentCategories
import com.pft.financetracker.ui.model.TypedAmount
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.surfaces
import kotlinx.coroutines.launch

/** Short names for the grid, where a tile is a quarter of the sheet wide. */
private fun short(c: Category): String = when (c) {
    Category.FOOD -> "Food"
    Category.ENTERTAINMENT -> "Fun"
    Category.ATM -> "Cash / ATM"
    Category.BILLS -> "Bills"
    else -> c.label
}

/**
 * Add an expense in one sheet: amount on a big pad, a category (the ones you added last first), an optional note,
 * Save. "More details" opens the full editor with everything filled in. Used from Home, Activity, the widget and the
 * app shortcut.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickAddSheet(
    recent: List<Transaction>,
    cashCounted: Boolean,
    startCash: Boolean = false,
    onSave: (Transaction) -> Unit,
    onMoreDetails: (QuickAddDraft) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val categories = remember(recent) { RecentCategories.order(recent).take(8) }
    var draft by remember { mutableStateOf(QuickAddDraft(category = categories.firstOrNull(), cash = startCash)) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    // Set on the first Save or "More details": a second tap while the sheet slides away does nothing, so a payment is
    // never saved twice and the editor never opens twice.
    var closing by remember { mutableStateOf(false) }
    fun close(after: () -> Unit) {
        if (closing) return
        closing = true
        scope.launch { state.hide() }.invokeOnCompletion { after() }
    }

    FinSheet(onDismiss = onDismiss, state = state) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(if (draft.cash) "Paid in cash" else "Add an expense", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextAction("More details", { close { onMoreDetails(draft) } })
        }
        // The figure being typed, centred and large, with a caret so it reads as an input. Shown as typed (a trailing
        // point or zero stays), grouped the Indian way, and a size smaller rather than cut when it gets long.
        val shown = TypedAmount.show(draft.amount)
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        BoxWithConstraints(
            Modifier.fillMaxWidth().semantics(mergeDescendants = true) {
                contentDescription = "Amount: " + (draft.paise?.let { money(it) } ?: "none yet")
                liveRegion = LiveRegionMode.Polite
            },
            contentAlignment = Alignment.Center,
        ) {
            val room = with(density) { (maxWidth - 8.dp).toPx() }
            val ladder = listOf(MoneyType.large, MoneyType.medium, MoneyType.title)
            val style = ladder.firstOrNull { measurer.measure(shown, it, softWrap = false, maxLines = 1).size.width <= room }
                ?: ladder.last().let { s ->
                    val w = measurer.measure(shown, s, softWrap = false, maxLines = 1).size.width
                    if (w <= 0 || room <= 0f) s else s.copy(fontSize = s.fontSize * (room / w * 0.98f).coerceIn(0.5f, 1f))
                }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    shown,
                    style = style,
                    color = if (draft.amount.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1, softWrap = false,
                )
                Box(Modifier.padding(start = 3.dp).width(2.dp).heightIn(min = 40.dp).background(MaterialTheme.colorScheme.primary))
            }
        }

        CategoryGrid(categories, draft.category) { haptics.tick(); draft = draft.copy(category = it) }

        // Note: a sunken one-line field.
        Row(
            Modifier.fillMaxWidth().heightIn(min = 52.dp).clip(ControlShape).background(surfaces.sunken).padding(horizontal = Space.lg),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.EditNote, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.md))
            Box(Modifier.weight(1f)) {
                if (draft.note.isEmpty()) Text("What for? (optional)", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                BasicTextField(
                    draft.note, { draft = draft.copy(note = it) },
                    textStyle = MaterialTheme.typography.bodyLarge.copy(color = MaterialTheme.colorScheme.onSurface),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "What for, optional" },
                )
            }
        }

        Row(
            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Switch) { haptics.tick(); draft = draft.copy(cash = !draft.cash) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Payments, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Spacer(Modifier.width(Space.md))
            Text("Paid in cash", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
            Switch(draft.cash, null)
        }
        // ATM withdrawals already count as spend when that setting is on, so cash purchases would count twice.
        if (draft.cash && cashCounted) Text(
            "ATM withdrawals already count as your spend, so this is kept out of totals. It shows where the cash went.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        NumberPad(
            keyHeight = 50.dp,
            onKey = { k -> AmountKeys.press(draft.amount, k)?.let { draft = draft.copy(amount = it); true } ?: false },
            onBackspace = { draft = draft.copy(amount = AmountKeys.backspace(draft.amount)) },
            onClear = { draft = draft.copy(amount = "") },
        )

        val label = when {
            draft.paise == null -> "Type an amount"
            draft.category == null -> "Pick a category"
            else -> "Save ${money(draft.paise!!)} to ${short(draft.category!!)}"
        }
        PrimaryButton(label, {
            val t = draft.toTransaction(System.currentTimeMillis(), cashCounted)
            if (t == null) haptics.reject() else { haptics.confirm(); close { onSave(t) } }
        }, enabled = draft.canSave && !closing)
    }
}

/** Four tiles a row: icon in its category tint, a short name; the chosen one takes the accent edge and tint. */
@Composable
private fun CategoryGrid(categories: List<Category>, selected: Category?, onPick: (Category) -> Unit) {
    val s = surfaces
    // Four tiles a row; two at a large font, so names wrap between words rather than inside them.
    val perRow = if (LocalDensity.current.fontScale > 1.3f) 2 else 4
    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        categories.chunked(perRow).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { c ->
                    val on = c == selected
                    val tint = categoryColor(c)
                    Column(
                        Modifier.weight(1f).heightIn(min = 64.dp).clip(ControlShape)
                            .background(if (on) s.accentSoft else s.card)
                            .border(if (on) 1.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else s.hairline, ControlShape)
                            .clickable(role = Role.RadioButton) { onPick(c) }
                            .semantics { this.selected = on; contentDescription = c.label }
                            .padding(vertical = 8.dp, horizontal = 4.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(categoryIcon(c), null, tint = avatarInk(tint))
                        Text(
                            short(c), style = MaterialTheme.typography.labelMedium,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                repeat(perRow - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}
