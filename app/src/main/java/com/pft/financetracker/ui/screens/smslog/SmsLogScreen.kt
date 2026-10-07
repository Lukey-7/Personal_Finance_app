package com.pft.financetracker.ui.screens.smslog

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.sms.Outcomes
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSnackbarHost
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.LedgerRow
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.RowIcon
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SearchField
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.fullDate
import com.pft.financetracker.ui.components.matchesAmount
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.reasonLabel
import com.pft.financetracker.ui.components.shortDate
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.Motion
import com.pft.financetracker.ui.theme.Neutral
import com.pft.financetracker.ui.theme.motion
import kotlinx.coroutines.launch

/**
 * Every SMS the importer looked at, with what happened to it and why, as a ledger. Bodies are not stored: opening a
 * row re-reads that one message from the phone's inbox. A saved row opens its payment instead.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsLogScreen(vm: AppViewModel, runId: Long?, onBack: () -> Unit, onOpenTransaction: (Long) -> Unit, onOpenReview: () -> Unit) {
    val all by vm.smsLog.collectAsState()
    val counts by vm.smsLogCounts.collectAsState()
    // One filter at a time: this scan, everything, one outcome, or the possible misses.
    var filter by rememberSaveable { mutableStateOf(if (runId != null) THIS_RUN else ALL) }
    var query by rememberSaveable { mutableStateOf("") }
    var open by rememberSaveable { mutableStateOf<Long?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun isMiss(e: SmsLogEntity) = e.outcome == Outcomes.IGNORED && e.reason == "no_transaction_hint" && e.amountPaise != null
    val misses = all.count(::isMiss)
    val list = all.filter { e ->
        when (filter) {
            THIS_RUN -> runId == null || e.runId == runId
            ALL -> true
            MISSES -> isMiss(e)
            else -> e.outcome == filter
        } && (query.isBlank() || e.sender.contains(query, true) || reasonLabel(e.reason).contains(query, true) ||
            e.reason.contains(query, true) || (e.amountPaise?.let { matchesAmount(it, query) } ?: false))
    }
    val options = buildList {
        if (runId != null) add(THIS_RUN to "This scan (${all.count { it.runId == runId }})")
        add(ALL to "All (${all.size})")
        OUTCOMES.forEach { (k, label) -> add(k to "$label (${counts[k] ?: 0})") }
        // Skipped messages that still carry an amount: worth a look, one tap sends them to Review.
        if (misses > 0 || filter == MISSES) add(MISSES to "Possible misses ($misses)")
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("SMS log") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { FinSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding())) {
            item(key = "intro") {
                LearnMore(
                    summary = "Every bank or UPI SMS FinTrack looked at, and what it did with it.",
                    title = "About the SMS log",
                    body = LOG_BODY,
                    modifier = Modifier.padding(horizontal = Gutter),
                )
            }
            item(key = "search") {
                SearchField(query, { query = it }, "Search sender, reason, amount", Modifier.padding(horizontal = Gutter, vertical = Space.sm))
            }
            item(key = "filters") {
                ChipFlow(Modifier.padding(horizontal = Gutter).padding(bottom = Space.sm)) {
                    options.forEach { (k, label) -> PillChip(filter == k, label) { filter = k; open = null } }
                }
            }

            if (misses > 0 && filter != MISSES) item(key = "misses") {
                FinCard(Modifier.padding(horizontal = Gutter).padding(bottom = Space.md), padding = PaddingValues(horizontal = Space.lg, vertical = Space.md), spacing = Space.xs) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TintedSquare(Icons.Outlined.Search)
                        Spacer(Modifier.width(Space.md))
                        Column(Modifier.weight(1f)) {
                            Text(
                                if (misses == 1) "1 possible miss" else "$misses possible misses",
                                style = MaterialTheme.typography.titleSmall,
                            )
                            Text(
                                "Skipped messages that still mention an amount",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextAction("Show them", { filter = MISSES; open = null }, alignStart = true)
                }
            } else if (filter == MISSES) item(key = "misses-note") {
                Text(
                    "Skipped messages that still mention an amount. Open one and choose “This was a transaction” if it was a payment.",
                    Modifier.padding(horizontal = Gutter).padding(bottom = Space.sm),
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (list.isEmpty()) item(key = "empty") {
                when {
                    all.isEmpty() -> EmptyState(
                        Icons.Outlined.Sms, "No SMS scanned yet",
                        "Every bank and UPI message FinTrack reads shows up here, with what it did and why.",
                    ) { PrimaryButton("Scan SMS", { vm.scanInbox() }, fill = false) }
                    filter == THIS_RUN && query.isBlank() -> EmptyState(
                        Icons.Outlined.SearchOff, "Nothing new in this scan",
                        "Every message in it was already in the log. Choose All to see earlier ones.",
                    ) { TextAction("Show all", { filter = ALL }) }
                    else -> EmptyState(
                        Icons.Outlined.SearchOff, "Nothing matches",
                        "Try another filter or a shorter search.",
                    ) { TextAction("Clear search and filter", { query = ""; filter = ALL }) }
                }
            }

            itemsIndexed(list, key = { _, e -> e.id }) { i, e ->
                val expanded = open == e.id
                Column(Modifier.animateItem().animateContentSize(motion(Motion.spatial<IntSize>()))) {
                    if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                    LogRow(e, expanded) {
                        val id = e.transactionId
                        if (e.outcome == Outcomes.SAVED && id != null) onOpenTransaction(id)
                        else open = if (expanded) null else e.id
                    }
                    if (expanded) LogDetail(
                        e, vm,
                        onOpenTransaction = onOpenTransaction,
                        onOpenReview = onOpenReview,
                        onFlag = {
                            vm.flagLogEntry(e.id) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Sent to Review" else "Couldn't read that message from your inbox") } }
                            open = null
                        },
                    )
                }
            }
        }
    }
}

/** One message as a ledger row: what happened (icon and words), the sender, and the amount it read. */
@Composable
private fun LogRow(e: SmsLogEntity, expanded: Boolean, onClick: () -> Unit) {
    val tint = outcomeColor(e.outcome)
    val (amount, amountColor) = amountOf(e)
    val what = outcomeLabel(e.outcome) + " · " + reasonLabel(e.reason)
    val opens = if (e.outcome == Outcomes.SAVED && e.transactionId != null) "opens the payment"
    else if (expanded) "message shown, tap to hide" else "tap to read the message"
    LedgerRow(
        title = e.sender,
        subtitle = what,
        amount = amount,
        amountColor = amountColor,
        leading = { RowIcon(outcomeIcon(e.outcome), tint) },
        tag = shortDate(e.receivedAt),
        spoken = listOfNotNull(e.sender, what, e.amountPaise?.let { spokenAmount(it, e.type) }, fullDate(e.receivedAt), opens).joinToString(", "),
        onClick = onClick,
    )
}

/** The opened row: the message re-read from the inbox in a sunken well, and the one thing to do next. */
@Composable
private fun LogDetail(
    e: SmsLogEntity,
    vm: AppViewModel,
    onOpenTransaction: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onFlag: () -> Unit,
) {
    var body by remember(e.id) { mutableStateOf<String?>(null) }
    var loaded by remember(e.id) { mutableStateOf(false) }
    LaunchedEffect(e.id) { body = vm.smsBody(e); loaded = true }
    Column(
        Modifier.fillMaxWidth().padding(start = RowTextInset, end = Gutter, bottom = Space.lg),
        verticalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(
            "${outcomeLabel(e.outcome)} · ${reasonLabel(e.reason)}",
            style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
        )
        Text(fullDate(e.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        e.amountPaise?.let {
            Text(
                "Amount read: ${money(it)}" + when (e.type) { "DEBIT" -> ", going out"; "CREDIT" -> ", coming in"; else -> "" },
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SoftPanel(padding = PaddingValues(horizontal = Space.lg, vertical = Space.md), spacing = Space.xs) {
            CapsLabel("Message")
            Text(
                when {
                    !loaded -> "Reading from your inbox…"
                    body == null -> "Not found in the inbox (deleted, or SMS permission turned off)."
                    else -> body!!
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (loaded && body != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        when (e.outcome) {
            Outcomes.SAVED -> e.transactionId?.let { id -> TextAction("Open the payment", { onOpenTransaction(id) }, alignStart = true) }
            Outcomes.REVIEW -> TextAction("Open review", onOpenReview, alignStart = true)
            Outcomes.DUPLICATE -> e.transactionId?.let { id -> TextAction("Open the kept payment", { onOpenTransaction(id) }, alignStart = true) }
            else -> TextAction("This was a transaction", onFlag, enabled = body != null, alignStart = true)
        }
    }
}

/** The amount as read, signed by direction: green with "+" for money in, ink with "-" for money out. */
@Composable
private fun amountOf(e: SmsLogEntity): Pair<String, Color> {
    val p = e.amountPaise ?: return "" to MaterialTheme.colorScheme.onSurface
    return when (e.type) {
        "CREDIT" -> "+" + money(p) to Income
        "DEBIT" -> "-" + money(p) to MaterialTheme.colorScheme.onSurface
        else -> money(p) to MaterialTheme.colorScheme.onSurface
    }
}

private fun spokenAmount(p: Long, type: String?): String = money(p) + when (type) { "DEBIT" -> " out"; "CREDIT" -> " in"; else -> "" }

private fun outcomeIcon(o: String): ImageVector = when (o) {
    Outcomes.SAVED -> Icons.Outlined.CheckCircle
    Outcomes.REVIEW -> Icons.Outlined.ErrorOutline
    Outcomes.DUPLICATE -> Icons.Outlined.ContentCopy
    else -> Icons.Outlined.Block
}

private const val THIS_RUN = "THIS_RUN"
private const val ALL = "ALL"
private const val MISSES = "MISSES"

private val OUTCOMES = listOf(
    Outcomes.SAVED to "Saved",
    Outcomes.REVIEW to "To review",
    Outcomes.DUPLICATE to "Duplicate",
    Outcomes.IGNORED to "Skipped",
)

private fun outcomeLabel(o: String) = when (o) { Outcomes.SAVED -> "Saved"; Outcomes.REVIEW -> "To review"; Outcomes.DUPLICATE -> "Duplicate"; else -> "Skipped" }

/** Status by icon and words first; the tint only backs them up. Red is kept for the one that needs you. */
@Composable
private fun outcomeColor(o: String): Color = when (o) {
    Outcomes.SAVED -> MaterialTheme.colorScheme.primary
    Outcomes.REVIEW -> Expense
    Outcomes.DUPLICATE -> Neutral
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

private const val LOG_BODY =
    "Every bank and UPI SMS FinTrack scanned is listed here with what happened to it: saved as a payment, sent to " +
        "Review, recognised as a duplicate, or skipped (with the reason). The message text itself is not stored. Open a " +
        "row to read it again from your inbox; a saved row opens its payment. If a skipped message was really a payment, " +
        "open it and choose “This was a transaction” to send it to Review."
