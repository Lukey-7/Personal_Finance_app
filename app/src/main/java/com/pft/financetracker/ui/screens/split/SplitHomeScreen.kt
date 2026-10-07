package com.pft.financetracker.ui.screens.split

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.split.Balance
import com.pft.financetracker.domain.split.Split
import com.pft.financetracker.domain.split.SplitCalculator
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AddButton
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.AmountSize
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SkeletonBlock
import com.pft.financetracker.ui.components.SkeletonCard
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.rememberAtTop
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.surfaces

/**
 * Split home, a ledger: a balance board (who owes you, whom you owe, person by person), then every split as a receipt
 * with what is still open. "New split" folds into a circle once the list scrolls.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitHomeScreen(vm: AppViewModel, onNew: () -> Unit, onOpen: (Long) -> Unit) {
    val splits by vm.splits.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val balances = remember(splits) { SplitCalculator.balances(splits) }
    val owedToMe = balances.filter { it.netPaise > 0 }.sumOf { it.netPaise }
    val iOwe = -balances.filter { it.netPaise < 0 }.sumOf { it.netPaise }
    val listState = rememberLazyListState()
    val atTop = rememberAtTop(listState)

    val barPad = LocalBottomBarPadding.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = { TopAppBar(title = { Text("Split bills") }, colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)) },
        // With no splits yet the empty state carries the button, so a floating one would only repeat it.
        floatingActionButton = { if (loaded && splits.isNotEmpty()) AddButton(onNew, Modifier.padding(bottom = barPad), expanded = atTop, text = "New split") },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            state = listState,
            contentPadding = PaddingValues(start = Gutter, top = Space.sm, end = Gutter, bottom = bottomPadding(FabClearance)),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            // Until the database answers, the board would read ₹0 and the list "No splits yet": show their shape instead.
            if (!loaded) {
                item { SkeletonCard(height = 168.dp) }
                item { SkeletonBlock(80.dp, 16.dp) }
                items(3) { SkeletonCard(height = 112.dp) }
                return@LazyColumn
            }

            if (splits.isEmpty()) {
                item {
                    EmptyState(
                        Icons.AutoMirrored.Outlined.CallSplit,
                        "No splits yet",
                        "Snap a bill or type an amount, add the people, and FinTrack works out who pays what. Only your share counts as your spending.",
                    ) { PrimaryButton("New split", onNew, fill = false) }
                }
                return@LazyColumn
            }

            item(key = "board") { BalanceBoard(owedToMe, iOwe, balances) }

            item(key = "splits-title") {
                Text(
                    "Splits", style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.padding(top = Space.sm).semantics { heading() },
                )
            }
            items(splits, key = { it.id }) { s -> SplitReceipt(s) { onOpen(s.id) } }
        }
    }
}

/**
 * The balance board: "Owed to you" beside "You owe", each a figure with the people behind it. At a large font the two
 * columns stack, so neither squeezes the other.
 */
@Composable
private fun BalanceBoard(owedToMe: Long, iOwe: Long, balances: List<Balance>) {
    val stacked = LocalDensity.current.fontScale > 1.3f
    val theyOwe = balances.filter { it.netPaise > 0 }
    val youOwe = balances.filter { it.netPaise < 0 }
    val line = surfaces.hairline
    FinCard(raised = true) {
        if (stacked) {
            BoardColumn("Owed to you", owedToMe, theyOwe, owedToYou = true, Modifier.fillMaxWidth())
            Hairline()
            BoardColumn("You owe", iOwe, youOwe, owedToYou = false, Modifier.fillMaxWidth())
        } else {
            // A hairline down the middle, drawn rather than laid out: the figures size themselves to their column, and
            // a height-matching divider would need intrinsic measurements they cannot give.
            Row(
                Modifier.fillMaxWidth().drawBehind {
                    val x = size.width / 2f
                    drawLine(line, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                },
                horizontalArrangement = Arrangement.spacedBy(Space.xl),
            ) {
                BoardColumn("Owed to you", owedToMe, theyOwe, owedToYou = true, Modifier.weight(1f))
                BoardColumn("You owe", iOwe, youOwe, owedToYou = false, Modifier.weight(1f))
            }
        }
    }
}

/** One side of the board: its total, then each person on that side with what they owe or are owed. */
@Composable
private fun BoardColumn(label: String, total: Long, people: List<Balance>, owedToYou: Boolean, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        CapsLabel(label)
        AmountDisplay(
            total,
            size = AmountSize.Medium,
            // Green only for money coming back to you, and only when there is some; what you owe stays plain ink.
            color = if (owedToYou && total > 0) Income else MaterialTheme.colorScheme.onSurface,
            spokenLabel = label,
        )
        if (people.isEmpty()) {
            Text(
                if (owedToYou) "Nobody owes you" else "You don't owe anyone",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else {
            Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                people.forEach { b ->
                    val amount = money(kotlin.math.abs(b.netPaise))
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = 40.dp)
                            .clearAndSetSemantics { contentDescription = if (owedToYou) "${b.name} owes you $amount" else "You owe ${b.name} $amount" },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(b.name, 32.dp)
                        Spacer(Modifier.width(Space.sm + 2.dp))
                        // Name over amount: a half-width column has no room for both on one line.
                        Column(Modifier.weight(1f)) {
                            Text(b.name, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            LedgerAmount(amount, if (owedToYou) Income else MaterialTheme.colorScheme.onSurface, style = MoneyType.small)
                        }
                    }
                }
            }
        }
    }
}

/**
 * A split as a receipt: what and when, a tear line, then the total and where it stands, in words ("₹1,600 open",
 * "Settled", "Tap to review"), never colour alone.
 */
@Composable
private fun SplitReceipt(s: Split, onClick: () -> Unit) {
    ReceiptCard(onClick = onClick, onClickLabel = "Open split") {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TintedSquare(if (s.isAuto) Icons.Outlined.AutoAwesome else Icons.AutoMirrored.Outlined.ReceiptLong, size = 36.dp)
            Spacer(Modifier.width(Space.md))
            Column(Modifier.weight(1f)) {
                Text(s.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        when { s.isSuggestion -> "Suggested"; s.isAuto -> "Auto-split"; else -> null },
                        dateOnly(s.date), countLabel(s.people.size, "person", "people"),
                        if (s.isAuto) "your share ${money(s.myShare?.amountPaise ?: 0)}" else "${s.mode.label} · paid by ${s.people.getOrNull(s.payerIndex)?.name ?: "?"}",
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TearLine()
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                when {
                    s.isSuggestion -> Text("Tap to review", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                    s.settled -> {
                        Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = Income)
                        Spacer(Modifier.width(Space.xs + 2.dp))
                        Text("Settled", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold, color = Income)
                    }
                    else -> Text("${money(s.outstandingPaise)} open", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.width(Space.md))
            LedgerAmount(money(s.totalPaise), MaterialTheme.colorScheme.onSurface)
        }
    }
}
