package com.pft.financetracker.ui.screens.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.ImportUiState
import com.pft.financetracker.ui.components.DonutChart
import com.pft.financetracker.ui.components.Legend
import com.pft.financetracker.ui.components.Slice
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenInsights: () -> Unit,
) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val importState by vm.importState.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    val month = Periods.month()
    val summary = InsightsEngine.summarize(txns, month)
    val prev = InsightsEngine.summarize(txns, Periods.month(-1))
    val budgetStatus = InsightsEngine.budgetStatus(txns, budgets, month)
    val recent = txns.take(8)

    LaunchedEffect(importState) {
        val s = importState
        if (s is ImportUiState.Done) {
            snackbar.showSnackbar("Scanned ${s.stats.scanned} SMS: ${s.stats.inserted} added, ${s.stats.queuedForReview} to review, ${s.stats.duplicates} already known")
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
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 88.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Spent in ${month.label}", style = MaterialTheme.typography.labelLarge)
                        Text(money(summary.spend), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.Bold)
                        val diff = if (prev.spend > 0) ((summary.spend - prev.spend) / prev.spend * 100).roundToInt() else null
                        if (diff != null) {
                            Text(
                                if (diff >= 0) "$diff% more than last month" else "${-diff}% less than last month",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                        Spacer(Modifier.height(12.dp))
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Column {
                                Text("Income", style = MaterialTheme.typography.labelMedium)
                                Text(money(summary.income), color = Income, fontWeight = FontWeight.SemiBold)
                            }
                            Column {
                                Text("Expense", style = MaterialTheme.typography.labelMedium)
                                Text(money(summary.spend), color = Expense, fontWeight = FontWeight.SemiBold)
                            }
                            Column(horizontalAlignment = Alignment.End) {
                                Text("Net", style = MaterialTheme.typography.labelMedium)
                                val net = summary.income - summary.spend
                                Text(money(net), color = if (net >= 0) Income else Expense, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }

            if (reviewCount > 0) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("$reviewCount SMS need manual review", Modifier.weight(1f))
                        TextButton(onClick = onOpenReview) { Text("Review") }
                    }
                }
                Spacer(Modifier.height(8.dp))
            }

            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Spend by category", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        val slices = summary.byCategory.take(7).map { Slice(it.category.label, it.amount, colorFor(Category.entries.indexOf(it.category))) }
                        if (slices.isEmpty()) {
                            Text("No spending recorded this month yet.", style = MaterialTheme.typography.bodyMedium)
                        } else Row(verticalAlignment = Alignment.CenterVertically) {
                            DonutChart(slices, centerText = "${summary.count} txns")
                            Spacer(Modifier.width(12.dp))
                            Legend(slices, Modifier.weight(1f))
                        }
                    }
                }
            }

            if (budgetStatus.isNotEmpty()) item {
                Card(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Budgets", style = MaterialTheme.typography.titleMedium)
                        budgetStatus.take(4).forEach { b ->
                            Column {
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(b.budget.category.label, style = MaterialTheme.typography.bodyMedium)
                                    Text("${money(b.spent)} / ${money(b.budget.monthlyLimit)}", style = MaterialTheme.typography.bodySmall)
                                }
                                LinearProgressIndicator(
                                    progress = { b.fraction.coerceIn(0f, 1f) },
                                    modifier = Modifier.fillMaxWidth(),
                                    color = if (b.over) Expense else MaterialTheme.colorScheme.primary,
                                )
                                if (b.over) Text("Over budget by ${money(b.spent - b.budget.monthlyLimit)}", color = Expense, style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            item {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    TextButton(onClick = onOpenInsights) { Text("Insights") }
                    TextButton(onClick = onOpenTransactions) { Text("See all") }
                }
            }
            if (recent.isEmpty()) item {
                Text(
                    "Nothing yet. Tap refresh to scan SMS or + to add a transaction.",
                    Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium
                )
            }
            items(recent, key = { it.id }) { t -> TransactionRow(t) { onEdit(t.id) } }
        }
    }
}
