package com.pft.financetracker.ui.screens.importer

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Image
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.data.local.ImportBatchEntity
import com.pft.financetracker.domain.importer.StatementRow
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.StatementUiState
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.AdaptiveRow
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.AmountSize
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CategoryIcon
import com.pft.financetracker.ui.components.ExpandableCard
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.LedgerAmountMinWidth
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.Tone
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.reasonLabel
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.surfaces

/**
 * Import a bank statement (PDF, Excel, CSV) or payment-app screenshots, as three steps read top to bottom: choose a
 * file, check what was found, saved. Everything is read on the phone: the file is opened through the system picker
 * (no storage permission) and nothing is uploaded. A locked PDF asks for its password in a sheet. Nothing is saved
 * until the preview is confirmed, and every import can be undone later.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(vm: AppViewModel, onBack: () -> Unit, onOpenSplits: () -> Unit) {
    val state by vm.statementState.collectAsState()
    val batches by vm.importBatches.collectAsState()
    var password by remember { mutableStateOf("") }
    var confirmUndo by remember { mutableStateOf<Long?>(null) }
    val haptics = rememberHaptics()

    val fileLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> if (uri != null) { password = ""; vm.importFile(uri) } }
    val imagesLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris -> vm.importImages(uris) }
    fun pickFile() = fileLauncher.launch(arrayOf(
        "application/pdf", "text/csv", "text/comma-separated-values", "text/plain", "text/html", "application/vnd.ms-excel",
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "application/octet-stream",
    ))
    fun pickImages() = imagesLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(state) {
        when (val s = state) {
            is StatementUiState.Saved -> haptics.confirm()
            is StatementUiState.Error -> haptics.reject()
            is StatementUiState.NeedsPassword -> if (s.wrong) haptics.reject() else Unit
            else -> Unit
        }
    }

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
            verticalArrangement = Arrangement.spacedBy(Space.xl),
        ) {
            StepTrack(stepOf(state))
            when (val s = state) {
                StatementUiState.Idle, is StatementUiState.Error, is StatementUiState.NeedsPassword ->
                    PickStep((s as? StatementUiState.Error)?.message, onFile = { pickFile() }, onImages = { pickImages() })
                StatementUiState.Reading -> ReadingCard()
                is StatementUiState.Preview -> PreviewStep(s, onImport = { vm.confirmImport() }, onCancel = { vm.resetStatementImport() })
                is StatementUiState.Saved -> SavedStep(
                    s.batch,
                    onOpenSplits = { vm.resetStatementImport(); onOpenSplits() },
                    onAnother = { vm.resetStatementImport() },
                    onUndo = { confirmUndo = s.batch.id },
                )
            }

            if (batches.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
                Text("Previous imports", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
                FinCard(padding = PaddingValues(start = Space.lg, end = Space.xs, top = Space.xs, bottom = Space.xs), spacing = 0.dp) {
                    batches.forEachIndexed { i, b ->
                        if (i > 0) Hairline(endInset = Space.md)
                        BatchRow(b) { confirmUndo = b.id }
                    }
                }
            }
        }
    }

    // Step 1½: a locked PDF. The sheet closes when the file opens (or the person cancels).
    (state as? StatementUiState.NeedsPassword)?.let { s ->
        FinSheet(onDismiss = { password = ""; vm.resetStatementImport() }, title = "This statement is locked") {
            Text(
                "${s.fileName} needs the password your bank gave for it. It is used only to open the file on this phone and is never stored.",
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (s.wrong) Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp), tint = Expense)
                Spacer(Modifier.width(Space.sm))
                Text(
                    "That password didn't open it. Banks often use your date of birth (DDMMYYYY) or part of your PAN or customer ID; the statement email usually says which.",
                    style = MaterialTheme.typography.bodySmall, color = Expense,
                )
            }
            val submit: () -> Unit = { if (password.isNotEmpty()) { vm.importFile(s.uri, password); password = "" } }
            OutlinedTextField(
                password, { password = it }, label = { Text("Statement password") }, singleLine = true,
                isError = s.wrong,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { submit() }),
                modifier = Modifier.fillMaxWidth(),
            )
            PrimaryButton("Open statement", { submit() }, enabled = password.isNotEmpty())
            TextAction("Cancel", { password = ""; vm.resetStatementImport() }, Modifier.fillMaxWidth())
        }
    }

    confirmUndo?.let { id ->
        AlertDialog(
            onDismissRequest = { confirmUndo = null },
            title = { Text("Undo this import?") },
            text = { Text("Every transaction this import added is removed. Transactions that were already in the app are left as they are.") },
            confirmButton = {
                TextButton(onClick = {
                    vm.undoImport(id)
                    // The "Imported" step would otherwise go on describing a batch that no longer exists.
                    if ((state as? StatementUiState.Saved)?.batch?.id == id) vm.resetStatementImport()
                    confirmUndo = null
                }) { Text("Undo import", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmUndo = null }) { Text("Cancel") } },
        )
    }
}

private val StepLabels = listOf("Choose a file", "Check what's found", "Saved")

private fun stepOf(s: StatementUiState): Int = when (s) {
    StatementUiState.Idle, is StatementUiState.Error, is StatementUiState.NeedsPassword -> 0
    StatementUiState.Reading, is StatementUiState.Preview -> 1
    is StatementUiState.Saved -> 2
}

/** Where you are in the three steps: a bar per step, filled up to the current one, with its name under it. */
@Composable
private fun StepTrack(step: Int) {
    Row(
        Modifier.fillMaxWidth().clearAndSetSemantics { contentDescription = "Step ${step + 1} of ${StepLabels.size}: ${StepLabels[step]}" },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        StepLabels.forEachIndexed { i, label ->
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Box(
                    Modifier.fillMaxWidth().height(4.dp).clip(CircleShape)
                        .background(if (i <= step) MaterialTheme.colorScheme.primary else surfaces.hairline)
                )
                Text(
                    "${i + 1}. $label",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = if (i == step) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (i == step) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Step 1: what can be imported, the two ways in, and why the last try failed (if it did). */
@Composable
private fun PickStep(error: String?, onFile: () -> Unit, onImages: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.md)) {
        Text("Add payments from a file", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
        LearnMore(
            summary = "Read on this phone; nothing is uploaded or kept. Payments you already have from SMS are skipped.",
            title = "Importing a statement",
            body = IMPORT_BODY,
        )
        if (error != null) SoftPanel(spacing = Space.xs) {
            Row(verticalAlignment = Alignment.Top) {
                Icon(Icons.Outlined.ErrorOutline, null, Modifier.size(20.dp), tint = Expense)
                Spacer(Modifier.width(Space.md))
                Column(Modifier.weight(1f)) {
                    Text("Couldn't import that", style = MaterialTheme.typography.titleSmall)
                    Text(error, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        FinCard(raised = error == null, padding = PaddingValues(horizontal = Space.lg, vertical = Space.xs), spacing = 0.dp) {
            ActionRow("Choose a statement", Icons.Outlined.Description, onFile, subtitle = "PDF (password-protected too), Excel or CSV, from any bank")
            Hairline(startInset = 52.dp)
            ActionRow("Choose screenshots or photos", Icons.Outlined.Image, onImages, subtitle = "Google Pay, PhonePe, Paytm or Amazon Pay history")
        }
    }
}

/** Step 2, while the file is read. */
@Composable
private fun ReadingCard() {
    FinCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            Spacer(Modifier.width(Space.lg))
            Column(Modifier.weight(1f)) {
                Text("Reading…", style = MaterialTheme.typography.titleSmall)
                Text("Large statements and scans take a little while.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

/** Step 2: what will be added, as a count with money in and out, then the rows themselves. Nothing is saved yet. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PreviewStep(s: StatementUiState.Preview, onImport: () -> Unit, onCancel: () -> Unit) {
    val p = s.preview
    val st = p.statement
    val inPaise = p.newRows.filter { it.type == TransactionType.CREDIT }.sumOf { it.amountPaise }
    val outPaise = p.newRows.filter { it.type != TransactionType.CREDIT }.sumOf { it.amountPaise }
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        // The hero: how many new payments, and where the file came from.
        Column {
            CapsLabel("Ready to add · ${st.format.label}")
            Spacer(Modifier.height(Space.sm))
            BigCount(p.newRows.size, if (p.newRows.size == 1) "new payment" else "new payments")
            Spacer(Modifier.height(Space.xs))
            Text(
                listOfNotNull(p.fileName, st.firstDate?.let { "${dateOnly(it)} – ${dateOnly(st.lastDate ?: it)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        AdaptiveRow(count = 2, minItemWidth = 140.dp) { m ->
            FinCard(m, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), spacing = 4.dp) {
                Text("Money in", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountDisplay(
                    inPaise, size = AmountSize.Title, color = if (inPaise > 0) Income else MaterialTheme.colorScheme.onSurface,
                    prefix = if (inPaise > 0) "+" else "", spokenLabel = "Money in",
                )
            }
            FinCard(m, padding = PaddingValues(horizontal = 14.dp, vertical = 12.dp), spacing = 4.dp) {
                Text("Money out", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                AmountDisplay(
                    outPaise, size = AmountSize.Title, color = MaterialTheme.colorScheme.onSurface,
                    prefix = if (outPaise > 0) "-" else "", spokenLabel = "Money out",
                )
            }
        }

        SoftPanel(spacing = Space.sm) {
            Line("New payments", p.newRows.size.toString())
            Line("Already in the app (skipped)", p.duplicates.size.toString())
            if (st.problems.isNotEmpty()) Line("Unclear, sent to review", st.problems.size.toString(), Expense)
            val checked = st.rows.count { it.balanceOk == true }
            if (checked > 0 || st.balanceMismatches > 0) Line(
                "Running balance",
                if (st.balanceMismatches == 0) "adds up ✓" else "${st.balanceMismatches} rows didn't add up",
                if (st.balanceMismatches == 0) Income else Expense,
            )
        }

        if (st.problems.isNotEmpty()) ExpandableCard(
            title = if (st.problems.size == 1) "1 unclear row" else "${st.problems.size} unclear rows",
            summary = "They go to Review so you can check them, not guessed",
            icon = Icons.Outlined.ErrorOutline,
            tint = Expense,
            stateKey = "import-problems",
        ) {
            st.problems.take(30).forEachIndexed { i, pr ->
                if (i > 0) Hairline()
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(reasonLabel(pr.reason), Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        pr.amountPaise?.let { a ->
                            Spacer(Modifier.width(Space.md))
                            Text(money(a), style = MoneyType.small, color = MaterialTheme.colorScheme.onSurface, maxLines = 1, softWrap = false)
                        }
                    }
                    Text(
                        listOfNotNull(pr.date?.let { dateOnly(it) }, pr.raw.trim().ifBlank { null }).joinToString(" · "),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2, overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            if (st.problems.size > 30) Text("…and ${st.problems.size - 30} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        if (p.newRows.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
            CapsLabel("New")
            FinCard(padding = PaddingValues(horizontal = Space.lg, vertical = Space.xs), spacing = 0.dp) {
                p.newRows.sortedByDescending { it.date }.take(40).forEachIndexed { i, r ->
                    if (i > 0) Hairline(startInset = 36.dp + Space.md)
                    PreviewRow(r)
                }
            }
            if (p.newRows.size > 40) Text("…and ${p.newRows.size - 40} more", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            PrimaryButton(
                if (p.newRows.isEmpty() && st.problems.isEmpty()) "Nothing new to add" else "Import ${p.newRows.size}",
                onImport,
                enabled = p.newRows.isNotEmpty() || st.problems.isNotEmpty(),
            )
            TextAction("Cancel", onCancel, Modifier.fillMaxWidth())
        }
    }
}

/** One row that will be added: category, who, when, and the signed amount in a lined-up column. */
@Composable
private fun PreviewRow(r: StatementRow) {
    val credit = r.type == TransactionType.CREDIT
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp).padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        CategoryIcon(r.category, size = 36.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(r.counterparty, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            Text(
                "${dateOnly(r.time ?: r.date)} · ${r.category.label}",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.width(Space.md))
        Box(Modifier.widthIn(min = LedgerAmountMinWidth), contentAlignment = Alignment.CenterEnd) {
            LedgerAmount(
                (if (credit) "+" else "-") + money(r.amountPaise),
                if (credit) Income else MaterialTheme.colorScheme.onSurface,
                Modifier.semantics { contentDescription = money(r.amountPaise) + if (credit) " in" else " out" },
            )
        }
    }
}

/** Step 3: how many were added, what else happened, and the way back out (Undo). */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SavedStep(batch: ImportBatchEntity, onOpenSplits: () -> Unit, onAnother: () -> Unit, onUndo: () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(Space.lg)) {
        Column {
            CapsLabel("Imported · ${batch.fileName}")
            Spacer(Modifier.height(Space.sm))
            BigCount(batch.added, "added")
            Spacer(Modifier.height(Space.xs))
            Text(
                listOfNotNull(
                    "${batch.duplicates} already in the app",
                    if (batch.needsReview > 0) "${batch.needsReview} to review" else null,
                ).joinToString(" · "),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        SoftPanel(spacing = Space.xs) {
            Text(
                "Shared payments are being checked: bills your friends paid you back for will count only your share. Look in the Split tab for anything that needs your yes.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextAction("Open Split", onOpenSplits, alignStart = true)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            PrimaryButton("Import another", onAnother, fill = false)
            TextAction("Undo this import", onUndo, Modifier.align(Alignment.CenterVertically), tone = Tone.Danger)
        }
    }
}

/** A count as the hero: the figure in the display face, its unit in words beside it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun BigCount(n: Int, unit: String) {
    FlowRow(
        Modifier.clearAndSetSemantics { contentDescription = "$n $unit" },
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(n.toString(), Modifier.alignByBaseline(), style = MaterialTheme.typography.displayMedium, maxLines = 1, softWrap = false)
        Text(unit, Modifier.alignByBaseline(), style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** A past import: file, when, how many, and Undo. */
@Composable
private fun BatchRow(b: ImportBatchEntity, onUndo: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = Space.sm)) {
            Text(b.fileName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                listOfNotNull(dateOnly(b.importedAt), countLabel(b.added, "payment") + " added", b.firstDate?.let { f -> "${dateOnly(f)} – ${dateOnly(b.lastDate ?: f)}" }).joinToString(" · "),
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconButton(onClick = onUndo) { Icon(Icons.AutoMirrored.Outlined.Undo, "Undo the import of ${b.fileName}", tint = MaterialTheme.colorScheme.primary) }
    }
}

@Composable
private fun Line(label: String, value: String, color: Color = MaterialTheme.colorScheme.onSurface) {
    Row(Modifier.fillMaxWidth()) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(Space.md))
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, color = color)
    }
}

private const val IMPORT_BODY =
    "Bank statements from any bank as PDF (password-protected too), Excel or CSV, or screenshots of your Google Pay, " +
        "PhonePe, Paytm or Amazon Pay history. The file is read on this phone; nothing is uploaded or kept. Payments " +
        "you already have from SMS are recognised and skipped, and rows FinTrack can't read clearly go to Review " +
        "instead of being guessed. You see everything before it is saved, and any import can be undone later."
