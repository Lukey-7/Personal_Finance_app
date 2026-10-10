package com.pft.financetracker.ui.screens.split

import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Payments
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.split.BillItem
import com.pft.financetracker.domain.split.Split
import com.pft.financetracker.domain.split.SplitKind
import com.pft.financetracker.domain.split.SplitShare
import com.pft.financetracker.domain.split.SplitSource
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AmountDisplay
import com.pft.financetracker.ui.components.AmountSize
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CardTitle
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LedgerAmount
import com.pft.financetracker.ui.components.LedgerAmountMinWidth
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SkeletonCard
import com.pft.financetracker.ui.components.SkeletonHero
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TintedSquare
import com.pft.financetracker.ui.components.Tone
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.displayMerchant
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.paiseToInput
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics
import com.pft.financetracker.ui.theme.surfaces

/**
 * One split, read like a ledger: the total as the hero with who paid, then each person's share and whether it is
 * settled. Settling a share opens a sheet that offers that person's matching incoming payments first, then a typed
 * amount. Automatic splits show why they were found and can be accepted, turned down or undone.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SplitDetailScreen(vm: AppViewModel, id: Long, onBack: () -> Unit, onOpenTransaction: (Long) -> Unit) {
    val splits by vm.splits.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val split = splits.firstOrNull { it.id == id }
    val ctx = LocalContext.current
    var items by remember { mutableStateOf<List<BillItem>>(emptyList()) }
    var settling by remember { mutableStateOf<SplitShare?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    LaunchedEffect(id) { items = vm.splitItems(id) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                // The split's name is in the hero below; the bar only says where you are.
                title = { Text("Split") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
                actions = {
                    if (split != null) {
                        IconButton(onClick = {
                            // Plain-text summary through the system share sheet. You pick the app; FinTrack sends nothing itself.
                            val text = buildString {
                                append("${split.title} · ${dateOnly(split.date)} · total ${money(split.totalPaise)}\n")
                                append("Paid by ${split.people.getOrNull(split.payerIndex)?.name}\n")
                                split.shares.forEach { sh -> append("${split.people.getOrNull(sh.personIndex)?.name}: ${money(sh.amountPaise)}${if (sh.settledPaise >= sh.amountPaise && sh.personIndex != split.payerIndex) " ✓" else ""}\n") }
                            }
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text), "Share split"))
                        }) { Icon(Icons.Outlined.Share, "Share split") }
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Outlined.Delete, "Delete split") }
                    }
                },
            )
        },
    ) { padding ->
        val page = Modifier.fillMaxSize().padding(padding)
        // Until the database answers the split would look missing: show its shape instead.
        if (!loaded) {
            Column(page.padding(top = Space.md), verticalArrangement = Arrangement.spacedBy(Space.lg)) {
                SkeletonHero(cards = 0)
                SkeletonCard(Modifier.padding(horizontal = Gutter), height = 200.dp)
            }
            return@Scaffold
        }
        if (split == null) {
            Column(page) {
                EmptyState(Icons.Outlined.SearchOff, "Split not found", "It may have been deleted or undone.") {
                    SecondaryButton("Go back", onBack)
                }
            }
            return@Scaffold
        }
        Column(
            page.verticalScroll(rememberScrollState()).padding(start = Gutter, top = Space.sm, end = Gutter, bottom = bottomPadding()),
            verticalArrangement = Arrangement.spacedBy(Space.lg),
        ) {
            Hero(split, onOpenTransaction)
            if (split.isAuto) AutoCard(split, vm, onBack)
            Shares(split, onSettle = { settling = it })
            if (items.isNotEmpty()) Items(split, items)
        }
    }

    val s = split
    settling?.let { sh ->
        if (s != null) {
            // My own share when someone else paid: I pay them. Anyone else's share is money coming to me.
            if (!s.iPaid && sh.personIndex == s.myIndex) PayBackSheet(vm, s, sh) { settling = null }
            else SettleSheet(vm, s, sh) { settling = null }
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Delete this split?") },
        text = { Text(if (split?.isAuto == true) "Your numbers go back to how the bank reported them, and this payment won't be split automatically again." else "The split and its balances are removed. The payment goes back to its full amount, a share it added as a payment is removed, and transfers you linked to it count as before.") },
        confirmButton = { TextButton(onClick = { vm.deleteSplit(id); confirmDelete = false; onBack() }) { Text("Delete split", color = MaterialTheme.colorScheme.error) } },
        dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Cancel") } },
    )
}

/** The hero: total, what it was, when and who paid; then your share in a sunken well, the linked payment and the note. */
@Composable
private fun Hero(split: Split, onOpenTransaction: (Long) -> Unit) {
    val payer = split.people.getOrNull(split.payerIndex)?.name
    Column(Modifier.fillMaxWidth().padding(top = Space.sm), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        CapsLabel("Total · ${dateOnly(split.date)}")
        AmountDisplay(split.totalPaise, size = AmountSize.Large, spokenLabel = "Total")
        Text(split.title, style = MaterialTheme.typography.titleMedium)
        Text("${split.mode.label} · paid by $payer", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    split.myShare?.let { mine ->
        SoftPanel(spacing = Space.xs) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Your share", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.width(Space.md))
                LedgerAmount(money(mine.amountPaise), MaterialTheme.colorScheme.onSurface, style = MoneyType.tile)
            }
            Text(
                if (split.isSuggestion) "Not applied yet: this is what it would be" else "Counted as your spend",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
    split.linkedTransactionId?.let { txId -> TextAction("Open linked payment", { onOpenTransaction(txId) }, alignStart = true) }
    split.note?.let { Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
}

/** Why an automatic split was found, and the yes / no (or undo) that goes with it. */
@Composable
private fun AutoCard(split: Split, vm: AppViewModel, onBack: () -> Unit) {
    FinCard(raised = split.isSuggestion, spacing = Space.md) {
        CardTitle(
            when {
                split.isSuggestion -> "Suggested: waiting for your yes"
                split.source == SplitSource.AUTO_AI -> "Found automatically (checked with AI)"
                else -> "Found automatically"
            },
            Icons.Outlined.AutoAwesome,
        )
        if (split.kind == SplitKind.ADVANCE) Text("Friends sent money before you paid (collected in advance).", style = MaterialTheme.typography.bodyMedium)
        if (split.reasons.isNotEmpty()) Column(verticalArrangement = Arrangement.spacedBy(Space.xs)) {
            split.reasons.forEach { Text("• $it", style = MaterialTheme.typography.bodyMedium) }
        }
        split.confidence?.let { Text("Confidence $it%", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
        if (split.isSuggestion) {
            PrimaryButton("Split it", { vm.acceptSplit(split.id) })
            TextAction("Not shared", { vm.rejectSplit(split.id); onBack() }, Modifier.fillMaxWidth())
        } else {
            Text("Your spend counts only your share; your friends' transfers are marked as settlements, not income.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextAction("Undo: this wasn't shared", { vm.rejectSplit(split.id); onBack() }, tone = Tone.Danger, alignStart = true)
        }
    }
}

/** Who pays what, as ledger rows on a receipt: avatar, name and where their share stands, amount in a column. */
@Composable
private fun Shares(split: Split, onSettle: (SplitShare) -> Unit) {
    Text("Who pays what", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    ReceiptCard(spacing = 0.dp) {
        split.shares.forEachIndexed { i, sh ->
            if (i > 0) Hairline(startInset = 36.dp + Space.md)
            ShareRow(split, sh, onSettle)
        }
        Spacer(Modifier.height(Space.sm))
        TearLine()
        Row(Modifier.fillMaxWidth().padding(top = Space.md), verticalAlignment = Alignment.CenterVertically) {
            Text("Total", Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
            LedgerAmount(money(split.shares.sumOf { it.amountPaise }), MaterialTheme.colorScheme.onSurface)
        }
    }
}

@Composable
private fun ShareRow(split: Split, sh: SplitShare, onSettle: (SplitShare) -> Unit) {
    val name = split.people.getOrNull(sh.personIndex)?.name ?: "?"
    val isPayer = sh.personIndex == split.payerIndex
    val settled = sh.remainingPaise <= 0
    val status = when {
        isPayer -> "Paid the bill"
        settled -> "Settled"
        sh.settledPaise > 0 -> "${money(sh.settledPaise)} paid · ${money(sh.remainingPaise)} left"
        else -> "Owes ${money(sh.remainingPaise)}"
    }
    Row(Modifier.fillMaxWidth().padding(vertical = Space.md), verticalAlignment = Alignment.Top) {
        PersonAvatar(name, 36.dp)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Row(Modifier.clearAndSetSemantics { contentDescription = "$name, share ${money(sh.amountPaise)}, $status" }, verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (!isPayer && settled) {
                            Icon(Icons.Outlined.CheckCircle, null, Modifier.size(14.dp), tint = Income)
                            Spacer(Modifier.width(Space.xs))
                        }
                        Text(
                            status, style = MaterialTheme.typography.bodySmall,
                            color = if (!isPayer && settled) Income else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Spacer(Modifier.width(Space.md))
                Column(Modifier.widthIn(min = LedgerAmountMinWidth), horizontalAlignment = Alignment.End) {
                    LedgerAmount(money(sh.amountPaise), MaterialTheme.colorScheme.onSurface)
                }
            }
            // Only what involves me: when I paid, each friend's share owed to me; when someone else paid, my share owed
            // to them. What a third person owes the payer is between them.
            if (!isPayer && sh.remainingPaise > 0 && !split.isAuto) {
                val payerName = split.people.getOrNull(split.payerIndex)?.name ?: "them"
                when {
                    split.iPaid -> TextAction("Settle up", { onSettle(sh) }, alignStart = true)
                    sh.personIndex == split.myIndex -> TextAction("I paid $payerName", { onSettle(sh) }, alignStart = true)
                }
            }
        }
    }
}

/** The bill's items and who shared each one. */
@Composable
private fun Items(split: Split, items: List<BillItem>) {
    Text("Items", style = MaterialTheme.typography.titleMedium, modifier = Modifier.semantics { heading() })
    FinCard(spacing = Space.sm) {
        items.forEachIndexed { i, it ->
            if (i > 0) Hairline()
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text((if (it.quantity > 1) "${it.quantity} × " else "") + it.name, style = MaterialTheme.typography.bodyMedium)
                    val who = it.assignedTo.mapNotNull { p -> split.people.getOrNull(p)?.name }
                    Text(if (who.isEmpty()) "everyone" else who.joinToString(", "), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Spacer(Modifier.width(Space.md))
                LedgerAmount(money(it.pricePaise * it.quantity), MaterialTheme.colorScheme.onSurface, style = MoneyType.small)
            }
        }
    }
}

/**
 * Settle up with one person, as a sheet: their likely incoming payments first (pick one and it stops counting as
 * income), then an amount typed by hand for cash or money paid elsewhere. Part of the share is fine.
 */
@Composable
private fun SettleSheet(vm: AppViewModel, split: Split, sh: SplitShare, onDone: () -> Unit) {
    val haptics = rememberHaptics()
    val name = split.people.getOrNull(sh.personIndex)?.name ?: "them"
    // Their transfer is already in the app: pick it, and it stops counting as income.
    val candidates = remember(split, sh) { vm.settleCandidates(split, sh.remainingPaise) }
    val matches = candidates.take(4)
    var input by remember(sh.id) { mutableStateOf(paiseToInput(sh.remainingPaise)) }
    val amt = Money.parsePaise(input)
    // A typed amount that one of their transfers matches: offer to use that transfer, so it stops counting as income.
    val typedMatch = amt?.takeIf { it > 0 }?.let { com.pft.financetracker.domain.split.SettleMatch.forTypedAmount(candidates, it) }

    FinSheet(onDismiss = onDone, title = "Settle up with $name") {
        SoftPanel(spacing = Space.xs) {
            CapsLabel("Still owed")
            AmountDisplay(sh.remainingPaise, size = AmountSize.Title, color = MaterialTheme.colorScheme.onSurface, spokenLabel = "Still owed")
        }
        if (matches.isNotEmpty()) {
            CapsLabel("Pick the payment they sent")
            Column {
                matches.forEach { t ->
                    CandidateRow(t) {
                        haptics.confirm()
                        vm.settleWithTransaction(split.id, sh.id, (sh.settledPaise + t.amountPaise).coerceAtMost(sh.amountPaise), t)
                        onDone()
                    }
                }
            }
            Hairline()
            CapsLabel("Or enter an amount")
            Text("Cash, or paid elsewhere. Part of it is fine.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Enter what they paid you. Part of it is fine.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            input, { input = it },
            label = { Text("Amount") }, prefix = { Text("₹") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            isError = input.isNotBlank() && (amt == null || amt <= 0),
            shape = ControlShape,
            modifier = Modifier.fillMaxWidth(),
        )
        typedMatch?.let { t ->
            Text(
                "${displayMerchant(t.merchant)} sent ${money(t.amountPaise)} on ${dateOnly(t.timestamp)}. Use that payment so it isn't counted as income.",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            TextAction("Use that payment", {
                haptics.confirm()
                vm.settleWithTransaction(split.id, sh.id, (sh.settledPaise + t.amountPaise).coerceAtMost(sh.amountPaise), t)
                onDone()
            }, alignStart = true)
        }
        PrimaryButton("Mark paid", {
            val a = amt
            if (a == null || a <= 0) { haptics.reject(); return@PrimaryButton }
            haptics.confirm()
            vm.settleShare(sh.id, (sh.settledPaise + a).coerceAtMost(sh.amountPaise))
            onDone()
        }, enabled = amt != null && amt > 0)
    }
}

/**
 * Pay back whoever paid, as a sheet: my own payments that could be it first (pick one and it stops counting as spend,
 * since my share is already counted), then an amount typed by hand for cash.
 */
@Composable
private fun PayBackSheet(vm: AppViewModel, split: Split, sh: SplitShare, onDone: () -> Unit) {
    val haptics = rememberHaptics()
    val payer = split.people.getOrNull(split.payerIndex)?.name ?: "them"
    val matches = remember(split, sh) { vm.payoutCandidates(split, sh.remainingPaise).take(4) }
    var input by remember(sh.id) { mutableStateOf(paiseToInput(sh.remainingPaise)) }
    val amt = Money.parsePaise(input)

    FinSheet(onDismiss = onDone, title = "Pay back $payer") {
        SoftPanel(spacing = Space.xs) {
            CapsLabel("You owe")
            AmountDisplay(sh.remainingPaise, size = AmountSize.Title, color = MaterialTheme.colorScheme.onSurface, spokenLabel = "You owe")
        }
        if (matches.isNotEmpty()) {
            CapsLabel("Pick the payment you sent")
            Column {
                matches.forEach { t ->
                    CandidateRow(t, incoming = false) {
                        haptics.confirm()
                        vm.settleWithTransaction(split.id, sh.id, (sh.settledPaise + t.amountPaise).coerceAtMost(sh.amountPaise), t)
                        onDone()
                    }
                }
            }
            Hairline()
            CapsLabel("Or enter an amount")
            Text("Cash, or paid from an account FinTrack doesn't read. Part of it is fine.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            Text("Enter what you paid $payer. Part of it is fine.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        OutlinedTextField(
            input, { input = it },
            label = { Text("Amount") }, prefix = { Text("₹") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
            isError = input.isNotBlank() && (amt == null || amt <= 0),
            shape = ControlShape,
            modifier = Modifier.fillMaxWidth(),
        )
        PrimaryButton("Mark paid", {
            val a = amt
            if (a == null || a <= 0) { haptics.reject(); return@PrimaryButton }
            haptics.confirm()
            vm.settleShare(sh.id, (sh.settledPaise + a).coerceAtMost(sh.amountPaise))
            onDone()
        }, enabled = amt != null && amt > 0)
    }
}

/** A payment offered as the settlement: who it came from (or went to), when, and how much. */
@Composable
private fun CandidateRow(t: Transaction, incoming: Boolean = true, onPick: () -> Unit) {
    val who = displayMerchant(t.merchant)
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp).clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = "Use this payment", role = Role.Button, onClick = onPick)
            .clearAndSetSemantics { contentDescription = "${money(t.amountPaise)} ${if (incoming) "in from" else "paid to"} $who, ${dateOnly(t.timestamp)}"; role = Role.Button }
            .padding(vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TintedSquare(Icons.Outlined.Payments, tint = if (incoming) Income else MaterialTheme.colorScheme.onSurfaceVariant, size = 36.dp, background = surfaces.sunken)
        Spacer(Modifier.width(Space.md))
        Column(Modifier.weight(1f)) {
            Text(who, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(dateOnly(t.timestamp), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(Space.md))
        if (incoming) LedgerAmount("+" + money(t.amountPaise), Income) else LedgerAmount(money(t.amountPaise), MaterialTheme.colorScheme.onSurface)
    }
}
