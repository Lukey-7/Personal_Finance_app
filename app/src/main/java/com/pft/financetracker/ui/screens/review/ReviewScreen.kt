package com.pft.financetracker.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.isLoaded
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LearnMore
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.RowIcon
import com.pft.financetracker.ui.components.RowIconGap
import com.pft.financetracker.ui.components.RowIconSize
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.fullDate
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.reasonLabel
import com.pft.financetracker.ui.theme.Income
import com.pft.financetracker.ui.theme.MoneyType
import com.pft.financetracker.ui.theme.rememberHaptics

/**
 * Messages FinTrack wasn't sure about, one ledger entry each: who sent it, why it stopped, what it looks like, and the
 * message itself in a sunken well. "Add it" opens the editor filled in from the message; "Not a transaction" drops it.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, onEnter: (Long) -> Unit, onBack: () -> Unit) {
    val queue by vm.reviewQueue.collectAsState()
    val haptics = rememberHaptics()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Needs review", style = MaterialTheme.typography.titleLarge)
                        if (queue.isNotEmpty()) Text(
                            if (queue.size == 1) "1 message to check" else "${queue.size} messages to check",
                            style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (!isLoaded(queue)) {
            SkeletonRows(4, Modifier.padding(padding))
            return@Scaffold
        }
        if (queue.isEmpty()) {
            EmptyState(
                Icons.Outlined.TaskAlt,
                "All caught up",
                "When FinTrack can't tell whether a message was a payment, it waits here for you. Nothing needs a look right now.",
                Modifier.padding(padding),
            ) { TextAction("Go back", onBack) }
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(top = Space.xs, bottom = bottomPadding())) {
            item(key = "intro") {
                LearnMore(
                    summary = "FinTrack wasn't sure about these. Add the ones that were payments.",
                    title = "Why messages end up here",
                    body = REVIEW_BODY,
                    modifier = Modifier.padding(horizontal = Gutter).padding(bottom = Space.sm),
                )
            }
            itemsIndexed(queue, key = { _, r -> r.id }) { i, r ->
                Column(Modifier.animateItem()) {
                    if (i > 0) Hairline(startInset = Gutter, endInset = Gutter)
                    ReviewEntry(
                        r,
                        onAdd = { onEnter(r.id) },
                        onDismiss = { haptics.confirm(); vm.dismissReview(r.id) },
                    )
                }
            }
        }
    }
}

/** One message in the queue, laid out like a ledger row with the message and its two choices under it. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ReviewEntry(r: ReviewItemEntity, onAdd: () -> Unit, onDismiss: () -> Unit) {
    var whole by rememberSaveable(r.id) { mutableStateOf(false) }
    var long by rememberSaveable(r.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = Gutter, vertical = Space.lg)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            RowIcon(Icons.Outlined.Sms)
            Spacer(Modifier.width(RowIconGap))
            Column(Modifier.weight(1f).semantics(mergeDescendants = true) { heading() }) {
                Text(r.sender, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(fullDate(r.receivedAt), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Column(
            Modifier.fillMaxWidth().padding(start = RowIconSize + RowIconGap, top = Space.sm),
            verticalArrangement = Arrangement.spacedBy(Space.sm),
        ) {
            Text(reasonLabel(r.reason), style = MaterialTheme.typography.bodyMedium)
            r.guessedAmountPaise?.let { a -> LooksLike(a, r.guessedType) }
            SoftPanel(padding = PaddingValues(horizontal = Space.lg, vertical = Space.md), spacing = Space.xs) {
                CapsLabel("Message")
                Text(
                    r.body,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = if (whole) Int.MAX_VALUE else 4,
                    overflow = TextOverflow.Ellipsis,
                    onTextLayout = { if (it.hasVisualOverflow) long = true },
                )
                if (long) TextAction(if (whole) "Show less" else "Show the whole message", { whole = !whole }, alignStart = true)
            }
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(Space.sm),
                verticalArrangement = Arrangement.spacedBy(Space.xs),
            ) {
                PrimaryButton("Add it", onAdd, fill = false)
                TextAction("Not a transaction", onDismiss, Modifier.align(Alignment.CenterVertically))
            }
        }
    }
}

/** "Looks like ₹500 going out": the guess in words, the figure in the money face. */
@Composable
private fun LooksLike(paise: Long, type: String?) {
    val tone = if (type == "CREDIT") Income else MaterialTheme.colorScheme.onSurface
    Text(
        buildAnnotatedString {
            append("Looks like ")
            withStyle(MoneyType.small.toSpanStyle().merge(SpanStyle(color = tone, fontWeight = FontWeight.SemiBold))) { append(money(paise)) }
            append(
                when (type) {
                    "DEBIT" -> " going out"
                    "CREDIT" -> " coming in"
                    else -> ", direction unclear"
                }
            )
        },
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

private const val REVIEW_BODY =
    "FinTrack reads bank and UPI messages on this phone. When one could be a payment but something is missing – it " +
        "can't find the amount, can't tell whether money came in or went out, or isn't sure enough – it stops here " +
        "instead of guessing. \"Add it\" opens the editor filled in from the message so you can check it and save. " +
        "\"Not a transaction\" removes it from this list; nothing is saved."
