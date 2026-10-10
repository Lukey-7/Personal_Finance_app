package com.pft.financetracker.ui.screens.edit

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.EmptyState
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
import com.pft.financetracker.ui.model.AmountInput
import com.pft.financetracker.domain.ledger.FlowRules
import com.pft.financetracker.ui.model.PickerDate
import com.pft.financetracker.ui.model.ReviewBank
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics

/** What the quick-add sheet hands over when "More details" is tapped. */
data class Prefill(val amount: String, val category: Category?, val note: String, val cash: Boolean)

/** A tax choice made in the editor, applied on Save: the rule's guess again, not a deduction, or a section by name. */
private const val TAX_RULE = "RULE"
private const val TAX_NONE = "NONE"

/**
 * Add or edit a transaction (or enter one from Review). The amount is the hero at the top; the rest is grouped: what it
 * was, when and where, how it counts, and its tax section. Reading a transaction happens on its detail screen.
 *
 * The form survives rotation and the app being closed in the background; the stored row fills it once only, so it
 * never overwrites what was typed. [onDeleted] runs after a delete (default [onBack]); the caller can pop past the
 * payment's detail screen there, which would otherwise say the payment is gone.
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
    onDeleted: (() -> Unit)? = null,
    onBack: () -> Unit,
) {
    val haptics = rememberHaptics()
    val cashCounted by vm.countCashAsSpend.collectAsState()
    // The stored row and the review message are read again after a rotation; the fields below are the person's.
    var existing by remember { mutableStateOf<Transaction?>(null) }
    var reviewBody by remember { mutableStateOf<String?>(null) }
    var loaded by remember { mutableStateOf(false) }
    var missing by remember { mutableStateOf(false) }

    var initialised by rememberSaveable { mutableStateOf(false) }
    var amount by rememberSaveable { mutableStateOf("") }
    var merchant by rememberSaveable { mutableStateOf("") }
    var typeName by rememberSaveable { mutableStateOf(TransactionType.DEBIT.name) }
    var categoryName by rememberSaveable { mutableStateOf(Category.OTHER.name) }
    var categoryTouched by rememberSaveable { mutableStateOf(false) }
    var flowName by rememberSaveable { mutableStateOf(Flow.EXPENSE.name) }
    var flowTouched by rememberSaveable { mutableStateOf(false) }
    var refNumber by rememberSaveable { mutableStateOf<String?>(null) }
    var bank by rememberSaveable { mutableStateOf("") }
    var account by rememberSaveable { mutableStateOf("") }
    var note by rememberSaveable { mutableStateOf("") }
    var timestamp by rememberSaveable { mutableStateOf(System.currentTimeMillis()) }
    var smsHash by rememberSaveable { mutableStateOf<String?>(null) }
    /** null: unchanged; [TAX_RULE], [TAX_NONE] or a section name: what Save will apply. */
    var pendingTax by rememberSaveable { mutableStateOf<String?>(null) }
    var showDate by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    // Set by the first Save, Back or Delete: a second tap neither saves twice nor leaves twice.
    var saving by remember { mutableStateOf(false) }
    var left by remember { mutableStateOf(false) }
    fun leave(action: () -> Unit) { if (!left) { left = true; action() } }

    val type = runCatching { TransactionType.valueOf(typeName) }.getOrDefault(TransactionType.DEBIT)
    val category = Category.entries.firstOrNull { it.name == categoryName } ?: Category.OTHER
    val flow = runCatching { Flow.valueOf(flowName) }.getOrDefault(FlowRules.default(type))

    LaunchedEffect(id, reviewId) {
        val t = id?.let { vm.getTransaction(it) }
        val r = reviewId?.let { vm.getReview(it) }
        existing = t
        reviewBody = r?.body
        // Asked for a payment or a message that has gone (deleted, merged, already reviewed): say so, never a blank
        // form that would save a second copy.
        if ((id != null && t == null) || (reviewId != null && r == null)) { missing = true; loaded = true; return@LaunchedEffect }
        if (!initialised) {
            if (t != null) {
                amount = paiseToInput(t.amountPaise)
                merchant = t.merchant; typeName = t.type.name; categoryName = t.category.name; categoryTouched = true
                flowName = t.flow.name; flowTouched = true; refNumber = t.refNumber
                bank = t.bankName ?: ""; account = t.accountRef ?: ""; note = t.note ?: ""; timestamp = t.timestamp; smsHash = t.smsHash
            }
            if (r != null) {
                amount = r.guessedAmountPaise?.let { paiseToInput(it) } ?: ""
                typeName = (r.guessedType?.let { g -> runCatching { TransactionType.valueOf(g) }.getOrNull() } ?: TransactionType.DEBIT).name
                // The bank's name as the parser reads it from the sender ("AD-HDFCBK" is HDFC Bank); blank if unknown.
                timestamp = r.receivedAt; smsHash = r.smsHash; bank = ReviewBank.of(r.sender, r.body) ?: ""
            }
            // From the quick-add sheet: the same fields it would have saved, now open for more detail.
            if (id == null && reviewId == null && prefill != null) {
                amount = prefill.amount
                merchant = prefill.note.trim()
                prefill.category?.let { categoryName = it.name; categoryTouched = true }
                if (prefill.cash) {
                    bank = "Cash"; note = "Paid in cash"
                    flowName = (if (cashCounted) Flow.TRANSFER else Flow.EXPENSE).name; flowTouched = true
                }
            }
            initialised = true
        }
        loaded = true
    }

    // Auto-suggest category and flow while the user has not picked them manually.
    LaunchedEffect(merchant, typeName, categoryName) {
        if (!categoryTouched && merchant.length >= 3) categoryName = Categorizer.categorize(merchant, type).name
        if (!flowTouched) flowName = FlowClassifier.classify(type, reviewBody ?: "", merchant, category).name
    }

    val amountValue = AmountInput.paiseOf(amount)
    val valid = amountValue != null && merchant.isNotBlank()

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
        // Never money in counted as spend, nor money out as income.
        flow = FlowRules.fit(type, flow),
        note = note.trim().ifBlank { null },
        smsHash = smsHash,
        refNumber = refNumber,
        confidence = existing?.confidence ?: 100,
        needsReview = false,
        originalAmountPaise = existing?.originalAmountPaise,
        counterpartyKind = existing?.counterpartyKind,
        importBatchId = existing?.importBatchId,
    )

    // System back goes through the same guard as the arrow, so a Save finishing later never pops a second screen.
    BackHandler { leave(onBack) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                // Decided from the route, not the loaded row: the row arrives a frame later.
                title = { Text(if (id != null) "Edit transaction" else if (reviewId != null) "Review SMS" else "Add transaction") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = { leave(onBack) }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { if (existing != null) IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete transaction") } },
            )
        },
    ) { padding ->
        if (!loaded) return@Scaffold
        if (missing) {
            EmptyState(
                Icons.Outlined.SearchOff,
                if (id != null) "This payment isn't here any more" else "This message isn't waiting any more",
                if (id != null) "It may have been deleted or merged with a duplicate." else "It may have been added or dismissed already.",
                Modifier.padding(padding),
            ) { TextAction("Go back", { leave(onBack) }) }
            return@Scaffold
        }
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
                // Centred, with the ₹ drawn as part of the text so the figure and its sign stay together as it grows.
                BasicTextField(
                    amount, { amount = AmountInput.clean(it) },
                    textStyle = MoneyType.large.copy(color = MaterialTheme.colorScheme.onSurface, textAlign = TextAlign.Center),
                    singleLine = true,
                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = RupeePrefix,
                    modifier = Modifier.fillMaxWidth().semantics { contentDescription = "Amount in rupees" },
                    decorationBox = { inner ->
                        Box(contentAlignment = Alignment.Center) {
                            if (amount.isEmpty()) Text("₹0", style = MoneyType.large, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            inner()
                        }
                    },
                )
                SegmentedControl(
                    listOf("Money out", "Money in"),
                    if (type == TransactionType.DEBIT) 0 else 1,
                    { i ->
                        val next = if (i == 0) TransactionType.DEBIT else TransactionType.CREDIT
                        typeName = next.name
                        // A choice that doesn't fit the new direction (income on money out, spend on money in) goes
                        // back to the usual one, and the suggestion takes over again.
                        if (!FlowRules.fits(next, flow)) { flowName = FlowRules.default(next).name; flowTouched = false }
                    },
                )
            }

            Section("What it was") {
                OutlinedTextField(merchant, { merchant = it }, label = { Text("Merchant or payee") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                CapsLabel("Category")
                ChipFlow {
                    Category.entries.forEach { c ->
                        PillChip(category == c, c.label, icon = categoryIcon(c)) { categoryName = c.name; categoryTouched = true }
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
                    FlowRules.options(type).forEach { f -> PillChip(flow == f, f.label) { flowName = f.name; flowTouched = true } }
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

            // Tax: the rule's guess, or the person's own tag, for a payment out as the form now stands. A new choice
            // is applied on Save, so backing out leaves the tag as it was.
            existing?.takeIf { type == TransactionType.DEBIT }?.let { t ->
                val tags by vm.taxTags.collectAsState()
                var picking by remember { mutableStateOf(false) }
                val p = pendingTax
                val (section, byYou) = when {
                    p == TAX_RULE -> com.pft.financetracker.domain.tax.TaxTagger.suggest(build()) to false
                    p == TAX_NONE -> null to true
                    p != null -> com.pft.financetracker.domain.tax.TaxSection.fromName(p) to true
                    tags.containsKey(t.id) -> tags[t.id] to true
                    else -> com.pft.financetracker.domain.tax.TaxTagger.suggest(build()) to false
                }
                Section("Tax") {
                    ActionRow(
                        "Tax section", Icons.Outlined.AccountBalance, { picking = true },
                        subtitle = (section?.let { "${it.code} · ${it.label}" } ?: "Not a deduction") + if (byYou) " (set by you)" else "",
                    )
                }
                if (picking) com.pft.financetracker.ui.screens.tax.TaxTagDialog(
                    merchant.trim().ifBlank { t.merchant },
                    onPick = { s -> pendingTax = s?.name ?: TAX_NONE; picking = false },
                    onRule = { pendingTax = TAX_RULE; picking = false },
                    onDismiss = { picking = false },
                )
            }

            PrimaryButton(
                text = "Save",
                enabled = valid && !saving,
                onClick = onClick@{
                    if (saving || left) return@onClick
                    saving = true
                    haptics.confirm()
                    val t = build()
                    val txId = existing?.id
                    if (txId != null && t.type == TransactionType.DEBIT) when (val p = pendingTax) {
                        null -> {}
                        TAX_RULE -> vm.clearTaxTag(txId)
                        TAX_NONE -> vm.tagTax(txId, null)
                        else -> com.pft.financetracker.domain.tax.TaxSection.fromName(p)?.let { vm.tagTax(txId, it) }
                    }
                    if (reviewId != null) vm.resolveReview(reviewId, t) { leave(onBack) } else vm.save(t, existing) { leave(onBack) }
                },
            )
            if (!valid && (amount.isNotEmpty() || merchant.isNotEmpty())) Text(
                when {
                    amountValue == null -> AmountInput.problem(amount) ?: "Enter an amount above ₹0."
                    else -> "Add who it was paid to or from."
                },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (reviewId != null) TextAction("Not a transaction", { if (!saving && !left) { vm.dismissReview(reviewId); leave(onBack) } }, Modifier.fillMaxWidth())
        }
    }

    if (showDate) {
        // The picker works in UTC midnights: it shows the payment's local day, and picking a day keeps its time.
        val state = rememberDatePickerState(initialSelectedDateMillis = PickerDate.toPicker(timestamp))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { timestamp = PickerDate.fromPicker(it, timestamp) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete transaction?") },
        text = { Text("This cannot be undone.") },
        confirmButton = {
            TextButton(onClick = {
                confirmDelete = false
                if (!left && !saving) existing?.let { vm.delete(it); leave(onDeleted ?: onBack) }
            }) { Text("Delete", color = Expense) }
        },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}

/** Shows "₹" before the typed amount without it being part of the value. */
private val RupeePrefix = VisualTransformation { text ->
    if (text.isEmpty()) TransformedText(text, OffsetMapping.Identity)
    else TransformedText(AnnotatedString("₹") + text, object : OffsetMapping {
        override fun originalToTransformed(offset: Int) = offset + 1
        override fun transformedToOriginal(offset: Int) = (offset - 1).coerceIn(0, text.length)
    })
}

/** A titled group of fields on a card. */
@Composable
private fun Section(title: String, content: @Composable ColumnScope.() -> Unit) {
    FinCard(padding = PaddingValues(Space.lg), spacing = Space.md) {
        Text(title, style = MaterialTheme.typography.titleSmall)
        content()
    }
}
