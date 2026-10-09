package com.pft.financetracker.ui.screens.budgets

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CardPadding
import com.pft.financetracker.ui.components.CategoryIcon
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.ProgressMeter
import com.pft.financetracker.ui.components.RowIconGap
import com.pft.financetracker.ui.components.RowIconSize
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.Tone
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.model.BudgetLines
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.launch

/**
 * Monthly budgets: spend against the limits this month as the hero, then every spending category with its bar (striped
 * and said in words when over). Tap a category to set, change or remove its limit.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BudgetsScreen(vm: AppViewModel, onBack: () -> Unit) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val includeCash by vm.countCashAsSpend.collectAsState()
    val summary = InsightsEngine.summarize(txns, Periods.month(), includeCash)
    var editing by remember { mutableStateOf<Category?>(null) }
    var input by remember { mutableStateOf("") }

    val limitOf = { cat: Category -> budgets.firstOrNull { it.category == cat }?.monthlyLimitPaise }
    val spentOf = { cat: Category -> summary.byCategory.firstOrNull { it.category == cat }?.amountPaise ?: 0L }
    val withLimit = Category.spendCategories.filter { limitOf(it) != null }
    val withoutLimit = Category.spendCategories.filter { limitOf(it) == null }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Budgets") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            // Until transactions and budgets load, every figure here would read ₹0: show the page's shape instead.
            if (!loaded) {
                item { SkeletonHero(Modifier.padding(top = Space.md), cards = 0) }
                item { SkeletonRows(6) }
                return@LazyColumn
            }

            item(key = "hero") {
                val limits = withLimit.sumOf { limitOf(it) ?: 0L }
                val spent = withLimit.sumOf { spentOf(it) }
                Hero(summary.period.label, limits, spent, totalSpent = Category.spendCategories.sumOf { spentOf(it) })
            }

            if (withLimit.isNotEmpty()) {
                item(key = "h-limit") { CapsLabel("With a limit", Modifier.padding(start = Gutter, end = Gutter, top = Space.lg)) }
                item(key = "limit") {
                    CategoryCard(withLimit, limitOf, spentOf) { cat, limit -> editing = cat; input = limit?.let { (it / 100).toString() } ?: "" }
                }
            }
            if (withoutLimit.isNotEmpty()) {
                item(key = "h-none") {
                    CapsLabel(if (withLimit.isEmpty()) "Tap a category to set a limit" else "No limit yet", Modifier.padding(start = Gutter, end = Gutter, top = Space.lg))
                }
                item(key = "none") {
                    CategoryCard(withoutLimit, limitOf, spentOf) { cat, limit -> editing = cat; input = limit?.let { (it / 100).toString() } ?: "" }
                }
            }
        }
    }

    editing?.let { cat ->
        BudgetSheet(
            cat,
            input = input,
            onInput = { input = it },
            limit = limitOf(cat),
            spent = spentOf(cat),
            onSave = { paise -> vm.setBudget(cat, paise) },
            onRemove = { vm.setBudget(cat, 0L) },
            onDismiss = { editing = null },
        )
    }
}

/** Spent against the limits set (or, with none, all spend this month), with a bar and what is left or over in words. */
@Composable
private fun Hero(period: String, limits: Long, spent: Long, totalSpent: Long) {
    Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.md)) {
        if (limits <= 0L) {
            CapsLabel("Spent · $period")
            Spacer(Modifier.height(Space.sm))
            AmountDisplay(totalSpent, spokenLabel = "Spent, $period")
            Spacer(Modifier.height(Space.sm))
            Text(
                "No limits yet. Set one for a category to see how close you are each month.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            return@Column
        }
        val over = spent > limits
        CapsLabel("Spent of budget · $period")
        Spacer(Modifier.height(Space.sm))
        AmountDisplay(spent, spokenLabel = "Spent of budget, $period")
        Spacer(Modifier.height(Space.sm))
        ProgressMeter((spent.toDouble() / limits).toFloat(), over = over)
        Spacer(Modifier.height(Space.sm))
        Text(
            BudgetLines.hero(spent, limits),
            style = MaterialTheme.typography.bodyMedium,
            color = if (over) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** A card of category rows, separated by hairlines that line up with the text. */
@Composable
private fun CategoryCard(
    cats: List<Category>,
    limitOf: (Category) -> Long?,
    spentOf: (Category) -> Long,
    onOpen: (Category, Long?) -> Unit,
) {
    FinCard(Modifier.padding(horizontal = Gutter), padding = PaddingValues(vertical = Space.xs), spacing = 0.dp) {
        cats.forEachIndexed { i, cat ->
            if (i > 0) Hairline(startInset = CardPadding + RowIconSize + RowIconGap, endInset = CardPadding)
            val limit = limitOf(cat)
            BudgetRow(cat, limit, spentOf(cat)) { onOpen(cat, limit) }
        }
    }
}

/** One category: icon, name, "₹X of ₹Y", its bar, and what is left or over in words. */
@Composable
private fun BudgetRow(cat: Category, limit: Long?, spent: Long, onClick: () -> Unit) {
    val frac = if (limit != null && limit > 0) (spent.toDouble() / limit).toFloat() else 0f
    val over = limit != null && spent > limit
    Row(
        Modifier.fillMaxWidth().heightIn(min = 64.dp)
            .clickable(role = Role.Button, onClickLabel = if (limit != null) "Change limit" else "Set limit", onClick = onClick)
            .padding(horizontal = CardPadding, vertical = Space.md),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(cat)
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(cat.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.width(Space.sm))
                Text(
                    if (limit != null) "${money(spent)} of ${money(limit)}" else money(spent),
                    style = MoneyType.small, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false,
                )
            }
            if (limit != null) {
                ProgressMeter(frac, over = over, height = 6.dp)
                Text(
                    BudgetLines.row(spent, limit),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (over) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text("No limit · tap to set one", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Set, change or remove one category's monthly limit. 0 (or Remove limit) takes it away; a blank field asks for a figure. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BudgetSheet(
    cat: Category,
    input: String,
    onInput: (String) -> Unit,
    limit: Long?,
    spent: Long,
    onSave: (Long) -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val haptics = rememberHaptics()
    var error by remember { mutableStateOf<String?>(null) }
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    fun close(after: () -> Unit) { scope.launch { state.hide() }.invokeOnCompletion { after() } }

    FinSheet(onDismiss = onDismiss, title = "${cat.label} budget", state = state) {
        SoftPanel(spacing = Space.xs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                CategoryIcon(cat, size = 32.dp)
                Spacer(Modifier.width(Space.md))
                Text("Spent this month", Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Space.sm))
                Text(money(spent), style = MoneyType.row, maxLines = 1, softWrap = false)
            }
        }
        OutlinedTextField(
            input, { error = null; onInput(it.filter { ch -> ch.isDigit() }) }, Modifier.fillMaxWidth(),
            label = { Text("Monthly limit (₹), 0 to remove") },
            isError = error != null,
            supportingText = error?.let { e -> { Text(e) } },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            shape = ControlShape,
        )
        PrimaryButton("Save", {
            when (val r = BudgetLines.parse(input)) {
                is BudgetLines.Input.Invalid -> { error = r.message }
                is BudgetLines.Input.Limit -> { haptics.confirm(); onSave(r.paise); close(onDismiss) }
                // 0 takes the limit away; with no limit set there is nothing to remove, so the sheet just closes.
                BudgetLines.Input.Remove -> { haptics.confirm(); if (limit != null) onRemove(); close(onDismiss) }
            }
        })
        if (limit != null) {
            TextAction("Remove limit", { haptics.confirm(); onRemove(); close(onDismiss) }, Modifier.align(Alignment.CenterHorizontally), tone = Tone.Danger)
        }
    }
}
