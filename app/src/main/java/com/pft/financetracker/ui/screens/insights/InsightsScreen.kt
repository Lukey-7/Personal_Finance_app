package com.pft.financetracker.ui.screens.insights

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Apps
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Insights
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.insights.Insight
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.CardPadding
import com.pft.financetracker.ui.components.CategoryIcon
import com.pft.financetracker.ui.components.ChartBar
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.LedgerAmountMinWidth
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.ProgressMeter
import com.pft.financetracker.ui.components.RowIconGap
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SectionHeader
import com.pft.financetracker.ui.components.SegmentedControl
import com.pft.financetracker.ui.components.SkeletonCard
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.SpendChart
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.categoryColor
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.motion
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * Insights: one chart of net spend you can read by touch (monthly or weekly), the categories that moved most against
 * the same days before, ranked, and "reduce spending" tips you can dismiss for the session.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InsightsScreen(
    vm: AppViewModel,
    onOpenBudgets: () -> Unit,
    onOpenTools: () -> Unit = {},
    /** Opens the payments behind a row, for the period this screen shows (not Home's). */
    onDrill: (Bucket, Category?, Period) -> Unit = { _, _, _ -> },
) {
    val books by vm.books.collectAsState()
    val budgets by vm.budgets.collectAsState()
    val loaded by vm.loaded.collectAsState()
    var weekly by rememberSaveable { mutableStateOf(false) }
    var selected by remember(weekly) { mutableStateOf<Int?>(null) }
    val dismissed = rememberSaveable(
        saver = listSaver<SnapshotStateList<String>, String>(save = { it.toList() }, restore = { it.toMutableStateList() }),
    ) { mutableStateListOf<String>() }

    val now = System.currentTimeMillis()
    val periods = if (weekly) (5 downTo 0).map { Periods.week(-it) } else (5 downTo 0).map { Periods.month(-it) }
    val bars = periods.mapIndexed { i, p -> ChartBar(shortLabel(p, weekly, i, periods), books.summary(p).netSpendPaise, current = now in p) }
    // Category rows compare like with like, the same way as the trend line: this period so far against the same days before.
    val current = if (weekly) Periods.week() else Periods.month()
    val before = Periods.sameSpanBefore(current, if (weekly) Periods.week(-1) else Periods.month(-1), now)
    val trends = books.trends(current, before).sortedByDescending { it.magnitude }
    val curByCat = books.summary(current).byCategory.associate { it.category to it.amountPaise }
    val prevByCat = books.summary(before).byCategory.associate { it.category to it.amountPaise }
    val biggest = trends.maxOfOrNull { t -> t.category?.let { curByCat[it] } ?: 0L }?.coerceAtLeast(1L) ?: 1L
    val suggestions = books.tips(budgets, now)
    // Tips look at this month, so their payments open on this month.
    val tipsPeriod = Periods.month(0, now)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Insights") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    // Named, not bare icons: a new user can see where budgets and the other tools are.
                    androidx.compose.material3.TextButton(onClick = onOpenBudgets) { Text("Budgets", maxLines = 1) }
                    androidx.compose.material3.TextButton(onClick = onOpenTools) { Text("Tools", maxLines = 1) }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "period") {
                SegmentedControl(
                    options = listOf("Monthly", "Weekly"),
                    selected = if (weekly) 1 else 0,
                    onSelect = { weekly = it == 1 },
                    modifier = Modifier.padding(horizontal = Gutter),
                )
            }

            // Until the database answers, every figure below would read ₹0: show the page's shape instead.
            if (!loaded) {
                item { SkeletonCard(Modifier.padding(horizontal = Gutter, vertical = Space.sm), height = 240.dp) }
                item { SkeletonRows(4) }
                return@LazyColumn
            }

            if (books.all.isEmpty()) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Outlined.Insights,
                        "Nothing to compare yet",
                        "Insights compare your spending month by month and week by week. They fill in as payments arrive from SMS or you add them.",
                    ) { PrimaryButton("Scan SMS", { vm.scanInbox() }, fill = false) }
                }
                return@LazyColumn
            }

            // ---- The one chart ----
            item(key = "chart") {
                FinCard(Modifier.padding(horizontal = Gutter, vertical = Space.xs)) {
                    Column {
                        Text("Net spending trend", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                        Text("Expenses minus refunds. Transfers and investments excluded.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    SpendChart(
                        bars,
                        selected = selected,
                        onSelect = { selected = it },
                        caption = { i -> periods[i].label },
                    )
                    // The current period is still running, so it is compared with the same days of the one before.
                    val cur = books.summary(periods.last()).netSpendPaise
                    val span = Periods.sameSpanBefore(periods.last(), periods[periods.lastIndex - 1], now)
                    val spanSpend = books.summary(span).netSpendPaise
                    val change = InsightsEngine.changePercent(cur, spanSpend)
                    if (change != null) {
                        Hairline()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            val tone = when { change > 0 -> Expense; change < 0 -> Income; else -> MaterialTheme.colorScheme.onSurfaceVariant }
                            if (change != 0) Icon(if (change > 0) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, Modifier.size(18.dp), tint = tone)
                            if (change != 0) Spacer(Modifier.width(Space.sm))
                            Text(
                                (if (change >= 0) "$change% more" else "${-change}% less") + " than ${span.label} (${money(spanSpend)})",
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }

            // ---- Category trends, biggest change first ----
            item(key = "trends-h") {
                Column(Modifier.padding(top = Space.lg)) {
                    SectionHeader("Category changes")
                    Text(
                        "This ${if (weekly) "week" else "month"} so far, against ${before.label}",
                        Modifier.padding(horizontal = Gutter),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trends.isEmpty()) item(key = "trends-none") { Hint("Not enough data for comparisons yet.") }
            else item(key = "trends") {
                // One ledger: rows flush, hairlines lined up with the text.
                Column(Modifier.fillMaxWidth()) {
                    trends.forEachIndexed { i, t ->
                        if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                        TrendRow(
                            insight = t,
                            nowPaise = t.category?.let { curByCat[it] } ?: 0L,
                            beforePaise = t.category?.let { prevByCat[it] } ?: 0L,
                            biggest = biggest,
                            onOpen = t.category?.let { cat -> { onDrill(Bucket.SPEND, cat, current) } },
                        )
                    }
                }
            }

            // ---- Tips, dismissible for this session ----
            item(key = "tips-h") { SectionHeader("Reduce spending", Modifier.padding(top = Space.lg)) }
            if (suggestions.isEmpty()) item(key = "tips-none") { Hint("Suggestions appear once there are a few weeks of transactions.") }
            else item(key = "tips") {
                Column(Modifier.fillMaxWidth().padding(horizontal = Gutter)) {
                    suggestions.forEach { s ->
                        AnimatedVisibility(
                            visible = s.title !in dismissed,
                            enter = expandVertically(motion(Motion.spatial())) + fadeIn(motion(Motion.effects())),
                            exit = shrinkVertically(motion(Motion.spatial())) + fadeOut(motion(Motion.effects())),
                        ) {
                            TipCard(
                                s,
                                onDismiss = { dismissed.add(s.title) },
                                onOpen = s.category?.let { cat -> { onDrill(Bucket.SPEND, cat, tipsPeriod) } },
                                modifier = Modifier.padding(bottom = Space.md),
                            )
                        }
                    }
                    if (suggestions.all { it.title in dismissed }) {
                        Text("You've put every tip away for now.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextAction("Show tips again", { dismissed.clear() }, alignStart = true)
                    }
                }
            }
        }
    }
}

/**
 * One category that moved: its icon and name, a short bar of this period's spend (scaled to the biggest row), the figure,
 * and which way it went as an arrow and words, so the direction never rests on colour. Tap to see its payments.
 */
@Composable
private fun TrendRow(insight: Insight, nowPaise: Long, beforePaise: Long, biggest: Long, onOpen: (() -> Unit)?) {
    val cat = insight.category
    val name = cat?.label ?: insight.title
    val (icon, words, tone) = when (insight.severity) {
        Insight.Severity.WARN -> Triple(Icons.Outlined.ArrowUpward, "Up ${insight.magnitude}%", Expense)
        Insight.Severity.GOOD -> Triple(Icons.Outlined.ArrowDownward, "Down ${insight.magnitude}%", Income)
        Insight.Severity.INFO -> Triple(Icons.Outlined.Add, "New", MaterialTheme.colorScheme.onSurfaceVariant)
    }
    val from = if (beforePaise > 0) "from ${money(beforePaise)}" else "nothing before"
    Row(
        Modifier.fillMaxWidth()
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClickLabel = "See payments", onClick = onOpen) else Modifier)
            .clearAndSetSemantics {
                contentDescription = "$name, ${money(nowPaise)}, ${words.lowercase(Locale.ENGLISH)}, $from"
                if (onOpen != null) role = Role.Button
            }
            .heightIn(min = 64.dp)
            .padding(horizontal = Gutter, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (cat != null) CategoryIcon(cat) else TintedSquare(Icons.Outlined.Insights)
        Spacer(Modifier.width(RowIconGap))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            ProgressMeter((nowPaise.toFloat() / biggest).coerceIn(0f, 1f), color = cat?.let { categoryColor(it) } ?: MaterialTheme.colorScheme.primary, height = 6.dp)
            Text(from.replaceFirstChar { it.uppercase() }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.width(Space.md))
        Column(Modifier.widthIn(min = LedgerAmountMinWidth), horizontalAlignment = Alignment.End) {
            LedgerAmount(money(nowPaise), MaterialTheme.colorScheme.onSurface)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, Modifier.size(14.dp), tint = tone)
                Spacer(Modifier.width(Space.xs))
                Text(words, style = MaterialTheme.typography.labelMedium, color = tone, fontWeight = FontWeight.SemiBold, maxLines = 1, softWrap = false)
            }
        }
    }
}

/** A "reduce spending" tip: what we noticed, why it matters, a way to its payments, and a cross to put it away. */
@Composable
private fun TipCard(i: Insight, onDismiss: () -> Unit, onOpen: (() -> Unit)?, modifier: Modifier = Modifier) {
    val (icon, tint) = tipLook(i.severity)
    FinCard(modifier, padding = PaddingValues(start = CardPadding, top = Space.lg, end = Space.xs, bottom = if (onOpen != null) Space.xs else Space.lg), spacing = Space.xs) {
        Row(verticalAlignment = Alignment.Top) {
            TintedSquare(icon, tint)
            Spacer(Modifier.width(Space.md + Space.xs))
            Column(Modifier.weight(1f).padding(top = Space.xs), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                Text(i.title, style = MaterialTheme.typography.titleSmall, modifier = Modifier.semantics { heading() })
                Text(i.body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Dismiss tip", tint = MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (onOpen != null) Row {
            Spacer(Modifier.width(36.dp + Space.md + Space.xs))
            TextAction("See payments", onOpen, alignStart = true)
        }
    }
}

@Composable
private fun tipLook(s: Insight.Severity): Pair<ImageVector, Color> = when (s) {
    Insight.Severity.WARN -> Icons.Outlined.WarningAmber to Expense
    Insight.Severity.GOOD -> Icons.Outlined.ThumbUp to Income
    Insight.Severity.INFO -> Icons.Outlined.Lightbulb to MaterialTheme.colorScheme.primary
}

@Composable
private fun Hint(text: String) {
    Text(text, Modifier.padding(horizontal = Gutter), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/**
 * Each bar label gets about 50dp. Months read "Apr"; the year is added ("Jan '26") only on a bar where it
 * changes from the bar before. Weeks read as their first day ("14 Sep").
 */
private fun shortLabel(p: Period, weekly: Boolean, i: Int, all: List<Period>): String {
    fun fmt(pattern: String) = SimpleDateFormat(pattern, Locale.ENGLISH).format(Date(p.start))
    if (weekly) return fmt("d MMM")
    val year = { t: Long -> Calendar.getInstance().apply { timeInMillis = t }.get(Calendar.YEAR) }
    val yearChanged = i > 0 && year(all[i - 1].start) != year(p.start)
    return if (yearChanged) fmt("MMM ''yy") else fmt("MMM")
}
