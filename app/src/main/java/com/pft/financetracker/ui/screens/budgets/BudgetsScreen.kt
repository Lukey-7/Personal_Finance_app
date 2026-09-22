package com.pft.financetracker.ui.screens.budgets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.foundation.clickable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val summary = InsightsEngine.summarize(txns, Periods.month())
    var editing by remember { mutableStateOf<Category?>(null) }
    var input by remember { mutableStateOf("") }

    Scaffold(topBar = { TopAppBar(title = { Text("Budgets · ${summary.period.label}") }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } }) }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { Text("Tap a category to set a monthly limit. Alerts show when you cross it.", style = MaterialTheme.typography.bodyMedium) }
            items(Category.spendCategories) { cat ->
                val limit = budgets.firstOrNull { it.category == cat }?.monthlyLimitPaise
                val spent = summary.byCategory.firstOrNull { it.category == cat }?.amountPaise ?: 0L
                val frac = if (limit != null && limit > 0) (spent.toDouble() / limit).toFloat() else 0f
                val over = limit != null && spent > limit
                Card(Modifier.fillMaxWidth().clickable { editing = cat; input = limit?.let { (it / 100).toString() } ?: "" }) {
                    Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(cat.label, style = MaterialTheme.typography.titleSmall)
                            Text(if (limit != null) "${money(spent)} / ${money(limit)}" else "${money(spent)} · no limit", style = MaterialTheme.typography.bodySmall)
                        }
                        if (limit != null) {
                            LinearProgressIndicator(progress = { frac.coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth(), color = if (over) Expense else MaterialTheme.colorScheme.primary)
                            Text(
                                if (over) "Over by ${money(spent - limit)}" else "${money(limit - spent)} left · ${(frac * 100).roundToInt()}% used",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (over) Expense else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }

    editing?.let { cat ->
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("${cat.label} budget") },
            text = {
                OutlinedTextField(
                    input, { input = it.filter { ch -> ch.isDigit() } },
                    label = { Text("Monthly limit (₹), 0 to remove") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                )
            },
            confirmButton = { TextButton(onClick = { vm.setBudget(cat, (input.toLongOrNull() ?: 0L) * 100); editing = null }) { Text("Save") } },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } }
        )
    }
}
