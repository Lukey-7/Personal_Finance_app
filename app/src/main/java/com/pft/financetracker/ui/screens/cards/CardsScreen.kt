package com.pft.financetracker.ui.screens.cards

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CreditCard
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pft.financetracker.domain.cards.Card
import com.pft.financetracker.domain.cards.CardSummary
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AdaptiveRow
import com.pft.financetracker.ui.components.AddButton
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.ProgressMeter
import com.pft.financetracker.ui.components.SkeletonHero
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
import com.pft.financetracker.ui.theme.surfaces
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

private fun newCard() = Card(last4 = "", name = "", statementDay = 1, dueDay = 20)

/** "today", "tomorrow", "in 3 days". */
private fun inDays(n: Long): String = when (n) { 0L -> "today"; 1L -> "tomorrow"; else -> "in $n days" }

/**
 * Each credit card's current billing cycle: the total on all cards as the hero, then one card each with its spend,
 * how far through the cycle it is, and when the statement comes and the bill is due. Tap a card to edit it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val summaries by vm.cardSummaries.collectAsState()
    val dbLoaded by vm.loaded.collectAsState()
    // These lists start as "not loaded", so a first open shows the skeleton rather than flashing the empty state.
    val loaded = dbLoaded && com.pft.financetracker.ui.isLoaded(summaries)
    var editing by remember { mutableStateOf<Card?>(null) }
    val listState = rememberLazyListState()

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Credit cards") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        floatingActionButton = { AddButton({ editing = newCard() }, expanded = rememberAtTop(listState), text = "Add card") },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding(FabClearance)),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // Cycle spend comes from the transactions: until they load, show the page's shape, not ₹0.
            if (!loaded) {
                item { SkeletonHero(Modifier.padding(top = Space.md), cards = 2) }
                return@LazyColumn
            }
            if (summaries.isEmpty()) {
                item {
                    EmptyState(
                        Icons.Outlined.CreditCard,
                        "No cards yet",
                        "Add one to see its spend for each billing cycle and when to pay.",
                    ) { PrimaryButton("Add card", { editing = newCard() }, fill = false) }
                }
                return@LazyColumn
            }

            item(key = "hero") { Hero(summaries) }
            item(key = "about") {
                LearnMore(
                    "Counted per billing cycle from card SMS.",
                    "How card spend is counted",
                    "Spend is counted per billing cycle, from card SMS with the card's last four digits. Refunds to the card are taken off.",
                    Modifier.padding(horizontal = Gutter),
                )
            }
            items(summaries, key = { it.card.id }) { s ->
                CardTile(s, Modifier.padding(horizontal = Gutter).animateItem()) { editing = s.card }
            }
        }
    }

    editing?.let { c -> CardEditor(vm, c, onDismiss = { editing = null }) }
}

/** Spend this cycle across every card, and which bill is due first. */
@Composable
private fun Hero(summaries: List<CardSummary>) {
    val total = summaries.sumOf { it.spendPaise }
    val next = summaries.minByOrNull { it.daysToDue }
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.md)) {
        CapsLabel("Card spend · this cycle")
        Spacer(Modifier.height(Space.sm))
        AmountDisplay(total, spokenLabel = "Card spend this cycle")
        Spacer(Modifier.height(Space.sm))
        Text(
            "Across ${countLabel(summaries.size, "card")}" +
                (next?.let { " · ${it.card.name} due ${it.cycle.due.format(dayFmt)}, ${inDays(it.daysToDue)}" } ?: ""),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A quiet card shape: the card's name and last four digits on the accent tint, with a chip. */
@Composable
private fun CardFace(card: Card) {
    val shape = RoundedCornerShape(14.dp)
    val ink = MaterialTheme.colorScheme.primary
    Column(
        Modifier.fillMaxWidth()
            .clip(shape)
            .background(surfaces.accentSoft)
            .border(1.dp, ink.copy(alpha = 0.18f), shape)
            .clearAndSetSemantics { contentDescription = "${card.name}, card ending ${card.last4}" }
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.md),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(card.name, Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, color = ink, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.width(Space.sm))
            Icon(Icons.Outlined.CreditCard, null, Modifier.size(22.dp), tint = ink)
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(width = 30.dp, height = 22.dp).clip(RoundedCornerShape(5.dp)).background(ink.copy(alpha = 0.2f)))
            Spacer(Modifier.weight(1f))
            Text("••${card.last4}", style = MoneyType.tile.copy(letterSpacing = 1.5.sp), color = ink, maxLines = 1, softWrap = false)
        }
    }
}

/** One card: its face, this cycle's spend (and rewards), the cycle's progress by day, statement and due dates. */
@Composable
private fun CardTile(s: CardSummary, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val total = ChronoUnit.DAYS.between(s.cycle.start, s.cycle.end).toFloat() + 1
    val gone = (total - ChronoUnit.DAYS.between(LocalDate.now(), s.cycle.end).toFloat()).coerceIn(0f, total)
    val day = gone.toInt().coerceAtLeast(1)
    val urgent = s.daysToDue <= 3
    FinCard(modifier, onClick = onClick) {
        CardFace(s.card)
        Row(verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                Text("Spent this cycle", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(money(s.spendPaise), style = MoneyType.title, maxLines = 1, softWrap = false)
            }
            if (s.card.rewardBp > 0) {
                Spacer(Modifier.width(Space.md))
                Column(horizontalAlignment = Alignment.End) {
                    Text("Rewards", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text("about ${approxMoney(s.rewardPaise)} back", style = MoneyType.small, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, softWrap = false)
                }
            }
        }
        // How far through the billing cycle, not how much is spent: say so, or the bar reads as spend.
        Column(
            Modifier.clearAndSetSemantics { contentDescription = "Day $day of ${total.toInt()} in this billing cycle, ${s.cycle.start.format(dayFmt)} to ${s.cycle.end.format(dayFmt)}" },
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ProgressMeter(gone / total, height = 6.dp)
            Row {
                Text("Day $day of ${total.toInt()}", Modifier.weight(1f), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("${s.cycle.start.format(dayFmt)} – ${s.cycle.end.format(dayFmt)}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Hairline()
        Row {
            Column(Modifier.weight(1f)) {
                Text("Statement", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(s.cycle.end.format(dayFmt), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text("Pay by", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    "${s.cycle.due.format(dayFmt)} · ${inDays(s.daysToDue)}",
                    style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
                    color = if (urgent) Expense else MaterialTheme.colorScheme.onSurface,
                )
            }
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
) {
    OutlinedTextField(
        value, onChange, modifier.fillMaxWidth(),
        label = { Text(label) },
        singleLine = true,
        shape = ControlShape,
        keyboardOptions = KeyboardOptions(keyboardType = keyboard),
    )
}

/** Add or change a card. A new card offers the last four digits seen in card SMS. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CardEditor(vm: AppViewModel, start: Card, onDismiss: () -> Unit) {
    var last4 by remember { mutableStateOf(start.last4) }
    var name by remember { mutableStateOf(start.name) }
    var stmt by remember { mutableStateOf(start.statementDay.toString()) }
    var due by remember { mutableStateOf(start.dueDay.toString()) }
    var reward by remember { mutableStateOf(if (start.rewardBp > 0) "%.2f".format(Locale.ENGLISH, start.rewardBp / 100.0) else "") }
    var suggestions by remember { mutableStateOf<List<String>>(emptyList()) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { if (start.id == 0L) suggestions = vm.cardSuggestions() }

    val s = stmt.toIntOrNull()?.takeIf { it in 1..31 }
    val d = due.toIntOrNull()?.takeIf { it in 1..31 }
    val r = if (reward.isBlank()) 0.0 else reward.toDoubleOrNull()
    val valid = last4.length == 4 && last4.all { it.isDigit() } && name.isNotBlank() && s != null && d != null && r != null && r in 0.0..20.0

    val haptics = rememberHaptics()
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }

    FinSheet(onDismiss = onDismiss, title = if (start.id == 0L) "Add a card" else "Edit card", state = state) {
        if (suggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                CapsLabel("Seen in your SMS")
                ChipFlow { suggestions.take(6).forEach { n -> PillChip(last4 == n, "••$n") { last4 = n } } }
            }
        }
        Field(last4, { if (it.length <= 4) last4 = it }, "Last 4 digits", keyboard = KeyboardType.Number)
        Field(name, { name = it }, "Name, e.g. HDFC Millennia")
        AdaptiveRow(count = 2, minItemWidth = 150.dp) { m ->
            Field(stmt, { stmt = it }, "Statement day (1–31)", m, KeyboardType.Number)
            Field(due, { due = it }, "Payment due day (1–31)", m, KeyboardType.Number)
        }
        Field(reward, { reward = it }, "Reward rate % (optional)", keyboard = KeyboardType.Decimal)
        PrimaryButton(
            "Save",
            onClick = {
                haptics.confirm()
                vm.saveCard(start.copy(last4 = last4, name = name.trim(), statementDay = s!!, dueDay = d!!, rewardBp = Math.round(r!! * 100).toInt()))
                close(onDismiss)
            },
            enabled = valid,
        )
        if (start.id != 0L) {
            Box(Modifier.fillMaxWidth().heightIn(min = 48.dp), contentAlignment = Alignment.Center) {
                TextAction("Delete card", { confirmDelete = true }, tone = Tone.Danger)
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete ${start.name}?") },
            text = { Text("Its cycle stops showing here. Your payments stay as they are.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; vm.deleteCard(start.id); close(onDismiss) }) {
                    Text("Delete", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}
