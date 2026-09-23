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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.SectionHeader
import com.pft.financetracker.ui.components.bottomPadding
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.material3.TopAppBarDefaults
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PillChip

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(vm: AppViewModel, onOpenBudgets: () -> Unit) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    var weekly by remember { mutableStateOf(false) }

    val periods = if (weekly) (5 downTo 0).map { Periods.week(-it) } else (5 downTo 0).map { Periods.month(-it) }
    val series = periods.mapIndexed { i, p -> shortLabel(p, weekly, i, periods) to InsightsEngine.summarize(txns, p).spend }
    val trends = if (weekly) InsightsEngine.categoryTrends(txns, Periods.week(), Periods.week(-1)) else InsightsEngine.categoryTrends(txns, Periods.month(), Periods.month(-1))
    val suggestions = InsightsEngine.suggestions(txns, budgets)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = { IconButton(onClick = onOpenBudgets) { Icon(Icons.Outlined.Savings, "Budgets") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding()), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                ChipRow {
                    PillChip(!weekly, "Monthly") { weekly = false }
                    PillChip(weekly, "Weekly") { weekly = true }
                }
            }
            item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Column {
                        Text("Net spending trend", style = MaterialTheme.typography.titleMedium)
                        Text("Expenses minus refunds. Transfers and investments excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(12.dp))
                        BarChart(series)
                        val cur = series.last().second
                        val prev = series[series.lastIndex - 1].second
                        // With nothing to compare against, the bar's own label already states the value.
                        if (prev > 0.0) {
                            Spacer(Modifier.height(12.dp))
                            Text(
                                if (cur > prev) "Up ${((cur - prev) / prev * 100).toInt()}% vs previous (${money(prev)})"
                                else "Down ${((prev - cur) / prev * 100).toInt()}% vs previous (${money(prev)})",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                }
            }
            item { SectionHeader("Category changes", Modifier.padding(top = 16.dp)) }
            if (trends.isEmpty()) item { Hint("Not enough data for comparisons yet.") }
            items(trends.size) { i -> InsightCard(trends[i]) }

            item { SectionHeader("Reduce spending", Modifier.padding(top = 16.dp)) }
            if (suggestions.isEmpty()) item { Hint("Suggestions appear once there are a few weeks of transactions.") }
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
    val (icon, tint) = when (i.severity) {
        Insight.Severity.WARN -> Icons.Outlined.TrendingUp to MaterialTheme.colorScheme.error
        Insight.Severity.GOOD -> Icons.Outlined.TrendingDown to MaterialTheme.colorScheme.primary
        Insight.Severity.INFO -> Icons.Outlined.Lightbulb to MaterialTheme.colorScheme.onSurfaceVariant
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = Gutter),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = container),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Row(Modifier.padding(horizontal = 20.dp, vertical = 18.dp)) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Spacer(Modifier.width(14.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(i.title, style = MaterialTheme.typography.titleMedium)
                Text(i.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = Gutter), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Each bar label gets about 50dp. Months read "Apr"; the year is added ("Jan '26") only on a bar where it
 * changes from the bar before. Weeks read as their first day ("14 Sep").
 */
private fun shortLabel(p: Period, weekly: Boolean, i: Int, all: List<Period>): String {
    fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(p.start))
    if (weekly) return fmt("d MMM")
    val year = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) }
    val yearChanged = i > 0 && year(all[i - 1].start) != year(p.start)
    return if (yearChanged) fmt("MMM ''yy") else fmt("MMM")
}
