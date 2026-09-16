package com.pft.financetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Badge
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.dateOnly

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: AppViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit, onOpenReview: () -> Unit) {
    val txns by vm.transactions.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Category?>(null) }

    val filtered = txns.filter { t ->
        (filter == null || t.category == filter) &&
            (query.isBlank() || t.merchant.contains(query, true) || (t.bankName ?: "").contains(query, true) || t.amount.toString().contains(query))
    }
    val grouped = filtered.groupBy { dateOnly(it.timestamp) }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Activity") }, actions = {
                if (reviewCount > 0) TextButton(onClick = onOpenReview) {
                    Badge { Text(reviewCount.toString()) }
                    Text("  Review")
                }
            })
        },
        floatingActionButton = { FloatingActionButton(onClick = onAdd) { Icon(Icons.Filled.Add, "Add") } }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                placeholder = { Text("Search merchant, bank, amount") }, singleLine = true
            )
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                FilterChip(selected = filter == null, onClick = { filter = null }, label = { Text("All") })
                Category.entries.forEach { c ->
                    FilterChip(selected = filter == c, onClick = { filter = if (filter == c) null else c }, label = { Text(c.label) })
                }
            }
            if (filtered.isEmpty()) {
                Text("No transactions.", Modifier.padding(16.dp), style = MaterialTheme.typography.bodyMedium)
            }
            LazyColumn(contentPadding = PaddingValues(bottom = 88.dp)) {
                grouped.forEach { (day, list) ->
                    item(key = "h_$day") {
                        Text(day, Modifier.padding(horizontal = 16.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                        HorizontalDivider()
                    }
                    items(list, key = { it.id }) { t -> TransactionRow(t) { onEdit(t.id) } }
                }
            }
        }
    }
}
