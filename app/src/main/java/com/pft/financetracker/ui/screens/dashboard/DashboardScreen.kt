package com.pft.financetracker.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.ImportUiState
import com.pft.financetracker.ui.PeriodChoice
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.DonutChart
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.Legend
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.SectionHeader
import com.pft.financetracker.ui.components.Slice
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.Neutral
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenSmsLog: (Long?) -> Unit,
    onDrill: (Bucket, Category?) -> Unit,
) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val importState by vm.importState.collectAsState()
    val choice by vm.period.collectAsState()
    val includeCash by vm.countCashAsSpend.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showRange by remember { mutableStateOf(false) }

    val period = choice.period()
    val summary = InsightsEngine.summarize(txns, period, includeCash)
    val prev = InsightsEngine.summarize(txns, choice.previous(), includeCash)
    val budgetStatus = InsightsEngine.budgetStatus(txns, budgets, period)
    val recent = txns.take(6)

    LaunchedEffect(importState) {
        val s = importState
        if (s is ImportUiState.Done) {
            val res = snackbar.showSnackbar(
                "Scanned ${s.stats.scanned} SMS: ${s.stats.inserted} added, ${s.stats.queuedForReview} to review, ${s.stats.duplicates} duplicates, ${s.stats.ignored} ignored",
                actionLabel = "View log"
            )
            if (res == SnackbarResult.ActionPerformed) onOpenSmsLog(s.stats.runId)
            vm.dismissImportResult()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("FinTrack", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    if (importState is ImportUiState.Running) CircularProgressIndicator(Modifier.padding(14.dp).size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { if (vm.hasSmsPermission()) vm.scanInbox() }) { Icon(Icons.Filled.Refresh, "Scan SMS") }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = onAdd,
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
            ) { Icon(Icons.Filled.Add, "Add") }
        },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = Gutter - 4.dp), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    PillChip(choice is PeriodChoice.ThisMonth, "This month") { vm.setPeriod(PeriodChoice.ThisMonth) }
                    PillChip(choice is PeriodChoice.LastMonth, "Last month") { vm.setPeriod(PeriodChoice.LastMonth) }
                    PillChip(choice is PeriodChoice.ThisWeek, "This week") { vm.setPeriod(PeriodChoice.ThisWeek) }
                    PillChip(choice is PeriodChoice.Custom, if (choice is PeriodChoice.Custom) period.label else "Custom") { showRange = true }
                }
            }

            // ---- The hero: one number, stated plainly, with the arithmetic underneath ----
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    CapsLabel("Net spend · ${period.label}")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        money(summary.netSpendPaise),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable { onDrill(Bucket.SPEND, null) },
                    )
                    Spacer(Modifier.height(6.dp))
                    val diff = if (prev.netSpendPaise > 0) ((summary.netSpendPaise - prev.netSpendPaise).toDouble() / prev.netSpendPaise * 100).roundToInt() else null
                    val proj = summary.projectedPaise()
                    Text(
                        listOfNotNull(
                            diff?.let { if (it >= 0) "↗ $it% vs previous" else "↘ ${-it}% vs previous" },
                            "≈${money(summary.dailyAveragePaise())}/day",
                            proj?.let { "on track for ${money(it)}" },
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (diff != null && diff > 0) Expense else MaterialTheme.colorScheme.primary,
                    )
                }
            }

            item {
                SoftPanel(Modifier.padding(horizontal = Gutter)) {
                    MathRow("Income", summary.incomePaise, "+", Income) { onDrill(Bucket.INCOME, null) }
                    MathRow("Gross spend", summary.grossSpendPaise, "−", MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.SPEND, null) }
                    if (summary.refundsPaise > 0) MathRow("Refunds & cashback", summary.refundsPaise, "+", Income) { onDrill(Bucket.REFUNDS, null) }
                    Hairline()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Savings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            money(summary.savingsPaise),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (summary.savingsPaise >= 0) Income else Expense,
                        )
                    }
                }
            }

            // ---- Money that moved but is not spend ----
            if (summary.transfersOutPaise + summary.transfersInPaise + summary.investmentsPaise + (if (includeCash) 0L else summary.cashPaise) > 0) item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Text("Not counted as spend", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Moving money between your own accounts, paying card bills and investing are shown here so they never inflate your spending.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (summary.transfersOutPaise > 0) LineRow("Transfers & card bill payments", summary.transfersOutPaise) { onDrill(Bucket.TRANSFERS, null) }
                    if (summary.transfersInPaise > 0) LineRow("Transfers in", summary.transfersInPaise) { onDrill(Bucket.TRANSFERS, null) }
                    if (summary.investmentsPaise > 0) LineRow("Investments", summary.investmentsPaise) { onDrill(Bucket.INVESTMENTS, null) }
                    if (!includeCash && summary.cashPaise > 0) LineRow("Cash withdrawals", summary.cashPaise) { onDrill(Bucket.CASH, null) }
                }
            }

            if (reviewCount > 0) item {
                SoftPanel(Modifier.padding(horizontal = Gutter), onClick = onOpenReview) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("$reviewCount SMS need manual review", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("Review", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Text("Where it went", style = MaterialTheme.typography.titleMedium)
                    val slices = summary.byCategory.filter { it.amountPaise > 0 }.take(7)
                        .map { Slice(it.category.label, it.amount, colorFor(Category.entries.indexOf(it.category)), it.category) }
                    if (slices.isEmpty()) {
                        Text("No spending recorded in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(slices, centerText = "${summary.expenseCount} txns")
                        Spacer(Modifier.width(12.dp))
                        Legend(slices, Modifier.weight(1f)) { cat -> onDrill(Bucket.SPEND, cat) }
                    }
                }
            }

            if (summary.byMerchant.isNotEmpty()) item {
                FinCard(Modifier.padding(horizontal = Gutter), padding = PaddingValues(vertical = 8.dp)) {
                    Text("Top merchants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 18.dp, vertical = 8.dp))
                    summary.byMerchant.take(5).forEachIndexed { i, m ->
                        if (i > 0) Hairline(startInset = 18.dp)
                        Row(
                            Modifier.fillMaxWidth().clickable { onDrill(Bucket.SPEND, m.category) }.padding(horizontal = 18.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text(m.merchant, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                                Text("${m.count} txn${if (m.count > 1) "s" else ""} · ${m.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Text(money(m.amountPaise), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
            }

            if (summary.byAccount.size > 1) item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Text("By account / card", style = MaterialTheme.typography.titleMedium)
                    summary.byAccount.take(6).forEach { a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (a.incomePaise > 0) Text("+${money(a.incomePaise)}  ", color = Income, style = MaterialTheme.typography.bodySmall)
                            Text(money(a.spendPaise), color = Expense, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextButton(onClick = onOpenBudgets) { Text(if (budgetStatus.isEmpty()) "Set budgets" else "Manage") }
                    }
                    budgetStatus.take(4).forEach { b ->
                        Column(Modifier.clickable { onDrill(Bucket.SPEND, b.budget.category) }) {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Text(b.budget.category.label, style = MaterialTheme.typography.bodyMedium)
                                Text("${money(b.spentPaise)} / ${money(b.budget.monthlyLimitPaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { b.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (b.over) Expense else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            if (b.over) Text("Over budget by ${money(b.spentPaise - b.budget.monthlyLimitPaise)}", color = Expense, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                SectionHeader("Recent") {
                    TextButton(onClick = onOpenTransactions) { Text("See all") }
                }
            }
            if (recent.isEmpty()) item {
                Text(
                    "Nothing yet. Tap refresh to scan SMS or + to add a transaction.",
                    Modifier.padding(horizontal = Gutter), style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            itemsIndexed(recent) { i, t ->
                if (i > 0) Hairline(startInset = Gutter + 58.dp)
                TransactionRow(t) { onEdit(t.id) }
            }
        }
    }

    if (showRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis; val e = state.selectedEndDateMillis
                    if (s != null && e != null) vm.setPeriod(PeriodChoice.Custom(s, e))
                    showRange = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } }
        ) { DateRangePicker(state, modifier = Modifier.height(480.dp)) }
    }
}

@Composable
private fun MathRow(label: String, paise: Long, sign: String, color: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("$sign ${money(paise)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, color = color)
    }
}

@Composable
private fun LineRow(label: String, paise: Long, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(money(paise), color = Neutral, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
    }
}
