package com.pft.financetracker.ui.screens.settings

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.DocumentsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Backup
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.DeleteForever
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.KeyOff
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.ManageHistory
import androidx.compose.material.icons.outlined.Memory
import androidx.compose.material.icons.outlined.Restore
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Widgets
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.pft.financetracker.data.ai.NanoAi
import com.pft.financetracker.ui.AiUiState
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.ImportUiState
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.AdaptiveRow
import com.pft.financetracker.ui.components.CardTitle
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.MarkdownText
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.fullDate
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/*
 * The body of each settings page. Every control here is the one that lived on the old single Settings scroll, with the
 * same calls into the view model; only the layout and the wording around it changed.
 */

private fun stamp(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.ENGLISH).format(Date())

/** SMS & import: permission, auto-import, scans, the SMS log, statement import, learned shapes, duplicates. */
@Composable
internal fun SmsSection(vm: AppViewModel, snackbar: SnackbarHostState, onOpenSmsLog: () -> Unit, onOpenImport: () -> Unit) {
    val scope = rememberCoroutineScope()
    val autoImport by vm.autoImport.collectAsState()
    val templates by vm.learnedTemplates.collectAsState()
    val importState by vm.importState.collectAsState()
    val dupes by vm.duplicates.collectAsState()
    val findingDupes by vm.scanningDuplicates.collectAsState()
    val dupesScanned by vm.duplicatesScanned.collectAsState()
    var smsGranted by remember { mutableStateOf(vm.hasSmsPermission()) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        smsGranted = r[Manifest.permission.READ_SMS] == true
        if (smsGranted) vm.scanInbox(full = true)
    }

    SettingsGroup("SMS") {
        if (!smsGranted) {
            Text("FinTrack can't read your SMS yet. You can still add transactions yourself.", style = MaterialTheme.typography.bodyMedium)
            PrimaryButton("Allow SMS access", { permLauncher.launch(arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)) }, fill = false)
        } else {
            SwitchRow("Auto-import new SMS", autoImport, { vm.setAutoImport(it) }, subtitle = "Bank messages are read as they arrive")
        }
        // Rows rather than side-by-side buttons: they wrap at any font size instead of clipping.
        Column {
            if (smsGranted) {
                if (importState is ImportUiState.Running) BusyLine("Reading your bank messages…")
                ActionRow("Scan for new SMS", Icons.Outlined.Sync, { vm.scanInbox(full = false) })
                ActionRow("Rescan the last 12 months", Icons.Outlined.ManageHistory, { vm.scanInbox(full = true) })
            }
            ActionRow("SMS log", Icons.Outlined.History, onOpenSmsLog, subtitle = "Every message scanned and what happened to it")
        }
    }

    SettingsGroup("Statements") {
        ActionRow("Import a statement or screenshots", Icons.Outlined.UploadFile, onOpenImport, subtitle = "PDF, Excel or CSV, or payment-app screenshots")
        Note("Read on this phone; payments you already have from SMS are skipped.")
    }

    // Shapes learned from Review. Only the bank's fixed wording is kept: amounts, names and numbers are masked.
    if (templates.isNotEmpty()) SettingsGroup("Learned from Review · ${templates.size}") {
        LearnMore(
            "FinTrack reads the next message from these senders by itself.",
            "Learned from Review",
            "When you confirm a message in Review, FinTrack remembers that sender's wording and reads the next one by " +
                "itself. Only the bank's fixed wording is kept: amounts, names and numbers are masked. Forget a shape and " +
                "messages like it go back to Review.",
        )
        Column {
            templates.forEachIndexed { i, t ->
                if (i > 0) Hairline()
                Row(Modifier.fillMaxWidth().padding(vertical = Space.xs), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${t.senderCore} · ${if (t.type == "CREDIT") "money in" else "money out"}", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(t.skeleton, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    }
                    IconButton(onClick = { vm.deleteTemplate(t.id) }) { Icon(Icons.Outlined.Delete, "Forget this shape from ${t.senderCore}") }
                }
            }
        }
    }

    SettingsGroup("Duplicates") {
        LearnMore(
            "Finds the same payment stored twice. Nothing is deleted until you confirm.",
            "Clean up duplicates",
            "Looks for the same payment stored twice, usually by an older version that couldn't yet tell a bank and a UPI " +
                "app were reporting one payment. Nothing is deleted until you confirm.",
        )
        SecondaryButton(if (findingDupes) "Looking…" else "Find duplicates", { vm.findDuplicates() }, enabled = !findingDupes)
        if (dupesScanned && dupes.isEmpty() && !findingDupes) {
            Text("No duplicates found.", style = MaterialTheme.typography.bodyMedium)
        }
        if (dupes.isNotEmpty()) {
            val total = dupes.sumOf { it.amountPaise }
            Text(
                "${dupes.size} duplicate${if (dupes.size > 1) "s" else ""} worth ${money(total)} in total:",
                style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold,
            )
            Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                dupes.take(8).forEach { d ->
                    Text(
                        "• ${money(d.amountPaise)} ${d.keep.merchant}: keeps the ${d.keep.bankName ?: "first"} record, removes the ${d.drop.bankName ?: "other"} one",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                if (dupes.size > 8) Text("…and ${dupes.size - 8} more", style = MaterialTheme.typography.bodySmall)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
                PrimaryButton("Remove ${dupes.size}", {
                    vm.mergeDuplicates { n -> scope.launch { snackbar.showSnackbar("Removed ${countLabel(n, "duplicate")}") } }
                }, fill = false)
                TextAction("Cancel", { vm.clearDuplicates() })
            }
        }
    }
}

/** Calculation: whether ATM cash counts as spend, and what "spend" means. */
@Composable
internal fun CalculationSection(vm: AppViewModel) {
    val cashAsSpend by vm.countCashAsSpend.collectAsState()
    SettingsGroup {
        SwitchRow(
            "Count ATM cash as spend", cashAsSpend, { vm.setCountCashAsSpend(it) },
            subtitle = "Off: cash withdrawals are shown separately and left out of spend totals.",
        )
        LearnMore(
            "Spend is what you paid out, minus refunds.",
            "How spend is worked out",
            "Spend = expenses minus refunds. Transfers between your accounts, credit-card bill payments, investments and " +
                "split settlements are never counted. Tap any number on the Home tab to see the transactions behind it.",
        )
    }
}

/** Splits: your name in splits, bill photos, and split intelligence (with its optional AI). */
@Composable
internal fun SplitsSection(vm: AppViewModel) {
    val myName by vm.myName.collectAsState()
    val splitAi by vm.splitAi.collectAsState()
    val hasKey by vm.hasApiKey.collectAsState()
    var nameInput by remember(myName) { mutableStateOf(myName) }
    val haptics = rememberHaptics()

    SettingsGroup("You") {
        OutlinedTextField(nameInput, { nameInput = it }, label = { Text("Your name in splits") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        // Only offered once there is something to save; a disabled pill just looked broken.
        if (nameInput.trim().isNotEmpty() && nameInput.trim() != myName) {
            PrimaryButton("Save name", { haptics.confirm(); vm.setMyName(nameInput) }, fill = false)
        }
        Note("Bill photos are read on this phone with an offline text recogniser and are not stored.")
    }

    SettingsGroup("Split intelligence") {
        LearnMore(
            "Only your share of a group payment counts as your spending.",
            "Split intelligence",
            "When you pay for a group and friends pay you back, only your share counts as your spending. FinTrack spots " +
                "this by itself: clear cases are applied (tap Undo on any of them), unclear ones wait for your yes in the " +
                "Split tab.\n\nWith AI on, uneven shares and one transfer covering two bills are worked out too. Sent to " +
                "OpenAI: amounts, days and payment types only; people appear as \"Person A\", never by name. Without an " +
                "OpenAI key, the rules on this phone still handle the clear cases.",
        )
        SwitchRow(
            "Use AI for unclear cases",
            checked = splitAi && hasKey,
            onChange = { vm.setSplitAi(it) },
            subtitle = if (hasKey) "Sends amounts, days and payment types only, never names"
            else "Needs an OpenAI key (Settings › AI). The rules on this phone still handle the clear cases.",
            enabled = hasKey,
        )
        ActionRow("Check again now", Icons.Outlined.Sync, { vm.refreshSplits() })
    }
}

/** Reminders: bill and renewal notifications, asking for the notification permission when needed. */
@Composable
internal fun RemindersSection(vm: AppViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val remindersOn by vm.remindersEnabled.collectAsState()
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        vm.setRemindersEnabled(granted)
        if (!granted) scope.launch { snackbar.showSnackbar("Notifications are blocked. Allow them in Android settings to get reminders.") }
    }

    SettingsGroup {
        SwitchRow(
            "Remind me before bills and renewals", remindersOn,
            { on ->
                if (on && ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                } else vm.setRemindersEnabled(on)
            },
            subtitle = "A notification a few days before a bill, EMI or subscription is due",
        )
        Note("Worked out on this phone; amounts are hidden on the lock screen.")
    }
}

/** Backup: a passphrase-locked file you keep, and restoring from one. */
@Composable
internal fun BackupSection(vm: AppViewModel, snackbar: SnackbarHostState) {
    val scope = rememberCoroutineScope()
    val lastBackup by vm.lastBackupAt.collectAsState()
    val backupBusy by vm.backupBusy.collectAsState()
    val ctx = LocalContext.current
    // The passphrase comes first, then the file picker, so cancelling never leaves an empty backup file behind. The
    // passphrase itself is only held in memory (never in saved state); if Android recreated the screen while the
    // picker was open, it is asked for again, and a file left without one is deleted.
    var askPassphrase by rememberSaveable { mutableStateOf(false) }
    var pendingPassphrase by remember { mutableStateOf<CharArray?>(null) }
    var backupAsk by rememberSaveable { mutableStateOf<Uri?>(null) }
    var restoreAsk by rememberSaveable { mutableStateOf<Uri?>(null) }
    fun discard(uri: Uri) {
        runCatching { DocumentsContract.deleteDocument(ctx.contentResolver, uri) }
    }
    fun write(uri: Uri, pw: CharArray) {
        scope.launch {
            val error = vm.writeBackup(uri, pw)
            if (error != null) discard(uri)
            snackbar.showSnackbar(error ?: "Backup saved")
        }
    }
    val backupLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        val pw = pendingPassphrase
        pendingPassphrase = null
        when {
            uri == null -> pw?.fill(' ')
            pw != null -> write(uri, pw)
            else -> backupAsk = uri
        }
    }
    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoreAsk = uri }

    SettingsGroup {
        Text(
            if (lastBackup == 0L) "No backup yet." else "Last backup: ${fullDate(lastBackup)}",
            style = MaterialTheme.typography.titleMedium,
        )
        LearnMore(
            "One file with all your data, locked with a passphrase only you know.",
            "About backups",
            "One file with all your data, locked with a passphrase only you know (AES-256). Save it anywhere you like: " +
                "another folder, a USB drive, your own cloud. FinTrack never uploads it, and without the passphrase nobody " +
                "can open it, not even you.",
        )
        val busy = backupBusy
        if (busy != null) {
            Text(busy, style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(Modifier.fillMaxWidth())
        } else {
            Column {
                ActionRow("Back up now", Icons.Outlined.Backup, { askPassphrase = true })
                ActionRow("Restore from a backup", Icons.Outlined.Restore, { restoreLauncher.launch(arrayOf("*/*")) })
            }
        }
    }

    if (askPassphrase) {
        BackupPassphraseDialog(
            restoring = false,
            onConfirm = { pw ->
                askPassphrase = false
                pendingPassphrase = pw
                backupLauncher.launch("FinTrack-${java.time.LocalDate.now()}.ftbackup")
            },
            onDismiss = { askPassphrase = false },
        )
    }
    // Only after the screen was recreated while the file picker was open: the file exists, the passphrase was lost.
    backupAsk?.let { uri ->
        BackupPassphraseDialog(
            restoring = false,
            onConfirm = { pw -> backupAsk = null; write(uri, pw) },
            onDismiss = { backupAsk = null; discard(uri) },
        )
    }
    restoreAsk?.let { uri ->
        BackupPassphraseDialog(
            restoring = true,
            onConfirm = { pw -> restoreAsk = null; scope.launch { snackbar.showSnackbar(vm.restoreBackup(uri, pw) ?: "Restored. Everything now matches the backup.") } },
            onDismiss = { restoreAsk = null },
        )
    }
}

/** Widget: hiding amounts, and pinning the widget to the home screen. */
@Composable
internal fun WidgetSection(vm: AppViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val widgetHide by vm.widgetHideAmounts.collectAsState()

    SettingsGroup {
        SwitchRow(
            "Hide amounts on the widget", widgetHide, { vm.setWidgetHideAmounts(it) },
            subtitle = "Shows ₹•••• instead of figures, since anyone can see your home screen",
        )
        // Launchers that support it show their own "Add to home screen" sheet; others need a long-press on the home screen.
        ActionRow("Add the widget to your home screen", Icons.Outlined.Widgets, {
            scope.launch {
                val ok = runCatching {
                    androidx.glance.appwidget.GlanceAppWidgetManager(ctx).requestPinGlanceAppWidget(com.pft.financetracker.ui.widget.FinTrackWidgetReceiver::class.java)
                }.getOrDefault(false)
                if (!ok) snackbar.showSnackbar("Long-press your home screen, choose Widgets and find FinTrack.")
            }
        })
        Note("Long-press the app icon or use the widget to note a purchase quickly.")
    }
}

/** AI: Gemini Nano on this phone, and the optional OpenAI key, monthly summary and its exact payload. */
@Composable
internal fun AiSection(vm: AppViewModel) {
    val hasKey by vm.hasApiKey.collectAsState()
    val keyBuiltIn by vm.apiKeyBuiltIn.collectAsState()
    val aiState by vm.aiState.collectAsState()
    val useNano by vm.useNano.collectAsState()
    val askOpenAi by vm.askUseOpenAi.collectAsState()
    val nanoStatus by vm.nanoStatus.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var changingKey by remember { mutableStateOf(false) }
    var showPayload by remember { mutableStateOf(false) }
    val haptics = rememberHaptics()
    LaunchedEffect(Unit) { vm.refreshNano() }

    SettingsGroup("On this phone") {
        CardTitle("On-device AI (Gemini Nano)", Icons.Outlined.Memory)
        when (nanoStatus) {
            NanoAi.Status.READY -> Note("Ready on this phone. Ask hands it questions its rules don't understand, with your totals only; nothing leaves the phone.")
            NanoAi.Status.DOWNLOADABLE -> Note("This phone supports it. Android downloads the model once (about 1–2 GB, over Wi-Fi is best).")
            NanoAi.Status.DOWNLOADING -> Note("Android is downloading the model…")
            NanoAi.Status.UNSUPPORTED -> LearnMore(
                "Not available on this phone. Ask still answers with its rules.",
                "On-device AI",
                "Gemini Nano needs Android AICore: Pixel 9 or later, Galaxy S24 or later and some others. Ask still answers with its rules.",
            )
            null -> Note("Checking…")
        }
        if (nanoStatus == NanoAi.Status.DOWNLOADABLE) ActionRow("Download the model", Icons.Outlined.Download, { vm.downloadNano() })
        if (nanoStatus == NanoAi.Status.READY) SwitchRow("Use it in Ask", useNano, { vm.setUseNano(it) })
    }

    SettingsGroup("OpenAI (optional)") {
        CardTitle("Ask with ChatGPT", Icons.Outlined.AutoAwesome)
        SwitchRow("Answer Ask with ChatGPT", askOpenAi && hasKey, { vm.setAskUseOpenAi(it) }, enabled = hasKey)
        LearnMore(
            if (hasKey) "Each question goes to OpenAI with a summary of your payments, so answers can use all your numbers."
            else "Needs an OpenAI key, below.",
            "What Ask sends",
            "Your question, the last few questions and answers, a year of monthly totals, categories, your main payees " +
                "and your latest 120 payments (date, payee name, category and amount). Phone numbers and UPI IDs are " +
                "blanked out. Never SMS text, account numbers, reference numbers or notes. With this off, Ask answers on the phone.",
        )
        Hairline()
        CardTitle("AI monthly summary", Icons.Outlined.AutoAwesome)
        LearnMore(
            "Uses your own OpenAI key. Nothing is sent until you tap Generate.",
            "What the summary sends",
            "Uses your own OpenAI API key. Only category totals for this month and last are sent, never SMS text, merchant " +
                "names or account numbers. Nothing is sent until you tap Generate.",
        )
        if (hasKey) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Lock, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Space.sm))
                Note(
                    if (keyBuiltIn) "Using the key built into this app, encrypted with Android Keystore"
                    else "Using your own key, encrypted with Android Keystore",
                )
            }
            // Side by side where they fit, stacked at a large font.
            AdaptiveRow(count = 2, minItemWidth = 140.dp) { m ->
                PrimaryButton("Generate", { vm.generateAiSummary() }, m, enabled = aiState !is AiUiState.Loading)
                SecondaryButton("What is sent?", { showPayload = true }, m)
            }
            // Change replaces the key in place; either action makes the key yours, so the built-in one is not restored
            // on the next launch.
            Column {
                if (changingKey) {
                    KeyField(keyInput) { keyInput = it }
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), modifier = Modifier.padding(top = Space.sm)) {
                        PrimaryButton("Save new key", {
                            haptics.confirm()
                            vm.setApiKey(keyInput); keyInput = ""; changingKey = false; vm.clearAi()
                        }, enabled = keyInput.trim().length > 20, fill = false)
                        TextAction("Cancel", { keyInput = ""; changingKey = false })
                    }
                } else {
                    ActionRow("Change key", Icons.Outlined.Key, { changingKey = true })
                }
                ActionRow("Remove key", Icons.Outlined.KeyOff, { vm.setApiKey(null); vm.clearAi(); changingKey = false })
            }
        } else {
            SecondaryButton("What would be sent?", { showPayload = true })
            KeyField(keyInput) { keyInput = it }
            PrimaryButton("Save key", { haptics.confirm(); vm.setApiKey(keyInput); keyInput = "" }, enabled = keyInput.trim().length > 20, fill = false)
        }
        when (val s = aiState) {
            is AiUiState.Loading -> BusyLine("Writing your summary…")
            is AiUiState.Result -> SoftPanel { MarkdownText(s.text) }
            is AiUiState.Error -> Text("Couldn't get a summary. ${s.message}", color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            AiUiState.Idle -> {}
        }
    }

    if (showPayload) FinSheet({ showPayload = false }, title = "Exact payload sent to OpenAI") {
        // Built from your payments once they have loaded, never from the empty lists the app starts with.
        val ready by vm.loaded.collectAsState()
        SoftPanel {
            if (ready) Text(vm.aiPayloadPreview(), style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
            else BusyLine("Loading your figures…")
        }
        PrimaryButton("Close", { showPayload = false })
    }
}

@Composable
private fun KeyField(value: String, onChange: (String) -> Unit) {
    OutlinedTextField(
        value, onChange,
        label = { Text("OpenAI API key (sk-...)") }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth(),
    )
}

/** Your data: where it lives, CSV exports, and clearing everything (last, red, typed confirmation). */
@Composable
internal fun DataSection(vm: AppViewModel, snackbar: SnackbarHostState) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    var exportingSplits by remember { mutableStateOf(false) }
    var confirmClear by remember { mutableStateOf(false) }
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

    SoftPanel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TintedSquare(Icons.Outlined.Lock)
            Spacer(Modifier.width(Space.md))
            Text(
                "All data lives in an app-private database on this device. No cloud sync, no analytics, no crash reporting.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.weight(1f),
            )
        }
    }

    SettingsGroup("Export") {
        Column {
            ActionRow("Export all transactions as CSV", Icons.Outlined.FileDownload, {
                exportingSplits = false
                exportLauncher.launch("fintrack-${stamp()}.csv")
            })
            ActionRow("Export splits as CSV", Icons.Outlined.FileDownload, {
                exportingSplits = true
                exportLauncher.launch("fintrack-splits-${stamp()}.csv")
            })
        }
    }

    // Destructive and rare: last on the page, clearly red, and guarded by typing DELETE.
    SettingsGroup("Start over") {
        ActionRow(
            "Clear all data", Icons.Outlined.DeleteForever, { confirmClear = true },
            tint = MaterialTheme.colorScheme.error,
            subtitle = "Erases everything FinTrack keeps on this phone",
        )
    }

    if (confirmClear) ClearAllSheet(
        onConfirm = { confirmClear = false; vm.clearAllData { scope.launch { snackbar.showSnackbar("All data cleared") } } },
        onDismiss = { confirmClear = false },
    )
}
