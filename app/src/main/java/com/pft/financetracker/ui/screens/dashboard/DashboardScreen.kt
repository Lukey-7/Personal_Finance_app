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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
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
import com.pft.financetracker.ui.components.DonutChart
import com.pft.financetracker.ui.components.Legend
import com.pft.financetracker.ui.components.Slice
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
        topBar = {
            TopAppBar(
                title = { Text("FinTrack") },
                actions = {
                    if (importState is ImportUiState.Running) CircularProgressIndicator(Modifier.padding(12.dp).width(24.dp).height(24.dp))
                    else IconButton(onClick = { if (vm.hasSmsPermission()) vm.scanInbox() }) { Icon(Icons.Filled.Refresh, "Scan SMS") }
                }
            )
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add") } },
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 88.dp)) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(selected = choice is PeriodChoice.ThisMonth, onClick = { vm.setPeriod(PeriodChoice.ThisMonth) }, label = { Text("This month") })
                    FilterChip(selected = choice is PeriodChoice.LastMonth, onClick = { vm.setPeriod(PeriodChoice.LastMonth) }, label = { Text("Last month") })
                    FilterChip(selected = choice is PeriodChoice.ThisWeek, onClick = { vm.setPeriod(PeriodChoice.ThisWeek) }, label = { Text("This week") })
                    FilterChip(selected = choice is PeriodChoice.Custom, onClick = { showRange = true }, label = { Text(if (choice is PeriodChoice.Custom) period.label else "Custom…") })
                }
            }

            // ---- The headline: net spend, with the arithmetic laid out so it can be checked ----
            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary)) {
                    val onP = MaterialTheme.colorScheme.onPrimary
                    Column(Modifier.padding(20.dp)) {
                        Text("Net spend · ${period.label}", style = MaterialTheme.typography.labelLarge, color = onP.copy(alpha = 0.8f))
                        Text(money(summary.netSpendPaise), style = MaterialTheme.typography.displaySmall, color = onP, modifier = Modifier.clickable { onDrill(Bucket.SPEND, null) })
                        val diff = if (prev.netSpendPaise > 0) ((summary.netSpendPaise - prev.netSpendPaise).toDouble() / prev.netSpendPaise * 100).roundToInt() else null
                        val proj = summary.projectedPaise()
                        Text(
                            listOfNotNull(
                                diff?.let { if (it >= 0) "$it% more than previous" else "${-it}% less than previous" },
                                "≈${money(summary.dailyAveragePaise())}/day",
                                proj?.let { "on track for ${money(it)}" },
                            ).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall, color = onP.copy(alpha = 0.85f)
                        )
                        Spacer(Modifier.height(16.dp))
                        HorizontalDivider(color = onP.copy(alpha = 0.2f))
                        Spacer(Modifier.height(12.dp))
                        MathRow("Income", summary.incomePaise, onP, "+") { onDrill(Bucket.INCOME, null) }
                        MathRow("Gross spend", summary.grossSpendPaise, onP, "−") { onDrill(Bucket.SPEND, null) }
                        if (summary.refundsPaise > 0) MathRow("Refunds & cashback", summary.refundsPaise, onP, "+") { onDrill(Bucket.REFUNDS, null) }
                        Spacer(Modifier.height(6.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Savings", style = MaterialTheme.typography.titleMedium, color = onP)
                            Text(money(summary.savingsPaise), style = MaterialTheme.typography.titleMedium, color = onP, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // ---- Money that moved but is not spend ----
            if (summary.transfersOutPaise + summary.transfersInPaise + summary.investmentsPaise + (if (includeCash) 0L else summary.cashPaise) > 0) item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Not counted as spend", style = MaterialTheme.typography.titleMedium)
                        Text("Moving money between your own accounts, paying card bills and investing are shown here so they never inflate your spending.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        if (summary.transfersOutPaise > 0) LineRow("Transfers & card bill payments", summary.transfersOutPaise, Neutral) { onDrill(Bucket.TRANSFERS, null) }
                        if (summary.transfersInPaise > 0) LineRow("Transfers in", summary.transfersInPaise, Neutral) { onDrill(Bucket.TRANSFERS, null) }
                        if (summary.investmentsPaise > 0) LineRow("Investments", summary.investmentsPaise, Neutral) { onDrill(Bucket.INVESTMENTS, null) }
                        if (!includeCash && summary.cashPaise > 0) LineRow("Cash withdrawals", summary.cashPaise, Neutral) { onDrill(Bucket.CASH, null) }
                    }
                }
            } else item { Spacer(Modifier.height(16.dp)) }

            if (reviewCount > 0) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$reviewCount SMS need manual review", Modifier.weight(1f))
                        TextButton(onClick = onOpenReview) { Text("Review") }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Where it went", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        val slices = summary.byCategory.filter { it.amountPaise > 0 }.take(7).map { Slice(it.category.label, it.amount, colorFor(Category.entries.indexOf(it.category)), it.category) }
                        if (slices.isEmpty()) {
                            Text("No spending recorded in this period.", style = MaterialTheme.typography.bodyMedium)
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(slices, centerText = "${summary.expenseCount} txns")
                            Spacer(Modifier.width(12.dp))
                            Legend(slices, Modifier.weight(1f)) { cat -> onDrill(Bucket.SPEND, cat) }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (summary.byMerchant.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Top merchants", style = MaterialTheme.typography.titleMedium)
                        summary.byMerchant.take(5).forEach { m ->
                            Row(Modifier.fillMaxWidth().clickable { onDrill(Bucket.SPEND, m.category) }, verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text(m.merchant, style = MaterialTheme.typography.bodyMedium)
                                    Text("${m.count} txn${if (m.count > 1) "s" else ""} · ${m.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(money(m.amountPaise), fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (summary.byAccount.size > 1) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("By account / card", style = MaterialTheme.typography.titleMedium)
                        summary.byAccount.take(6).forEach { a ->
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                if (a.incomePaise > 0) Text("+${money(a.incomePaise)}  ", color = Income, style = MaterialTheme.typography.bodySmall)
                                Text(money(a.spendPaise), color = Expense, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                            TextButton(onClick = onOpenBudgets) { Text(if (budgetStatus.isEmpty()) "Set budgets" else "Manage") }
                        }
                        budgetStatus.take(4).forEach { b ->
                            Column(Modifier.clickable { onDrill(Bucket.SPEND, b.budget.category) }) {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(b.budget.category.label, style = MaterialTheme.typography.bodyMedium)
                                    Text("${money(b.spentPaise)} / ${money(b.budget.monthlyLimitPaise)}", style = MaterialTheme.typography.bodySmall)
                                }
                                LinearProgressIndicator(
                                    progress = { b.fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (b.over) Expense else MaterialTheme.colorScheme.secondary,
                                )
                                if (b.over) Text("Over budget by ${money(b.spentPaise - b.budget.monthlyLimitPaise)}", color = Expense, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenTransactions) { Text("See all") }
                }
            }
            if (recent.isEmpty()) item {
                Text("Nothing yet. Tap refresh to scan SMS or + to add a transaction.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            items(recent, key = { it.id }) { t -> TransactionRow(t) { onEdit(t.id) } }
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
private fun MathRow(label: String, paise: Long, color: Color, sign: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium, color = color.copy(alpha = 0.9f))
        Text("$sign ${money(paise)}", style = MaterialTheme.typography.bodyMedium, color = color)
    }
}

@Composable
private fun LineRow(label: String, paise: Long, color: Color, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().clickable(onClick = onClick), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(money(paise), color = color, fontWeight = FontWeight.SemiBold)
    }
}
