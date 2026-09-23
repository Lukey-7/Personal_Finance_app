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
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.IconCircle
import com.pft.financetracker.ui.components.RowIconGap
import com.pft.financetracker.ui.components.RowIconSize
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.colorFor
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
import androidx.compose.material3.TopAppBarDefaults
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val summary = InsightsEngine.summarize(txns, Periods.month())
    var editing by remember { mutableStateOf<Category?>(null) }
    var input by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Budgets · ${summary.period.label}") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = 8.dp, end = Gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item { Text("Tap a category to set a monthly limit. Alerts show when you cross it.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            // One card holding a row per category, as in the reference's lists, rather than a stack of cards.
            item {
                FinCard(padding = PaddingValues(vertical = 8.dp)) {
                    Column {
                        Category.spendCategories.forEachIndexed { i, cat ->
                            if (i > 0) Hairline(startInset = 20.dp + RowIconSize + RowIconGap, endInset = 20.dp)
                            BudgetRow(
                                cat,
                                limit = budgets.firstOrNull { it.category == cat }?.monthlyLimitPaise,
                                spent = summary.byCategory.firstOrNull { it.category == cat }?.amountPaise ?: 0L,
                            ) { limit -> editing = cat; input = limit?.let { (it / 100).toString() } ?: "" }
                        }
                    }
                }
            }
        }
    }

    BudgetDialog(editing, input, { input = it }, onSave = { cat -> vm.setBudget(cat, (input.toLongOrNull() ?: 0L) * 100); editing = null }, onDismiss = { editing = null })
}

@Composable
private fun BudgetRow(cat: Category, limit: Long?, spent: Long, onClick: (Long?) -> Unit) {
    val frac = if (limit != null && limit > 0) (spent.toDouble() / limit).toFloat() else 0f
    val over = limit != null && spent > limit
    Row(
        Modifier.fillMaxWidth().clickable { onClick(limit) }.padding(horizontal = 20.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconCircle(categoryIcon(cat), tint = colorFor(Category.entries.indexOf(cat)))
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.label, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    if (limit != null) "${money(spent)} of ${money(limit)}" else money(spent),
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                )
            }
            if (limit != null) {
                LinearProgressIndicator(
                    progress = { frac.coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(6.dp),
                    color = if (over) Expense else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant,
                    gapSize = 0.dp,
                    drawStopIndicator = {},
                )
                Text(
                    if (over) "Over by ${money(spent - limit)}" else "${money(limit - spent)} left · ${(frac * 100).roundToInt()}% used",
                    style = MaterialTheme.typography.labelSmall,
                    color = if (over) Expense else MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Text("No limit · tap to set one", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun BudgetDialog(editing: Category?, input: String, onInput: (String) -> Unit, onSave: (Category) -> Unit, onDismiss: () -> Unit) {
    editing?.let { cat ->
        AlertDialog(
            onDismissRequest = onDismiss,
            icon = { Icon(categoryIcon(cat), null) },
            title = { Text("${cat.label} budget") },
            text = {
                OutlinedTextField(
                    input, { onInput(it.filter { ch -> ch.isDigit() }) },
                    label = { Text("Monthly limit (₹), 0 to remove") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true
                )
            },
            confirmButton = { TextButton(onClick = { onSave(cat) }) { Text("Save") } },
            dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
        )
    }
}
