package com.pft.financetracker.ui.screens.split

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.split.SplitCalculator
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitHomeScreen(vm: AppViewModel, onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val splits by vm.splits.collectAsState()
    val balances = SplitCalculator.balances(splits)
    val owedToMe = balances.filter { it.netPaise > 0 }.sumOf { it.netPaise }
    val iOwe = -balances.filter { it.netPaise < 0 }.sumOf { it.netPaise }

    Scaffold(
        topBar = { TopAppBar(title = { Text("Split bills") }) },
        floatingActionButton = { ExtendedFloatingActionButton(onClick = onNew, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New split") }) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp, 8.dp, 16.dp, 96.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                SoftPanel {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Column {
                            CapsLabel("Owed to you")
                            Text(money(owedToMe), style = MaterialTheme.typography.headlineLarge, color = Income)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            CapsLabel("You owe")
                            Text(money(iOwe), style = MaterialTheme.typography.headlineLarge, color = Expense)
                        }
                    }
                }
            }
            if (balances.isNotEmpty()) {
                item { Text("Balances", style = MaterialTheme.typography.titleMedium) }
                item {
                    FinCard {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            balances.forEach { b ->
                                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                    Text(b.name)
                                    Text(
                                        if (b.netPaise > 0) "owes you ${money(b.netPaise)}" else "you owe ${money(-b.netPaise)}",
                                        color = if (b.netPaise > 0) Income else Expense, fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(4.dp)); Text("Splits", style = MaterialTheme.typography.titleMedium) }
            if (splits.isEmpty()) item {
                Text("No splits yet. Snap a bill or type an amount, add the people, and FinTrack works out who pays what. Only your share counts as your spending.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            items(splits, key = { it.id }) { s ->
                FinCard(onClick = { onOpen(s.id) }, padding = androidx.compose.foundation.layout.PaddingValues(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                "${dateOnly(s.date)} · ${s.people.size} people · ${s.mode.label} · paid by ${s.people.getOrNull(s.payerIndex)?.name ?: "?"}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(s.totalPaise), fontWeight = FontWeight.SemiBold)
                            Text(if (s.settled) "settled" else "${money(s.outstandingPaise)} open", style = MaterialTheme.typography.labelSmall, color = if (s.settled) Income else MaterialTheme.colorScheme.tertiary)
                        }
                    }
                }
            }
        }
    }
}
