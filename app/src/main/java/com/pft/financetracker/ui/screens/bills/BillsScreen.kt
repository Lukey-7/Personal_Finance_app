package com.pft.financetracker.ui.screens.bills

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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ReceiptLong
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import com.pft.financetracker.ui.components.DateField
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.Space
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.domain.bills.Loan
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AddFab
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LetterAvatar
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.RowIconGap
import com.pft.financetracker.ui.components.RowIconSize
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Bills, EMIs and card bills: what is overdue, what is coming up, and what is already paid this cycle. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BillsScreen(vm: AppViewModel, onOpenTransaction: (Long) -> Unit, onBack: () -> Unit) {
    val states by vm.billStates.collectAsState()
    var selected by remember { mutableStateOf<Pair<Bill, BillState>?>(null) }
    var editing by remember { mutableStateOf<Bill?>(null) }
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
        floatingActionButton = { AddFab({ editing = Bill(name = "", amountPaise = null, dueDay = LocalDate.now().dayOfMonth, keyword = null) }, label = "Add bill") },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = 8.dp, end = Gutter, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                Text("Paid automatically when a matching payment shows up near the due date. Card bills appear by themselves from statement SMS.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (sorted.isEmpty()) item {
                EmptyState(Icons.Outlined.ReceiptLong, "No bills yet. Add rent, phone, insurance or a loan EMI, and FinTrack marks each one paid when the payment comes in.") {
                    SecondaryButton("Add a bill", { editing = Bill(name = "", amountPaise = null, dueDay = LocalDate.now().dayOfMonth, keyword = null) })
                }
            }
            else item {
                FinCard(padding = PaddingValues(vertical = 8.dp)) {
                    Column {
                        sorted.forEachIndexed { i, (b, s) ->
                            if (i > 0) Hairline(startInset = 20.dp + RowIconSize + RowIconGap, endInset = 20.dp)
                            BillRow(b, s) { selected = b to s }
                        }
                    }
                }
            }
        }
    }

    selected?.let { (b, s) ->
        val due = when (s) { is BillState.Upcoming -> s.due; is BillState.Overdue -> s.due; is BillState.Paid -> s.due; BillState.Done -> null }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(b.name) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(statusText(b, s))
                    BillTracker.loanProgress(b, LocalDate.now())?.let { p ->
                        Text("EMI ${p.paidInstalments} of ${p.totalInstalments} · ${money(p.outstandingPaise)} still owed", style = MaterialTheme.typography.bodySmall)
                    }
                    if (s is BillState.Paid && s.transactionId != null) TextButton(onClick = { selected = null; onOpenTransaction(s.transactionId) }) { Text("Open the payment") }
                }
            },
            confirmButton = {
                Row {
                    if (due != null && s !is BillState.Paid) TextButton(onClick = { vm.markBillPaid(b.id, due); selected = null }) { Text("Mark paid") }
                    if (s is BillState.Paid && s.transactionId == null) TextButton(onClick = { vm.unmarkBillPaid(b.id, s.due); selected = null }) { Text("Not paid") }
                    TextButton(onClick = { editing = b; selected = null }) { Text("Edit") }
                }
            },
            dismissButton = { TextButton(onClick = { vm.deleteBill(b.id); selected = null }) { Text("Delete", color = Expense) } },
        )
    }

    editing?.let { b -> BillEditor(b, onSave = { vm.saveBill(it); editing = null }, onDismiss = { editing = null }) }
}

private fun statusText(b: Bill, s: BillState): String {
    val amount = BillTracker.amountDue(b)?.let { money(it) } ?: "Amount varies"
    return when (s) {
        is BillState.Upcoming -> "$amount · due ${s.due.format(dayFmt)}" + when (s.daysLeft) { 0L -> " (today)"; 1L -> " (tomorrow)"; else -> " (in ${s.daysLeft} days)" }
        is BillState.Overdue -> "$amount · was due ${s.due.format(dayFmt)}, ${s.daysLate} day${if (s.daysLate == 1L) "" else "s"} ago"
        is BillState.Paid -> "Paid for ${s.due.format(dayFmt)}" + if (s.transactionId == null) " (marked by you)" else ""
        BillState.Done -> "Nothing more due"
    }
}

@Composable
private fun BillRow(b: Bill, s: BillState, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        LetterAvatar(b.name, colorFor(Category.entries.indexOf(b.category)), size = RowIconSize)
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f)) {
            Text(b.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val (text, color) = when (s) {
                is BillState.Overdue -> "Overdue since ${s.due.format(dayFmt)}" to Expense
                is BillState.Upcoming -> (if (s.daysLeft == 0L) "Due today" else "Due ${s.due.format(dayFmt)}") to MaterialTheme.colorScheme.onSurfaceVariant
                is BillState.Paid -> "Paid" to Income
                BillState.Done -> "Done" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(text, style = MaterialTheme.typography.bodySmall, color = color, maxLines = 1)
        }
        Spacer(Modifier.width(10.dp))
        Text(BillTracker.amountDue(b)?.let { money(it) } ?: "Varies", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
    }
}

/** Add or change a bill. A loan needs its amount, rate and length; its EMI is worked out. */
@Composable
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
private fun BillEditor(start: Bill, onSave: (Bill) -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(start.name) }
    var amount by remember { mutableStateOf(start.amountPaise?.let { (it / 100).toString() } ?: "") }
    var day by remember { mutableStateOf(start.dueDay.takeIf { it > 0 }?.toString() ?: "") }
    var every by remember { mutableStateOf(start.everyMonths) }
    var keyword by remember { mutableStateOf(start.keyword ?: "") }
    var isLoan by remember { mutableStateOf(start.loan != null) }
    var principal by remember { mutableStateOf(start.loan?.let { (it.principalPaise / 100).toString() } ?: "") }
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id == 0L) "Add a bill" else "Edit bill") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. Rent or Airtel") }, singleLine = true)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Loan EMI", Modifier.weight(1f)); Switch(isLoan, { isLoan = it })
                }
                if (isLoan) {
                    OutlinedTextField(principal, { principal = it }, label = { Text("Loan amount (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(rate, { rate = it }, label = { Text("Interest rate (% a year)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    OutlinedTextField(tenure, { tenure = it }, label = { Text("Length (months)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                    DateField("First EMI date", runCatching { LocalDate.parse(first) }.getOrNull(), { first = it.toString() })
                    loan?.let { Text("EMI ${money(BillTracker.amountDue(Bill(name = "", amountPaise = null, dueDay = 1, keyword = null, loan = it))!!)}", style = MaterialTheme.typography.bodySmall) }
                } else {
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹), blank if it varies") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    CapsLabel("Repeats")
                    // Wrapping pills: a scrolling row was cut off at the dialog's edge ("Half-yearl…").
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs)) {
                        listOf(1 to "Monthly", 3 to "Quarterly", 6 to "Half-yearly", 12 to "Yearly").forEach { (m, l) -> PillChip(every == m, l) { every = m } }
                    }
                }
                if (start.fixedDue == null) OutlinedTextField(day, { day = it }, label = { Text("Due on day (1-31)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(keyword, { keyword = it }, label = { Text("Payment shows as (optional), e.g. airtel") }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                val f = loan?.firstDue
                onSave(
                    start.copy(
                        name = name.trim(), amountPaise = if (isLoan) null else Money.parsePaise(amount)?.takeIf { it > 0 },
                        dueDay = f?.dayOfMonth ?: dueDay ?: start.dueDay, everyMonths = if (isLoan) 1 else every,
                        startMonth = if (isLoan) 1 else LocalDate.now().monthValue, keyword = keyword.trim().ifBlank { null }, loan = loan,
                        category = if (isLoan) Category.BILLS else start.category,
                    )
                )
            }) { Text("Save") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
