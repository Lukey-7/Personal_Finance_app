package com.pft.financetracker.ui.screens.tax

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.reminders.DayClock
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.PossibleGroup
import com.pft.financetracker.domain.tax.PossibleKind
import com.pft.financetracker.domain.tax.SectionTotal
import com.pft.financetracker.domain.tax.TaxReport
import com.pft.financetracker.domain.tax.TaxSection
import com.pft.financetracker.domain.tax.TaxTagger
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CardPadding
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.FinSnackbarHost
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.LedgerAmountMinWidth
import com.pft.financetracker.ui.components.ProgressMeter
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SegmentedControl
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.displayMerchant
import com.pft.financetracker.ui.components.edgeToEdge
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.shortDate
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Tax helper: what may count toward deductions this financial year as the screen's hero, then the payments that may
 * count but need a yes or no, then each section with a one-line explanation, its usual limit and the payments behind
 * it (tap one to change its section), and a CSV for the records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxScreen(vm: AppViewModel, onBack: () -> Unit) {
    val fy by vm.taxYear.collectAsState()
    val txns by vm.transactions.collectAsState()
    val tags by vm.taxTags.collectAsState()
    val loaded by vm.loaded.collectAsState()
    // The date ticks over at midnight, so the year choices move on 1 April even if the app stays open.
    val today by remember { DayClock.today() }.collectAsState(LocalDate.now())
    val thisFy = FinancialYear.of(today)
    val lastFy = FinancialYear(thisFy.startYear - 1)
    LaunchedEffect(thisFy) { if (vm.taxYear.value != thisFy && vm.taxYear.value != lastFy) vm.setTaxYear(thisFy) }

    // The year's report, worked out off the main thread. Null until the first answer, so the screen shows a skeleton
    // rather than "Nothing found" while the data arrives. It carries its own year, so a switch never mislabels figures.
    var shown by remember { mutableStateOf<Pair<FinancialYear, TaxReport>?>(null) }
    LaunchedEffect(txns, tags, fy, loaded) {
        if (!loaded) return@LaunchedEffect
        // The tags start as an empty map until the database answers; give them a moment once, so tagged payments
        // don't flash in the wrong place. A real answer restarts this effect straight away.
        if (shown == null && tags.isEmpty()) delay(250)
        val r = withContext(Dispatchers.Default) { TaxTagger.report(txns, fy, tags) }
        shown = fy to r
    }

    var tagging by remember { mutableStateOf<Transaction?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberHaptics()
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.taxCsv(fy).toByteArray()) } }.isSuccess }
            snackbar.showSnackbar(if (ok) "Exported ${fy.label}" else "Export failed")
        }
    }
    val byId = remember(txns) { txns.associateBy { it.id } }

    /** Tag every payment in a "possible" group at once, with an undo that puts them back to the rules. */
    fun decide(g: PossibleGroup, counts: Boolean) {
        haptics.confirm()
        g.transactionIds.forEach { vm.tagTax(it, if (counts) g.section else null) }
        scope.launch {
            val what = countLabel(g.transactionIds.size, "payment")
            val r = snackbar.showSnackbar(
                if (counts) "$what added to ${g.section.code}" else "$what marked as not a deduction",
                actionLabel = "Undo", duration = SnackbarDuration.Short,
            )
            if (r == SnackbarResult.ActionPerformed) g.transactionIds.forEach { vm.clearTaxTag(it) }
        }
    }

    val report = shown?.takeIf { loaded && it.first == fy }?.second
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tax helper") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (report != null && report.totals.isNotEmpty()) {
                        IconButton(onClick = { export.launch("FinTrack-tax-${fy.label.replace(' ', '-')}.csv") }) { Icon(Icons.Outlined.Download, "Export CSV") }
                    }
                },
            )
        },
        snackbarHost = { FinSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Gutter, top = Space.xs, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            item(key = "fy") {
                SegmentedControl(
                    options = listOf(thisFy.label, lastFy.label),
                    selected = when (fy) { thisFy -> 0; lastFy -> 1; else -> -1 },
                    onSelect = { vm.setTaxYear(if (it == 0) thisFy else lastFy) },
                )
            }

            if (report == null) {
                item(key = "skeleton") { SkeletonHero(Modifier.edgeToEdge(), cards = 3) }
                return@LazyColumn
            }

            // ---- Hero ----
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                    CapsLabel("May count in ${fy.label}")
                    Spacer(Modifier.height(Space.sm))
                    AmountDisplay(report.headlinePaise, spokenLabel = "May count toward deductions, ${fy.label}")
                    Spacer(Modifier.height(Space.sm))
                    if (report.totals.isNotEmpty()) Text(
                        "Across ${countLabel(report.totals.size, "section")} · ${countLabel(report.totals.sumOf { it.transactionIds.size }, "payment")}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "These deductions count only if you file under the old tax regime.",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LearnMore(
                        "For your records, not tax advice.",
                        "How the tax helper works",
                        "Payments are found from payee names and notes, using the old tax regime sections. Since FY 2023-24 " +
                            "the new regime is the default, and most of these deductions don't apply there (your employer's NPS " +
                            "contribution aside).\n\nThe figure counts each section up to its usual limit. Rent stays out of it, " +
                            "because your HRA exemption depends on your salary and city. Donations are shown as paid, because most " +
                            "count at 50%; PM CARES and the national relief funds count in full. Refunds of a premium or fee come off " +
                            "its section.\n\nSome payments may count but can't be told apart from ones that don't, such as a fund " +
                            "that may or may not be ELSS. They wait under \"To check\" until you say. Tap any payment to change its " +
                            "section or mark it as not a deduction. It is for your records, not tax advice.",
                    )
                }
            }

            if (report.totals.isEmpty() && report.possible.isEmpty()) item(key = "empty") {
                EmptyState(
                    Icons.AutoMirrored.Outlined.ReceiptLong,
                    "Nothing found for ${fy.label}",
                    "Insurance, PPF, ELSS, NPS, rent and donations show up here as they are paid.",
                ) { if (fy == thisFy) SecondaryButton("See ${lastFy.label}", { vm.setTaxYear(lastFy) }) }
            }

            if (report.possible.isNotEmpty()) {
                item(key = "check-head") {
                    Column(Modifier.semantics(mergeDescendants = true) { heading() }) {
                        Text("To check", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "These may count. Say yes or no and they move into place.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                items(report.possible, key = { "p-${it.kind.name}-${it.transactionIds.first()}" }) { g ->
                    PossibleCard(g, byId, onCounts = { decide(g, true) }, onNot = { decide(g, false) }, onTag = { tagging = it })
                }
            }

            if (report.totals.isNotEmpty()) item(key = "sections-head") {
                Text("Found", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
            }
            items(report.totals, key = { it.section.name }) { t -> SectionCard(t, byId) { tagging = it } }
        }
    }

    tagging?.let { tx -> TaxTagDialog(tx.merchant, onPick = { vm.tagTax(tx.id, it); tagging = null }, onRule = { vm.clearTaxTag(tx.id); tagging = null }, onDismiss = { tagging = null }) }
}

/** One plain line on what a section covers and its limit. */
private fun sectionNote(s: TaxSection): String = when (s) {
    TaxSection.S80C -> "Life insurance, PPF, ELSS funds, children's tuition fees and more – up to ${money(1_50_000_00L)} in all."
    TaxSection.S80CCD1B -> "Your own NPS Tier I contributions – up to ${money(50_000_00L)} more, on top of 80C."
    TaxSection.S80D -> "Health insurance – up to ${money(25_000_00L)} for your family (${money(50_000_00L)} for a senior citizen); parents' cover counts separately."
    TaxSection.S80E -> "Interest on an education loan – no upper limit, for up to eight years."
    TaxSection.S24B -> "Interest on a loan for the home you live in – up to ${money(2_00_000_00L)}."
    TaxSection.S80G -> "Donations to approved charities. Most count at 50%; PM CARES and national relief funds in full."
    TaxSection.HRA -> "Rent you paid. Your HRA exemption depends on your salary and city, so it isn't in the total above."
}

/** The heading and one-line hint for a payment that may count. */
private fun possibleTitle(k: PossibleKind): String = when (k) {
    PossibleKind.ELSS -> "Possible 80C: check if these are ELSS"
    PossibleKind.HOME_LOAN_EMI -> "Possible 24(b)/80C: home loan EMI"
    PossibleKind.RENT -> "Possible rent"
    PossibleKind.TUITION -> "Possible 80C: tuition fees"
    PossibleKind.NPS -> "Possible NPS"
    PossibleKind.INSURANCE -> "Possible 80D: health insurance"
    PossibleKind.DONATION -> "Possible 80G: donation"
    PossibleKind.EDUCATION_LOAN_EMI -> "Possible 80E: education loan"
}

private fun possibleHint(k: PossibleKind): String = when (k) {
    PossibleKind.ELSS -> "Only ELSS (tax saver) funds count. Other funds don't."
    PossibleKind.HOME_LOAN_EMI -> "The interest counts under 24(b), the principal under 80C. Your lender's certificate has the split."
    PossibleKind.RENT -> "About the same amount goes to this person most months. Rent counts toward HRA."
    PossibleKind.TUITION -> "Only the tuition part of school or college fees counts, for up to two children."
    PossibleKind.NPS -> "Counts if it went into your NPS Tier I account."
    PossibleKind.INSURANCE -> "Counts only if it's health insurance – not motor, travel or home."
    PossibleKind.DONATION -> "Counts only if the charity is approved under 80G."
    PossibleKind.EDUCATION_LOAN_EMI -> "Only the interest part of the EMI counts."
}

/** A payee whose payments may count: what it might be, the total, and one tap to say yes or no for all of them. */
@Composable
private fun PossibleCard(g: PossibleGroup, byId: Map<Long, Transaction>, onCounts: () -> Unit, onNot: () -> Unit, onTag: (Transaction) -> Unit) {
    var open by remember(g.transactionIds) { mutableStateOf(false) }
    val list = g.transactionIds.mapNotNull { byId[it] }
    val name = displayMerchant(g.payee)
    val dates = when (list.size) {
        0 -> ""
        1 -> shortDate(list.first().timestamp)
        else -> "${countLabel(list.size, "payment")} · last ${shortDate(list.last().timestamp)}"
    }
    FinCard(padding = PaddingValues(top = CardPadding, bottom = Space.sm), spacing = Space.sm) {
        Text(
            possibleTitle(g.kind), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary,
            maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(horizontal = CardPadding),
        )
        Row(
            Modifier.fillMaxWidth()
                .then(if (list.size > 1) Modifier.clickable(role = Role.Button, onClickLabel = if (open) "Hide payments" else "Show payments") { open = !open } else Modifier)
                .padding(horizontal = CardPadding),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(dates, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.width(Space.md))
            LedgerAmount(money(g.totalPaise), MaterialTheme.colorScheme.onSurface, style = MoneyType.small)
        }
        Text(
            possibleHint(g.kind), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = CardPadding),
        )
        if (open) Column {
            list.forEachIndexed { i, tx ->
                if (i > 0) Hairline(startInset = CardPadding, endInset = CardPadding)
                PaymentRow(tx) { onTag(tx) }
            }
        }
        Row(Modifier.padding(horizontal = CardPadding), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            SecondaryButton("Counts", onCounts)
            TextAction("Doesn't count", onNot)
        }
    }
}

/** One tax section: its name, what it covers, how much of its usual limit is used, and the payments behind it. */
@Composable
private fun SectionCard(t: SectionTotal, byId: Map<Long, Transaction>, onTag: (Transaction) -> Unit) {
    val s = t.section
    // Rent and donations show what was paid; the rest show what may count, up to the limit.
    val figure = if (s == TaxSection.HRA || s == TaxSection.S80G) t.totalPaise else t.claimablePaise
    val status = buildList {
        when {
            s == TaxSection.HRA -> add("${money(t.totalPaise)} paid")
            s == TaxSection.S80G -> add(if (t.claimablePaise > 0) "${money(t.totalPaise)} paid · ${money(t.claimablePaise)} counts in full" else "${money(t.totalPaise)} paid")
            s.limitPaise != null -> add("${money(t.totalPaise)} of ${money(s.limitPaise)}" + if (t.totalPaise >= s.limitPaise) " · limit reached" else "")
            else -> add("${money(t.totalPaise)} paid")
        }
        if (t.refundedPaise > 0) add("${money(t.refundedPaise)} refunded")
    }.joinToString(" · ")
    FinCard(padding = PaddingValues(top = CardPadding, bottom = Space.sm), spacing = Space.sm) {
        Row(Modifier.padding(horizontal = CardPadding).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
            Text(s.code, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            Text(
                " · ${s.label}", style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(Space.md))
            Text(money(figure), style = MoneyType.tile, maxLines = 1, softWrap = false)
        }
        Column(Modifier.padding(horizontal = CardPadding)) {
            Text(status, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(sectionNote(s), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        s.limitPaise?.let { limit ->
            ProgressMeter((t.totalPaise.toFloat() / limit).coerceIn(0f, 1f), Modifier.padding(horizontal = CardPadding), height = 6.dp)
        }
        Column {
            t.transactionIds.mapNotNull { byId[it] }.forEachIndexed { i, tx ->
                if (i > 0) Hairline(startInset = CardPadding, endInset = CardPadding)
                PaymentRow(tx) { onTag(tx) }
            }
        }
    }
}

/** A payment in a section: payee and date on the left, the amount on the right (a refund as a minus). Tap to re-tag. */
@Composable
private fun PaymentRow(tx: Transaction, onClick: () -> Unit) {
    val name = displayMerchant(tx.merchant)
    val refund = tx.type == TransactionType.CREDIT
    val amount = money(if (refund) -tx.amountPaise else tx.amountPaise)
    val date = shortDate(tx.timestamp) + if (refund) " · refund" else ""
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Change section", onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$name, $date, $amount"; role = Role.Button }
            .heightIn(min = 52.dp)
            .padding(horizontal = CardPadding, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(date, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(Space.md))
        LedgerAmount(amount, MaterialTheme.colorScheme.onSurface, Modifier.widthIn(min = LedgerAmountMinWidth), style = MoneyType.small)
    }
}

/** Pick a section for a payment, mark it "not a deduction", or go back to the automatic rule. A sheet titled [title]. */
@Composable
fun TaxTagDialog(title: String, onPick: (TaxSection?) -> Unit, onRule: () -> Unit, onDismiss: () -> Unit) {
    val haptics = rememberHaptics()
    FinSheet(onDismiss = onDismiss, title = title) {
        Text("Which section does this payment count toward?", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Column {
            TaxSection.entries.forEachIndexed { i, s ->
                if (i > 0) Hairline()
                SectionChoice(s.code, s.label) { haptics.confirm(); onPick(s) }
            }
            Hairline()
            SectionChoice(null, "Not a deduction") { haptics.confirm(); onPick(null) }
        }
        TextAction("Use the automatic rule", onRule, alignStart = true)
    }
}

@Composable
private fun SectionChoice(code: String?, label: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 52.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (code != null) {
            Text(code, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary, modifier = Modifier.widthIn(min = 56.dp))
            Spacer(Modifier.width(Space.sm))
        }
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
    }
}
