package com.pft.financetracker.ui.screens.insights

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.Insight
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.BarChart
import com.pft.financetracker.ui.components.money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(vm: AppViewModel) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    var weekly by remember { mutableStateOf(false) }

    val periods = if (weekly) (5 downTo 0).map { Periods.week(-it) } else (5 downTo 0).map { Periods.month(-it) }
    val series = periods.map { p -> p.label to InsightsEngine.summarize(txns, p).spend }
    val trends = if (weekly) InsightsEngine.categoryTrends(txns, Periods.week(), Periods.week(-1)) else InsightsEngine.categoryTrends(txns, Periods.month(), Periods.month(-1))
    val suggestions = InsightsEngine.suggestions(txns, budgets)

    Scaffold(topBar = { TopAppBar(title = { Text("Insights") }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = !weekly, onClick = { weekly = false }, label = { Text("Monthly") })
                    FilterChip(selected = weekly, onClick = { weekly = true }, label = { Text("Weekly") })
                }
            }
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text("Spending trend", style = MaterialTheme.typography.titleMedium)
                        Spacer(Modifier.height(12.dp))
                        BarChart(series)
                        val cur = series.last().second
                        val prev = series[series.lastIndex - 1].second
                        Spacer(Modifier.height(8.dp))
                        Text(
                            when {
                                prev == 0.0 -> "Current: ${money(cur)}"
                                cur > prev -> "Up ${((cur - prev) / prev * 100).toInt()}% vs previous (${money(prev)})"
                                else -> "Down ${((prev - cur) / prev * 100).toInt()}% vs previous (${money(prev)})"
                            }, style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
            }
            item { Text("Category changes", style = MaterialTheme.typography.titleMedium) }
            if (trends.isEmpty()) item { Text("Not enough data for comparisons yet.", style = MaterialTheme.typography.bodyMedium) }
            items(trends.size) { i -> InsightCard(trends[i]) }

            item { Spacer(Modifier.height(8.dp)); Text("Reduce spending", style = MaterialTheme.typography.titleMedium) }
            if (suggestions.isEmpty()) item { Text("Suggestions appear once there are a few weeks of transactions.", style = MaterialTheme.typography.bodyMedium) }
            items(suggestions.size) { i -> InsightCard(suggestions[i]) }
        }
    }
}

@Composable
fun InsightCard(i: Insight) {
    val container = when (i.severity) {
        Insight.Severity.WARN -> MaterialTheme.colorScheme.errorContainer
        Insight.Severity.GOOD -> MaterialTheme.colorScheme.primaryContainer
        Insight.Severity.INFO -> MaterialTheme.colorScheme.surfaceVariant
    }
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Column(Modifier.padding(14.dp)) {
            Text(i.title, style = MaterialTheme.typography.titleSmall)
            Text(i.body, style = MaterialTheme.typography.bodySmall)
        }
    }
}
