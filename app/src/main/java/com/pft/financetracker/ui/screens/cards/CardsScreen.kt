package com.pft.financetracker.ui.screens.cards

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.SecondaryButton
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.cards.Card
import com.pft.financetracker.domain.cards.CardSummary
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AddFab
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/** Each credit card's current billing cycle: what has gone on it, when the statement comes, when it is due. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val summaries by vm.cardSummaries.collectAsState()
    var editing by remember { mutableStateOf<Card?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Credit cards") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { AddFab({ editing = Card(last4 = "", name = "", statementDay = 1, dueDay = 20) }, label = "Add card") },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = Space.sm, end = Gutter, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            item {
                Text("Spend is counted per billing cycle, from card SMS with the card's last four digits. Refunds to the card are taken off.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (summaries.isEmpty()) item {
                EmptyState(Icons.Outlined.CreditCard, "No cards yet. Add one to see its spend for each billing cycle and when to pay.") {
                    SecondaryButton("Add a card", { editing = Card(last4 = "", name = "", statementDay = 1, dueDay = 20) })
                }
            }
            items(summaries, key = { it.card.id }) { s -> CardTile(s) { editing = s.card } }
        }
    }

    editing?.let { c -> CardEditor(vm, c, onDismiss = { editing = null }) }
}

@Composable
private fun CardTile(s: CardSummary, onClick: () -> Unit) {
    FinCard(onClick = onClick) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(s.card.name, style = MaterialTheme.typography.titleMedium)
                Text("••${s.card.last4}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(money(s.spendPaise), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("this cycle", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        val total = ChronoUnit.DAYS.between(s.cycle.start, s.cycle.end).toFloat() + 1
        val gone = (total - ChronoUnit.DAYS.between(java.time.LocalDate.now(), s.cycle.end).toFloat()).coerceIn(0f, total)
        // How far through the billing cycle, not how much is spent: say so, or the bar reads as spend.
        LinearProgressIndicator(progress = { gone / total }, modifier = Modifier.fillMaxWidth().height(6.dp), gapSize = 0.dp, drawStopIndicator = {})
        Text("Day ${gone.toInt().coerceAtLeast(1)} of ${total.toInt()} in this cycle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("${s.cycle.start.format(dayFmt)} – ${s.cycle.end.format(dayFmt)} · statement ${s.cycle.end.format(dayFmt)}", style = MaterialTheme.typography.bodySmall)
        Text(
            "Pay by ${s.cycle.due.format(dayFmt)} (in ${s.daysToDue} days)" + if (s.card.rewardBp > 0) " · about ${com.pft.financetracker.ui.components.approxMoney(s.rewardPaise)} back" else "",
            style = MaterialTheme.typography.bodySmall, color = if (s.daysToDue <= 3) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun CardEditor(vm: AppViewModel, start: Card, onDismiss: () -> Unit) {
    var last4 by remember { mutableStateOf(start.last4) }
    var name by remember { mutableStateOf(start.name) }
    var stmt by remember { mutableStateOf(start.statementDay.toString()) }
    var due by remember { mutableStateOf(start.dueDay.toString()) }
    var reward by remember { mutableStateOf(if (start.rewardBp > 0) "%.2f".format(Locale.ENGLISH, start.rewardBp / 100.0) else "") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(Unit) { if (start.id == 0L) suggestions = vm.cardSuggestions() }

    val s = stmt.toIntOrNull()?.takeIf { it in 1..31 }
    val d = due.toIntOrNull()?.takeIf { it in 1..31 }
    val r = if (reward.isBlank()) 0.0 else reward.toDoubleOrNull()
    val valid = last4.length == 4 && last4.all { it.isDigit() } && name.isNotBlank() && s != null && d != null && r != null && r in 0.0..20.0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (start.id == 0L) "Add a card" else "Edit card") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (suggestions.isNotEmpty()) {
                    CapsLabel("Seen in your SMS")
                    ChipRow(inset = 0.dp) { suggestions.take(6).forEach { n -> PillChip(last4 == n, "••$n") { last4 = n } } }
                }
                OutlinedTextField(last4, { if (it.length <= 4) last4 = it }, label = { Text("Last 4 digits") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(name, { name = it }, label = { Text("Name, e.g. HDFC Millennia") }, singleLine = true)
                OutlinedTextField(stmt, { stmt = it }, label = { Text("Statement day (1-31)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(due, { due = it }, label = { Text("Payment due day (1-31)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
                OutlinedTextField(reward, { reward = it }, label = { Text("Reward rate % (optional)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = {
            TextButton(enabled = valid, onClick = {
                vm.saveCard(start.copy(last4 = last4, name = name.trim(), statementDay = s!!, dueDay = d!!, rewardBp = Math.round(r!! * 100).toInt()))
                onDismiss()
            }) { Text("Save") }
        },
        dismissButton = {
            Row {
                if (start.id != 0L) TextButton(onClick = { vm.deleteCard(start.id); onDismiss() }) { Text("Delete", color = Expense) }
                TextButton(onClick = onDismiss) { Text("Cancel") }
            }
        },
    )
}
