package com.pft.financetracker.ui.screens.drilldown

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.money

/** The list behind one number on the dashboard, with its total, so every figure can be checked by hand. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrillDownScreen(vm: AppViewModel, bucket: InsightsEngine.Bucket, category: Category?, onBack: () -> Unit, onEdit: (Long) -> Unit) {
    val txns by vm.transactions.collectAsState()
    val choice by vm.period.collectAsState()
    val period = choice.period()
    val list = InsightsEngine.drillDown(txns, period, bucket, category)
    val total = list.sumOf { it.amountPaise }
    val title = (category?.label ?: bucket.name.lowercase().replaceFirstChar { it.uppercase() }) + " · " + period.label

    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(bottom = 24.dp)) {
            item {
                Card(Modifier.fillMaxWidth().padding(16.dp)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("${list.size} transactions", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(Modifier.fillMaxWidth()) {
                            Text(money(total, decimals = true), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
                        }
                        Text("Sum of the amounts below. Tap any row to correct it; totals update immediately.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
            if (list.isEmpty()) item { Text("Nothing here for this period.", Modifier.padding(16.dp)) }
            items(list, key = { it.id }) { t -> TransactionRow(t) { onEdit(t.id) } }
        }
    }
}
