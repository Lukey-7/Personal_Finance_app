package com.pft.financetracker.ui.screens.goals

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.goals.Goal
import com.pft.financetracker.domain.goals.GoalMath
import com.pft.financetracker.domain.goals.GoalProgress
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AddButton
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.DateField
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.ProgressMeter
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.Tone
import com.pft.financetracker.ui.components.approxMoney
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.rememberAtTop
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val dateFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)

private fun newGoal() = Goal(name = "", targetPaise = 0, targetDate = LocalDate.now().plusMonths(12))

/**
 * Savings goals: what is saved across all of them as the hero, then each goal with its progress, what it needs a month
 * to arrive on time, and whether it is on track. Money is added (or taken out) in a sheet.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val goals by vm.goalProgress.collectAsState()
    val lastMonthSavings by vm.lastMonthSavingsPaise.collectAsState()
    val dbLoaded by vm.loaded.collectAsState()
    // These lists start as "not loaded", so a first open shows the skeleton rather than flashing the empty state.
    val loaded = dbLoaded && com.pft.financetracker.ui.isLoaded(goals)
    var editing by remember { mutableStateOf<Goal?>(null) }
    var adding by remember { mutableStateOf<GoalProgress?>(null) }
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Goals") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { if (goals.isNotEmpty()) AddButton({ editing = newGoal() }, listState, text = "Add goal") },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding(FabClearance)),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // Last month's savings (offered when adding money) come from the transactions: wait for them to load.
            if (!loaded) {
                item { SkeletonHero(Modifier.padding(top = Space.md), cards = 2) }
                return@LazyColumn
            }
            if (goals.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.Flag,
                        "No goals yet",
                        "Save toward a trip, a phone or an emergency fund, and see what it needs each month to arrive on time.",
                    ) { PrimaryButton("Add a goal", { editing = newGoal() }, fill = false) }
                }
                return@LazyColumn
            }

            item(key = "hero") { Hero(goals) }
            items(goals, key = { it.goal.id }) { p ->
                GoalCard(p, Modifier.padding(horizontal = Gutter).animateItem(), onAdd = { adding = p }, onEdit = { editing = p.goal })
            }
        }
    }

    adding?.let { p ->
        AddMoneySheet(p, lastMonthSavings, onSave = { v -> vm.contributeToGoal(p.goal.id, v) }, onDismiss = { adding = null })
    }

    editing?.let { g ->
        GoalForm(g, onSave = { vm.saveGoal(it) }, onDelete = { vm.deleteGoal(g.id) }, onDismiss = { editing = null })
    }
}

/** Saved across every goal, against what they add up to. */
@Composable
private fun Hero(goals: List<GoalProgress>) {
    val saved = goals.sumOf { it.savedPaise }
    val target = goals.sumOf { it.goal.targetPaise }
    val pct = if (target > 0) (saved.coerceAtLeast(0) * 100 / target).toInt().coerceAtMost(100) else 0
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.md, bottom = Space.sm)) {
        CapsLabel("Saved toward goals")
        Spacer(Modifier.height(Space.sm))
        AmountDisplay(saved, spokenLabel = "Saved toward goals")
        Spacer(Modifier.height(Space.sm))
        Text(
            "of ${money(target)} · $pct% · ${countLabel(goals.size, "goal")}",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Where a goal stands, in a word: date passed, on track or behind. Null once reached (the line says so) or with no date. */
@Composable
private fun goalTag(p: GoalProgress): Pair<String, Color>? = when {
    p.done -> null
    p.late -> "Date passed" to Expense
    p.monthlyNeededPaise == null -> null
    p.onTrack -> "On track" to MaterialTheme.colorScheme.primary
    else -> "Behind" to Expense
}

/** One goal: name and date, progress bar, "₹X of ₹Y · 42%", what it needs a month, and its actions. */
@Composable
private fun GoalCard(p: GoalProgress, modifier: Modifier, onAdd: () -> Unit, onEdit: () -> Unit) {
    val tag = goalTag(p)
    FinCard(modifier, spacing = Space.md) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(p.goal.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    p.goal.targetDate?.let { "By ${it.format(dateFmt)}" } ?: "No deadline",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (tag != null) {
                Spacer(Modifier.width(Space.sm))
                Text(
                    tag.first,
                    Modifier.clip(CircleShape).background(tag.second.copy(alpha = 0.12f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = tag.second,
                    maxLines = 1,
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            ProgressMeter(p.percent / 100f)
            Text("${money(p.savedPaise)} of ${money(p.goal.targetPaise)} · ${p.percent}%", style = MoneyType.small, maxLines = 1, softWrap = false)
        }
        val line = when {
            p.done -> "Reached. Well done."
            p.late -> "The date has passed: ${money(p.remainingPaise)} still to go."
            p.monthlyNeededPaise != null -> "Needs about ${approxMoney(p.monthlyNeededPaise)} a month for ${countLabel(p.monthsLeft, "month")}"
            else -> "${money(p.remainingPaise)} to go"
        }
        Text(
            line,
            style = MaterialTheme.typography.bodySmall,
            color = if (p.late || (!p.onTrack && !p.done)) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (!p.done) TextAction("Add money", onAdd, alignStart = true)
            TextAction("Edit", onEdit, alignStart = p.done)
        }
    }
}

/** A text field in a sheet: full width, one line, the app's control shape. */
@Composable
private fun Field(
    value: String,
    onChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    keyboard: KeyboardType = KeyboardType.Text,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(),
        label = { Text(label) },
        placeholder = if (placeholder != null) ({ Text(placeholder) }) else null,
        singleLine = true,
        shape = ControlShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

/** Put money into a goal (or take it out with a minus), offering last month's savings as the amount. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddMoneySheet(p: GoalProgress, lastMonthSavings: Long, onSave: (Long) -> Unit, onDismiss: () -> Unit) {
    val suggestedRupees = GoalMath.suggestedTopUp(lastMonthSavings, p.remainingPaise) / 100
    val suggestedText = suggestedRupees.takeIf { it > 0 }?.toString() ?: ""
    var amount by remember { mutableStateOf(suggestedText) }
    val v = amount.trim().let { s -> if (s.startsWith("-")) Money.parsePaise(s.drop(1))?.let { -it } else Money.parsePaise(s) }

    val haptics = rememberHaptics()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }

    FinSheet(onDismiss = onDismiss, title = "Add to ${p.goal.name}", state = state) {
        Text(
            "${money(p.savedPaise)} saved · ${money(p.remainingPaise)} to go",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        if (lastMonthSavings > 0) {
            SoftPanel(spacing = Space.sm) {
                Text("Last month you saved ${money(lastMonthSavings)}.", style = MaterialTheme.typography.bodyMedium)
                if (suggestedRupees > 0) {
                    PillChip(amount.trim() == suggestedText, "Put in ${money(suggestedRupees * 100)} now") { amount = suggestedText }
                }
            }
        }
        Field(amount, { amount = it }, "Amount (₹), minus to take out")
        PrimaryButton(
            "Save",
            onClick = { haptics.confirm(); onSave(v!!); close(onDismiss) },
            enabled = v != null && v != 0L,
        )
    }
}

/** Add or change a goal: name, target and an optional date. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalForm(g: Goal, onSave: (Goal) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember { mutableStateOf(g.name) }
    var target by remember { mutableStateOf(if (g.targetPaise > 0) (g.targetPaise / 100).toString() else "") }
    var date by remember { mutableStateOf(g.targetDate?.toString() ?: "") }
    var confirmDelete by remember { mutableStateOf(false) }
    val t = Money.parsePaise(target)?.takeIf { it > 0 }
    val d = if (date.isBlank()) null else runCatching { LocalDate.parse(date.trim()) }.getOrNull()
    val valid = name.isNotBlank() && t != null && (date.isBlank() || d != null)

    val haptics = rememberHaptics()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }

    FinSheet(onDismiss = onDismiss, title = if (g.id == 0L) "New goal" else "Edit goal", state = state) {
        Field(name, { name = it }, "Goal name", placeholder = "e.g. Goa trip")
        Field(target, { target = it }, "Target (₹)", keyboard = KeyboardType.Decimal)
        DateField("Target date (optional)", d, { date = it.toString() }, onClear = { date = "" })
        PrimaryButton(
            "Save",
            onClick = { haptics.confirm(); onSave(g.copy(name = name.trim(), targetPaise = t!!, targetDate = d)); close(onDismiss) },
            enabled = valid,
        )
        if (g.id != 0L) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                TextAction("Delete goal", { confirmDelete = true }, tone = Tone.Danger)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${g.name}?") },
            text = { Text("It stops showing here. Your payments stay as they are.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; onDelete(); close(onDismiss) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
