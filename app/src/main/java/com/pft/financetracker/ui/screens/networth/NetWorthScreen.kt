package com.pft.financetracker.ui.screens.networth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.ArrowDownward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.ShowChart
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.networth.Asset
import com.pft.financetracker.domain.networth.AssetKind
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.CasUiState
import com.pft.financetracker.ui.components.ActionRow
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CardPadding
import com.pft.financetracker.ui.components.ChartBar
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.FinSnackbarHost
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SegmentedControl
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.SpendChart
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.Tone
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.edgeToEdge
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.rememberHaptics
import kotlinx.coroutines.delay
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Net worth: what you own minus what you owe, as the screen's hero, with how it moved month by month, then where it
 * comes from: kinds of asset, bank balances from SMS, mutual funds from a CAS statement, and what you typed in.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetWorthScreen(vm: AppViewModel, onBack: () -> Unit) {
    val nw by vm.netWorth.collectAsState()
    val assets by vm.assets.collectAsState()
    val balances by vm.accountBalances.collectAsState()
    val holdings by vm.holdings.collectAsState()
    val history by vm.netWorthHistory.collectAsState()
    val cas by vm.casState.collectAsState()
    var editing by remember { mutableStateOf<Asset?>(null) }
    var chartSelected by remember { mutableStateOf<Int?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { vm.importCas(it, null) } }
    val importCas = { pick.launch("application/pdf") }
    val addAsset = { editing = Asset(name = "", kind = AssetKind.FD, valuePaise = 0) }

    // These flows start empty and fill from the database a moment later; wait that moment before calling the page empty.
    val hasData = nw.ownPaise != 0L || nw.owePaise != 0L || assets.isNotEmpty() || balances.isNotEmpty() || holdings.isNotEmpty()
    var settled by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { delay(400); settled = true }

    LaunchedEffect(cas) {
        when (val s = cas) {
            is CasUiState.Done -> { snackbar.showSnackbar("Read ${countLabel(s.count, "fund")} from the statement"); vm.resetCas() }
            is CasUiState.Error -> { snackbar.showSnackbar(s.message); vm.resetCas() }
            else -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Net worth") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { FinSnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(start = Gutter, top = Space.xs, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            if (cas is CasUiState.Reading) item(key = "reading") {
                Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
                    Text("Reading the statement…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    LinearProgressIndicator(Modifier.fillMaxWidth())
                }
            }

            if (!hasData && !settled) {
                item(key = "skeleton") { SkeletonHero(Modifier.edgeToEdge(), cards = 3) }
                return@LazyColumn
            }

            if (!hasData) {
                item(key = "empty") {
                    EmptyState(
                        Icons.Outlined.ShowChart,
                        "Your net worth starts here",
                        "Add what you own and what you owe, or import a mutual-fund statement. Bank balances fill in from SMS that show one.",
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            PrimaryButton("Add an asset or a debt", addAsset, fill = false)
                            SecondaryButton("Import a CAS PDF", importCas)
                        }
                    }
                }
                return@LazyColumn
            }

            // ---- Hero ----
            item(key = "hero") {
                Column(Modifier.fillMaxWidth().padding(top = Space.md)) {
                    CapsLabel("Net worth")
                    Spacer(Modifier.height(Space.sm))
                    AmountDisplay(nw.totalPaise, color = if (nw.totalPaise < 0) Expense else MaterialTheme.colorScheme.onBackground, spokenLabel = "Net worth")
                    Spacer(Modifier.height(Space.sm))
                    Text("You own ${money(nw.ownPaise)} · you owe ${money(nw.owePaise)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (history.size >= 2) {
                        val change = history.last().totalPaise - history[history.size - 2].totalPaise
                        val up = change >= 0
                        Spacer(Modifier.height(Space.xs))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(if (up) Icons.Outlined.ArrowUpward else Icons.Outlined.ArrowDownward, null, Modifier.size(16.dp), tint = if (up) Income else Expense)
                            Spacer(Modifier.width(Space.xs))
                            Text((if (up) "Up " else "Down ") + money(kotlin.math.abs(change)) + " since last month", style = MaterialTheme.typography.bodyMedium, color = if (up) Income else Expense)
                        }
                    }
                }
            }

            // ---- Month by month (only when every month is above zero, so each bar reads as a figure) ----
            val recent = history.takeLast(6)
            if (recent.size >= 2 && recent.all { it.totalPaise > 0 }) item(key = "history") {
                FinCard {
                    Text("Month by month", style = MaterialTheme.typography.titleSmall)
                    SpendChart(
                        recent.mapIndexed { i, s -> ChartBar(monthLabel(s.month, "MMM"), s.totalPaise, current = i == recent.lastIndex) },
                        height = 96.dp,
                        selected = chartSelected,
                        onSelect = { chartSelected = it },
                        caption = { i -> "Net worth, " + monthLabel(recent[i].month, "MMM yyyy") },
                    )
                }
            }

            // ---- Where it comes from ----
            item(key = "own") {
                Group("What you own") {
                    if (nw.byKind.isEmpty()) Text(
                        "Nothing yet. Bank balances appear from SMS that show a balance.",
                        Modifier.padding(horizontal = CardPadding, vertical = Space.sm),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    nw.byKind.entries.sortedByDescending { it.value }.forEachIndexed { i, (k, v) ->
                        if (i > 0) Hairline(startInset = CardPadding, endInset = CardPadding)
                        ValueRow(k.label, money(v))
                    }
                }
            }

            if (balances.isNotEmpty()) item(key = "balances") {
                Group("Bank balances from SMS") {
                    balances.forEachIndexed { i, b ->
                        if (i > 0) Hairline(startInset = CardPadding, endInset = CardPadding)
                        ValueRow("${b.bankName ?: "Account"} ••${b.accountRef}", money(b.balancePaise))
                    }
                }
            }

            item(key = "funds") {
                Group("Mutual funds (CAS)") {
                    holdings.take(8).forEachIndexed { i, h ->
                        if (i > 0) Hairline(startInset = CardPadding, endInset = CardPadding)
                        ValueRow(h.scheme, money(h.valuePaise))
                    }
                    if (holdings.size > 8) Text(
                        "and ${holdings.size - 8} more", Modifier.padding(horizontal = CardPadding, vertical = Space.xs),
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(Modifier.padding(horizontal = CardPadding)) {
                        ActionRow(if (holdings.isEmpty()) "Import a CAMS / KFintech CAS PDF" else "Import a newer CAS", Icons.Outlined.UploadFile, importCas)
                        LearnMore(
                            "Read on this phone; the password is used once and not kept.",
                            "Importing a CAS",
                            "A Consolidated Account Statement (CAS) from CAMS or KFintech lists every mutual fund you hold. FinTrack reads " +
                                "the PDF on this phone; if it is locked, the password (usually your PAN in capitals) is used once to open it " +
                                "and is never stored. A newer statement replaces the funds from the last one. Values are as of the statement date.",
                        )
                    }
                }
            }

            item(key = "typed") {
                Group("Typed in by you") {
                    assets.forEachIndexed { i, a ->
                        if (i > 0) Hairline(startInset = CardPadding, endInset = CardPadding)
                        ValueRow(
                            a.name,
                            if (a.liability) money(-a.valuePaise) else money(a.valuePaise),
                            sub = if (a.liability) "Debt" else a.kind.label,
                            color = if (a.liability) Expense else MaterialTheme.colorScheme.onSurface,
                            onClick = { editing = a },
                        )
                    }
                    Column(Modifier.padding(horizontal = CardPadding)) {
                        ActionRow("Add an asset or a debt", Icons.Outlined.Add, addAsset)
                        Text("Loans set up in Bills & EMIs are counted as debts by themselves.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }

    if (cas is CasUiState.NeedsPassword) {
        val s = cas as CasUiState.NeedsPassword
        var pw by remember(s.uri) { mutableStateOf("") }
        FinSheet(onDismiss = { vm.resetCas() }, title = "This statement is locked") {
            Text(
                if (s.wrong) "That password did not open it. CAS passwords are usually your PAN in capitals."
                else "CAS passwords are usually your PAN in capitals. Used once on this phone, never stored.",
                style = MaterialTheme.typography.bodyMedium,
                color = if (s.wrong) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            OutlinedTextField(
                pw, { pw = it }, Modifier.fillMaxWidth(),
                label = { Text("Password") }, singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                isError = s.wrong,
            )
            PrimaryButton("Open", { vm.importCas(s.uri, pw) }, enabled = pw.isNotEmpty())
            TextAction("Cancel", { vm.resetCas() })
        }
    }

    editing?.let { a -> AssetSheet(a, onSave = { vm.saveAsset(it); editing = null }, onDelete = { vm.deleteAsset(a.id); editing = null }, onDismiss = { editing = null }) }
}

/** Add or edit an asset or a debt: own or owe, its name, its value and (for an asset) its kind. */
@Composable
private fun AssetSheet(a: Asset, onSave: (Asset) -> Unit, onDelete: () -> Unit, onDismiss: () -> Unit) {
    var name by remember(a) { mutableStateOf(a.name) }
    var value by remember(a) { mutableStateOf(if (a.valuePaise > 0) (a.valuePaise / 100).toString() else "") }
    var kind by remember(a) { mutableStateOf(a.kind) }
    var liability by remember(a) { mutableStateOf(a.liability) }
    val v = Money.parsePaise(value)
    val haptics = rememberHaptics()
    val valid = name.isNotBlank() && v != null
    FinSheet(onDismiss = onDismiss, title = if (a.id == 0L) "Add an asset or a debt" else "Edit ${a.name}") {
        SegmentedControl(listOf("Something you own", "A debt"), if (liability) 1 else 0, { liability = it == 1 })
        OutlinedTextField(
            name, { name = it }, Modifier.fillMaxWidth(),
            label = { Text(if (liability) "e.g. Card dues, money borrowed" else "e.g. SBI FD, gold, EPF") }, singleLine = true,
        )
        OutlinedTextField(
            value, { value = it }, Modifier.fillMaxWidth(),
            label = { Text("Value (₹)") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            isError = value.isNotBlank() && v == null,
            supportingText = if (value.isNotBlank() && v == null) ({ Text("Enter an amount, like 50000") }) else null,
        )
        if (!liability) {
            CapsLabel("Kind")
            ChipFlow { AssetKind.entries.forEach { k -> PillChip(kind == k, k.label) { kind = k } } }
        }
        PrimaryButton("Save", {
            if (valid) {
                haptics.confirm()
                onSave(a.copy(name = name.trim(), valuePaise = v!!, kind = if (liability) AssetKind.OTHER else kind, liability = liability))
            } else haptics.reject()
        }, enabled = valid)
        if (a.id != 0L) TextAction("Delete", onDelete, tone = Tone.Danger)
    }
}

/** A card of rows under a small caps heading. */
@Composable
private fun Group(title: String, content: @Composable ColumnScope.() -> Unit) {
    FinCard(padding = PaddingValues(top = Space.lg, bottom = Space.sm), spacing = 0.dp) {
        CapsLabel(title, Modifier.padding(horizontal = CardPadding, vertical = Space.xs))
        content()
    }
}

/** A name on the left (wrapping if it must), its figure in the amount column on the right, never wrapped. */
@Composable
private fun ValueRow(label: String, amount: String, sub: String? = null, color: Color = MaterialTheme.colorScheme.onSurface, onClick: (() -> Unit)? = null) {
    Row(
        Modifier.fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(role = Role.Button, onClickLabel = "Edit", onClick = onClick) else Modifier)
            .clearAndSetSemantics {
                contentDescription = listOfNotNull(label, sub, amount).joinToString(", ")
                if (onClick != null) role = Role.Button
            }
            .heightIn(min = 48.dp)
            .padding(horizontal = CardPadding, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 2, overflow = TextOverflow.Ellipsis)
            if (sub != null) Text(sub, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        Spacer(Modifier.width(Space.md))
        LedgerAmount(amount, color)
    }
}

/** "2026-09" as "Sep" or "Sep 2026"; the stored text itself if it does not parse. */
private fun monthLabel(month: String, pattern: String): String =
    runCatching { YearMonth.parse(month).format(DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH)) }.getOrDefault(month)
