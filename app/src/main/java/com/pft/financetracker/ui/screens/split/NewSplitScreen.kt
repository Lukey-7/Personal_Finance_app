package com.pft.financetracker.ui.screens.split

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.split.BillExtras
import com.pft.financetracker.domain.split.BillItem
import com.pft.financetracker.domain.split.Person
import com.pft.financetracker.domain.split.Split
import com.pft.financetracker.domain.split.SplitCalculator
import com.pft.financetracker.domain.split.SplitMode
import com.pft.financetracker.domain.split.SplitResult
import com.pft.financetracker.domain.split.SplitShare
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.OcrUiState
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.paiseToInput
import java.io.File
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryPill

/** Editable item row state. Strings so the user can type freely; parsed on use. */
private class ItemState(name: String, qty: Int, price: Long, assigned: Set<Int>) {
    var name by mutableStateOf(name)
    var qty by mutableStateOf(qty.toString())
    var price by mutableStateOf(paiseToInput(price))
    val assigned = mutableStateListOf<Int>().also { it.addAll(assigned) }
    fun toItem(): BillItem? {
        val p = Money.parsePaise(price) ?: return null
        return BillItem(name = name.trim().ifBlank { "Item" }, quantity = qty.toIntOrNull()?.coerceAtLeast(1) ?: 1, pricePaise = p, assignedTo = assigned.toSet())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSplitScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    val ocr by vm.ocrState.collectAsState()
    val recent by vm.recentPeople.collectAsState()
    val myName by vm.myName.collectAsState()

    var title by remember { mutableStateOf("") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var totalInput by remember { mutableStateOf("") }
    var taxInput by remember { mutableStateOf("") }
    var serviceInput by remember { mutableStateOf("") }
    var discountInput by remember { mutableStateOf("") }
    val items = remember { mutableStateListOf<ItemState>() }
    val people = remember { mutableStateListOf(myName) }
    var newPerson by remember { mutableStateOf("") }
    var mode by remember { mutableStateOf(SplitMode.EQUAL) }
    var payer by remember { mutableStateOf(0) }
    val shareWeights = remember { mutableStateListOf<String>() }
    val customAmounts = remember { mutableStateListOf<String>() }
    var category by remember { mutableStateOf(Category.FOOD) }
    var showDate by remember { mutableStateOf(false) }
    var ocrApplied by remember { mutableStateOf(false) }
    var cameraUri by remember { mutableStateOf<Uri?>(null) }

    fun syncPerPersonLists() {
        while (shareWeights.size < people.size) shareWeights += "1"
        while (shareWeights.size > people.size) shareWeights.removeAt(shareWeights.lastIndex)
        while (customAmounts.size < people.size) customAmounts += ""
        while (customAmounts.size > people.size) customAmounts.removeAt(customAmounts.lastIndex)
        if (payer >= people.size) payer = 0
    }
    syncPerPersonLists()

    // ---- image sources ----
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> if (uri != null) { ocrApplied = false; vm.runOcr(uri) } }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val u = cameraUri
        if (ok && u != null) { ocrApplied = false; vm.runOcr(u) }
    }
    fun launchCamera() {
        // Temporary file in app-private cache, shared with the camera app through FileProvider. Deleted after OCR.
        val f = File(ctx.cacheDir, "bill_capture.jpg").apply { if (exists()) delete() }
        val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
        cameraUri = uri
        camera.launch(uri)
    }

    // The capture file must never outlive recognition, on any path: a bill photo left in the cache
    // would contradict the promise that photos are not stored. Cleared when OCR finishes either way,
    // and again when this screen goes away (cancelled capture, back button, process kill).
    fun discardCapture() { runCatching { File(ctx.cacheDir, "bill_capture.jpg").delete() } }

    DisposableEffect(Unit) { onDispose { discardCapture() } }

    LaunchedEffect(ocr) {
        if (ocr is OcrUiState.Done || ocr is OcrUiState.Error) discardCapture()
    }

    // Apply OCR result once, into editable fields. Everything stays editable.
    LaunchedEffect(ocr) {
        val s = ocr
        if (s is OcrUiState.Done && !ocrApplied) {
            ocrApplied = true
            s.bill.merchant?.let { if (title.isBlank()) title = it }
            s.bill.date?.let { date = it }
            s.bill.totalPaise?.let { totalInput = paiseToInput(it) }
            if (s.bill.taxPaise > 0) taxInput = paiseToInput(s.bill.taxPaise)
            if (s.bill.servicePaise > 0) serviceInput = paiseToInput(s.bill.servicePaise)
            if (s.bill.discountPaise > 0) discountInput = paiseToInput(s.bill.discountPaise)
            items.clear(); s.bill.items.forEach { items += ItemState(it.name, it.quantity, it.pricePaise, emptySet()) }
            if (items.isNotEmpty()) mode = SplitMode.BY_ITEM
        }
    }

    // ---- maths ----
    val total = Money.parsePaise(totalInput)
    val extras = BillExtras(Money.parsePaise(taxInput) ?: 0L, Money.parsePaise(serviceInput) ?: 0L, 0L, Money.parsePaise(discountInput) ?: 0L)
    val billItems = items.mapNotNull { it.toItem() }
    val itemsSum = billItems.sumOf { it.pricePaise * it.quantity }
    val gap = if (total != null && billItems.isNotEmpty()) total - (itemsSum + extras.netPaise) else null

    val result: SplitResult? = runCatching {
        when (mode) {
            SplitMode.EQUAL -> total?.let { SplitCalculator.equal(it, people.size, payer) }
            SplitMode.SHARES -> total?.let { SplitCalculator.byShares(it, shareWeights.map { w -> w.toIntOrNull() ?: 0 }, payer) }
            SplitMode.CUSTOM -> total?.let { t -> val a = customAmounts.map { c -> Money.parsePaise(c) ?: 0L }; if (SplitCalculator.customDifference(t, a) == 0L) SplitCalculator.custom(t, a) else null }
            SplitMode.BY_ITEM -> if (billItems.isEmpty()) null else {
                // If the user typed a total that differs from items+extras, treat the difference as an extra so the split matches the bill.
                val adj = if (total != null && gap != null && gap != 0L) extras.copy(servicePaise = extras.servicePaise + gap) else extras
                SplitCalculator.byItem(billItems, adj, people.size, payer)
            }
        }
    }.getOrNull()

    Scaffold(topBar = {
        TopAppBar(title = { Text("New split") }, navigationIcon = { IconButton(onClick = { vm.clearOcr(); onBack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {

            // ---- 1. Source ----
            Section("1 · The bill") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = { launchCamera() }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(6.dp)); Text("Photo") }
                    OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.weight(1f)) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(6.dp)); Text("Gallery") }
                }
                when (val s = ocr) {
                    OcrUiState.Running -> Row(verticalAlignment = Alignment.CenterVertically) { CircularProgressIndicator(Modifier.height(18.dp).width(18.dp)); Spacer(Modifier.width(8.dp)); Text("Reading the bill on-device…", style = MaterialTheme.typography.bodySmall) }
                    is OcrUiState.Error -> Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    is OcrUiState.Done -> Text("Read ${s.bill.items.size} items" + (s.bill.totalPaise?.let { ", total ${money(it, true)}" } ?: ", no total found") + ". Check and correct below.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary)
                    OcrUiState.Idle -> Text("Or just type the total. Photos are processed on this phone and not stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(title, { title = it }, label = { Text("What was it? (e.g. Dinner at Truffles)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(totalInput, { totalInput = it }, label = { Text("Total (₹)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedButton(onClick = { showDate = true }, modifier = Modifier.weight(1f).height(56.dp).padding(top = 8.dp)) { Text(dateOnly(date)) }
                }
                Text("Category for your share", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Category.spendCategories.forEach { c -> PillChip(category == c, c.label) { category = c } }
                }
            }

            // ---- 2. Items (optional) ----
            Section("2 · Items (optional, needed for by-item split)") {
                items.forEachIndexed { idx, it ->
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedTextField(it.name, { v -> it.name = v }, label = { Text("Item") }, singleLine = true, modifier = Modifier.weight(2f))
                            OutlinedTextField(it.qty, { v -> it.qty = v.filter { ch -> ch.isDigit() } }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(0.7f))
                            OutlinedTextField(it.price, { v -> it.price = v }, label = { Text("₹ each") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1.1f))
                            IconButton(onClick = { items.removeAt(idx) }) { Icon(Icons.Filled.Close, "Remove") }
                        }
                        if (mode == SplitMode.BY_ITEM) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            people.forEachIndexed { pi, name ->
                                PillChip(pi in it.assigned, name) { if (pi in it.assigned) it.assigned.remove(pi) else it.assigned.add(pi) }
                            }
                            if (it.assigned.isEmpty()) Text("everyone", Modifier.padding(top = 10.dp), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                TextButton(onClick = { items += ItemState("", 1, 0, emptySet()) }) { Icon(Icons.Filled.Add, null); Text(" Add item") }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(taxInput, { taxInput = it }, label = { Text("Tax ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(serviceInput, { serviceInput = it }, label = { Text("Service/tip ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                    OutlinedTextField(discountInput, { discountInput = it }, label = { Text("Discount ₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.weight(1f))
                }
                if (billItems.isNotEmpty()) {
                    Text("Items ${money(itemsSum, true)} + tax/service − discount ${money(extras.netPaise, true)} = ${money(itemsSum + extras.netPaise, true)}", style = MaterialTheme.typography.bodySmall)
                    if (gap != null && gap != 0L) Text(
                        if (gap > 0) "Bill total is ${money(gap, true)} more than the items add up to. Fix an item, or the difference is shared like a service charge." else "Items add up to ${money(-gap, true)} more than the bill total. Check the prices or the total.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary
                    )
                    if (total == null) TextButton(onClick = { totalInput = paiseToInput(itemsSum + extras.netPaise) }) { Text("Use ${money(itemsSum + extras.netPaise, true)} as total") }
                }
            }

            // ---- 3. People ----
            Section("3 · People") {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEachIndexed { i, name ->
                        AssistChip(onClick = { if (i > 0) { people.removeAt(i); syncPerPersonLists() } }, label = { Text(name) }, trailingIcon = { if (i > 0) Icon(Icons.Filled.Close, null, Modifier.height(16.dp)) })
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(newPerson, { newPerson = it }, label = { Text("Add a name") }, singleLine = true, modifier = Modifier.weight(1f))
                    Button(enabled = newPerson.isNotBlank(), onClick = { people += newPerson.trim(); newPerson = ""; syncPerPersonLists() }) { Text("Add") }
                }
                val suggestions = recent.filter { r -> people.none { it.equals(r, true) } }
                if (suggestions.isNotEmpty()) Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    suggestions.take(10).forEach { r -> AssistChip(onClick = { people += r; syncPerPersonLists() }, label = { Text(r) }) }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf(2, 3, 4, 5).forEach { n ->
                        AssistChip(onClick = { while (people.size < n) people += "Person ${people.size + 1}"; syncPerPersonLists() }, label = { Text("$n people") })
                    }
                }
                Text("Who paid the bill?", style = MaterialTheme.typography.labelLarge)
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    people.forEachIndexed { i, name -> PillChip(payer == i, name) { payer = i } }
                }
            }

            // ---- 4. How to split ----
            Section("4 · How to split") {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    SplitMode.entries.forEachIndexed { i, m ->
                        SegmentedButton(selected = mode == m, onClick = { mode = m }, shape = SegmentedButtonDefaults.itemShape(i, SplitMode.entries.size)) { Text(m.label, maxLines = 1) }
                    }
                }
                when (mode) {
                    SplitMode.EQUAL -> Text("Total ÷ ${people.size}. Any leftover paise go to the payer.", style = MaterialTheme.typography.bodySmall)
                    SplitMode.SHARES -> people.forEachIndexed { i, name ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(name, Modifier.weight(1f))
                            OutlinedTextField(shareWeights[i], { v -> shareWeights[i] = v.filter { it.isDigit() } }, label = { Text("Shares") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.width(110.dp))
                        }
                    }
                    SplitMode.CUSTOM -> {
                        people.forEachIndexed { i, name ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(name, Modifier.weight(1f))
                                OutlinedTextField(customAmounts[i], { v -> customAmounts[i] = v }, label = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, modifier = Modifier.width(130.dp))
                            }
                        }
                        total?.let { t ->
                            val diff = SplitCalculator.customDifference(t, customAmounts.map { Money.parsePaise(it) ?: 0L })
                            Text(if (diff == 0L) "Adds up." else if (diff > 0) "${money(diff, true)} still to assign" else "${money(-diff, true)} over the total", color = if (diff == 0L) MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
                        }
                    }
                    SplitMode.BY_ITEM -> Text(if (billItems.isEmpty()) "Add items above and tap the names under each item." else "Each item is shared by the people tagged on it (or everyone). Tax, service and discount are spread in proportion.", style = MaterialTheme.typography.bodySmall)
                }
            }

            // ---- 5. Result ----
            Section("5 · Who pays what") {
                if (result == null) {
                    Text(if (total == null && mode != SplitMode.BY_ITEM) "Enter the total to see the split." else "Fill in the fields above until the numbers add up.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    result.shares.forEach { sh ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(people[sh.personIndex] + if (sh.personIndex == payer) " (paid)" else "")
                            Text(money(sh.amountPaise, true), fontWeight = FontWeight.SemiBold)
                        }
                    }
                    HorizontalDivider()
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Total", fontWeight = FontWeight.Bold); Text(money(result.totalPaise, true), fontWeight = FontWeight.Bold)
                    }
                    val mine = result.shares.first().amountPaise
                    Text(
                        if (payer == 0) "You paid ${money(result.totalPaise, true)}. Only your ${money(mine, true)} counts as spend; ${money(result.totalPaise - mine, true)} is tracked as owed to you."
                        else "${people[payer]} paid. Your ${money(mine, true)} is added as an expense you owe them.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                PrimaryPill(
                    text = "Save split",
                    enabled = result != null && people.size >= 2,
                    onClick = onClick@{
                        val r = result ?: return@onClick
                        val split = Split(
                            title = title.trim().ifBlank { "Split ${dateOnly(date)}" },
                            totalPaise = r.totalPaise, date = date, mode = mode, payerIndex = payer,
                            people = people.mapIndexed { i, n -> Person(name = n, isMe = i == 0) },
                            shares = r.shares.map { SplitShare(personIndex = it.personIndex, amountPaise = it.amountPaise, settledPaise = if (it.personIndex == payer) it.amountPaise else 0L) },
                        )
                        vm.saveSplit(split, billItems, category) { id -> vm.clearOcr(); onSaved(id) }
                    },
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (showDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = date)
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = it + 12 * 3600 * 1000 }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } }
        ) { DatePicker(state) }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    FinCard {
        Text(title, style = MaterialTheme.typography.titleMedium)
        content()
    }
}
