package com.pft.financetracker.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.ui.text.style.TextOverflow
import com.pft.financetracker.ui.components.AddFab
import com.pft.financetracker.ui.components.CardPadding
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.IconCircle
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.displayMerchant
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.SnackbarDuration
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.ui.components.AmountRow
import com.pft.financetracker.ui.components.approxMoney
import com.pft.financetracker.ui.components.LoadingState
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.scanResultLine
import com.pft.financetracker.ui.theme.moneyTone
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.ImportUiState
import com.pft.financetracker.ui.PeriodChoice
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.DonutChart
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.Legend
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.SectionHeader
import com.pft.financetracker.ui.components.Slice
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.colorFor
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.Neutral
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
fun DashboardScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onOpenTransactions: () -> Unit,
    onOpenBudgets: () -> Unit,
    onOpenSmsLog: (Long?) -> Unit,
    onDrill: (Bucket, Category?) -> Unit,
    onOpenSplit: (Long) -> Unit = {},
    onOpenTools: () -> Unit = {},
) {
    val txns by vm.transactions.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val importState by vm.importState.collectAsState()
    val choice by vm.period.collectAsState()
    val includeCash by vm.countCashAsSpend.collectAsState()
    val suggestions by vm.splitSuggestions.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val snackbar = remember { SnackbarHostState() }
    var showRange by remember { mutableStateOf(false) }

    val period = choice.period()
    val summary = InsightsEngine.summarize(txns, period, includeCash)
    // Six days into a month compares with the first six days of the last one, not all of it.
    val now = System.currentTimeMillis()
    val comparedWith = Periods.sameSpanBefore(period, choice.previous(), now)
    val prev = InsightsEngine.summarize(txns, comparedWith, includeCash)
    val budgetStatus = InsightsEngine.budgetStatus(txns, budgets, period)
    val recent = txns.take(6)

    LaunchedEffect(importState) {
        val s = importState
        if (s is ImportUiState.Done) {
            // An action makes a Material snackbar wait for a tap forever; this one goes away by itself.
            val res = snackbar.showSnackbar(
                scanResultLine(s.stats, failed = false),
                actionLabel = if (s.stats.scanned > 0) "View log" else null,
                duration = SnackbarDuration.Long,
            )
            if (res == SnackbarResult.ActionPerformed) onOpenSmsLog(s.stats.runId)
            vm.dismissImportResult()
        } else if (s is ImportUiState.Failed) {
            snackbar.showSnackbar(scanResultLine(null, failed = true), duration = SnackbarDuration.Long)
            vm.dismissImportResult()
        }
    }

    val barPad = LocalBottomBarPadding.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("FinTrack", style = MaterialTheme.typography.titleLarge) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton(onClick = onOpenTools) { Icon(Icons.Outlined.Apps, "Money tools") }
                    if (importState is ImportUiState.Running) CircularProgressIndicator(Modifier.padding(14.dp).size(22.dp), strokeWidth = 2.dp)
                    else IconButton(onClick = { if (vm.hasSmsPermission()) vm.scanInbox() }) { Icon(Icons.Outlined.Sync, "Scan SMS") }
                }
            )
        },
        floatingActionButton = { AddFab(onAdd, Modifier.padding(bottom = barPad)) },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = barPad)) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            // Clears the nav pill and the + button, so the last card can be scrolled fully into view.
            contentPadding = PaddingValues(top = 8.dp, bottom = bottomPadding(FabClearance)),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                ChipRow {
                    PillChip(choice is PeriodChoice.ThisMonth, "This month") { vm.setPeriod(PeriodChoice.ThisMonth) }
                    PillChip(choice is PeriodChoice.LastMonth, "Last month") { vm.setPeriod(PeriodChoice.LastMonth) }
                    PillChip(choice is PeriodChoice.ThisWeek, "This week") { vm.setPeriod(PeriodChoice.ThisWeek) }
                    PillChip(choice is PeriodChoice.Custom, if (choice is PeriodChoice.Custom) period.label else "Custom", icon = Icons.Outlined.DateRange) { showRange = true }
                }
            }

            // Until the database answers, every figure below would read ₹0: show a spinner instead.
            if (!loaded) {
                item { LoadingState() }
                return@LazyColumn
            }

            // ---- The hero: one number, stated plainly, with the arithmetic underneath ----
            item {
                Column(Modifier.padding(horizontal = Gutter)) {
                    CapsLabel("Net spend · ${period.label}")
                    Spacer(Modifier.height(6.dp))
                    Text(
                        money(summary.netSpendPaise),
                        style = MaterialTheme.typography.displayLarge,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.clickable { onDrill(Bucket.SPEND, null) },
                    )
                    Spacer(Modifier.height(6.dp))
                    val change = InsightsEngine.changePercent(summary.netSpendPaise, prev.netSpendPaise)
                    val running = now in period
                    // Averages over one or two days say more about the calendar than about spending.
                    val daysIn = if (running) ((now - period.start) / 86_400_000L).toInt() + 1 else period.days
                    Text(
                        if (summary.netSpendPaise <= 0) (if (running) "Nothing spent yet" else "Nothing spent in this period")
                        else listOfNotNull(
                            change?.let { if (it >= 0) "$it% more than ${comparedWith.label}" else "${-it}% less than ${comparedWith.label}" },
                            if (daysIn >= 3) "about ${approxMoney(summary.dailyAveragePaise(now))} a day" else null,
                            if (running && daysIn >= 3) summary.projectedPaise(now)?.let { "on track for ${approxMoney(it)}" } else null,
                        ).joinToString("  ·  "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            change == null || summary.netSpendPaise <= 0 -> MaterialTheme.colorScheme.onSurfaceVariant
                            change > 0 -> Expense
                            else -> Income
                        },
                    )
                }
            }

            item {
                SoftPanel(Modifier.padding(horizontal = Gutter)) {
                    AmountRow("Income", summary.incomePaise, moneyTone(summary.incomePaise), sign = "+") { onDrill(Bucket.INCOME, null) }
                    AmountRow("Gross spend", summary.grossSpendPaise, MaterialTheme.colorScheme.onSurface, sign = "−") { onDrill(Bucket.SPEND, null) }
                    if (summary.refundsPaise > 0) AmountRow("Refunds & cashback", summary.refundsPaise, Income, sign = "+") { onDrill(Bucket.REFUNDS, null) }
                    Hairline()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Savings", style = MaterialTheme.typography.titleMedium)
                        Text(
                            money(summary.savingsPaise),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = moneyTone(summary.savingsPaise),
                        )
                    }
                }
            }

            // ---- Shared payments waiting for a yes ----
            if (suggestions.isNotEmpty()) item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Groups, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(10.dp))
                        Text(if (suggestions.size == 1) "Looks like a shared payment" else "${suggestions.size} payments look shared", style = MaterialTheme.typography.titleMedium)
                    }
                    suggestions.take(3).forEachIndexed { i, s ->
                        if (i > 0) Hairline()
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            val mine = s.myShare?.amountPaise ?: s.totalPaise
                            Text("${money(s.totalPaise)} at ${s.title} · your share would be ${money(mine)}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                            s.reasons.firstOrNull { !it.startsWith("Your share") }?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                                PrimaryButton("Split it", { vm.acceptSplit(s.id) }, fill = false)
                                TextAction("Not shared", { vm.rejectSplit(s.id) })
                                TextAction("Details", { onOpenSplit(s.id) })
                            }
                        }
                    }
                }
            }

            // ---- Money that moved but is not spend ----
            if (summary.transfersOutPaise + summary.transfersInPaise + summary.investmentsPaise + (if (includeCash) 0L else summary.cashPaise) > 0) item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Text("Not counted as spend", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Moving money between your own accounts, paying card bills, investing and friends paying back their share of a split are shown here so they never inflate your spending or income.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (summary.transfersOutPaise > 0) AmountRow("Transfers & card bill payments", summary.transfersOutPaise, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.TRANSFERS, null) }
                    if (summary.transfersInPaise - summary.settlementsInPaise > 0) AmountRow("Transfers in", summary.transfersInPaise - summary.settlementsInPaise, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.TRANSFERS, null) }
                    if (summary.settlementsInPaise > 0) AmountRow("Paid back by friends", summary.settlementsInPaise, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.TRANSFERS, null) }
                    if (summary.investmentsPaise > 0) AmountRow("Investments", summary.investmentsPaise, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.INVESTMENTS, null) }
                    if (!includeCash && summary.cashPaise > 0) AmountRow("Cash withdrawals", summary.cashPaise, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.CASH, null) }
                }
            }

            if (reviewCount > 0) item {
                SoftPanel(Modifier.padding(horizontal = Gutter), onClick = onOpenReview) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp), tint = Expense)
                        Spacer(Modifier.width(12.dp))
                        Text(reviewLine(reviewCount), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                        Text("Review", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Text("Where it went", style = MaterialTheme.typography.titleMedium)
                    val slices = summary.byCategory.filter { it.amountPaise > 0 }.take(7)
                        .map { Slice(it.category.label, it.amount, colorFor(Category.entries.indexOf(it.category)), it.category) }
                    if (slices.isEmpty()) {
                        Text("No spending recorded in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    } else {
                        // Legend sits below the donut at full width, so category names are never cut short.
                        DonutChart(slices, Modifier.fillMaxWidth(), centerText = countLabel(summary.expenseCount, "payment"))
                        Legend(slices, Modifier.fillMaxWidth()) { cat -> onDrill(Bucket.SPEND, cat) }
                    }
                }
            }

            if (summary.byMerchant.isNotEmpty()) item {
                FinCard(Modifier.padding(horizontal = Gutter), padding = PaddingValues(top = CardPadding, bottom = 8.dp)) {
                    Text("Top merchants", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = CardPadding))
                    Column {
                        summary.byMerchant.take(5).forEachIndexed { i, m ->
                            if (i > 0) Hairline(startInset = CardPadding + 56.dp, endInset = CardPadding)
                            Row(
                                Modifier.fillMaxWidth().clickable { onDrill(Bucket.SPEND, m.category) }.padding(horizontal = CardPadding, vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                IconCircle(categoryIcon(m.category), tint = colorFor(Category.entries.indexOf(m.category)))
                                Spacer(Modifier.width(16.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(displayMerchant(m.merchant), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text("${countLabel(m.count, "payment")} · ${m.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(money(m.amountPaise), fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyLarge)
                            }
                        }
                    }
                }
            }

            if (summary.byAccount.size > 1) item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Text("By account / card", style = MaterialTheme.typography.titleMedium)
                    summary.byAccount.take(6).forEach { a ->
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (a.incomePaise > 0) Text("+${money(a.incomePaise)}  ", color = Income, style = MaterialTheme.typography.bodySmall)
                            Text(money(a.spendPaise), color = if (a.spendPaise > 0) Expense else MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                FinCard(Modifier.padding(horizontal = Gutter)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("Budgets", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                        TextAction(if (budgetStatus.isEmpty()) "Set budgets" else "Manage", onOpenBudgets)
                    }
                    budgetStatus.take(4).forEach { b ->
                        Column(Modifier.heightIn(min = 48.dp).clickable { onDrill(Bucket.SPEND, b.budget.category) }, verticalArrangement = Arrangement.Center) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(b.budget.category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(Space.md))
                                Text("${money(b.spentPaise)} of ${money(b.budget.monthlyLimitPaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
                            }
                            Spacer(Modifier.height(6.dp))
                            LinearProgressIndicator(
                                progress = { b.fraction.coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth().height(6.dp),
                                color = if (b.over) Expense else MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.surfaceVariant,
                                gapSize = 0.dp,
                                drawStopIndicator = {},
                            )
                            if (b.over) Text("Over budget by ${money(b.spentPaise - b.budget.monthlyLimitPaise)}", color = Expense, style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
            }

            item {
                SectionHeader("Recent") {
                    TextAction("See all", onOpenTransactions)
                }
            }
            if (recent.isEmpty()) item {
                EmptyState(Icons.AutoMirrored.Outlined.ReceiptLong, "No transactions yet. Scan your SMS with the sync button above, or add one with +.")
            }
            itemsIndexed(recent) { i, t ->
                if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                TransactionRow(t) { onEdit(t.id) }
            }
        }
    }

    if (showRange) {
        val state = rememberDateRangePickerState()
        DatePickerDialog(
            onDismissRequest = { showRange = false },
            confirmButton = {
                TextButton(onClick = {
                    val s = state.selectedStartDateMillis; val e = state.selectedEndDateMillis
                    if (s != null && e != null) vm.setPeriod(PeriodChoice.Custom(s, e))
                    showRange = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } }
        ) { DateRangePicker(state, modifier = Modifier.height(480.dp)) }
    }
}

/** "1 item needs review" / "3 items need review". Neutral: the queue holds both SMS and statement rows. */
internal fun reviewLine(n: Int) = if (n == 1) "1 item needs review" else "$n items need review"
