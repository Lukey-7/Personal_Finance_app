package com.pft.financetracker.ui.screens.networth

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
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
import com.pft.financetracker.ui.components.Space
import androidx.compose.ui.text.font.FontWeight
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
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.theme.Expense
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.UploadFile

/** What you own minus what you owe: typed-in assets and debts, SMS bank balances, CAS funds and loans. */
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
    val snackbar = remember { SnackbarHostState() }
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? -> uri?.let { vm.importCas(it, null) } }

    LaunchedEffect(cas) {
        when (val s = cas) {
            is CasUiState.Done -> { snackbar.showSnackbar("Read ${s.count} fund${if (s.count == 1) "" else "s"} from the statement"); vm.resetCas() }
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
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = Space.sm, end = Gutter, bottom = Space.xl), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
            item {
                FinCard {
                    CapsLabel("Net worth")
                    Text(money(nw.totalPaise), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold, color = if (nw.totalPaise < 0) Expense else MaterialTheme.colorScheme.onSurface)
                    Text("You own ${money(nw.ownPaise)} · you owe ${money(nw.owePaise)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    if (history.size >= 2) {
                        val change = history.last().totalPaise - history[history.size - 2].totalPaise
                        Text((if (change >= 0) "Up " else "Down ") + money(kotlin.math.abs(change)) + " since last month", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                FinCard {
                    CapsLabel("What you own")
                    nw.byKind.entries.sortedByDescending { it.value }.forEach { (k, v) -> Line(k.label, money(v)) }
                    if (nw.byKind.isEmpty()) Text("Nothing yet. Bank balances appear from SMS that show a balance.", style = MaterialTheme.typography.bodySmall)
                }
            }
            if (balances.isNotEmpty()) item {
                FinCard {
                    CapsLabel("Bank balances from SMS")
                    balances.forEach { Line("${it.bankName ?: "Account"} ••${it.accountRef}", money(it.balancePaise)) }
                }
            }
            item {
                FinCard {
                    CapsLabel("Mutual funds (CAS)")
                    holdings.take(8).forEach { Line(it.scheme, money(it.valuePaise)) }
                    if (holdings.size > 8) Text("and ${holdings.size - 8} more", style = MaterialTheme.typography.bodySmall)
                    ActionRow(if (holdings.isEmpty()) "Import a CAMS / KFintech CAS PDF" else "Import a newer CAS", Icons.Outlined.UploadFile, { pick.launch("application/pdf") })
                    Text("Read on this phone; the password is used once and not kept. Values are as of the statement date.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                FinCard {
                    CapsLabel("Typed in by you")
                    assets.forEach { a ->
                        Row(Modifier.fillMaxWidth().clickable { editing = a }.padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(a.name, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text((if (a.liability) "− " else "") + money(a.valuePaise), color = if (a.liability) Expense else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    ActionRow("Add an asset or a debt", Icons.Outlined.Add, { editing = Asset(name = "", kind = AssetKind.FD, valuePaise = 0) })
                    Text("Loans set up in Bills & EMIs are counted as debts by themselves.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }

    if (cas is CasUiState.NeedsPassword) {
        var pw by remember { mutableStateOf("") }
        val s = cas as CasUiState.NeedsPassword
        AlertDialog(
            onDismissRequest = { vm.resetCas() },
            title = { Text("This statement is locked") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Text(if (s.wrong) "That password did not open it. CAS passwords are usually your PAN in capitals." else "CAS passwords are usually your PAN in capitals. Used once on this phone, never stored.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(pw, { pw = it }, label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                }
            },
            confirmButton = { TextButton(enabled = pw.isNotEmpty(), onClick = { vm.importCas(s.uri, pw) }) { Text("Open") } },
            dismissButton = { TextButton(onClick = { vm.resetCas() }) { Text("Cancel") } },
        )
    }

    editing?.let { a ->
        var name by remember { mutableStateOf(a.name) }
        var value by remember { mutableStateOf(if (a.valuePaise > 0) (a.valuePaise / 100).toString() else "") }
        var kind by remember { mutableStateOf(a.kind) }
        var liability by remember { mutableStateOf(a.liability) }
        val v = Money.parsePaise(value)
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (a.id == 0L) "Add" else "Edit") },
            text = {
                Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                    Row(verticalAlignment = Alignment.CenterVertically) { Text("This is a debt", Modifier.weight(1f)); Switch(liability, { liability = it }) }
                    OutlinedTextField(name, { name = it }, label = { Text(if (liability) "e.g. Card dues, money borrowed" else "e.g. SBI FD, gold, EPF") }, singleLine = true)
                    OutlinedTextField(value, { value = it }, label = { Text("Value (₹)") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                    if (!liability) ChipRow(inset = 0.dp) { AssetKind.entries.forEach { k -> PillChip(kind == k, k.label) { kind = k } } }
                }
            },
            confirmButton = { TextButton(enabled = name.isNotBlank() && v != null, onClick = { vm.saveAsset(a.copy(name = name.trim(), valuePaise = v!!, kind = if (liability) AssetKind.OTHER else kind, liability = liability)); editing = null }) { Text("Save") } },
            dismissButton = {
                Row {
                    if (a.id != 0L) TextButton(onClick = { vm.deleteAsset(a.id); editing = null }) { Text("Delete", color = Expense) }
                    TextButton(onClick = { editing = null }) { Text("Cancel") }
                }
            },
        )
    }
}

@Composable
private fun Line(label: String, value: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyMedium)
        Text(value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
    }
}
