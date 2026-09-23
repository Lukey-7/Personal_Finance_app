package com.pft.financetracker.ui.screens.split

import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.split.BillItem
import com.pft.financetracker.domain.split.SplitShare
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.paiseToInput
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.components.FinCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val splits by vm.splits.collectAsState()
    val split = splits.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var settling by remember { mutableStateOf<SplitShare?>(null) }
    var settleInput by remember { mutableStateOf("") }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { items = vm.splitItems(id) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(split?.title ?: "Split") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            actions = {
                if (split != null) {
                    IconButton(onClick = {
                        // Plain-text summary through the system share sheet. You pick the app; FinTrack sends nothing itself.
                        val text = buildString {
                            append("${split.title} · ${dateOnly(split.date)} · total ${money(split.totalPaise, true)}\n")
                            append("Paid by ${split.people.getOrNull(split.payerIndex)?.name}\n")
                            split.shares.forEach { sh -> append("${split.people.getOrNull(sh.personIndex)?.name}: ${money(sh.amountPaise, true)}${if (sh.settledPaise >= sh.amountPaise && sh.personIndex != split.payerIndex) " ✓" else ""}\n") }
                        }
                        ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share split"))
                    }) { Icon(Icons.Outlined.Share, "Share") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete") }
                }
            }
        )
    }) { padding ->
        if (split == null) { Text("Split not found.", Modifier.padding(padding).padding(16.dp)); return@Scaffold }
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = com.pft.financetracker.ui.components.Gutter, top = 8.dp, end = com.pft.financetracker.ui.components.Gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            FinCard {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(money(split.totalPaise, true), style = MaterialTheme.typography.displaySmall)
                    Text("${dateOnly(split.date)} · ${split.mode.label} · paid by ${split.people.getOrNull(split.payerIndex)?.name}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    split.myShare?.let { Text("Your share: ${money(it.amountPaise, true)} (counted as your spend)", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold) }
                    split.linkedTransactionId?.let { txId -> TextButton(onClick = { onOpenTransaction(txId) }, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) { Text("Open linked transaction") } }
                    split.note?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                }
            }

            Text("Who pays what", style = MaterialTheme.typography.titleMedium)
            FinCard {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    split.shares.forEach { sh ->
                        val person = split.people.getOrNull(sh.personIndex)
                        val isPayer = sh.personIndex == split.payerIndex
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text((person?.name ?: "?") + if (isPayer) " · paid the bill" else "", fontWeight = FontWeight.SemiBold)
                                if (!isPayer) Text(
                                    when {
                                        sh.remainingPaise <= 0 -> "Settled"
                                        sh.settledPaise > 0 -> "${money(sh.settledPaise, true)} paid · ${money(sh.remainingPaise, true)} left"
                                        else -> "Owes ${money(sh.remainingPaise, true)}"
                                    },
                                    style = MaterialTheme.typography.bodySmall, color = if (sh.remainingPaise <= 0) Income else MaterialTheme.colorScheme.tertiary
                                )
                            }
                            Text(money(sh.amountPaise, true), fontWeight = FontWeight.SemiBold)
                            if (!isPayer && sh.remainingPaise > 0) {
                                Spacer(Modifier.padding(4.dp))
                                OutlinedButton(onClick = { settling = sh; settleInput = paiseToInput(sh.remainingPaise) }) { Text("Settle") }
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", fontWeight = FontWeight.Bold)
                        Text(money(split.shares.sumOf { it.amountPaise }, true), fontWeight = FontWeight.Bold)
                    }
                }
            }

            if (items.isNotEmpty()) {
                Text("Items", style = MaterialTheme.typography.titleMedium)
                FinCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        items.forEach { it ->
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                Column(Modifier.weight(1f)) {
                                    Text((if (it.quantity > 1) "${it.quantity} × " else "") + it.name)
                                    val who = it.assignedTo.mapNotNull { i -> split.people.getOrNull(i)?.name }
                                    Text(if (who.isEmpty()) "everyone" else who.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                Text(money(it.pricePaise * it.quantity, true))
                            }
                        }
                    }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    settling?.let { sh ->
        AlertDialog(
            onDismissRequest = { settling = null },
            title = { Text("Record payment from ${split?.people?.getOrNull(sh.personIndex)?.name}") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Outstanding: ${money(sh.remainingPaise, true)}. Enter what they paid you (partial is fine).")
                    OutlinedTextField(settleInput, { settleInput = it }, label = { Text("Amount (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true)
                }
            },
            confirmButton = {
                val amt = Money.parsePaise(settleInput)
                TextButton(enabled = amt != null && amt > 0, onClick = {
                    vm.settleShare(sh.id, (sh.settledPaise + (amt ?: 0L)).coerceAtMost(sh.amountPaise)); settling = null
                }) { Text("Mark paid") }
            },
            dismissButton = { TextButton(onClick = { settling = null }) { Text("Cancel") } }
        )
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this split?") },
        text = { Text("The split and its balances are removed. Any transaction it created or adjusted is left as it is.") },
        confirmButton = { TextButton(onClick = { vm.deleteSplit(id); confirmDelete = false; onBack() }) { Text("Delete") } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } }
    )
}
