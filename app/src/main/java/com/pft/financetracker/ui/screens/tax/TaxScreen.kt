package com.pft.financetracker.ui.screens.tax

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.TaxSection
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.shortDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/** Payments that may count toward tax deductions this financial year, per section, with a CSV for the records. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaxScreen(vm: AppViewModel, onBack: () -> Unit) {
    val fy by vm.taxYear.collectAsState()
    val totals by vm.taxSummary.collectAsState()
    val txns by vm.transactions.collectAsState()
    val byId = remember(txns) { txns.associateBy { it.id } }
    var tagging by remember { mutableStateOf<Transaction?>(null) }
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val export = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) { runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(vm.taxCsv(fy).toByteArray()) } }.isSuccess }
            snackbar.showSnackbar(if (ok) "Exported ${fy.label}" else "Export failed")
        }
    }
    val thisFy = FinancialYear.of(LocalDate.now())

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Tax helper") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = { if (totals.isNotEmpty()) IconButton(onClick = { export.launch("FinTrack-tax-${fy.label.replace(' ', '-')}.csv") }) { Icon(Icons.Outlined.Download, "Export CSV") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = 8.dp, end = Gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item {
                ChipRow(inset = 0.dp) {
                    PillChip(fy == thisFy, thisFy.label) { vm.setTaxYear(thisFy) }
                    val last = FinancialYear(thisFy.startYear - 1)
                    PillChip(fy == last, last.label) { vm.setTaxYear(last) }
                }
            }
            item {
                Text("For your records, not tax advice. Found from payee names (old tax regime sections); tap any payment to change its section. Limits shown are the usual caps.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (totals.isEmpty()) item { EmptyState(Icons.Outlined.ReceiptLong, "Nothing found for ${fy.label}. Insurance, PPF, ELSS, NPS, rent and donations show up here.") }
            items(totals, key = { it.section.name }) { t ->
                FinCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("${t.section.code} · ${t.section.label}", style = MaterialTheme.typography.titleMedium)
                            Text(t.section.limitPaise?.let { "${money(t.totalPaise)} of ${money(it)} limit" } ?: money(t.totalPaise), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Text(money(t.claimablePaise), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    }
                    t.section.limitPaise?.let { limit ->
                        LinearProgressIndicator(progress = { (t.totalPaise.toFloat() / limit).coerceIn(0f, 1f) }, modifier = Modifier.fillMaxWidth().height(6.dp), gapSize = 0.dp, drawStopIndicator = {})
                    }
                    Column {
                        t.transactionIds.mapNotNull { byId[it] }.forEach { tx ->
                            Row(Modifier.fillMaxWidth().clickable { tagging = tx }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                                Text(tx.merchant, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
                                Text("${shortDate(tx.timestamp)} · ${money(tx.amountPaise)}", style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
            }
        }
    }

    tagging?.let { tx -> TaxTagDialog(tx.merchant, onPick = { vm.tagTax(tx.id, it); tagging = null }, onRule = { vm.clearTaxTag(tx.id); tagging = null }, onDismiss = { tagging = null }) }
}

/** Pick a section for a payment, mark it "not a deduction", or go back to the automatic rule. */
@Composable
fun TaxTagDialog(title: String, onPick: (TaxSection?) -> Unit, onRule: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                TaxSection.entries.forEach { s ->
                    Text("${s.code} · ${s.label}", Modifier.fillMaxWidth().clickable { onPick(s) }.padding(vertical = 10.dp))
                }
                Text("Not a deduction", Modifier.fillMaxWidth().clickable { onPick(null) }.padding(vertical = 10.dp))
            }
        },
        confirmButton = { TextButton(onClick = onRule) { Text("Use the automatic rule") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
