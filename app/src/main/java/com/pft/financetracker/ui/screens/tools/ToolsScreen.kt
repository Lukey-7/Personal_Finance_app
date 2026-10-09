package com.pft.financetracker.ui.screens.tools

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.CreditCard
import androidx.compose.material.icons.outlined.EventRepeat
import androidx.compose.material.icons.outlined.Flag
import androidx.compose.material.icons.outlined.QuestionAnswer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.CardRadius
import com.pft.financetracker.ui.components.CardShape
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.SkeletonBlock
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.nav.Routes
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.surfaces
import kotlinx.coroutines.delay
import java.time.format.DateTimeFormatter
import java.util.Locale

/** What a tile shows as its main line: a live figure, or words (the Ask tile, or a tool not set up yet). */
private sealed class TileValue {
    data class Figure(val text: String, val warn: Boolean = false) : TileValue()
    data class Words(val text: String) : TileValue()
}

/** One tool on the grid: its icon, name, live main line, a quiet status under it, and where it opens. */
private data class ToolTile(
    val title: String,
    val icon: ImageVector,
    val route: String,
    val value: TileValue,
    val status: String?,
    val statusWarn: Boolean = false,
)

/**
 * Money tools: the v1.3 tools as a two-column grid, each tile showing a live number (subscriptions a month, the next
 * bill, this card cycle, the top goal, net worth, tax found) so the page answers before you open anything. One
 * column at a large font. Keeps the bottom bar at five tabs.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolsScreen(vm: AppViewModel, onOpen: (route: String) -> Unit, onBack: () -> Unit) {
    val loaded by vm.loaded.collectAsState()
    val book by vm.recurringBook.collectAsState()
    val bills by vm.billStates.collectAsState()
    val cards by vm.cardSummaries.collectAsState()
    val goals by vm.goalProgress.collectAsState()
    val tax by vm.taxSummary.collectAsState()
    val fy by vm.taxYear.collectAsState()
    val columns = if (LocalDensity.current.fontScale > 1.3f) 1 else 2
    // The tool figures are worked out from flows that start empty when this page opens; give them a moment before
    // showing "None found yet" in place of a number that is about to arrive.
    val anyData = book.shown.isNotEmpty() || bills.isNotEmpty() || cards.isNotEmpty() || goals.isNotEmpty() || tax.isNotEmpty()
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(400); settled = true }

    val tiles = buildList {
        add(ToolTile("Ask FinTrack", Icons.Outlined.QuestionAnswer, Routes.ASK, TileValue.Words("Ask about your money"), "Answered on this phone"))

        val active = book.shown.count { it.counted }
        add(
            if (book.shown.isEmpty()) ToolTile("Subscriptions", Icons.Outlined.EventRepeat, Routes.RECURRING, TileValue.Words("None found yet"), null)
            else ToolTile("Subscriptions", Icons.Outlined.EventRepeat, Routes.RECURRING, TileValue.Figure(money(book.monthlyPaise)), "a month · $active active")
        )

        val overdue = bills.count { it.second is BillState.Overdue }
        val soon = bills.count { (it.second as? BillState.Upcoming)?.daysLeft?.let { d -> d <= 7 } == true }
        val next = bills.mapNotNull { (b, s) ->
            when (s) {
                is BillState.Overdue -> Triple(b, s.due, true)
                is BillState.Upcoming -> Triple(b, s.due, false)
                else -> null
            }
        }.minByOrNull { it.second }
        add(
            when {
                bills.isEmpty() -> ToolTile("Bills & EMIs", Icons.AutoMirrored.Outlined.ReceiptLong, Routes.BILLS, TileValue.Words("Add rent, phone or a loan"), null)
                next == null -> ToolTile("Bills & EMIs", Icons.AutoMirrored.Outlined.ReceiptLong, Routes.BILLS, TileValue.Words("Nothing due"), countLabel(bills.size, "bill"))
                else -> {
                    val (bill, due, late) = next
                    val amount = BillTracker.amountDue(bill)
                    val day = due.format(DayFormat)
                    val status = buildString {
                        append(bill.name)
                        append(if (late) " · overdue since $day" else " · due $day")
                        if (overdue > 1 || (overdue == 1 && !late)) append(" · $overdue overdue")
                        else if (!late && soon > 1) append(" · $soon due this week")
                    }
                    ToolTile(
                        "Bills & EMIs", Icons.AutoMirrored.Outlined.ReceiptLong, Routes.BILLS,
                        if (amount != null) TileValue.Figure(money(amount)) else TileValue.Words("Amount varies"),
                        status, statusWarn = overdue > 0,
                    )
                }
            }
        )

        add(
            if (cards.isEmpty()) ToolTile("Credit cards", Icons.Outlined.CreditCard, Routes.CARDS, TileValue.Words("Billing cycles and rewards"), null)
            else ToolTile("Credit cards", Icons.Outlined.CreditCard, Routes.CARDS, TileValue.Figure(money(cards.sumOf { it.spendPaise })), "this cycle on ${countLabel(cards.size, "card")}")
        )

        val top = goals.firstOrNull { !it.done } ?: goals.firstOrNull()
        add(
            if (top == null) ToolTile("Goals", Icons.Outlined.Flag, Routes.GOALS, TileValue.Words("Save toward something"), null)
            else ToolTile(
                "Goals", Icons.Outlined.Flag, Routes.GOALS, TileValue.Figure("${top.percent}%"),
                top.goal.name + if (goals.size > 1) " · ${goals.size - 1} more" else "",
            )
        )

        add(
            // Rent and most donations stay out of the figure, so a year with only those reads as words, not ₹0.
            if (tax.sumOf { it.claimablePaise } == 0L) ToolTile("Tax helper", Icons.Outlined.AccountBalance, Routes.TAX, TileValue.Words("80C, 80D, NPS, rent and donations"), null)
            else ToolTile("Tax helper", Icons.Outlined.AccountBalance, Routes.TAX, TileValue.Figure(money(tax.sumOf { it.claimablePaise })), "found in ${fy.label} · ${countLabel(tax.size, "section")}")
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Money tools") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Gutter, top = Space.xs, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.md),
        ) {
            item(key = "intro") {
                Text("Worked out on this phone from your transactions.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (!loaded || (!anyData && !settled)) {
                items(List(4) { it }, key = { "skel-$it" }) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                        repeat(columns) { SkeletonBlock(null, 136.dp, Modifier.weight(1f), radius = CardRadius) }
                    }
                }
                return@LazyColumn
            }
            items(tiles.chunked(columns), key = { row -> row.joinToString("|") { it.route } }) { row ->
                Row(Modifier.fillMaxWidth().height(IntrinsicSize.Min), horizontalArrangement = Arrangement.spacedBy(Space.md)) {
                    row.forEach { t -> Tile(t, { onOpen(t.route) }, Modifier.weight(1f).fillMaxHeight()) }
                    if (row.size < columns) Spacer(Modifier.weight((columns - row.size).toFloat()))
                }
            }
        }
    }
}

/** A tool tile: icon in a tinted square, name, the live figure (never wrapped; it steps down a size to fit), a quiet status. */
@Composable
private fun Tile(t: ToolTile, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val s = surfaces
    val spoken = listOfNotNull(
        t.title,
        when (val v = t.value) { is TileValue.Figure -> v.text; is TileValue.Words -> v.text },
        t.status,
    ).joinToString(", ")
    Column(
        modifier
            .clip(CardShape)
            .background(s.card)
            .border(1.dp, s.hairline, CardShape)
            .clickable(role = Role.Button, onClick = onClick)
            .clearAndSetSemantics { contentDescription = spoken; role = Role.Button }
            .heightIn(min = 48.dp)
            .padding(Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        TintedSquare(t.icon)
        Spacer(Modifier.height(Space.xs))
        Text(t.title, style = MaterialTheme.typography.titleSmall)
        when (val v = t.value) {
            is TileValue.Figure -> FitFigure(v.text, if (v.warn) Expense else MaterialTheme.colorScheme.onSurface)
            is TileValue.Words -> Text(
                v.text, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium,
                color = if (t.route == Routes.ASK) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (t.status != null) Text(
            t.status, style = MaterialTheme.typography.bodySmall,
            color = if (t.statusWarn) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/**
 * A figure in the tile face, one line always: [MoneyType.tile], stepping down a size whenever it would overflow the
 * tile's width. Plain Text (not BoxWithConstraints), so the grid row can ask for its intrinsic height.
 */
@Composable
private fun FitFigure(text: String, color: Color) {
    val ladder = listOf(MoneyType.tile, MoneyType.row, MoneyType.small)
    var step by remember(text) { mutableIntStateOf(0) }
    Text(
        text, style = ladder[step], color = color, maxLines = 1, softWrap = false,
        onTextLayout = { r -> if (r.didOverflowWidth && step < ladder.lastIndex) step++ },
    )
}

private val DayFormat: DateTimeFormatter = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
