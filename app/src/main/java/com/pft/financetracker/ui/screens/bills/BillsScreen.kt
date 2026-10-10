package com.pft.financetracker.ui.screens.bills

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.domain.bills.Loan
import com.pft.financetracker.domain.bills.LoanProgress
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AdaptiveRow
import com.pft.financetracker.ui.components.AddButton
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.AmountSize
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.DateField
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.LedgerAmountMinWidth
import com.pft.financetracker.ui.components.LetterAvatar
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.ProgressMeter
import com.pft.financetracker.ui.components.RowIconGap
import com.pft.financetracker.ui.components.RowIconSize
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.Tone
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.paiseToInput
import com.pft.financetracker.ui.components.rememberAtTop
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** The list's groups, most urgent first. "Due soon" is the coming week. */
private enum class Group(val title: String) {
    OVERDUE("Overdue"), SOON("Due soon"), LATER("Later"), PAID("Paid"), DONE("Finished"),
}

private fun groupOf(s: BillState): Group = when (s) {
    is BillState.Overdue -> Group.OVERDUE
    is BillState.Upcoming -> if (s.daysLeft <= 7) Group.SOON else Group.LATER
    is BillState.Paid -> Group.PAID
    BillState.Done -> Group.DONE
}

private fun newBill() = Bill(name = "", amountPaise = null, dueDay = LocalDate.now().dayOfMonth, keyword = null)

/**
 * Bills, EMIs and card bills: the next one to pay as the hero, then everything grouped by what needs paying first
 * (overdue, due soon, later, paid). A row opens a sheet to mark it paid, edit or delete it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(vm: AppViewModel, onOpenTransaction: (Long) -> Unit, onBack: () -> Unit) {
    val states by vm.billStates.collectAsState()
    val dbLoaded by vm.loaded.collectAsState()
    // These lists start as "not loaded", so a first open shows the skeleton rather than flashing the empty state.
    val loaded = dbLoaded && com.pft.financetracker.ui.isLoaded(states)
    var selected by remember { mutableStateOf<Pair<Bill, BillState>?>(null) }
    var editing by remember { mutableStateOf<Bill?>(null) }
    val listState = rememberLazyListState()
    val order = { s: BillState -> when (s) { is BillState.Overdue -> 0; is BillState.Upcoming -> 1; is BillState.Paid -> 2; BillState.Done -> 3 } }
    val sorted = states.sortedWith(compareBy({ order(it.second) }, { (it.second as? BillState.Upcoming)?.daysLeft ?: 0L }))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Bills & EMIs") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { if (states.isNotEmpty()) AddButton({ editing = newBill() }, listState, text = "Add bill") },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding(FabClearance)),
        ) {
            // Bill status comes from the transactions: until they load, show the page's shape, not "no bills".
            if (!loaded) {
                item { SkeletonHero(Modifier.padding(top = Space.md), cards = 0) }
                item { SkeletonRows(4, Modifier.padding(top = Space.lg)) }
                return@LazyColumn
            }
            if (sorted.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Outlined.ReceiptLong,
                        "No bills yet",
                        "Add rent, phone, insurance or a loan EMI, and FinTrack marks each one paid when the payment comes in.",
                    ) { PrimaryButton("Add a bill", { editing = newBill() }, fill = false) }
                }
                return@LazyColumn
            }

            item(key = "hero") { Hero(sorted) }
            item(key = "about") {
                LearnMore(
                    "Marked paid by itself when a matching payment shows up.",
                    "How bills are tracked",
                    "Paid automatically when a matching payment shows up near the due date. Card bills appear by themselves from statement SMS.",
                    Modifier.padding(start = Gutter, end = Gutter, top = Space.sm),
                )
            }
            Group.entries.forEach { g ->
                val rows = sorted.filter { groupOf(it.second) == g }
                if (rows.isNotEmpty()) {
                    item(key = "h-${g.name}") {
                        CapsLabel(
                            "${g.title} · ${rows.size}",
                            Modifier.padding(start = Gutter, end = Gutter, top = Space.xl, bottom = Space.xs),
                            color = if (g == Group.OVERDUE) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    itemsIndexed(rows, key = { _, row -> "b${row.first.id}" }) { i, (b, s) ->
                        Column(Modifier.animateItem()) {
                            if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                            BillRow(b, s) { selected = b to s }
                        }
                    }
                }
            }
        }
    }

    selected?.let { (b, s) ->
        BillSheet(
            b, s,
            onMarkPaid = { due -> vm.markBillPaid(b.id, due) },
            onUnmark = { due -> vm.unmarkBillPaid(b.id, due) },
            // Stored with the bill's paid marks (BillTracker.ignoreMark), so the bill stops counting that payment.
            onUnlink = { txId -> vm.markBillPaid(b.id, BillTracker.ignoreMark(txId)) },
            onDelete = { vm.deleteBill(b.id) },
            onEdit = { editing = b; selected = null },
            onOpenTransaction = onOpenTransaction,
            onDismiss = { selected = null },
        )
    }

    editing?.let { b -> BillEditor(b, onSave = { vm.saveBill(it) }, onDismiss = { editing = null }) }
}

/** "today", "tomorrow", "in 3 days". */
private fun inDays(n: Long): String = when (n) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $n days" }

/** The next bill to pay: an overdue one first, else the soonest coming up. */
@Composable
private fun Hero(sorted: List<Pair<Bill, BillState>>) {
    val next = sorted.firstOrNull { it.second is BillState.Overdue || it.second is BillState.Upcoming }
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.md)) {
        if (next == null) {
            CapsLabel("Bills")
            Spacer(Modifier.height(Space.sm))
            Text("All paid for now", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(Space.sm))
            Text("${countLabel(sorted.size, "bill")} · nothing due", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            return@Column
        }
        val (b, s) = next
        val overdue = s is BillState.Overdue
        val amount = BillTracker.amountDue(b)
        val line = when (s) {
            is BillState.Overdue -> "Was due ${s.due.format(dayFmt)} · ${countLabel(s.daysLate.toInt(), "day")} late"
            is BillState.Upcoming -> "Due ${s.due.format(dayFmt)} · ${inDays(s.daysLeft)}"
            else -> ""
        }
        CapsLabel(if (overdue) "Overdue · ${b.name}" else "Next bill · ${b.name}", color = if (overdue) Expense else MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(Space.sm))
        if (amount != null) AmountDisplay(amount, spokenLabel = "${b.name}, $line")
        else Text("Amount varies", style = MaterialTheme.typography.headlineMedium)
        Spacer(Modifier.height(Space.sm))
        Text(line, style = MaterialTheme.typography.bodyMedium, color = if (overdue) Expense else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** The full status sentence in the bill's sheet. */
private fun statusText(b: Bill, s: BillState): String {
    val amount = BillTracker.amountDue(b)?.let { money(it) } ?: "Amount varies"
    return when (s) {
        is BillState.Upcoming -> "$amount · due ${s.due.format(dayFmt)}" + when (s.daysLeft) { 0L -> " (today)"; 1L -> " (tomorrow)"; else -> " (in ${s.daysLeft} days)" }
        is BillState.Overdue -> "$amount · was due ${s.due.format(dayFmt)}, ${s.daysLate} day${if (s.daysLate == 1L) "" else "s"} ago"
        is BillState.Paid -> "Paid for ${s.due.format(dayFmt)}" + if (s.transactionId == null) " (marked by you)" else ""
        BillState.Done -> "Nothing more due"
    }
}

/** The status under a bill's name, in words; red only when it is overdue. */
@Composable
private fun rowStatus(s: BillState): Pair<String, Color> = when (s) {
    is BillState.Overdue -> "Overdue · was due ${s.due.format(dayFmt)}" to Expense
    is BillState.Upcoming -> when (s.daysLeft) {
        0L -> "Due today" to MaterialTheme.colorScheme.onSurface
        1L -> "Due tomorrow" to MaterialTheme.colorScheme.onSurface
        else -> "Due ${s.due.format(dayFmt)} · in ${s.daysLeft} days" to MaterialTheme.colorScheme.onSurfaceVariant
    }
    is BillState.Paid -> (if (s.transactionId == null) "Paid · marked by you" else "Paid") to MaterialTheme.colorScheme.onSurfaceVariant
    BillState.Done -> "Nothing more due" to MaterialTheme.colorScheme.onSurfaceVariant
}

/** A bill as a ledger row; a loan adds how many EMIs are paid, as a bar and in words. */
@Composable
private fun BillRow(b: Bill, s: BillState, onClick: () -> Unit) {
    val (status, statusColor) = rowStatus(s)
    val amount = BillTracker.amountDue(b)
    val loan = BillTracker.loanProgress(b, LocalDate.now())
    val settled = s is BillState.Paid || s == BillState.Done
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        LetterAvatar(b.name, colorFor(Category.entries.indexOf(b.category)), size = RowIconSize)
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(b.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(status, style = MaterialTheme.typography.bodySmall, color = statusColor)
            if (loan != null) LoanMeter(loan, Modifier.padding(top = Space.xs))
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.widthIn(min = LedgerAmountMinWidth), horizontalAlignment = Alignment.End) {
            LedgerAmount(
                amount?.let { money(it) } ?: "Varies",
                if (amount == null || settled) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
        }
    }
}

/** "EMI 10 of 36" with a bar showing how far through the loan it is. */
@Composable
private fun LoanMeter(p: LoanProgress, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        ProgressMeter(if (p.totalInstalments > 0) p.paidInstalments.toFloat() / p.totalInstalments else 0f, height = 6.dp)
        Text("EMI ${p.paidInstalments} of ${p.totalInstalments}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** One bill in full: amount, status, loan progress, and mark paid / not paid / edit / delete. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillSheet(
    b: Bill,
    s: BillState,
    onMarkPaid: (LocalDate) -> Unit,
    onUnmark: (LocalDate) -> Unit,
    onUnlink: (Long) -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }
    var confirmDelete by remember { mutableStateOf(false) }
    val due = when (s) { is BillState.Upcoming -> s.due; is BillState.Overdue -> s.due; is BillState.Paid -> s.due; BillState.Done -> null }
    val amount = BillTracker.amountDue(b)

    FinSheet(onDismiss = onDismiss, title = b.name, state = state) {
        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            if (amount != null) AmountDisplay(amount, size = AmountSize.Medium, color = MaterialTheme.colorScheme.onSurface, spokenLabel = b.name)
            else Text("Amount varies", style = MaterialTheme.typography.headlineSmall)
            Text(
                statusText(b, s),
                style = MaterialTheme.typography.bodyMedium,
                color = if (s is BillState.Overdue) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        BillTracker.loanProgress(b, LocalDate.now())?.let { p ->
            SoftPanel(spacing = Space.sm) {
                Text("EMI ${p.paidInstalments} of ${p.totalInstalments} · ${money(p.outstandingPaise)} still owed", style = MaterialTheme.typography.bodyMedium)
                ProgressMeter(if (p.totalInstalments > 0) p.paidInstalments.toFloat() / p.totalInstalments else 0f)
            }
        }
        if (s is BillState.Paid && s.transactionId != null) {
            TextAction("Open the payment", { close { onDismiss(); onOpenTransaction(s.transactionId) } }, alignStart = true)
        }
        if (due != null && s !is BillState.Paid) {
            PrimaryButton("Mark paid", { haptics.confirm(); onMarkPaid(due); close(onDismiss) })
        }
        if (s is BillState.Paid && s.transactionId == null) {
            SecondaryButton("Not paid", { haptics.confirm(); onUnmark(s.due); close(onDismiss) }, fill = true)
        }
        if (s is BillState.Paid && s.transactionId != null) {
            // The payment was matched by itself: let the person say it was for something else.
            val txId = s.transactionId
            SecondaryButton("Not this payment", { haptics.confirm(); onUnmark(s.due); onUnlink(txId); close(onDismiss) }, fill = true)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            SecondaryButton("Edit", { close(onEdit) }, Modifier.weight(1f))
            Spacer(Modifier.width(Space.sm))
            TextAction("Delete", { confirmDelete = true }, tone = Tone.Danger)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${b.name}?") },
            text = { Text("It stops showing here and you won't be reminded about it. Your payments stay as they are.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(); close(onDismiss) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

/** A text field in a sheet: full width, one line, the app's control shape. */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = ControlShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

/** Add or change a bill. A loan needs its amount, rate and length; its EMI is worked out. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BillEditor(start: Bill, onSave: (Bill) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var amount by remember { mutableStateOf(start.amountPaise?.let { paiseToInput(it) } ?: "") }
    var day by remember { mutableStateOf(start.dueDay.takeIf { it > 0 }?.toString() ?: "") }
    var every by remember { mutableStateOf(start.everyMonths) }
    // Which months a quarterly, half-yearly or yearly bill falls in: kept as it was when editing, this month for a new one.
    var firstMonth by remember { mutableStateOf(if (start.id != 0L && start.everyMonths > 1) start.startMonth else LocalDate.now().monthValue) }
    var keyword by remember { mutableStateOf(start.keyword ?: "") }
    var isLoan by remember { mutableStateOf(start.loan != null) }
    var principal by remember { mutableStateOf(start.loan?.let { paiseToInput(it.principalPaise) } ?: "") }
    var saving by remember { mutableStateOf(false) }
    var rate by remember { mutableStateOf(start.loan?.let { "%.2f".format(Locale.ENGLISH, it.annualRateBp / 100.0) } ?: "") }
    var tenure by remember { mutableStateOf(start.loan?.tenureMonths?.toString() ?: "") }
    var first by remember { mutableStateOf(start.loan?.firstDue?.toString() ?: LocalDate.now().withDayOfMonth(1).plusMonths(1).toString()) }

    val dueDay = day.toIntOrNull()?.takeIf { it in 1..31 }
    val loan = if (!isLoan) null else {
        val p = Money.parsePaise(principal); val r = rate.toDoubleOrNull(); val n = tenure.toIntOrNull()
        val f = runCatching { LocalDate.parse(first) }.getOrNull()
        if (p != null && p > 0 && r != null && r >= 0 && n != null && n > 0 && f != null) Loan(p, Math.round(r * 100).toInt(), n, f) else null
    }
    val valid = name.isNotBlank() && (dueDay != null || start.fixedDue != null) && (!isLoan || loan != null)

    val haptics = rememberHaptics()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }

    FinSheet(onDismiss = onDismiss, title = if (start.id == 0L) "Add a bill" else "Edit bill", state = state) {
        Field(name, { name = it }, "Name, e.g. Rent or Airtel")
        Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp)
                .toggleable(value = isLoan, role = Role.Switch, onValueChange = { isLoan = it }),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Loan EMI", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text("FinTrack works out the EMI from the loan", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(Space.md))
            Switch(checked = isLoan, onCheckedChange = null)
        }
        if (isLoan) {
            Field(principal, { principal = it }, "Loan amount (₹)", keyboard = KeyboardType.Decimal)
            AdaptiveRow(count = 2, minItemWidth = 150.dp) { m ->
                Field(rate, { rate = it }, "Interest rate (% a year)", m, KeyboardType.Decimal)
                Field(tenure, { tenure = it }, "Length (months)", m, KeyboardType.Number)
            }
            DateField("First EMI date", runCatching { LocalDate.parse(first) }.getOrNull(), { first = it.toString() })
            loan?.let {
                val emi = BillTracker.amountDue(Bill(name = "", amountPaise = null, dueDay = 1, keyword = null, loan = it))!!
                SoftPanel(spacing = Space.xs) {
                    CapsLabel("EMI")
                    Text(money(emi), style = MoneyType.title, maxLines = 1, softWrap = false)
                }
            }
        } else {
            Field(amount, { amount = it }, "Amount (₹), blank if it varies", keyboard = KeyboardType.Decimal)
            CapsLabel("Repeats")
            // Wrapping chips, so "Half-yearly" is never cut at the sheet's edge.
            ChipFlow {
                listOf(1 to "Monthly", 3 to "Quarterly", 6 to "Half-yearly", 12 to "Yearly").forEach { (m, l) -> PillChip(every == m, l) { every = m } }
            }
            if (every > 1) {
                CapsLabel("Due in")
                // One chip per set of months, e.g. "Jan, Apr, Jul, Oct" for a quarterly bill.
                ChipFlow {
                    (1..every).forEach { m ->
                        val label = (m..12 step every).joinToString(", ") { java.time.Month.of(it).getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) }
                        PillChip(Math.floorMod(firstMonth - m, every) == 0, label) { firstMonth = m }
                    }
                }
            }
        }
        if (start.fixedDue == null) Field(day, { day = it }, "Due on day (1–31)", keyboard = KeyboardType.Number)
        Field(keyword, { keyword = it }, "Payment shows as (optional), e.g. airtel")
        PrimaryButton(
            "Save",
            onClick = {
                // One save per sheet, however fast the button is tapped while it closes.
                if (saving) return@PrimaryButton
                saving = true
                val f = loan?.firstDue
                haptics.confirm()
                onSave(
                    start.copy(
                        name = name.trim(), amountPaise = if (isLoan) null else Money.parsePaise(amount)?.takeIf { it > 0 },
                        dueDay = f?.dayOfMonth ?: dueDay ?: start.dueDay, everyMonths = if (isLoan) 1 else every,
                        // A monthly bill or a loan keeps whatever it had; only the "Due in" choice moves the months.
                        startMonth = if (!isLoan && every > 1) firstMonth else start.startMonth,
                        keyword = keyword.trim().ifBlank { null }, loan = loan,
                        category = if (isLoan) Category.BILLS else start.category,
                    )
                )
                close(onDismiss)
            },
            enabled = valid && !saving,
        )
    }
}
