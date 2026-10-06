package com.pft.financetracker.ui.screens.goals

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flag
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.DateField
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.TextAction
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.goals.Goal
import com.pft.financetracker.domain.goals.GoalMath
import com.pft.financetracker.domain.goals.GoalProgress
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AddFab
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

/** Savings goals: how far along each one is and what it needs a month to arrive on time. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val goals by vm.goalProgress.collectAsState()
    val lastMonthSavings by vm.lastMonthSavingsPaise.collectAsState()
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf<GoalProgress?>(null) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Goals") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { AddFab({ editing = Goal(name = "", targetPaise = 0, targetDate = LocalDate.now().plusMonths(12)) }, label = "Add goal") },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = Space.sm, end = Gutter, bottom = 120.dp), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            if (goals.isEmpty()) item {
                EmptyState(Icons.Outlined.Flag, "No goals yet. Save toward a trip, a phone or an emergency fund.") {
                    SecondaryButton("Add a goal", { editing = Goal(name = "", targetPaise = 0, targetDate = LocalDate.now().plusMonths(12)) })
                }
            }
            items(goals, key = { it.goal.id }) { p ->
                FinCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(p.goal.name, style = MaterialTheme.typography.titleMedium)
                            Text(p.goal.targetDate?.let { "By ${it.format(dateFmt)}" } ?: "No deadline", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("${p.percent}%", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    }
                    LinearProgressIndicator(progress = { p.percent / 100f }, modifier = Modifier.fillMaxWidth().height(6.dp), gapSize = 0.dp, drawStopIndicator = {})
                    Text("${money(p.savedPaise)} of ${money(p.goal.targetPaise)}", style = MaterialTheme.typography.bodyMedium)
                    val line = when {
                        p.done -> "Reached. Well done."
                        p.late -> "The date has passed: ${money(p.remainingPaise)} still to go."
                        p.monthlyNeededPaise != null -> "Put ${com.pft.financetracker.ui.components.approxMoney(p.monthlyNeededPaise)} a month aside for ${p.monthsLeft} month${if (p.monthsLeft == 1) "" else "s"}" + if (p.onTrack) " · on track" else " · behind"
                        else -> "${money(p.remainingPaise)} to go"
                    }
                    Text(line, style = MaterialTheme.typography.bodySmall, color = if (p.late || (!p.onTrack && !p.done)) Expense else if (p.done) Income else MaterialTheme.colorScheme.onSurfaceVariant)
                    Row {
                        if (!p.done) TextAction("Add money", { adding = p }, alignStart = true)
                        TextAction("Edit", { editing = p.goal }, alignStart = p.done)
                    }
                }
            }
        }
    }

    adding?.let { p ->
        var amount by remember { mutableStateOf(GoalMath.suggestedTopUp(lastMonthSavings, p.remainingPaise).takeIf { it > 0 }?.let { (it / 100).toString() } ?: "") }
        AlertDialog(
            onDismissRequest = { adding = null },
            title = { Text("Add to ${p.goal.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    if (lastMonthSavings > 0) Text("Last month you saved ${money(lastMonthSavings)}.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(amount, { amount = it }, label = { Text("Amount (₹), minus to take out") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text))
                }
            },
            confirmButton = {
                val v = amount.trim().let { s -> if (s.startsWith("-")) Money.parsePaise(s.drop(1))?.let { -it } else Money.parsePaise(s) }
                TextButton(enabled = v != null && v != 0L, onClick = { vm.contributeToGoal(p.goal.id, v!!); adding = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { adding = null }) { Text("Cancel") } },
        )
    }

    editing?.let { g ->
        var name by remember { mutableStateOf(g.name) }
        var target by remember { mutableStateOf(if (g.targetPaise > 0) (g.targetPaise / 100).toString() else "") }
        var date by remember { mutableStateOf(g.targetDate?.toString() ?: "") }
        val t = Money.parsePaise(target)?.takeIf { it > 0 }
        val d = if (date.isBlank()) null else runCatching { LocalDate.parse(date.trim()) }.getOrNull()
        val valid = name.isNotBlank() && t != null && (date.isBlank() || d != null)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (g.id == 0L) "New goal" else "Edit goal") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(name, { name = it }, label = { Text("Goal name") }, placeholder = { Text("e.g. Goa trip") }, singleLine = true)
                    OutlinedTextField(target, { target = it }, label = { Text("Target (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    DateField("Target date (optional)", d, { date = it.toString() }, onClear = { date = "" })
                }
            },
            confirmButton = { TextButton(enabled = valid, onClick = { vm.saveGoal(g.copy(name = name.trim(), targetPaise = t!!, targetDate = d)); editing = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    if (g.id != 0L) TextButton(onClick = { vm.deleteGoal(g.id); editing = null }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}
