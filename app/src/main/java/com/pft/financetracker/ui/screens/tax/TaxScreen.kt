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
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
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
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.SectionTotal
import com.pft.financetracker.domain.tax.TaxSection
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * Tax helper: what may count toward deductions this financial year, as the screen's hero, then each section with its
 * usual limit and the payments behind it (tap one to change its section), and a CSV for the records.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxScreen(vm: AppViewModel, onBack: () -> Unit) {
    val fy by vm.taxYear.collectAsState()
    val totals by vm.taxSummary.collectAsState()
    val txns by vm.transactions.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val byId = remember(txns) { txns.associateBy { it.id } }
    var tagging by remember { mutableStateOf<Transaction?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.taxCsv(fy).toByteArray()) } }.isSuccess }
            snackbar.showSnackbar(if (ok) "Exported ${fy.label}" else "Export failed")
        }
    }
    val thisFy = FinancialYear.of(LocalDate.now())
    val lastFy = FinancialYear(thisFy.startYear - 1)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tax helper") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { if (totals.isNotEmpty()) IconButton(onClick = { export.launch("FinTrack-tax-${fy.label.replace(' ', '-')}.csv") }) { Icon(Icons.Outlined.Download, "Export CSV") } },
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

            if (!loaded) {
                item(key = "skeleton") { SkeletonHero(Modifier.edgeToEdge(), cards = 3) }
                return@LazyColumn
            }

            // ---- Hero ----
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(top = Space.sm)) {
                    CapsLabel("Found in ${fy.label}")
                    Spacer(Modifier.height(Space.sm))
                    AmountDisplay(totals.sumOf { it.claimablePaise }, spokenLabel = "May count toward deductions, ${fy.label}")
                    Spacer(Modifier.height(Space.sm))
                    if (totals.isNotEmpty()) Text(
                        "Across ${countLabel(totals.size, "section")} · ${countLabel(totals.sumOf { it.transactionIds.size }, "payment")}",
                        style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    LearnMore(
                        "For your records, not tax advice.",
                        "How the tax helper works",
                        "Payments are found from payee names, using the old tax regime sections (80C, 80D, NPS, rent, donations and " +
                            "so on). Tap any payment to change its section or mark it as not a deduction. The figure counts each " +
                            "section up to its usual limit. It is for your records, not tax advice.",
                    )
                }
            }

            if (totals.isEmpty()) item(key = "empty") {
                EmptyState(
                    Icons.AutoMirrored.Outlined.ReceiptLong,
                    "Nothing found for ${fy.label}",
                    "Insurance, PPF, ELSS, NPS, rent and donations show up here as they are paid.",
                ) { if (fy == thisFy) SecondaryButton("See ${lastFy.label}", { vm.setTaxYear(lastFy) }) }
            }

            items(totals, key = { it.section.name }) { t -> SectionCard(t, byId) { tagging = it } }
        }
    }

    tagging?.let { tx -> TaxTagDialog(tx.merchant, onPick = { vm.tagTax(tx.id, it); tagging = null }, onRule = { vm.clearTaxTag(tx.id); tagging = null }, onDismiss = { tagging = null }) }
}

/** One tax section: its name, how much of its usual limit is used, what may be claimed, and the payments behind it. */
@Composable
private fun SectionCard(t: SectionTotal, byId: Map<Long, Transaction>, onTag: (Transaction) -> Unit) {
    FinCard(padding = PaddingValues(top = CardPadding, bottom = Space.sm), spacing = Space.sm) {
        Row(Modifier.padding(horizontal = CardPadding).semantics(mergeDescendants = true) { heading() }, verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("${t.section.code} · ${t.section.label}", style = MaterialTheme.typography.titleSmall)
                Text(
                    t.section.limitPaise?.let { "${money(t.totalPaise)} of ${money(it)} limit" + if (t.totalPaise >= it) " · limit reached" else "" } ?: money(t.totalPaise),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.width(Space.md))
            Text(money(t.claimablePaise), style = MoneyType.tile, maxLines = 1, softWrap = false)
        }
        t.section.limitPaise?.let { limit ->
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

/** A payment counted in a section: payee and date on the left, the amount in the right-hand column. Tap to re-tag. */
@Composable
private fun PaymentRow(tx: Transaction, onClick: () -> Unit) {
    val name = displayMerchant(tx.merchant)
    Row(
        Modifier.fillMaxWidth()
            .clickable(role = Role.Button, onClickLabel = "Change section", onClick = onClick)
            .clearAndSetSemantics { contentDescription = "$name, ${shortDate(tx.timestamp)}, ${money(tx.amountPaise)}"; role = Role.Button }
            .heightIn(min = 52.dp)
            .padding(horizontal = CardPadding, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(shortDate(tx.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.md))
        LedgerAmount(money(tx.amountPaise), MaterialTheme.colorScheme.onSurface, Modifier.widthIn(min = LedgerAmountMinWidth), style = MoneyType.small)
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
