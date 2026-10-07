package com.pft.financetracker.ui.screens.edit

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PickerField
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SegmentedControl
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.paiseToInput
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics

/** What the quick-add sheet hands over when "More details" is tapped. */
data class Prefill(val amount: String, val category: Category?, val note: String, val cash: Boolean)

/**
 * Add or edit a transaction (or enter one from Review). The amount is the hero at the top; the rest is grouped: what it
 * was, when and where, how it counts, and its tax section. Reading a transaction happens on its detail screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditTransactionScreen(
    vm: AppViewModel,
    id: Long?,
    reviewId: Long?,
    prefill: Prefill? = null,
    onOpenSplit: (Long) -> Unit = {},
    onOpenTransaction: (Long) -> Unit = {},
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    val cashCounted by vm.countCashAsSpend.collectAsState()
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
        // From the quick-add sheet: the same fields it would have saved, now open for more detail.
        if (id == null && reviewId == null && prefill != null) {
            amount = prefill.amount
            merchant = prefill.note.trim()
            prefill.category?.let { category = it; categoryTouched = true }
            if (prefill.cash) {
                bank = "Cash"; note = "Paid in cash"
                flow = if (cashCounted) Flow.TRANSFER else Flow.EXPENSE; flowTouched = true
            }
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
                // Decided from the route, not the loaded row: the row arrives a frame later.
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
                .padding(start = Gutter, top = Space.sm, end = Gutter, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            reviewBody?.let {
                SoftPanel {
                    CapsLabel("Original message")
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }

            // ---- Amount & type: the hero ----
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.md)) {
                CapsLabel("Amount")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("₹", style = MoneyType.medium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    BasicTextField(
                        amount, { amount = it.filter { c -> c.isDigit() || c == '.' || c == ',' }.take(13) },
                        textStyle = MoneyType.large.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Start),
                        singleLine = true,
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier.widthIn(min = 64.dp, max = 280.dp).semantics { contentDescription = "Amount in rupees" },
                        decorationBox = { inner ->
                            if (amount.isEmpty()) Text("0", style = MoneyType.large, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        },
                    )
                }
                SegmentedControl(
                    listOf("Money out", "Money in"),
                    if (type == TransactionType.DEBIT) 0 else 1,
                    { type = if (it == 0) TransactionType.DEBIT else TransactionType.CREDIT },
                )
            }

            Section("What it was") {
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant or payee") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CapsLabel("Category")
                ChipFlow {
                    Category.entries.forEach { c ->
                        PillChip(category == c, c.label, icon = categoryIcon(c)) { category = c; categoryTouched = true }
                    }
                }
                OutlinedTextField(note, { note = it }, label = { Text("Note (optional)") }, modifier = Modifier.fillMaxWidth())
            }

            Section("When & where") {
                PickerField("Date", dateOnly(timestamp), { showDate = true })
                OutlinedTextField(bank, { bank = it }, label = { Text("Bank or app (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(account, { account = it.filter { ch -> ch.isDigit() }.take(4) }, label = { Text("Account last 4 (optional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }

            Section("Counts as") {
                ChipFlow {
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
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // Tax: the rule's guess, or the person's own tag, changeable for any payment out.
            existing?.takeIf { it.type == TransactionType.DEBIT }?.let { t ->
                val tags by vm.taxTags.collectAsState()
                var picking by remember { mutableStateOf(false) }
                val section = if (tags.containsKey(t.id)) tags[t.id] else com.pft.financetracker.domain.tax.TaxTagger.suggest(t)
                Section("Tax") {
                    ActionRow(
                        "Tax section", Icons.Outlined.AccountBalance, { picking = true },
                        subtitle = (section?.let { "${it.code} · ${it.label}" } ?: "Not a deduction") + if (tags.containsKey(t.id)) " (set by you)" else "",
                    )
                }
                if (picking) com.pft.financetracker.ui.screens.tax.TaxTagDialog(t.merchant, onPick = { vm.tagTax(t.id, it); picking = false }, onRule = { vm.clearTaxTag(t.id); picking = false }, onDismiss = { picking = false })
            }

            PrimaryButton(
                text = "Save",
                enabled = valid,
                onClick = {
                    haptics.confirm()
                    val t = build()
                    if (reviewId != null) vm.resolveReview(reviewId, t) { onBack() } else vm.save(t) { onBack() }
                },
            )
            if (!valid && (amount.isNotEmpty() || merchant.isNotEmpty())) Text(
                when {
                    amountValue == null || amountValue <= 0 -> "Enter an amount above ₹0."
                    else -> "Add who it was paid to or from."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        confirmButton = { TextButton(onClick = { existing?.let { vm.delete(it) }; confirmDelete = false; onBack() }) { Text("Delete", color = Expense) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

/** A titled group of fields on a card. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    FinCard(padding = PaddingValues(Space.lg), spacing = Space.md) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}
