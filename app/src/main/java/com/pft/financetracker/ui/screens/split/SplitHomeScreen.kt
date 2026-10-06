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
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.text.style.TextAlign
import com.pft.financetracker.ui.components.LoadingState
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.theme.moneyTone
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
    val loaded by vm.loaded.collectAsState()
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
            // Until the database answers, the balances would read ₹0: show a spinner instead.
            if (!loaded) {
                item { LoadingState() }
                return@LazyColumn
            }
            item {
                SoftPanel {
                    // Two equal halves: at a large font the labels wrap inside their half instead of running together.
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.lg)) {
                        Column(Modifier.weight(1f)) {
                            CapsLabel("Owed to you")
                            Text(money(owedToMe), style = MaterialTheme.typography.headlineLarge, color = moneyTone(owedToMe))
                        }
                        Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                            CapsLabel("You owe")
                            Text(money(iOwe), style = MaterialTheme.typography.headlineLarge, color = moneyTone(-iOwe))
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
                                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                    // Both sides share the width, so at a large font neither squeezes the other to a sliver.
                                    Text(b.name, Modifier.weight(1f))
                                    Spacer(Modifier.width(Space.md))
                                    Text(
                                        if (b.netPaise > 0) "Owes you ${money(b.netPaise)}" else "You owe ${money(-b.netPaise)}",
                                        Modifier.weight(1f, fill = false),
                                        color = moneyTone(b.netPaise), fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End,
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
                ) { PrimaryButton("New split", onNew, fill = false) }
            } else item { Text("Splits", style = MaterialTheme.typography.titleMedium) }
            items(splits, key = { it.id }) { s ->
                FinCard(onClick = { onOpen(s.id) }, padding = PaddingValues(horizontal = 20.dp, vertical = 16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconCircle(Icons.AutoMirrored.Outlined.ReceiptLong, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(16.dp))
                        Column(Modifier.weight(1f)) {
                            Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                            Text(
                                listOfNotNull(
                                    when { s.isSuggestion -> "Suggested"; s.isAuto -> "Auto-split"; else -> null },
                                    dateOnly(s.date), countLabel(s.people.size, "person", "people"),
                                    if (s.isAuto) "your share ${money(s.myShare?.amountPaise ?: 0)}" else "${s.mode.label} · paid by ${s.people.getOrNull(s.payerIndex)?.name ?: "?"}",
                                ).joinToString(" · "),
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(money(s.totalPaise), fontWeight = FontWeight.SemiBold)
                            Text(
                                when { s.isSuggestion -> "Tap to review"; s.settled -> "Settled"; else -> "${money(s.outstandingPaise)} open" },
                                style = MaterialTheme.typography.labelSmall, color = if (s.settled && !s.isSuggestion) Income else MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
        }
    }
}
