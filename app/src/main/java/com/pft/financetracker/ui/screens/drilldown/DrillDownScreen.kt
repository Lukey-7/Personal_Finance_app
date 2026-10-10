package com.pft.financetracker.ui.screens.drilldown

import com.pft.financetracker.domain.books.Books
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.SearchOff
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.DayHeader
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.model.DayNet
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.Neutral
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The list behind one number on Home: its total as the hero, then every payment that makes it up, day by day, so each
 * figure can be checked by hand. Tapping a payment opens its details; any correction updates the total at once.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun DrillDownScreen(
    vm: AppViewModel,
    bucket: InsightsEngine.Bucket,
    category: Category?,
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    /** The period the tapping screen showed; null follows Home's period. */
    range: Period? = null,
) {
    val books by vm.books.collectAsState()
    val choice by vm.period.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val period = range ?: choice.period()
    // Spend lists its refunds too, so the total is the same net figure the tapping screen showed.
    val list = remember(books, choice, range, bucket, category) { books.payments(period, bucket, category) }
    val total = Books.total(list, bucket)
    val refunds = if (bucket == InsightsEngine.Bucket.SPEND) list.count { it.flow == com.pft.financetracker.domain.model.Flow.REFUND } else 0
    val name = category?.label ?: bucketLabel(bucket)
    val days = list.groupBy { dayOf(it.timestamp) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text(name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding())) {
            // Until the database answers the total would read ₹0: show the page's shape instead.
            if (!loaded) {
                item { SkeletonHero(Modifier.padding(top = Space.md), cards = 0) }
                item { SkeletonRows(6, Modifier.padding(top = Space.lg)) }
                return@LazyColumn
            }

            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(start = Gutter, end = Gutter, top = Space.md, bottom = Space.md)) {
                    CapsLabel("$name · ${period.label}")
                    Spacer(Modifier.height(Space.sm))
                    AmountDisplay(
                        total,
                        color = heroColor(bucket),
                        prefix = if (bucket == Bucket.INCOME || bucket == Bucket.REFUNDS) "+" else "",
                        spokenLabel = "$name, ${period.label}",
                    )
                    Spacer(Modifier.height(Space.sm))
                    Text(
                        if (list.isEmpty()) "Nothing in this period"
                        else if (refunds > 0) "${countLabel(list.size - refunds, "payment")} less ${countLabel(refunds, "refund")} · the amounts below"
                        else "${countLabel(list.size, "payment")} · the sum of the amounts below",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            if (list.isEmpty()) item(key = "empty") {
                EmptyState(
                    Icons.Outlined.SearchOff,
                    "Nothing here for ${period.label}",
                    if (range != null) "Payments that count towards this figure show up here." else "Payments that count towards this figure show up here. Choose another period on Home to look further back.",
                ) { SecondaryButton("Go back", onBack) }
            }

            days.forEach { (day, dayList) ->
                stickyHeader(key = "h_$day") {
                    val (dayName, date) = dayLabel(day)
                    DayHeader(dayName, date, DayNet.of(dayList))
                }
                itemsIndexed(dayList, key = { _, t -> t.id }) { i, t ->
                    Column(Modifier.animateItem()) {
                        if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                        TransactionRow(t, showDate = false) { onEdit(t.id) }
                    }
                }
            }
        }
    }
}

/** The bucket in plain words, for the title and the label above the figure. */
private fun bucketLabel(b: Bucket): String = when (b) {
    Bucket.SPEND -> "Spent"
    Bucket.REFUNDS -> "Refunds"
    Bucket.INCOME -> "Income"
    Bucket.TRANSFERS_OUT -> "Transfers & card bill payments"
    Bucket.TRANSFERS_IN -> "Transfers in"
    Bucket.PAID_BACK -> "Paid back by friends"
    Bucket.INVESTMENTS -> "Investments"
    Bucket.CASH -> "Cash withdrawals"
    Bucket.ALL -> "Everything"
}

/** Money in is green, money that only moved is grey, spend is plain ink. */
@Composable
private fun heroColor(b: Bucket): Color = when (b) {
    Bucket.INCOME, Bucket.REFUNDS -> Income
    Bucket.TRANSFERS_OUT, Bucket.TRANSFERS_IN, Bucket.PAID_BACK, Bucket.INVESTMENTS -> Neutral
    else -> MaterialTheme.colorScheme.onBackground
}

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

/** "Today", "Yesterday", or "Mon" with "5 Oct" (and the year when it is not this one). */
private fun dayLabel(d: LocalDate): Pair<String, String?> {
    val today = LocalDate.now(zone)
    val date = d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy", Locale.ENGLISH))
    return when (d) {
        today -> "Today" to date
        today.minusDays(1) -> "Yesterday" to date
        else -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) to date
    }
}
