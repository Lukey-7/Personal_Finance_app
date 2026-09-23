package com.pft.financetracker.ui.screens.smslog

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Block
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.foundation.layout.width
import androidx.compose.ui.graphics.vector.ImageVector
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.IconCircle
import com.pft.financetracker.ui.components.SearchField
import com.pft.financetracker.ui.components.bottomPadding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.sms.Outcomes
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.fullDate
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.Neutral
import kotlinx.coroutines.launch
import androidx.compose.material3.TopAppBarDefaults
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PillChip

/**
 * Every SMS the importer looked at, with what happened to it and why. Bodies are not stored; tapping a row
 * re-reads that one message from the phone's inbox.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SmsLogScreen(vm: AppViewModel, runId: Long?, onBack: () -> Unit, onOpenTransaction: (Long) -> Unit, onOpenReview: () -> Unit) {
    val all by vm.smsLog.collectAsState()
    val counts by vm.smsLogCounts.collectAsState()
    var outcome by remember { mutableStateOf<String?>(null) }
    var onlyThisRun by remember { mutableStateOf(runId != null) }
    var query by remember { mutableStateOf("") }
    var selected by remember { mutableStateOf<SmsLogEntity?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val list = all.filter { e ->
        (outcome == null || e.outcome == outcome) &&
            (!onlyThisRun || runId == null || e.runId == runId) &&
            (query.isBlank() || e.sender.contains(query, true) || e.reason.contains(query, true) || (e.amountPaise?.let { money(it) } ?: "").contains(query))
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
        snackbarHost = { SnackbarHost(snackbar) }
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Text(
                "Every bank/UPI SMS scanned, and what the app did with it. Message text is not stored; tap a row to read it from your inbox.",
                Modifier.padding(horizontal = Gutter, vertical = 8.dp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            SearchField(query, { query = it }, "Search sender, reason, amount", Modifier.padding(horizontal = Gutter, vertical = 8.dp))
            ChipRow(Modifier.padding(bottom = 8.dp)) {
                if (runId != null) PillChip(onlyThisRun, "This import") { onlyThisRun = !onlyThisRun }
                PillChip(outcome == null, "All (${all.size})") { outcome = null }
                listOf(Outcomes.SAVED to "Saved", Outcomes.REVIEW to "Review", Outcomes.DUPLICATE to "Duplicate", Outcomes.IGNORED to "Ignored").forEach { (k, label) ->
                    PillChip(outcome == k, "$label (${counts[k] ?: 0})") { outcome = if (outcome == k) null else k }
                }
            }
            if (list.isEmpty()) EmptyState(Icons.Outlined.SearchOff, if (all.isEmpty()) "No SMS scanned yet." else "No log entries match.")
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding())) {
                items(list, key = { it.id }) { e -> LogRow(e) { selected = e } }
            }
        }
    }

    selected?.let { e ->
        var body by remember(e.id) { mutableStateOf<String?>(null) }
        var loaded by remember(e.id) { mutableStateOf(false) }
        LaunchedEffect(e.id) { body = vm.smsBody(e); loaded = true }
        AlertDialog(
            onDismissRequest = { selected = null },
            title = { Text(e.sender) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(fullDate(e.receivedAt), style = MaterialTheme.typography.labelMedium)
                    Text("${outcomeLabel(e.outcome)} · ${e.reason.replace('_', ' ')}", style = MaterialTheme.typography.bodyMedium, color = outcomeColor(e.outcome), fontWeight = FontWeight.SemiBold)
                    e.amountPaise?.let { Text("Amount read: ${money(it, decimals = true)}" + (e.type?.let { t -> " ($t)" } ?: "")) }
                    Spacer(Modifier.height(4.dp))
                    Text("Message", style = MaterialTheme.typography.labelLarge)
                    Text(
                        when { !loaded -> "Reading from inbox…"; body == null -> "Not found in the inbox (deleted, or SMS permission revoked)."; else -> body!! },
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            },
            confirmButton = {
                when (e.outcome) {
                    Outcomes.SAVED -> e.transactionId?.let { id -> TextButton(onClick = { selected = null; onOpenTransaction(id) }) { Text("Open transaction") } }
                    Outcomes.REVIEW -> TextButton(onClick = { selected = null; onOpenReview() }) { Text("Open review") }
                    Outcomes.DUPLICATE -> e.transactionId?.let { id -> TextButton(onClick = { selected = null; onOpenTransaction(id) }) { Text("Open kept record") } }
                    else -> TextButton(enabled = body != null, onClick = {
                        vm.flagLogEntry(e.id) { ok -> scope.launch { snackbar.showSnackbar(if (ok) "Sent to review queue" else "Could not read message") } }
                        selected = null
                    }) { Text("This was a transaction") }
                }
            },
            dismissButton = { TextButton(onClick = { selected = null }) { Text("Close") } }
        )
    }
}

@Composable
private fun LogRow(e: SmsLogEntity, onClick: () -> Unit) {
    FinCard(Modifier.padding(horizontal = Gutter, vertical = 4.dp), onClick = onClick, padding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Status shown by an icon as well as its colour, so it survives colour blindness and greyscale.
            val c = outcomeColor(e.outcome)
            IconCircle(outcomeIcon(e.outcome), tint = c, background = c.copy(alpha = 0.12f))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(e.sender, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(outcomeLabel(e.outcome) + " · " + e.reason.replace('_', ' '), style = MaterialTheme.typography.labelMedium, color = c, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fullDate(e.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
            }
            e.amountPaise?.let {
                Spacer(Modifier.width(10.dp))
                Text(money(it), fontWeight = FontWeight.SemiBold, color = if (e.type == "CREDIT") Income else Expense)
            }
        }
    }
}

private fun outcomeIcon(o: String): ImageVector = when (o) {
    Outcomes.SAVED -> Icons.Outlined.CheckCircle
    Outcomes.REVIEW -> Icons.Outlined.ErrorOutline
    Outcomes.DUPLICATE -> Icons.Outlined.ContentCopy
    else -> Icons.Outlined.Block
}

private fun outcomeLabel(o: String) = when (o) { Outcomes.SAVED -> "Saved"; Outcomes.REVIEW -> "Needs review"; Outcomes.DUPLICATE -> "Duplicate"; else -> "Ignored" }

@Composable
private fun outcomeColor(o: String): Color = when (o) {
    Outcomes.SAVED -> Income
    Outcomes.REVIEW -> MaterialTheme.colorScheme.tertiary
    Outcomes.DUPLICATE -> Neutral
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}
