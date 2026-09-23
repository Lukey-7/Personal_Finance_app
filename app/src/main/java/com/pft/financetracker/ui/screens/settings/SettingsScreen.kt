package com.pft.financetracker.ui.screens.settings

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.AiUiState
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.MarkdownText
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.money
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import androidx.compose.material3.TopAppBarDefaults
import com.pft.financetracker.ui.components.FinCard
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.CleaningServices
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageHistory
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.Storage
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.vector.ImageVector
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.CardTitle
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.bottomPadding

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
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

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
        snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = LocalBottomBarPadding.current)) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Gutter, top = 8.dp, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {

            Section("SMS import", Icons.Outlined.Sms) {
                if (!smsGranted) {
                    Text("SMS permission not granted. Transactions can still be added manually.", style = MaterialTheme.typography.bodyMedium)
                    Button(onClick = { permLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }) { Text("Grant SMS permission") }
                } else {
                    // The whole row is the toggle, so TalkBack reads "Auto-import new SMS, switch, on" rather
                    // than an unnamed switch, and the label is a tap target too.
                    Row(
                        Modifier.fillMaxWidth().toggleable(value = autoImport, role = Role.Switch, onValueChange = { vm.setAutoImport(it) }),
                        verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                    ) {
                        Text("Auto-import new SMS", Modifier.weight(1f))
                        Switch(checked = autoImport, onCheckedChange = null)
                    }
                }
                // Rows rather than side-by-side buttons: they wrap at any font size instead of clipping.
                Column {
                    if (smsGranted) {
                        ActionRow("Scan for new SMS", Icons.Outlined.Sync, { vm.scanInbox(full = false) })
                        ActionRow("Rescan the last 12 months", Icons.Outlined.ManageHistory, { vm.scanInbox(full = true) })
                    }
                    ActionRow("SMS log: every message scanned and what happened to it", Icons.Outlined.History, onOpenSmsLog)
                }
            }

            Section("Clean up duplicates", Icons.Outlined.CleaningServices) {
                Text(
                    "Looks for the same payment stored twice - usually rows imported by an older version, before the app could spot a bank and a UPI app reporting one payment. Nothing is deleted until you confirm.",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                val dupes by vm.duplicates.collectAsState()
                val scanning by vm.scanningDuplicates.collectAsState()
                val scanned by vm.duplicatesScanned.collectAsState()
                OutlinedButton(onClick = { vm.findDuplicates() }, enabled = !scanning) {
                    Text(if (scanning) "Scanning…" else "Find duplicates")
                }
                if (scanned && dupes.isEmpty() && !scanning) {
                    Text("No duplicates found - every transaction looks distinct.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                }
                if (dupes.isNotEmpty()) {
                    val total = dupes.sumOf { it.amountPaise }
                    Text(
                        "${dupes.size} duplicate${if (dupes.size > 1) "s" else ""} worth ${money(total)} in total:",
                        style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold
                    )
                    dupes.take(8).forEach { d ->
                        Text(
                            "• ${money(d.amountPaise)} ${d.keep.merchant} — keeping the ${d.keep.bankName ?: "first"} record, removing the ${d.drop.bankName ?: "other"} one",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    if (dupes.size > 8) Text("…and ${dupes.size - 8} more", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = {
                            vm.mergeDuplicates { n -> scope.launch { snackbar.showSnackbar("Removed $n duplicate transaction${if (n == 1) "" else "s"}") } }
                        }) { Text("Remove ${dupes.size}") }
                        TextButton(onClick = { vm.clearDuplicates() }) { Text("Cancel") }
                    }
                }
            }

            Section("Calculation", Icons.Outlined.Calculate) {
                Row(
                    Modifier.fillMaxWidth().toggleable(value = cashAsSpend, role = Role.Switch, onValueChange = { vm.setCountCashAsSpend(it) }),
                    verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("Count ATM cash as spend")
                        Text("Off: cash withdrawals are shown separately and left out of spend totals.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(checked = cashAsSpend, onCheckedChange = null)
                }
                Text("Spend = expenses minus refunds. Transfers between your accounts, credit-card bill payments, investments and split settlements are never counted. Tap any number on the Home tab to see the transactions behind it.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("Bill splits", Icons.AutoMirrored.Outlined.CallSplit) {
                OutlinedTextField(nameInput, { nameInput = it }, label = { Text("Your name in splits") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                // Only offered once there is something to save; a disabled pill just looked broken.
                if (nameInput.trim().isNotEmpty() && nameInput.trim() != myName) {
                    Button(onClick = { vm.setMyName(nameInput) }) { Text("Save name") }
                }
                ActionRow("Export splits as CSV", Icons.Outlined.FileDownload, {
                    exportingSplits = true
                    exportLauncher.launch("fintrack-splits-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date()) + ".csv")
                })
                Text("Bill photos are read on this phone with an offline text recogniser and are not stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            Section("AI monthly summary (optional)", Icons.Outlined.AutoAwesome) {
                Text(
                    "Uses your own OpenAI API key. Only aggregated category totals for this and last month are sent, never SMS text, merchant names, or account numbers. Nothing is sent until you tap Generate.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (hasKey) {
                    Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                        Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.width(8.dp))
                        Text("API key saved, encrypted with Android Keystore", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pad = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp)
                        Button(onClick = { vm.generateAiSummary() }, Modifier.weight(1f), enabled = aiState !is AiUiState.Loading, contentPadding = pad) { Text("Generate", maxLines = 1) }
                        OutlinedButton(onClick = { showPayload = true }, Modifier.weight(1f), contentPadding = pad) { Text("What is sent?", maxLines = 1) }
                    }
                    ActionRow("Remove key", Icons.Outlined.KeyOff, { vm.setApiKey(null); vm.clearAi() })
                } else {
                    OutlinedButton(onClick = { showPayload = true }) { Text("What would be sent?") }
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
                    is AiUiState.Result -> SoftPanel { MarkdownText(s.text) }
                    is AiUiState.Error -> Text("Error: ${s.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    AiUiState.Idle -> {}
                }
            }

            Section("Your data", Icons.Outlined.Storage) {
                Text("All data lives in an app-private database on this device. No cloud sync, no analytics, no crash reporting.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Column {
                    ActionRow("Export all transactions as CSV", Icons.Outlined.FileDownload, {
                        exportingSplits = false
                        val name = "fintrack-" + SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date()) + ".csv"
                        exportLauncher.launch(name)
                    })
                    // Destructive but rare: present, clearly red, not the loudest thing on the page. The
                    // confirmation dialog below is unchanged.
                    ActionRow("Clear all data", Icons.Outlined.DeleteForever, { confirmClear = true }, tint = MaterialTheme.colorScheme.error)
                }
            }
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
private fun Section(title: String, icon: ImageVector, content: @Composable () -> Unit) {
    FinCard {
        CardTitle(title, icon)
        content()
    }
}
