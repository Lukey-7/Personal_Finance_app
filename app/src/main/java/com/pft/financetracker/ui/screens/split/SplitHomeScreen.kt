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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material3.TopAppBarDefaults
import com.pft.financetracker.ui.components.AddFabExtended
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.IconCircle
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.PrimaryPill
import com.pft.financetracker.ui.components.bottomPadding
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

    val barPad = LocalBottomBarPadding.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Split bills") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        // With no splits yet the empty state carries the button, so a floating one would only repeat it.
        floatingActionButton = { if (splits.isNotEmpty()) AddFabExtended("New split", onNew, Modifier.padding(bottom = barPad)) }
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = 8.dp, end = Gutter, bottom = bottomPadding(FabClearance)), verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
            if (splits.isEmpty()) item {
                EmptyState(
                    Icons.AutoMirrored.Outlined.CallSplit,
                    "No splits yet. Snap a bill or type an amount, add the people, and FinTrack works out who pays what. Only your share counts as your spending.",
                ) { PrimaryPill("New split", onNew) }
            } else item { Text("Splits", style = MaterialTheme.typography.titleMedium) }
            items(splits, key = { it.id }) { s ->
                FinCard(onClick = { onOpen(s.id) }, padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.AutoMirrored.Outlined.ReceiptLong, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
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
