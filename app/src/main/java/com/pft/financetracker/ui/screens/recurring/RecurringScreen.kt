package com.pft.financetracker.ui.screens.recurring

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.recurring.Period
import com.pft.financetracker.domain.recurring.RecurringBook
import com.pft.financetracker.domain.recurring.RecurringStatus
import com.pft.financetracker.domain.recurring.RecurringView
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AdaptiveRow
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.AmountSize
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LedgerRow
import com.pft.financetracker.ui.components.LetterAvatar
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.RowIconSize
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.approxMoney
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.displayMerchant
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.shortDate
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Subscriptions and other repeating charges found in the transactions: what they cost a month (the hero) and a year,
 * then one ledger row each. A row opens a sheet to keep it, say it is not one, or mark it cancelled.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(vm: AppViewModel, onOpenTransaction: (Long) -> Unit, onBack: () -> Unit) {
    val book by vm.recurringBook.collectAsState()
    val loaded by vm.loaded.collectAsState()
    var selected by remember { mutableStateOf<RecurringView?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Subscriptions") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding()),
        ) {
            // Subscriptions come from the transactions: until those load, show the page's shape, not "none found".
            if (!loaded) {
                item { SkeletonHero(Modifier.padding(top = Space.md), cards = 0) }
                item { SkeletonRows(5, Modifier.padding(top = Space.lg)) }
                return@LazyColumn
            }
            if (book.shown.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.EventRepeat,
                        "No subscriptions found yet",
                        "Netflix, a gym, a phone plan: a service shows up here once it has charged you twice at a steady amount and rhythm.",
                    )
                }
                return@LazyColumn
            }

            item(key = "hero") { Hero(book) }
            itemsIndexed(book.shown, key = { _, v -> v.item.key }) { i, v ->
                Column(Modifier.animateItem()) {
                    if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                    RecurringRow(v) { selected = v }
                }
            }
        }
    }

    selected?.let { v ->
        RecurringSheet(
            v,
            onDecide = { status -> vm.decideRecurring(v.item.key, status) },
            onOpenLast = { onOpenTransaction(v.item.transactionIds.last()) },
            onDismiss = { selected = null },
        )
    }
}

/** What the counted subscriptions cost a month, then a year. Monthly figures of weekly or yearly plans are estimates. */
@Composable
private fun Hero(book: RecurringBook) {
    val counted = book.shown.filter { it.counted }
    val estimate = counted.any { it.item.period != Period.MONTHLY && it.item.period != Period.UNKNOWN }
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.md, bottom = Space.lg)) {
        CapsLabel("Subscriptions · a month")
        Spacer(Modifier.height(Space.sm))
        AmountDisplay(book.monthlyPaise, estimate = estimate, spokenLabel = "Subscriptions, a month")
        Spacer(Modifier.height(Space.sm))
        Text(
            if (counted.isEmpty()) "Nothing active right now"
            else "${money(book.yearlyPaise)} a year · ${countLabel(counted.size, "service")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(Space.xs))
        Text(
            "Found on this phone from charges that repeat at a steady amount and rhythm.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** "Monthly · next 12 Oct", or what has happened to it. */
private fun rowNote(v: RecurringView): String {
    val i = v.item
    return when {
        v.chargedAfterCancel -> "Charged after you cancelled"
        v.status == RecurringStatus.CANCELLED -> "Cancelled"
        !i.active -> "Stopped? Last ${shortDate(i.lastChargeAt)}"
        i.period == Period.UNKNOWN -> "AutoPay set up · ${shortDate(i.lastChargeAt)}"
        else -> i.period.label + (i.nextExpectedAt?.let { " · next ${shortDate(it)}" } ?: "")
    }
}

/** One subscription as a ledger row: avatar, name, rhythm and next renewal, price; a price rise is said in words. */
@Composable
private fun RecurringRow(v: RecurringView, onClick: () -> Unit) {
    val i = v.item
    val name = displayMerchant(i.merchant)
    val note = rowNote(v)
    val flag = when {
        i.priceRise != null -> "Price up"
        i.autopay -> "AutoPay"
        else -> null
    }
    LedgerRow(
        title = name,
        subtitle = note,
        amount = money(i.amountPaise),
        amountColor = if (v.counted) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
        leading = { LetterAvatar(name, colorFor(Category.entries.indexOf(i.category)), size = RowIconSize) },
        tag = flag,
        spoken = listOfNotNull(name, money(i.amountPaise), note, flag).joinToString(", "),
        onClick = onClick,
    )
}

/** What the person told FinTrack about this one, if anything. */
private fun decisionLine(v: RecurringView): String? = when (v.status) {
    RecurringStatus.CONFIRMED -> "You chose to keep this."
    RecurringStatus.CANCELLED -> "You marked this cancelled."
    RecurringStatus.DISMISSED -> "You said this isn't a subscription."
    null -> null
}

/** One subscription in full: price, rhythm, charges seen, a price rise, and the Keep / Not one / I cancelled it choice. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RecurringSheet(
    v: RecurringView,
    onDecide: (RecurringStatus?) -> Unit,
    onOpenLast: () -> Unit,
    onDismiss: () -> Unit,
) {
    val i = v.item
    val haptics = rememberHaptics()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }
    fun decide(status: RecurringStatus?) {
        haptics.confirm()
        onDecide(status)
        close(onDismiss)
    }

    FinSheet(onDismiss = onDismiss, title = displayMerchant(i.merchant), state = state) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            AmountDisplay(i.amountPaise, size = AmountSize.Medium, color = MaterialTheme.colorScheme.onSurface, spokenLabel = i.period.label)
            Text(
                "${i.period.label} · ${countLabel(i.transactionIds.size, "charge")} seen",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SoftPanel(spacing = Space.xs) {
            val costs = if (i.period == Period.MONTHLY || i.period == Period.UNKNOWN) "${money(i.yearlyPaise)} a year"
                else "About ${approxMoney(i.monthlyPaise)} a month · ${money(i.yearlyPaise)} a year"
            Text(costs, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            Text(
                "Last charged ${shortDate(i.lastChargeAt)}" + (i.nextExpectedAt?.let { ", next around ${shortDate(it)}" } ?: ""),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        i.priceRise?.let {
            Text("Price went up from ${money(it.fromPaise)} to ${money(it.toPaise)}.", style = MaterialTheme.typography.bodyMedium, color = Expense)
        }
        if (v.chargedAfterCancel) {
            Text("You marked this cancelled, but it charged again. Check with the service.", style = MaterialTheme.typography.bodyMedium, color = Expense)
        }
        decisionLine(v)?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        TextAction("Open the last charge", { close { onDismiss(); onOpenLast() } }, alignStart = true)

        if (v.status == null) {
            PrimaryButton("Keep", { decide(RecurringStatus.CONFIRMED) })
            AdaptiveRow(count = 2, minItemWidth = 140.dp) { m ->
                SecondaryButton("I cancelled it", { decide(RecurringStatus.CANCELLED) }, m)
                SecondaryButton("Not one", { decide(RecurringStatus.DISMISSED) }, m)
            }
        } else {
            SecondaryButton("Undo my choice", { decide(null) }, fill = true)
        }
    }
}
