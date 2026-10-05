package com.pft.financetracker.ui.screens.recurring

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.recurring.Period
import com.pft.financetracker.domain.recurring.RecurringStatus
import com.pft.financetracker.domain.recurring.RecurringView
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LetterAvatar
import com.pft.financetracker.ui.components.RowIconGap
import com.pft.financetracker.ui.components.RowIconSize
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.shortDate
import com.pft.financetracker.ui.theme.Expense

/** Subscriptions and other repeating charges found in the transactions, with what they cost a month and a year. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecurringScreen(vm: AppViewModel, onOpenTransaction: (Long) -> Unit, onBack: () -> Unit) {
    val book by vm.recurringBook.collectAsState()
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
            contentPadding = PaddingValues(start = Gutter, top = 8.dp, end = Gutter, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                FinCard {
                    CapsLabel("Repeating charges")
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(money(book.monthlyPaise), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        Text(" a month", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("${money(book.yearlyPaise)} a year. Found on this phone from charges that repeat at a steady amount and rhythm.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (book.shown.isEmpty()) item { EmptyState(Icons.Outlined.EventRepeat, "No repeating charges yet. They show up after a service has charged you twice.") }
            else item {
                FinCard(padding = PaddingValues(vertical = 8.dp)) {
                    Column {
                        book.shown.forEachIndexed { i, v ->
                            if (i > 0) Hairline(startInset = 20.dp + RowIconSize + RowIconGap, endInset = 20.dp)
                            RecurringRow(v) { selected = v }
                        }
                    }
                }
            }
        }
    }

    selected?.let { v ->
        val i = v.item
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(i.merchant) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${money(i.amountPaise)} · ${i.period.label} · ${i.transactionIds.size} charge${if (i.transactionIds.size == 1) "" else "s"} seen")
                    Text("Last charged ${shortDate(i.lastChargeAt)}" + (i.nextExpectedAt?.let { ", next around ${shortDate(it)}" } ?: ""), style = MaterialTheme.typography.bodySmall)
                    i.priceRise?.let { Text("Price went up from ${money(it.fromPaise)} to ${money(it.toPaise)}.", style = MaterialTheme.typography.bodySmall, color = Expense) }
                    if (v.chargedAfterCancel) Text("You marked this cancelled, but it charged again. Check with the service.", style = MaterialTheme.typography.bodySmall, color = Expense)
                    TextButton(onClick = { selected = null; onOpenTransaction(i.transactionIds.last()) }) { Text("Open the last charge") }
                }
            },
            confirmButton = {
                Row {
                    if (v.status == null) {
                        TextButton(onClick = { vm.decideRecurring(i.key, RecurringStatus.DISMISSED); selected = null }) { Text("Not one") }
                        TextButton(onClick = { vm.decideRecurring(i.key, RecurringStatus.CANCELLED); selected = null }) { Text("I cancelled it") }
                        TextButton(onClick = { vm.decideRecurring(i.key, RecurringStatus.CONFIRMED); selected = null }) { Text("Keep") }
                    } else {
                        TextButton(onClick = { vm.decideRecurring(i.key, null); selected = null }) { Text("Undo my choice") }
                    }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } },
        )
    }
}

@Composable
private fun RecurringRow(v: RecurringView, onClick: () -> Unit) {
    val i = v.item
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LetterAvatar(i.merchant, colorFor(Category.entries.indexOf(i.category)), size = RowIconSize)
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f)) {
            Text(i.merchant, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val note = when {
                v.chargedAfterCancel -> "Charged after you cancelled"
                v.status == RecurringStatus.CANCELLED -> "Cancelled"
                !i.active -> "Stopped? Last ${shortDate(i.lastChargeAt)}"
                i.period == Period.UNKNOWN -> "AutoPay set up · ${shortDate(i.lastChargeAt)}"
                else -> "${i.period.label} · next ${i.nextExpectedAt?.let { shortDate(it) } ?: "?"}"
            }
            Text(note, style = MaterialTheme.typography.bodySmall, color = if (v.chargedAfterCancel) Expense else MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Column(horizontalAlignment = Alignment.End) {
            Text(money(i.amountPaise), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
            if (i.priceRise != null) Text("Price up", style = MaterialTheme.typography.labelSmall, color = Expense)
            else if (i.autopay) Text("AutoPay", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
