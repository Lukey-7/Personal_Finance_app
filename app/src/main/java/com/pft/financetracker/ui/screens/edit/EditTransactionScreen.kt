package com.pft.financetracker.ui.screens.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.edgeToEdge
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.pft.financetracker.ui.components.Space
import androidx.compose.material.icons.outlined.AccountBalance
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.PickerField
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.TextAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.ui.components.paiseToInput
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.dateOnly
import androidx.compose.material3.TopAppBarDefaults
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.SoftPanel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(vm: AppViewModel, id: Long?, reviewId: Long?, onOpenSplit: (Long) -> Unit = {}, onOpenTransaction: (Long) -> Unit = {}, onBack: () -> Unit) {
    var existing by remember { mutableStateOf<Transaction?>(null) }
    var amount by remember { mutableStateOf("") }
    var merchant by remember { mutableStateOf("") }
    var type by remember { mutableStateOf(TransactionType.DEBIT) }
    var category by remember { mutableStateOf(Category.OTHER) }
    var categoryTouched by remember { mutableStateOf(false) }
    var flow by remember { mutableStateOf(Flow.EXPENSE) }
    var flowTouched by remember { mutableStateOf(false) }
    var refNumber by remember { mutableStateOf<String?>(null) }
    var bank by remember { mutableStateOf("") }
    var account by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var timestamp by remember { mutableStateOf(System.currentTimeMillis()) }
    var smsHash by remember { mutableStateOf<String?>(null) }
    var reviewBody by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var showDate by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(id, reviewId) {
        if (id != null) vm.getTransaction(id)?.let { t ->
            existing = t
            amount = paiseToInput(t.amountPaise)
            merchant = t.merchant; type = t.type; category = t.category; categoryTouched = true
            flow = t.flow; flowTouched = true; refNumber = t.refNumber
            bank = t.bankName ?: ""; account = t.accountRef ?: ""; note = t.note ?: ""; timestamp = t.timestamp; smsHash = t.smsHash
        }
        if (reviewId != null) vm.getReview(reviewId)?.let { r ->
            reviewBody = r.body
            amount = r.guessedAmountPaise?.let { paiseToInput(it) } ?: ""
            type = r.guessedType?.let { runCatching { TransactionType.valueOf(it) }.getOrNull() } ?: TransactionType.DEBIT
            timestamp = r.receivedAt; smsHash = r.smsHash; bank = r.sender
        }
        loaded = true
    }

    // Auto-suggest category and flow while the user has not picked them manually.
    LaunchedEffect(merchant, type, category) {
        if (!categoryTouched && merchant.length >= 3) category = Categorizer.categorize(merchant, type)
        if (!flowTouched) flow = FlowClassifier.classify(type, reviewBody ?: "", merchant, category)
    }

    val amountValue = Money.parsePaise(amount)
    val valid = amountValue != null && amountValue > 0 && merchant.isNotBlank()

    fun build() = Transaction(
        id = existing?.id ?: 0,
        amountPaise = amountValue ?: 0L,
        type = type,
        merchant = merchant.trim(),
        category = category,
        timestamp = timestamp,
        bankName = bank.trim().ifBlank { null },
        accountRef = account.trim().takeLast(4).ifBlank { null },
        source = existing?.source ?: if (reviewId != null) Transaction.Source.SMS else Transaction.Source.MANUAL,
        flow = flow,
        note = note.trim().ifBlank { null },
        smsHash = smsHash,
        refNumber = refNumber,
        confidence = existing?.confidence ?: 100,
        needsReview = false,
        originalAmountPaise = existing?.originalAmountPaise,
        counterpartyKind = existing?.counterpartyKind,
        importBatchId = existing?.importBatchId,
    )

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                // Decided from the route, not the loaded row: the row arrives a frame later, and deciding
                // from it made an edit screen open titled "Add transaction" for that first frame.
                title = { Text(if (id != null) "Edit transaction" else if (reviewId != null) "Review SMS" else "Add transaction") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete transaction") } },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Gutter, top = Space.sm, end = Gutter, bottom = Space.xl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            val autoSplit = id?.let { vm.autoSplitOf.collectAsState().value[it] }
            existing?.takeIf { it.originalAmountPaise != null || autoSplit != null }?.let { t ->
                SoftPanel {
                    CapsLabel(if (autoSplit != null) "Auto-split" else "Split")
                    Text(
                        if (t.type == TransactionType.DEBIT && t.originalAmountPaise != null) "The bank reported ${com.pft.financetracker.ui.components.money(t.originalAmountPaise)}; only your share, ${com.pft.financetracker.ui.components.money(t.amountPaise)}, counts as spend."
                        else "This transfer is a friend paying back their share, so it isn't counted as income.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    autoSplit?.reasons?.take(3)?.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (autoSplit != null) Row {
                        TextAction("Open split", { onOpenSplit(autoSplit.id) }, alignStart = true)
                        TextAction("Not shared", { vm.rejectSplit(autoSplit.id); onBack() })
                    }
                }
            }
            val badges by vm.refundBadges.collectAsState()
            val all by vm.transactions.collectAsState()
            id?.let { badges.pairsOf(it) }?.forEach { p ->
                val other = all.firstOrNull { it.id == (if (p.refundId == id) p.debitId else p.refundId) }
                SoftPanel {
                    CapsLabel(if (p.kind == com.pft.financetracker.domain.refunds.RefundMatch.Kind.REVERSAL) "Reversed" else "Refund")
                    Text(
                        when {
                            other == null -> "Paired with a transaction that is no longer here."
                            p.refundId == id -> "${com.pft.financetracker.ui.components.money(p.amountPaise)} back for ${other.merchant.ifBlank { "a purchase" }} on ${com.pft.financetracker.ui.components.shortDate(other.timestamp)}. It lowers that purchase's category."
                            else -> "${com.pft.financetracker.ui.components.money(p.amountPaise)} of this came back on ${com.pft.financetracker.ui.components.shortDate(other.timestamp)}."
                        },
                        style = MaterialTheme.typography.bodySmall,
                    )
                    Row {
                        if (other != null) TextAction("Open the other one", { onOpenTransaction(other.id) }, alignStart = true)
                        TextAction("Not a refund", { vm.undoRefund(p.linkId) }, alignStart = other == null)
                    }
                }
            }
            reviewBody?.let {
                SoftPanel {
                    CapsLabel("Original message")
                    Text(it, style = MaterialTheme.typography.bodySmall)
                }
            }
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                TransactionType.entries.forEachIndexed { i, t ->
                    SegmentedButton(
                        selected = type == t, onClick = { type = t },
                        shape = SegmentedButtonDefaults.itemShape(i, TransactionType.entries.size)
                    ) { Text(if (t == TransactionType.DEBIT) "Expense" else "Income") }
                }
            }
            OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant / payee") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            CapsLabel("Category")
            ChipRow(Modifier.edgeToEdge(), inset = Gutter) {
                Category.entries.forEach { c ->
                    PillChip(category == c, c.label, icon = categoryIcon(c)) { category = c; categoryTouched = true }
                }
            }
            CapsLabel("Counts as")
            ChipRow(Modifier.edgeToEdge(), inset = Gutter) {
                val options = if (type == TransactionType.DEBIT) listOf(Flow.EXPENSE, Flow.TRANSFER, Flow.INVESTMENT, Flow.CASH, Flow.SETTLEMENT) else listOf(Flow.INCOME, Flow.REFUND, Flow.TRANSFER, Flow.INVESTMENT, Flow.SETTLEMENT)
                options.forEach { f -> PillChip(flow == f, f.label) { flow = f; flowTouched = true } }
            }
            Text(
                when (flow) {
                    Flow.EXPENSE -> "Counted in your spend."
                    Flow.CASH -> "ATM cash. Counted in spend unless turned off in Settings."
                    Flow.INCOME -> "Counted as income."
                    Flow.REFUND -> "Reduces your spend (and the matching category)."
                    Flow.TRANSFER -> "Money between your own accounts or a card bill payment. Not spend, not income."
                    Flow.INVESTMENT -> "Shown separately. Not spend."
                    Flow.SETTLEMENT -> "A friend paying you back for a split, or you paying them. Not spend, not income."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            PickerField("Date", dateOnly(timestamp), { showDate = true })
            OutlinedTextField(bank, { bank = it }, label = { Text("Bank / app (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(account, { account = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("Account last 4 (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            // Tax section: the rule's guess, or the person's own tag, changeable here for any payment out. A row below
            // the form, not a link above it: it is a detail of the payment, not the first thing to read.
            existing?.takeIf { it.type == TransactionType.DEBIT }?.let { t ->
                val tags by vm.taxTags.collectAsState()
                var picking by remember { mutableStateOf(false) }
                val section = if (tags.containsKey(t.id)) tags[t.id] else com.pft.financetracker.domain.tax.TaxTagger.suggest(t)
                ActionRow(
                    "Tax section", Icons.Outlined.AccountBalance, { picking = true },
                    subtitle = (section?.let { "${it.code} · ${it.label}" } ?: "Not a deduction") + if (tags.containsKey(t.id)) " (set by you)" else "",
                )
                if (picking) com.pft.financetracker.ui.screens.tax.TaxTagDialog(t.merchant, onPick = { vm.tagTax(t.id, it); picking = false }, onRule = { vm.clearTaxTag(t.id); picking = false }, onDismiss = { picking = false })
            }
            PrimaryButton(
                text = "Save",
                enabled = valid,
                onClick = {
                    val t = build()
                    if (reviewId != null) vm.resolveReview(reviewId, t) { onBack() } else vm.save(t) { onBack() }
                },
            )
            if (reviewId != null) TextAction("Not a transaction", { vm.dismissReview(reviewId); onBack() }, Modifier.fillMaxWidth())
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = timestamp)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { timestamp = it + 12 * 3600 * 1000 }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete transaction?") },
        text = { Text("This cannot be undone.") },
        confirmButton = { TextButton(onClick = { existing?.let { vm.delete(it) }; confirmDelete = false; onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}
