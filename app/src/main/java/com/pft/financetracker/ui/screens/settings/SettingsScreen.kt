package com.pft.financetracker.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.AiUiState
import com.pft.financetracker.ui.AppViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, onOpenSmsLog: () -> Unit) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }
    val hasKey by vm.hasApiKey.collectAsState()
    val autoImport by vm.autoImport.collectAsState()
    val cashAsSpend by vm.countCashAsSpend.collectAsState()
    val myName by vm.myName.collectAsState()
    var nameInput by remember(myName) { mutableStateOf(myName) }
    var exportingSplits by remember { mutableStateOf(false) }
    val aiState by vm.aiState.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var showPayload by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
    var smsGranted by remember { mutableStateOf(vm.hasSmsPermission()) }

    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        smsGranted = r[Manifest.permission.READ_SMS] == true
        if (smsGranted) vm.scanInbox(full = true)
    }

    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val csv = if (exportingSplits) vm.exportSplitsCsv() else vm.exportCsv()
            val ok = withContext(Dispatchers.IO) {
                runCatching { ctx.contentResolver.openOutputStream(uri)?.use { it.write(csv.toByteArray(Charsets.UTF_8)) } }.isSuccess
            }
            snackbar.showSnackbar(if (ok) "Exported CSV" else "Export failed")
        }
    }

    Scaffold(topBar = { TopAppBar(title = { Text("Settings") }) }, snackbarHost = { SnackbarHost(snackbar) }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {

            Section("SMS import") {
                if (!smsGranted) {
                    Text("SMS permission not granted. Transactions can still be added manually.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { permLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }) { Text("Grant SMS permission") }
                } else {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Text("Auto-import new SMS", Modifier.weight(1f))
                        Switch(checked = autoImport, onCheckedChange = { vm.setAutoImport(it) })
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { vm.scanInbox(full = false) }) { Text("Scan new") }
                        OutlinedButton(onClick = { vm.scanInbox(full = true) }) { Text("Rescan last 12 months") }
                    }
                }
                TextButton(onClick = onOpenSmsLog) { Text("Open SMS log: every message scanned and what happened to it") }
            }

            Section("Calculation") {
                Row(Modifier.fillMaxWidth(), verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Count ATM cash as spend")
                        Text("Off: cash withdrawals are shown separately and left out of spend totals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(checked = cashAsSpend, onCheckedChange = { vm.setCountCashAsSpend(it) })
                }
                Text("Spend = expenses minus refunds. Transfers between your accounts, credit-card bill payments, investments and split settlements are never counted. Tap any number on the Home tab to see the transactions behind it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("Bill splits") {
                OutlinedTextField(nameInput, { nameInput = it }, label = { Text("Your name in splits") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { vm.setMyName(nameInput) }, enabled = nameInput.trim() != myName) { Text("Save name") }
                    OutlinedButton(onClick = {
                        exportingSplits = true
                        exportLauncher.launch("fintrack-splits-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date()) + ".csv")
                    }) { Text("Export splits CSV") }
                }
                Text("Bill photos are read on this phone with an offline text recogniser and are not stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("AI monthly summary (optional)") {
                Text(
                    "Uses your own OpenAI API key. Only aggregated category totals for this and last month are sent, never SMS text, merchant names, or account numbers. Nothing is sent until you tap Generate.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (hasKey) {
                    Text("API key is saved (encrypted with Android Keystore).", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { vm.generateAiSummary() }, enabled = aiState !is AiUiState.Loading) { Text("Generate summary") }
                        OutlinedButton(onClick = { showPayload = true }) { Text("What is sent?") }
                        TextButton(onClick = { vm.setApiKey(null); vm.clearAi() }) { Text("Remove key") }
                    }
                } else {
                    OutlinedTextField(
                        keyInput, { keyInput = it },
                        label = { Text("OpenAI API key (sk-...)") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(onClick = { vm.setApiKey(keyInput); keyInput = "" }, enabled = keyInput.trim().length > 20) { Text("Save key") }
                }
                when (val s = aiState) {
                    is AiUiState.Loading -> Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) { CircularProgressIndicator(Modifier.height(20.dp).padding(end = 8.dp)); Text("Asking the model…") }
                    is AiUiState.Result -> Card { Text(s.text, Modifier.padding(12.dp), style = MaterialTheme.typography.bodyMedium) }
                    is AiUiState.Error -> Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    AiUiState.Idle -> {}
                }
            }

            Section("Your data") {
                Text("All data lives in an app-private database on this device. No cloud sync, no analytics, no crash reporting.", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        exportingSplits = false
                        val name = "fintrack-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date()) + ".csv"
                        exportLauncher.launch(name)
                    }) { Text("Export CSV") }
                    Button(onClick = { confirmClear = true }, colors = androidx.compose.material3.ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Clear all data") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showPayload) AlertDialog(
        onDismissRequest = { showPayload = false },
        title = { Text("Exact payload sent to OpenAI") },
        text = { Text(vm.aiPayloadPreview(), style = MaterialTheme.typography.bodySmall, modifier = Modifier.verticalScroll(rememberScrollState())) },
        confirmButton = { TextButton(onClick = { showPayload = false }) { Text("Close") } }
    )

    if (confirmClear) AlertDialog(
        onDismissRequest = { confirmClear = false },
        title = { Text("Delete everything?") },
        text = { Text("All transactions, budgets, splits, the SMS log, review items and the saved API key will be permanently erased from this device.") },
        confirmButton = { TextButton(onClick = { confirmClear = false; vm.clearAllData { scope.launch { snackbar.showSnackbar("All data cleared") } } }) { Text("Delete all") } },
        dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } }
    )
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}
