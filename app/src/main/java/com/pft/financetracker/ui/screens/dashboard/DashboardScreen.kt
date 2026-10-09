package com.pft.financetracker.ui.screens.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.outlined.DonutLarge
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Groups
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.Storefront
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.insights.PeriodSummary
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.ImportUiState
import com.pft.financetracker.ui.PeriodChoice
import com.pft.financetracker.ui.components.AddButton
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.AmountRow
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CategoryIcon
import com.pft.financetracker.ui.components.CategoryRing
import com.pft.financetracker.ui.components.ChartBar
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.ExpandableCard
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSnackbarHost
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.InfoButton
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.ProgressMeter
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SectionHeader
import com.pft.financetracker.ui.components.SegmentedControl
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.Slice
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.SpendChart
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.Trend
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.categoryColor
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.displayMerchant
import com.pft.financetracker.ui.components.heroLine
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.rememberAtTop
import com.pft.financetracker.ui.components.scanResultLine
import com.pft.financetracker.ui.model.HomeLines
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.Neutral
import com.pft.financetracker.ui.theme.moneyTone
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Home, "today's statement": one hero figure with its comparison and a six-period spark chart, three tiles, then cards
 * ordered by what needs you first, each summed up in one line until opened. Every figure still opens its payments.
 */
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
    onOpenRoute: (String) -> Unit = {},
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
    var sparkSelected by remember(choice) { mutableStateOf<Int?>(null) }
    val listState = rememberLazyListState()

    // Worked out once per change of data or period, off the main thread's hot path of every recomposition.
    val view = remember(txns, budgets, choice, includeCash) { HomeFigures.of(txns, budgets, choice, includeCash) }
    val period = view.period
    val summary = view.summary
    val now = view.now
    val comparedWith = view.comparedWith
    val prev = view.previous
    val budgetPeriod = view.budgetPeriod
    val budgetStatus = view.budgetStatus
    val recent = view.recent
    val spark = view.spark

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
                title = {
                    Column {
                        Text("FinTrack", style = MaterialTheme.typography.titleLarge)
                        Text(todayLine(), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    TextButton(onClick = onOpenTools) {
                        Icon(Icons.Outlined.Apps, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(Space.xs))
                        Text("Tools", maxLines = 1)
                    }
                    if (importState is ImportUiState.Running) CircularProgressIndicator(Modifier.padding(14.dp).size(20.dp).semantics { contentDescription = "Scanning SMS" }, strokeWidth = 2.dp)
                    else IconButton(onClick = { vm.scanInbox() }) { Icon(Icons.Outlined.Sync, "Scan SMS") }
                }
            )
        },
        floatingActionButton = { AddButton(onAdd, listState, Modifier.padding(bottom = barPad)) },
        snackbarHost = { FinSnackbarHost(snackbar, Modifier.padding(bottom = barPad)) }
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            // Clears the nav pill and the Add button, so the last row can be scrolled fully into view.
            contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding(FabClearance)),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item {
                SegmentedControl(
                    options = listOf("Month", "Week"),
                    selected = when (choice) { is PeriodChoice.Month -> 0; is PeriodChoice.Week -> 1; is PeriodChoice.Custom -> -1 },
                    onSelect = { vm.setPeriod(if (it == 0) PeriodChoice.Month(0) else PeriodChoice.Week(0)) },
                    modifier = Modifier.padding(horizontal = Gutter),
                    trailing = Icons.Outlined.DateRange,
                    trailingLabel = if (choice is PeriodChoice.Custom) "Custom range: ${period.label}" else "Pick a custom range",
                    trailingSelected = choice is PeriodChoice.Custom,
                    onTrailing = { showRange = true },
                )
            }
            item(key = "stepper") { PeriodStepper(choice, onChoose = vm::setPeriod, onPickRange = { showRange = true }) }

            // Until the database answers, every figure below would read ₹0: show the page's shape instead.
            if (!loaded) {
                item { SkeletonHero(Modifier.padding(top = Space.lg), cards = 3) }
                item { SkeletonRows(3) }
                return@LazyColumn
            }

            // ---- The hero: one number, stated plainly, then how it compares and the last six periods ----
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.md)) {
                    CapsLabel("Net spend · ${period.label}")
                    Spacer(Modifier.height(Space.sm))
                    AmountDisplay(summary.netSpendPaise, spokenLabel = "Net spend, ${period.label}", onClick = { onDrill(Bucket.SPEND, null) })
                    Spacer(Modifier.height(Space.sm))
                    val running = now in period
                    val (line, trend) = heroLine(
                        spendPaise = summary.netSpendPaise,
                        change = InsightsEngine.changePercent(summary.netSpendPaise, prev.netSpendPaise),
                        comparedWith = comparedWith.label,
                        running = running,
                        daysIn = if (running) ((now - period.start) / 86_400_000L).toInt() + 1 else period.days,
                        dailyPaise = summary.dailyAveragePaise(now),
                        projectedPaise = summary.projectedPaise(now),
                    )
                    Text(
                        line,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when (trend) {
                            Trend.UP -> Expense
                            Trend.DOWN -> Income
                            Trend.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
                        },
                    )
                }
            }

            item(key = "spark") {
                SpendChart(
                    view.sparkBars,
                    Modifier.padding(horizontal = Gutter),
                    height = 64.dp,
                    selected = sparkSelected,
                    onSelect = { sparkSelected = it },
                    onOpen = { i -> vm.setPeriod(spark[i]) },
                    compact = true,
                    caption = { i -> view.sparkPeriods[i].label },
                )
            }

            item(key = "tiles") { Tiles(summary, onDrill) }

            // Every feature one tap from Home, named, so nobody has to find it behind an icon.
            item(key = "tools") { ToolShortcuts(onOpenRoute, onOpenTools) }

            // ---- Cards, most urgent first ----
            if (reviewCount > 0) item(key = "review") {
                FinCard(Modifier.padding(horizontal = Gutter), onClick = onOpenReview, raised = true, padding = PaddingValues(horizontal = Space.lg, vertical = 14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TintedSquare(Icons.Outlined.ErrorOutline, Expense)
                        Spacer(Modifier.width(Space.lg))
                        Column(Modifier.weight(1f)) {
                            Text(reviewLine(reviewCount), style = MaterialTheme.typography.titleSmall)
                            Text("Messages FinTrack wasn't sure about", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text("Review", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            if (suggestions.isNotEmpty()) item(key = "shared") {
                val first = suggestions.first()
                ExpandableCard(
                    title = if (suggestions.size == 1) "Looks like a shared payment" else "${suggestions.size} payments look shared",
                    summary = "${money(first.totalPaise)} at ${first.title} · your share ${money(first.myShare?.amountPaise ?: first.totalPaise)}",
                    icon = Icons.Outlined.Groups,
                    modifier = Modifier.padding(horizontal = Gutter),
                    raised = true, initiallyExpanded = true, stateKey = "home-shared",
                ) {
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

            item(key = "budgets") {
                val over = budgetStatus.any { it.over }
                ExpandableCard(
                    // Budgets are monthly: a week or a range shows the month it ends in, and says which.
                    title = if (budgetPeriod.start == Periods.month(0, now).start) "Budgets" else "Budgets · ${budgetPeriod.label}",
                    summary = HomeLines.budgets(budgetStatus),
                    icon = Icons.Outlined.Savings,
                    tint = if (over) Expense else MaterialTheme.colorScheme.primary,
                    summaryColor = if (over) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Gutter),
                    raised = over, initiallyExpanded = over, stateKey = "home-budgets-${budgetPeriod.start}-$over",
                ) {
                    if (budgetStatus.isEmpty()) Text("Set a monthly limit for a category and FinTrack shows how close you are.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    budgetStatus.sortedByDescending { it.fraction }.take(5).forEach { b -> key(b.budget.category) {
                        Column(
                            Modifier.fillMaxWidth().heightIn(min = 48.dp).clickable(role = Role.Button, onClickLabel = "See payments") { onDrill(Bucket.SPEND, b.budget.category) },
                            verticalArrangement = Arrangement.spacedBy(6.dp),
                        ) {
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                Text(b.budget.category.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                                Spacer(Modifier.width(Space.md))
                                Text("${money(b.spentPaise)} of ${money(b.budget.monthlyLimitPaise)}", style = MoneyType.label, color = MaterialTheme.colorScheme.onSurfaceVariant, softWrap = false)
                            }
                            ProgressMeter(b.fraction, over = b.over)
                            if (b.over) Text("Over budget by ${money(b.spentPaise - b.budget.monthlyLimitPaise)}", color = Expense, style = MaterialTheme.typography.labelMedium)
                        }
                    } }
                    TextAction(if (budgetStatus.isEmpty()) "Set budgets" else "Manage budgets", onOpenBudgets, alignStart = true)
                }
            }

            item(key = "where") {
                val slices = summary.byCategory.filter { it.amountPaise > 0 }.take(7)
                    .map { Slice(it.category.label, it.amount, categoryColor(it.category), it.category) }
                ExpandableCard(
                    title = "Where it went",
                    summary = HomeLines.whereItWent(summary.byCategory),
                    icon = Icons.Outlined.DonutLarge,
                    modifier = Modifier.padding(horizontal = Gutter),
                    stateKey = "home-where",
                ) {
                    if (slices.isEmpty()) Text("No spending recorded in this period.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    else CategoryRing(slices, money(summary.netSpendPaise), countLabel(summary.expenseCount, "payment")) { cat -> onDrill(Bucket.SPEND, cat) }
                }
            }

            if (summary.byMerchant.isNotEmpty()) item(key = "merchants") {
                ExpandableCard(
                    title = "Top merchants",
                    summary = HomeLines.merchants(summary.byMerchant),
                    icon = Icons.Outlined.Storefront,
                    modifier = Modifier.padding(horizontal = Gutter),
                    stateKey = "home-merchants",
                ) {
                    summary.byMerchant.take(5).forEachIndexed { i, m ->
                        if (i > 0) Hairline(startInset = 48.dp)
                        Row(
                            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button) { onDrill(Bucket.SPEND, m.category) },
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CategoryIcon(m.category, size = 36.dp)
                            Spacer(Modifier.width(Space.md))
                            Column(Modifier.weight(1f)) {
                                Text(displayMerchant(m.merchant), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text("${countLabel(m.count, "payment")} · ${m.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.width(Space.md))
                            Text(money(m.amountPaise), style = MoneyType.row, softWrap = false)
                        }
                    }
                }
            }

            // ---- Money that moved but is not spend ----
            val paidBack = summary.settlementsInPaise
            val movedIn = summary.transfersInPaise - paidBack
            val cashOut = if (includeCash) 0L else summary.cashPaise
            if (summary.transfersOutPaise + summary.transfersInPaise + summary.investmentsPaise + cashOut > 0) item(key = "notcounted") {
                ExpandableCard(
                    title = "Not counted as spend",
                    summary = HomeLines.notCounted(summary.transfersOutPaise + movedIn.coerceAtLeast(0), summary.investmentsPaise, cashOut, paidBack),
                    icon = Icons.Outlined.SwapHoriz,
                    tint = Neutral,
                    modifier = Modifier.padding(horizontal = Gutter),
                    stateKey = "home-notcounted",
                ) {
                    if (summary.transfersOutPaise > 0) AmountRow("Transfers & card bill payments", summary.transfersOutPaise, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.TRANSFERS, null) }
                    if (movedIn > 0) AmountRow("Transfers in", movedIn, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.TRANSFERS, null) }
                    if (paidBack > 0) AmountRow("Paid back by friends", paidBack, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.TRANSFERS, null) }
                    if (summary.investmentsPaise > 0) AmountRow("Investments", summary.investmentsPaise, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.INVESTMENTS, null) }
                    if (cashOut > 0) AmountRow("Cash withdrawals", cashOut, Neutral, labelColor = MaterialTheme.colorScheme.onSurface) { onDrill(Bucket.CASH, null) }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Why aren't these spend?", Modifier.weight(1f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        InfoButton("Not counted as spend", NOT_COUNTED_BODY)
                    }
                }
            }

            if (summary.byAccount.size > 1) item(key = "accounts") {
                val top = summary.byAccount.maxByOrNull { it.spendPaise }
                ExpandableCard(
                    title = "By account",
                    summary = "${summary.byAccount.size} accounts" + (top?.takeIf { it.spendPaise > 0 }?.let { " · most spent on ${it.label}" } ?: ""),
                    icon = Icons.Outlined.AccountBalance,
                    modifier = Modifier.padding(horizontal = Gutter),
                    stateKey = "home-accounts",
                ) {
                    summary.byAccount.take(6).forEach { a ->
                        Row(Modifier.fillMaxWidth().heightIn(min = 40.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
                            if (a.incomePaise > 0) Text("+${money(a.incomePaise)}  ", color = Income, style = MoneyType.label, softWrap = false)
                            Text(
                                if (a.spendPaise > 0) "-${money(a.spendPaise)}" else money(0),
                                color = if (a.spendPaise > 0) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MoneyType.small.copy(fontWeight = FontWeight.SemiBold), softWrap = false,
                            )
                        }
                    }
                }
            }

            item(key = "recent-h") {
                // A past period lists its own latest payments, not this week's.
                SectionHeader(if (now in period) "Recent" else "Latest in ${period.label}", Modifier.padding(top = Space.md)) { TextAction("See all", onOpenTransactions) }
            }
            if (recent.isEmpty() && txns.isNotEmpty()) item(key = "recent-none") {
                Text(
                    "No payments in ${period.label}.",
                    Modifier.padding(horizontal = Gutter),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (txns.isEmpty()) item(key = "recent-empty") {
                EmptyState(
                    Icons.AutoMirrored.Outlined.ReceiptLong,
                    "No transactions yet",
                    "FinTrack reads bank and UPI alerts on this phone. Scan your SMS, or add a payment yourself.",
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        PrimaryButton("Scan SMS", { vm.scanInbox() }, fill = false)
                        SecondaryButton("Add one", onAdd)
                    }
                }
            }
            itemsIndexed(recent, key = { _, t -> "r${t.id}" }) { i, t ->
                Column(Modifier.animateItem()) {
                    if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                    TransactionRow(t) { onEdit(t.id) }
                }
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
                    if (s != null && e != null) vm.setPeriod(PeriodChoice.fromPicker(s, e))
                    showRange = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { showRange = false }) { Text("Cancel") } }
        ) { DateRangePicker(state, modifier = Modifier.height(480.dp)) }
    }
}

/** Income, spend and savings side by side; stacked as rows at a large font so no figure is squeezed. */
@Composable
private fun Tiles(summary: PeriodSummary, onDrill: (Bucket, Category?) -> Unit) {
    val big = LocalDensity.current.fontScale > 1.3f
    val items = listOf(
        Triple("Income", summary.incomePaise, Bucket.INCOME),
        // Net of refunds, like the figure above and the savings beside it: Income − Spent = Savings.
        Triple("Spent", summary.netSpendPaise, Bucket.SPEND),
        Triple("Savings", summary.savingsPaise, null),
    )
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        if (big) {
            FinCard(padding = PaddingValues(horizontal = Space.lg, vertical = Space.xs), spacing = 0.dp) {
                items.forEach { (label, paise, bucket) ->
                    AmountRow(label, paise, tileColor(label, paise), labelColor = MaterialTheme.colorScheme.onSurface, sign = if (label == "Income" && paise > 0) "+" else "",
                        onClick = bucket?.let { b -> { onDrill(b, null) } })
                }
            }
        } else Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
            items.forEach { (label, paise, bucket) ->
                FinCard(
                    Modifier.weight(1f),
                    onClick = bucket?.let { b -> { onDrill(b, null) } },
                    padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
                    spacing = 4.dp,
                ) {
                    Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    AmountDisplay(paise, size = com.pft.financetracker.ui.components.AmountSize.Title, color = tileColor(label, paise),
                        prefix = if (label == "Income" && paise > 0) "+" else "", spokenLabel = label)
                }
            }
        }
        if (summary.refundsPaise > 0) Row(
            Modifier.fillMaxWidth().heightIn(min = 40.dp).clickable(role = Role.Button) { onDrill(Bucket.REFUNDS, null) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // One text, so it wraps as a sentence instead of squeezing its last words into a column.
            val refundColor = Income
            Text(
                buildAnnotatedString {
                    append("Spent is after ")
                    withStyle(MoneyType.label.toSpanStyle().copy(color = refundColor)) { append("+${money(summary.refundsPaise)}") }
                    append(" back in refunds and cashback")
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun tileColor(label: String, paise: Long) = when (label) {
    "Income" -> if (paise > 0) Income else MaterialTheme.colorScheme.onSurface
    "Savings" -> moneyTone(paise)
    else -> MaterialTheme.colorScheme.onSurface
}

private const val NOT_COUNTED_BODY =
    "Moving money between your own accounts, paying a credit card bill, investing, and friends paying back their share " +
        "of a split are shown here, so they never inflate your spending or income. ATM cash is here too when you've " +
        "chosen not to count it as spend (Settings › Calculation)."

/** "Wed 7 Oct" under the app name. */
private fun todayLine(): String = SimpleDateFormat("EEE d MMM", Locale.ENGLISH).format(Date())

/**
 * The six bars of the spark chart, oldest first. They stay put while you move between the last six months (or weeks),
 * so a bar never slides out from under your finger; further back, the window ends at the chosen one. A picked range
 * shows the last six months.
 */
internal fun sparkChoices(choice: PeriodChoice): List<PeriodChoice> {
    fun window(o: Int) = if (o >= -5) (-5..0) else (o - 5..o)
    return when (choice) {
        is PeriodChoice.Week -> window(choice.offset).map { PeriodChoice.Week(it) }
        is PeriodChoice.Month -> window(choice.offset).map { PeriodChoice.Month(it) }
        is PeriodChoice.Custom -> (-5..0).map { PeriodChoice.Month(it) }
    }
}

/** Everything Home shows for one period, worked out together so every figure agrees. */
internal class HomeFigures(
    val now: Long,
    val period: Period,
    val summary: PeriodSummary,
    val comparedWith: Period,
    val previous: PeriodSummary,
    val budgetPeriod: Period,
    val budgetStatus: List<com.pft.financetracker.domain.insights.BudgetStatus>,
    val recent: List<com.pft.financetracker.domain.model.Transaction>,
    val spark: List<PeriodChoice>,
    val sparkPeriods: List<Period>,
    val sparkBars: List<ChartBar>,
) {
    companion object {
        fun of(
            txns: List<com.pft.financetracker.domain.model.Transaction>,
            budgets: List<com.pft.financetracker.domain.model.Budget>,
            choice: PeriodChoice,
            includeCash: Boolean,
            now: Long = System.currentTimeMillis(),
        ): HomeFigures {
            val period = choice.period(now)
            val summary = InsightsEngine.summarize(txns, period, includeCash)
            // Six days into a month compares with the first six days of the last one, not all of it.
            val comparedWith = Periods.sameSpanBefore(period, choice.previous(now), now)
            val previous = InsightsEngine.summarize(txns, comparedWith, includeCash)
            // Budgets are monthly limits: show the month the chosen period ends in.
            val budgetPeriod = if (choice is PeriodChoice.Month) period else Periods.month(0, minOf(period.end - 1, now))
            val budgetStatus = InsightsEngine.budgetStatus(txns, budgets, budgetPeriod, includeCash)
            val recent = if (now in period) txns.take(6) else txns.asSequence().filter { it.timestamp in period }.take(6).toList()
            val spark = sparkChoices(choice)
            val sparkPeriods = spark.map { it.period(now) }
            val weekly = choice is PeriodChoice.Week
            val bars = sparkPeriods.map { p ->
                ChartBar(sparkLabel(p, weekly), InsightsEngine.summarize(txns, p, includeCash).netSpendPaise, current = p.start == period.start)
            }
            return HomeFigures(now, period, summary, comparedWith, previous, budgetPeriod, budgetStatus, recent, spark, sparkPeriods, bars)
        }
    }
}

private data class Shortcut(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector, val route: String?)

/** Two rows of four named shortcuts: budgets, bills, cards, subscriptions, goals, tax, Ask and all tools. */
@Composable
private fun ToolShortcuts(onOpenRoute: (String) -> Unit, onOpenTools: () -> Unit) {
    val items = listOf(
        Shortcut("Budgets", Icons.Outlined.Savings, com.pft.financetracker.ui.nav.Routes.BUDGETS),
        Shortcut("Bills", Icons.AutoMirrored.Outlined.ReceiptLong, com.pft.financetracker.ui.nav.Routes.BILLS),
        Shortcut("Cards", Icons.Outlined.CreditCard, com.pft.financetracker.ui.nav.Routes.CARDS),
        Shortcut("Subscriptions", Icons.Outlined.EventRepeat, com.pft.financetracker.ui.nav.Routes.RECURRING),
        Shortcut("Goals", Icons.Outlined.Flag, com.pft.financetracker.ui.nav.Routes.GOALS),
        Shortcut("Tax", Icons.Outlined.AccountBalance, com.pft.financetracker.ui.nav.Routes.TAX),
        Shortcut("Ask", Icons.Outlined.QuestionAnswer, com.pft.financetracker.ui.nav.Routes.ASK),
        Shortcut("All tools", Icons.Outlined.Apps, null),
    )
    Column(Modifier.padding(horizontal = Gutter), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
        CapsLabel("Money tools")
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth()) {
                row.forEach { s ->
                    Column(
                        Modifier.weight(1f).clip(RoundedCornerShape(12.dp))
                            .clickable(role = Role.Button) { if (s.route != null) onOpenRoute(s.route) else onOpenTools() }
                            .padding(vertical = Space.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        TintedSquare(s.icon, MaterialTheme.colorScheme.primary, 40.dp)
                        Text(
                            s.label, style = MaterialTheme.typography.labelMedium, maxLines = 1, softWrap = false,
                            overflow = TextOverflow.Ellipsis, textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                }
            }
        }
    }
}

/** "‹ This month ›": steps one month or week at a time; the title opens the range picker. */
@Composable
private fun PeriodStepper(choice: PeriodChoice, onChoose: (PeriodChoice) -> Unit, onPickRange: () -> Unit) {
    val back = choice.step(-1)
    val forward = choice.step(1)
    val unit = if (choice is PeriodChoice.Week) "week" else "month"
    Row(Modifier.fillMaxWidth().padding(horizontal = Space.xs), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { back?.let(onChoose) }, enabled = back != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowLeft, "Previous $unit")
        }
        Text(
            choice.title(),
            Modifier.weight(1f).clickable(role = Role.Button, onClickLabel = "Pick a custom range", onClick = onPickRange).padding(vertical = Space.sm),
            style = MaterialTheme.typography.titleMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (choice is PeriodChoice.Custom) IconButton(onClick = { onChoose(PeriodChoice.Month(0)) }) {
            Icon(Icons.Outlined.Close, "Back to this month")
        } else IconButton(onClick = { forward?.let(onChoose) }, enabled = forward != null) {
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, "Next $unit")
        }
    }
}

private fun sparkLabel(p: Period, weekly: Boolean): String =
    SimpleDateFormat(if (weekly) "d MMM" else "MMM", Locale.ENGLISH).format(Date(p.start))

/** "1 item needs review" / "3 items need review". Neutral: the queue holds both SMS and statement rows. */
internal fun reviewLine(n: Int) = if (n == 1) "1 item needs review" else "$n items need review"

