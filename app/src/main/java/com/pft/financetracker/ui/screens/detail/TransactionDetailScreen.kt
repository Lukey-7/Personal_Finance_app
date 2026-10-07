package com.pft.financetracker.ui.screens.detail

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.automirrored.outlined.Undo
import androidx.compose.material.icons.outlined.AccountBalance
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.tax.TaxTagger
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.AmountSize
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CategoryIcon
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SharedKeys
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.displayMerchant
import com.pft.financetracker.ui.components.fullDate
import com.pft.financetracker.ui.components.sharedElement
import com.pft.financetracker.ui.components.toneColor
import com.pft.financetracker.ui.model.DetailLink
import com.pft.financetracker.ui.model.TransactionDetail
import com.pft.financetracker.ui.model.signOf
import com.pft.financetracker.ui.screens.tax.TaxTagDialog
import com.pft.financetracker.ui.screens.transactions.CategoryPickerSheet
import com.pft.financetracker.ui.theme.Expense

/**
 * One transaction, read first: the amount as the hero, what it was and how it counts, where it came from (with the
 * original SMS while the inbox has it), what it is linked to, and its tax section. "Edit" opens the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionDetailScreen(
    vm: AppViewModel,
    id: Long,
    onEdit: (Long) -> Unit,
    onOpenSplit: (Long) -> Unit,
    onOpenTransaction: (Long) -> Unit,
    onBack: () -> Unit,
) {
    val txns by vm.transactions.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val badges by vm.refundBadges.collectAsState()
    val autoSplits by vm.autoSplitOf.collectAsState()
    val splits by vm.splits.collectAsState()
    val bills by vm.billStates.collectAsState()
    val tags by vm.taxTags.collectAsState()
    val t = txns.firstOrNull { it.id == id }
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var taxPicking by remember { mutableStateOf(false) }
    var sms by remember { mutableStateOf<String?>(null) }
    var smsChecked by remember { mutableStateOf(false) }
    LaunchedEffect(t?.smsHash) { if (t != null) { sms = vm.smsTextFor(t); smsChecked = true } }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {},
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (t != null) {
                        IconButton(onClick = { onEdit(t.id) }) { Icon(Icons.Outlined.Edit, "Edit") }
                        IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More: delete") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem({ Text("Delete", color = MaterialTheme.colorScheme.error) }, { menu = false; confirmDelete = true },
                                leadingIcon = { Icon(Icons.Outlined.Delete, null, tint = MaterialTheme.colorScheme.error) })
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (t == null) {
            if (!loaded) SkeletonHero(Modifier.padding(padding).padding(top = Space.xl), cards = 2)
            else EmptyState(Icons.Outlined.SearchOff, "This payment isn't here any more", "It may have been deleted or merged with a duplicate.", Modifier.padding(padding)) {
                TextAction("Go back", onBack)
            }
            return@Scaffold
        }
        val autoSplit = autoSplits[t.id]
        val splitId = autoSplit?.id ?: splits.firstOrNull { it.linkedTransactionId == t.id }?.id
        val billName = bills.firstOrNull { (_, s) -> s is BillState.Paid && s.transactionId == t.id }?.first?.name
        val taxSection = if (tags.containsKey(t.id)) tags[t.id] else TaxTagger.suggest(t)
        val d = TransactionDetail.of(t, badges.pairsOf(t.id), txns.associateBy { it.id }, splitId, billName, taxSection, tags.containsKey(t.id))

        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
                .padding(start = Gutter, end = Gutter, top = Space.sm, bottom = Space.xxl),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            // ---- Hero ----
            Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                CategoryIcon(t.category, Modifier.sharedElement(SharedKeys.icon(t.id)), size = 56.dp)
                Text(
                    displayMerchant(t.merchant).ifBlank { t.category.label },
                    Modifier.sharedElement(SharedKeys.title(t.id)).semantics { heading() },
                    style = MaterialTheme.typography.titleLarge, textAlign = TextAlign.Center,
                )
                AmountDisplay(
                    t.amountPaise,
                    Modifier.sharedElement(SharedKeys.amount(t.id)),
                    size = AmountSize.Large,
                    color = toneColor(d.tone),
                    prefix = signOf(t),
                    spokenLabel = "Amount",
                )
                Text(d.countsAs, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
                PillChip(true, t.category.label, icon = categoryIcon(t.category), trailingIcon = Icons.Outlined.Edit, trailingLabel = "Change category") { picking = true }
            }

            d.shareNote?.let { note ->
                SoftPanel {
                    CapsLabel(if (autoSplit != null) "Auto-split" else "Your share")
                    Text(note, style = MaterialTheme.typography.bodyMedium)
                    autoSplit?.reasons?.take(3)?.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    if (autoSplit != null) TextAction("Not shared", { vm.rejectSplit(autoSplit.id); onBack() }, alignStart = true)
                }
            }

            // ---- Facts ----
            FinCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.lg, vertical = Space.xs), spacing = 0.dp) {
                val facts = listOfNotNull(
                    "When" to fullDate(t.timestamp),
                    d.account?.let { "Account" to it },
                    "Source" to d.source,
                    t.refNumber?.let { "Reference" to it },
                    t.note?.takeIf { it.isNotBlank() }?.let { "Note" to it },
                )
                facts.forEachIndexed { i, (k, v) ->
                    if (i > 0) Hairline()
                    Row(Modifier.fillMaxWidth().heightIn(min = 48.dp).padding(vertical = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                        Text(k, Modifier.width(96.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.End)
                    }
                }
            }

            // ---- What it is tied to ----
            if (d.links.isNotEmpty()) FinCard(padding = androidx.compose.foundation.layout.PaddingValues(horizontal = Space.lg, vertical = Space.xs), spacing = 0.dp) {
                d.links.forEachIndexed { i, link ->
                    if (i > 0) Hairline()
                    LinkRow(link, onOpen = when (link.kind) {
                        DetailLink.Kind.SPLIT -> link.targetId?.let { s -> { onOpenSplit(s) } }
                        DetailLink.Kind.REFUND, DetailLink.Kind.REVERSAL -> link.targetId?.let { o -> { onOpenTransaction(o) } }
                        DetailLink.Kind.BILL -> null
                    })
                    if (link.pairId != null) TextAction("Not a refund", { vm.undoRefund(link.pairId) }, alignStart = true)
                }
            }

            // ---- The original SMS ----
            if (d.hasSms) SoftPanel {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    TintedSquare(Icons.Outlined.Sms, size = 28.dp)
                    Spacer(Modifier.width(Space.sm))
                    CapsLabel("Original message")
                }
                Text(
                    when { sms != null -> sms!!; smsChecked -> "The message is no longer in your inbox."; else -> "Reading…" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (sms != null) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            // ---- Tax ----
            d.tax?.let { tax ->
                Row(
                    Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Button, onClickLabel = "Change tax section") { taxPicking = true },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TintedSquare(Icons.Outlined.AccountBalance)
                    Spacer(Modifier.width(Space.lg))
                    Column(Modifier.weight(1f)) {
                        Text("Tax section", style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                        Text(tax, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            PrimaryButton("Edit", { onEdit(t.id) }, icon = Icons.Outlined.Edit)
        }

        if (picking) CategoryPickerSheet("Move to a category", t.category, onPick = { c -> vm.recategorise(setOf(t.id), c); picking = false }, onDismiss = { picking = false })
        if (taxPicking) TaxTagDialog(t.merchant, onPick = { vm.tagTax(t.id, it); taxPicking = false }, onRule = { vm.clearTaxTag(t.id); taxPicking = false }, onDismiss = { taxPicking = false })
        if (confirmDelete) AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this payment?") },
            text = { Text("It is removed from every total. This cannot be undone.") },
            confirmButton = { TextButton(onClick = { vm.delete(t); confirmDelete = false; onBack() }) { Text("Delete", color = Expense) } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun LinkRow(link: DetailLink, onOpen: (() -> Unit)?) {
    val icon: ImageVector = when (link.kind) {
        DetailLink.Kind.SPLIT -> Icons.AutoMirrored.Outlined.CallSplit
        DetailLink.Kind.REFUND, DetailLink.Kind.REVERSAL -> Icons.AutoMirrored.Outlined.Undo
        DetailLink.Kind.BILL -> Icons.AutoMirrored.Outlined.ReceiptLong
    }
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .then(if (onOpen != null) Modifier.clickable(role = Role.Button, onClick = onOpen) else Modifier)
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedSquare(icon)
        Spacer(Modifier.width(Space.lg))
        Column(Modifier.weight(1f)) {
            Text(link.title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(link.label, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (onOpen != null) Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

