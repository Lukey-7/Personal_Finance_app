package com.pft.financetracker.ui.screens.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.pft.financetracker.ui.components.Space
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.StatementUiState
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CardTitle
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income

/**
 * Import a bank statement (PDF, Excel, CSV) or payment-app screenshots. Everything is read on the phone: the file
 * is opened through the system picker (no storage permission) and nothing is uploaded. A preview shows what will be
 * added before anything is saved; each import can be undone later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: AppViewModel, onBack: () -> Unit, onOpenSplits: () -> Unit) {
    val state by vm.statementState.collectAsState()
    val batches by vm.importBatches.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmUndo by remember { mutableStateOf<Long?>(null) }

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { password = ""; vm.importFile(uri) } }
    val imagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris -> vm.importImages(uris) }
    fun pickFile() = fileLauncher.launch(arrayOf(
        "application/pdf", "text/csv", "text/comma-separated-values", "text/plain", "text/html", "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream",
    ))
    fun pickImages() = imagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Import statement") },
                navigationIcon = { IconButton(onClick = { vm.resetStatementImport(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(start = Gutter, top = Space.sm, end = Gutter, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            when (val s = state) {
                StatementUiState.Idle, is StatementUiState.Error -> {
                    FinCard {
                        CardTitle("Add transactions from a file")
                        Text(
                            "Bank statements from any bank as PDF (password-protected too), Excel or CSV, or screenshots of your Google Pay, PhonePe, Paytm or Amazon Pay history. " +
                                "Read on this phone; nothing is uploaded or kept. Transactions you already have from SMS are recognised and skipped.",
                            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Column {
                            ActionRow("Choose a statement (PDF, Excel, CSV)", Icons.Outlined.Description, { pickFile() })
                            ActionRow("Choose screenshots or photos", Icons.Outlined.Image, { pickImages() })
                        }
                    }
                    if (s is StatementUiState.Error) SoftPanel {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp), tint = Expense)
                            Spacer(Modifier.width(Space.md))
                            Text(s.message, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                StatementUiState.Reading -> FinCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Space.md))
                        Text("Reading… large statements and scans take a little while.")
                    }
                }
                is StatementUiState.NeedsPassword -> FinCard {
                    CardTitle("${s.fileName} is password-protected", Icons.Outlined.Lock)
                    Text(
                        if (s.wrong) "That password didn't open it. Banks often use your date of birth (DDMMYYYY) or part of your PAN or customer ID; the statement email usually says which."
                        else "Enter the password your bank gave for this statement. It is used only to open the file on this phone and is never stored.",
                        style = MaterialTheme.typography.bodySmall, color = if (s.wrong) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedTextField(
                        password, { password = it }, label = { Text("Statement password") }, singleLine = true,
                        visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    PrimaryButton("Open statement", { vm.importFile(s.uri, password); password = "" }, enabled = password.isNotEmpty())
                    TextButton(onClick = { vm.resetStatementImport() }) { Text("Cancel") }
                }
                is StatementUiState.Preview -> PreviewCard(s, onImport = { vm.confirmImport() }, onCancel = { vm.resetStatementImport() })
                is StatementUiState.Saved -> FinCard {
                    CardTitle("Imported", Icons.Outlined.CheckCircle)
                    Text(
                        "${s.batch.added} added · ${s.batch.duplicates} already in the app" + (if (s.batch.needsReview > 0) " · ${s.batch.needsReview} to review" else ""),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        "Shared payments are being checked: bills your friends paid you back for will count only your share. Look in the Split tab for anything that needs your yes.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                        SecondaryButton("Open Split", { vm.resetStatementImport(); onOpenSplits() })
                        SecondaryButton("Import another", { vm.resetStatementImport() })
                    }
                }
            }

            if (batches.isNotEmpty()) {
                Text("Previous imports", style = MaterialTheme.typography.titleMedium)
                FinCard {
                    batches.forEachIndexed { i, b ->
                        if (i > 0) Hairline()
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(b.fileName, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(
                                    listOfNotNull(dateOnly(b.importedAt), "${b.added} added", b.firstDate?.let { f -> "${dateOnly(f)} – ${dateOnly(b.lastDate ?: f)}" }).joinToString(" · "),
                                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                            IconButton(onClick = { confirmUndo = b.id }) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo this import") }
                        }
                    }
                }
            }
        }
    }

    confirmUndo?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmUndo = null },
            title = { Text("Undo this import?") },
            text = { Text("Every transaction this import added is removed. Transactions that were already in the app are left as they are.") },
            confirmButton = { TextButton(onClick = { vm.undoImport(id); confirmUndo = null }) { Text("Undo import") } },
            dismissButton = { TextButton(onClick = { confirmUndo = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun PreviewCard(s: StatementUiState.Preview, onImport: () -> Unit, onCancel: () -> Unit) {
    val p = s.preview
    val st = p.statement
    FinCard {
        CardTitle(p.fileName, Icons.Outlined.Description)
        Text(
            listOfNotNull(st.format.label, st.firstDate?.let { "${dateOnly(it)} – ${dateOnly(st.lastDate ?: it)}" }).joinToString(" · "),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        SoftPanel {
            Line("New transactions", p.newRows.size.toString())
            Line("Already in the app (skipped)", p.duplicates.size.toString())
            if (st.problems.isNotEmpty()) Line("Unclear, sent to review", st.problems.size.toString(), Expense)
            val checked = st.rows.count { it.balanceOk == true }
            if (checked > 0 || st.balanceMismatches > 0) Line(
                "Running balance",
                if (st.balanceMismatches == 0) "adds up ✓" else "${st.balanceMismatches} rows didn't add up",
                if (st.balanceMismatches == 0) Income else Expense,
            )
        }
        if (p.newRows.isNotEmpty()) {
            CapsLabel("New")
            p.newRows.sortedByDescending { it.date }.take(40).forEach { r ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.counterparty, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text("${dateOnly(r.time ?: r.date)} · ${r.category.label}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text(
                        (if (r.type == TransactionType.CREDIT) "+" else "−") + money(r.amountPaise),
                        color = if (r.type == TransactionType.CREDIT) Income else MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold,
                    )
                }
            }
            if (p.newRows.size > 40) Text("…and ${p.newRows.size - 40} more", style = MaterialTheme.typography.bodySmall)
        }
        PrimaryButton(if (p.newRows.isEmpty() && st.problems.isEmpty()) "Nothing new to add" else "Import ${p.newRows.size}", onImport, enabled = p.newRows.isNotEmpty() || st.problems.isNotEmpty())
        TextButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun Line(label: String, value: String, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}
