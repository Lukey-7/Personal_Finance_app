package com.pft.financetracker.ui.screens.split

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
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
import com.pft.financetracker.ui.components.AdaptiveRow
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CardPadding
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.LedgerAmountMinWidth
import com.pft.financetracker.ui.components.PickerField
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SegmentedControl
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.cappedScale
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.paiseToInput
import com.pft.financetracker.ui.theme.Expense
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import java.io.File

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

/** Short names for the split modes, sized for a four-way segmented control. */
private fun SplitMode.shortLabel(): String = when (this) {
    SplitMode.EQUAL -> "Equal"
    SplitMode.SHARES -> "Shares"
    SplitMode.CUSTOM -> "Amounts"
    SplitMode.BY_ITEM -> "By item"
}

/**
 * A new split: the total as the hero, then the bill (photo read on the phone, what it was, when), its items, the
 * people, how to split, who paid and the category for your share, and finally who pays what before saving.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NewSplitScreen(vm: AppViewModel, onBack: () -> Unit, onSaved: (Long) -> Unit) {
    val ctx = LocalContext.current
    val haptics = rememberHaptics()
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
    var triedSave by remember { mutableStateOf(false) }

    fun syncPerPersonLists() {
        while (shareWeights.size < people.size) shareWeights += "1"
        while (shareWeights.size > people.size) shareWeights.removeAt(shareWeights.lastIndex)
        while (customAmounts.size < people.size) customAmounts += ""
        while (customAmounts.size > people.size) customAmounts.removeAt(customAmounts.lastIndex)
        if (payer >= people.size) payer = 0
    }
    syncPerPersonLists()

    fun addPerson() {
        if (newPerson.isBlank()) { haptics.reject(); return }
        people += newPerson.trim(); newPerson = ""; syncPerPersonLists()
    }

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
    val canSave = result != null && people.size >= 2

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("New split") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = { vm.clearOcr(); onBack() }) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Gutter, top = Space.sm, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {

            // ---- The amount, as the hero ----
            AmountHero(totalInput, { totalInput = it }, total)

            // ---- The bill: photo or gallery, what it was, when ----
            FormSection("The bill") {
                // Side by side where each button has room, stacked at a large font so neither label breaks.
                AdaptiveRow(count = 2, minItemWidth = 150.dp) { m ->
                    SecondaryButton("Take photo", { launchCamera() }, m, icon = Icons.Outlined.PhotoCamera)
                    SecondaryButton("Choose photo", { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, m, icon = Icons.Outlined.Image)
                }
                when (val s = ocr) {
                    OcrUiState.Running -> Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(Space.sm))
                        Text("Reading the bill on this phone…", style = MaterialTheme.typography.bodySmall)
                    }
                    is OcrUiState.Error -> Text(s.message, color = Expense, style = MaterialTheme.typography.bodySmall)
                    is OcrUiState.Done -> Text(
                        "Read ${countLabel(s.bill.items.size, "item")}" + (s.bill.totalPaise?.let { ", total ${money(it)}" } ?: ", no total found") + ". Check and correct below.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurface,
                    )
                    OcrUiState.Idle -> Text("Or just type the total. Photos are read on this phone and not stored.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                OutlinedTextField(title, { title = it }, label = { Text("What was it?") }, placeholder = { Text("e.g. Dinner at Truffles") }, singleLine = true, shape = ControlShape, modifier = Modifier.fillMaxWidth())
                PickerField("Date", dateOnly(date), { showDate = true })
            }

            // ---- Items (optional) ----
            FormSection("Items", subtitle = "Optional · needed to split by item") {
                items.forEachIndexed { idx, item ->
                    if (idx > 0) Hairline()
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            OutlinedTextField(item.name, { v -> item.name = v }, label = { Text("Item") }, singleLine = true, shape = ControlShape, modifier = Modifier.weight(1f))
                            IconButton(onClick = { items.removeAt(idx) }) { Icon(Icons.Outlined.Close, "Remove ${item.name.ifBlank { "item" }}") }
                        }
                        AdaptiveRow(count = 2, minItemWidth = 110.dp) { m ->
                            OutlinedTextField(item.qty, { v -> item.qty = v.filter { ch -> ch.isDigit() } }, label = { Text("Qty") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = ControlShape, modifier = m)
                            OutlinedTextField(item.price, { v -> item.price = v }, label = { Text("Each") }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = ControlShape, modifier = m)
                        }
                        if (mode == SplitMode.BY_ITEM) {
                            Text(
                                if (item.assigned.isEmpty()) "Shared by everyone. Tap names to choose who had it." else "Shared by ${countLabel(item.assigned.size, "person", "people")}",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            ChipFlow {
                                people.forEachIndexed { pi, name ->
                                    PillChip(pi in item.assigned, name) { if (pi in item.assigned) item.assigned.remove(pi) else item.assigned.add(pi) }
                                }
                            }
                        }
                    }
                }
                SecondaryButton("Add item", { items += ItemState("", 1, 0, emptySet()) }, icon = Icons.Outlined.Add)
                // One-word labels, ₹ as a prefix, three to a line where each gets 90dp and stacked where not,
                // so no label is ever cut short.
                AdaptiveRow(count = 3, minItemWidth = 90.dp) { m ->
                    OutlinedTextField(taxInput, { taxInput = it }, label = { Text("Tax", maxLines = 1, style = MaterialTheme.typography.bodyMedium) }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = ControlShape, modifier = m)
                    OutlinedTextField(serviceInput, { serviceInput = it }, label = { Text("Tip", maxLines = 1, style = MaterialTheme.typography.bodyMedium) }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = ControlShape, modifier = m)
                    OutlinedTextField(discountInput, { discountInput = it }, label = { Text("Discount", maxLines = 1, style = MaterialTheme.typography.bodyMedium) }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = ControlShape, modifier = m)
                }
                if (billItems.isNotEmpty()) {
                    // Each part on its own terms, so a discount never reads as "− discount -₹50".
                    val parts = listOfNotNull(
                        "Items ${money(itemsSum)}",
                        (extras.taxPaise + extras.servicePaise + extras.tipPaise).takeIf { it != 0L }?.let { "+ tax & charges ${money(it)}" },
                        extras.discountPaise.takeIf { it != 0L }?.let { "− discount ${money(it)}" },
                    )
                    Text("${parts.joinToString(" ")} = ${money(itemsSum + extras.netPaise)}", style = MaterialTheme.typography.bodySmall)
                    if (gap != null && gap != 0L) Text(
                        if (gap > 0) "Bill total is ${money(gap)} more than the items add up to. Fix an item, or the difference is shared like a service charge." else "Items add up to ${money(-gap)} more than the bill total. Check the prices or the total.",
                        style = MaterialTheme.typography.bodySmall, color = Expense,
                    )
                    if (total == null) TextAction("Use ${money(itemsSum + extras.netPaise)} as the total", { totalInput = paiseToInput(itemsSum + extras.netPaise) }, alignStart = true)
                }
            }

            // ---- People ----
            FormSection("People") {
                // Chips wrap onto new lines, so a long list of names is never cut at the card's edge.
                ChipFlow {
                    people.forEachIndexed { i, name ->
                        PillChip(
                            selected = false, label = name, icon = Icons.Outlined.Person,
                            trailingIcon = if (i > 0) Icons.Outlined.Close else null, trailingLabel = "Remove $name",
                        ) { if (i > 0) { people.removeAt(i); syncPerPersonLists() } }
                    }
                }
                // Placeholder rather than a floating label, so the field and the button share a centre line.
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                    OutlinedTextField(
                        newPerson, { newPerson = it }, placeholder = { Text("Add a name") }, singleLine = true, shape = ControlShape,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { addPerson() }),
                        modifier = Modifier.weight(1f),
                    )
                    SecondaryButton("Add", { addPerson() }, enabled = newPerson.isNotBlank())
                }
                val suggestions = recent.filter { r -> people.none { it.equals(r, true) } }
                if (suggestions.isNotEmpty()) {
                    CapsLabel("Recent")
                    ChipFlow {
                        suggestions.take(10).forEach { r -> PillChip(false, r, icon = Icons.Outlined.Add) { people += r; syncPerPersonLists() } }
                    }
                }
                CapsLabel("Quick add")
                ChipFlow {
                    listOf(2, 3, 4, 5).forEach { n ->
                        PillChip(false, "$n people") { while (people.size < n) people += "Person ${people.size + 1}"; syncPerPersonLists() }
                    }
                }
            }

            // ---- How to split ----
            FormSection("How to split") {
                val modes = SplitMode.entries
                val fontScale = LocalDensity.current.fontScale
                // Four short segments where they fit; wrapping chips with the full names on a narrow card or at a
                // large font, where a segment would break "Amounts" mid-word.
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    if (maxWidth >= 300.dp && fontScale <= 1.15f) {
                        SegmentedControl(options = modes.map { it.shortLabel() }, selected = modes.indexOf(mode), onSelect = { mode = modes[it] })
                    } else {
                        ChipFlow { modes.forEach { m -> PillChip(mode == m, m.label) { mode = m } } }
                    }
                }
                when (mode) {
                    SplitMode.EQUAL -> Text("Split equally between ${countLabel(people.size, "person", "people")}. Anything that doesn't divide evenly goes to whoever paid.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    SplitMode.SHARES -> people.forEachIndexed { i, name ->
                        PersonInputRow(name) {
                            OutlinedTextField(shareWeights[i], { v -> shareWeights[i] = v.filter { it.isDigit() } }, label = { Text("Shares") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, shape = ControlShape, modifier = Modifier.width(110.dp))
                        }
                    }
                    SplitMode.CUSTOM -> {
                        people.forEachIndexed { i, name ->
                            PersonInputRow(name) {
                                OutlinedTextField(customAmounts[i], { v -> customAmounts[i] = v }, label = { Text("Amount") }, prefix = { Text("₹") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true, shape = ControlShape, modifier = Modifier.width(130.dp))
                            }
                        }
                        total?.let { t ->
                            val diff = SplitCalculator.customDifference(t, customAmounts.map { Money.parsePaise(it) ?: 0L })
                            if (diff == 0L) Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Outlined.CheckCircle, null, Modifier.size(16.dp), tint = Income)
                                Spacer(Modifier.width(Space.xs))
                                Text("Adds up", color = Income, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.SemiBold)
                            } else Text(
                                if (diff > 0) "${money(diff)} still to assign" else "${money(-diff)} over the total",
                                color = Expense, style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    SplitMode.BY_ITEM -> Text(if (billItems.isEmpty()) "Add items above and tap the names under each item." else "Each item is shared by the people tagged on it (or everyone). Tax, service and discount are spread in proportion.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            // ---- Who paid ----
            FormSection("Who paid") {
                ChipFlow { people.forEachIndexed { i, name -> PillChip(payer == i, name) { payer = i } } }
            }

            // ---- Category ----
            FormSection("Category for your share") {
                ChipFlow { Category.spendCategories.forEach { c -> PillChip(category == c, c.label, icon = categoryIcon(c)) { category = c } } }
            }

            // ---- Who pays what, then save ----
            FormSection("Who pays what", raised = true) {
                if (result == null) {
                    Text(
                        if (total == null && mode != SplitMode.BY_ITEM) "Enter the total to see the split." else "Fill in the fields above until the numbers add up.",
                        style = MaterialTheme.typography.bodyMedium, color = if (triedSave) Expense else MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Column {
                        result.shares.forEachIndexed { i, sh ->
                            if (i > 0) Hairline(startInset = 32.dp + Space.md)
                            Row(Modifier.fillMaxWidth().padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                                PersonAvatar(people[sh.personIndex], 32.dp)
                                Spacer(Modifier.width(Space.md))
                                Text(
                                    people[sh.personIndex] + if (sh.personIndex == payer) " · paid" else "",
                                    Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, maxLines = 2, overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.width(Space.md))
                                Box(Modifier.widthIn(min = LedgerAmountMinWidth), contentAlignment = Alignment.CenterEnd) {
                                    LedgerAmount(money(sh.amountPaise), MaterialTheme.colorScheme.onSurface)
                                }
                            }
                        }
                        TearLine(Modifier.padding(vertical = Space.sm))
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                            LedgerAmount(money(result.totalPaise), MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    val mine = result.shares.first().amountPaise
                    Text(
                        if (payer == 0) "You paid ${money(result.totalPaise)}. Only your ${money(mine)} counts as spend; ${money(result.totalPaise - mine)} is tracked as owed to you."
                        else "${people[payer]} paid. Your ${money(mine)} is added as an expense you owe them.",
                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (triedSave && people.size < 2) Text("Add at least one more person to split with.", style = MaterialTheme.typography.bodyMedium, color = Expense)
                PrimaryButton(
                    text = "Save split",
                    onClick = onClick@{
                        val r = result
                        if (r == null || !canSave) { triedSave = true; haptics.reject(); return@onClick }
                        haptics.confirm()
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
        }
    }

    if (showDate) {
        // The picker works in UTC midnights: show the local day, and keep the time when the day changes.
        val state = rememberDatePickerState(initialSelectedDateMillis = com.pft.financetracker.ui.model.PickerDate.toPicker(date))
        DatePickerDialog(
            onDismissRequest = { showDate = false },
            confirmButton = { TextButton(onClick = { state.selectedDateMillis?.let { date = com.pft.financetracker.ui.model.PickerDate.fromPicker(it, date) }; showDate = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { showDate = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}

/**
 * The total as the screen's hero: a large figure typed straight into a sunken well, the ₹ smaller beside it. Grows
 * with the font size up to a point, then scrolls sideways rather than wrap.
 */
@Composable
private fun AmountHero(value: String, onChange: (String) -> Unit, parsed: Long?) {
    val style = MoneyType.large.cappedScale(1.5f).copy(color = MaterialTheme.colorScheme.onSurface)
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    SoftPanel(padding = PaddingValues(horizontal = CardPadding, vertical = Space.lg), spacing = Space.xs) {
        CapsLabel("Total")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("₹", style = style.copy(fontSize = style.fontSize * 0.62f, color = muted))
            Spacer(Modifier.width(Space.xs))
            BasicTextField(
                value = value,
                onValueChange = onChange,
                modifier = Modifier.weight(1f).semantics { contentDescription = "Total amount" },
                textStyle = style,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) Text("0", style = style.copy(color = muted))
                        inner()
                    }
                },
            )
        }
        if (value.isNotBlank() && parsed == null) Text("That doesn't look like an amount", style = MaterialTheme.typography.bodySmall, color = Expense)
    }
}

/** A person's name with their avatar on the left and an input on the right (shares, a custom amount). */
@Composable
private fun PersonInputRow(name: String, input: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        PersonAvatar(name, 32.dp)
        Spacer(Modifier.width(Space.md))
        Text(name, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.width(Space.sm))
        input()
    }
}
