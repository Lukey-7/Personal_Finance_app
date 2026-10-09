package com.pft.financetracker.ui.screens.ask

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.ask.AskAnswer
import com.pft.financetracker.domain.ask.AskEngine
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.MarkdownText
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.theme.surfaces
import kotlinx.coroutines.launch

private data class Turn(val question: String, val answer: AskAnswer, val markdown: Boolean = false)

/**
 * Ask about your own numbers in plain words. With an OpenAI key and "Answer Ask with ChatGPT" on, ChatGPT answers from
 * a summary of the payments (and the conversation so far); otherwise the rules on this phone, and Gemini Nano where the
 * phone has it. A friendly heading, the conversation, suggestions, the pill input.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(vm: AppViewModel, onOpenTransaction: (Long) -> Unit, onBack: () -> Unit) {
    val turns = remember { mutableStateListOf<Turn>() }
    var input by remember { mutableStateOf("") }
    val txns by vm.transactions.collectAsState()
    val loaded by vm.loaded.collectAsState()
    val byId = remember(txns) { txns.associateBy { it.id } }
    val hasKey by vm.hasApiKey.collectAsState()
    val askOpenAi by vm.askUseOpenAi.collectAsState()
    val online = hasKey && askOpenAi
    var thinking by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    LaunchedEffect(turns.size, thinking) { if (turns.isNotEmpty() || thinking != null) list.animateScrollToItem(turns.size + if (thinking != null) 1 else 0) }
    // Answers read the transactions, so questions wait until they have loaded rather than answer from nothing.
    val ready = loaded

    fun send(q: String) {
        if (q.isBlank() || !ready || thinking != null) return
        input = ""
        val question = q.trim()
        val history = turns.map { it.question to it.answer.text }
        thinking = question
        scope.launch {
            try { turns += Turn(question, vm.ask(question, history)) } finally { thinking = null }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Ask FinTrack") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).imePadding()) {
            LazyColumn(
                Modifier.weight(1f),
                state = list,
                contentPadding = PaddingValues(start = Gutter, top = Space.sm, end = Gutter, bottom = Space.lg),
                verticalArrangement = Arrangement.spacedBy(Space.lg),
            ) {
                item(key = "hello") {
                    Column(verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                        Text("What would you like to know?", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.semantics { heading() })
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.Lock, null, Modifier.size(16.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(Space.sm))
                            Text(
                                if (online) "ChatGPT answers from a summary of your payments. Turn this off in Settings › AI."
                                else "Answers come from your transactions on this phone. Nothing is sent anywhere.",
                                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        if (turns.isEmpty()) Text(
                            "Ask in plain words, like “how much on food this month”, or tap a suggestion below.",
                            style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = Space.sm),
                        )
                    }
                }
                items(turns) { t -> TurnCard(t, byId, onOpenTransaction) }
                thinking?.let { q ->
                    item(key = "thinking") {
                        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
                            Bubble(q, Modifier.align(Alignment.End))
                            SoftPanel {
                                Text("Thinking…", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
            Hairline()
            ChipRow(Modifier.padding(top = Space.sm, bottom = Space.xs)) {
                PillChip(false, "This month in words", icon = Icons.Outlined.AutoAwesome) {
                    if (ready) scope.launch { turns += Turn("This month in words", AskAnswer(vm.monthInWords()), markdown = true) }
                }
                AskEngine.examples.forEach { e -> PillChip(false, e) { send(e) } }
            }
            Row(
                Modifier.fillMaxWidth().padding(start = Gutter, end = Space.md, top = Space.xs, bottom = Space.md),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // The same sunken pill as the search field; the send button fills with the accent once there is a question.
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f),
                    placeholder = { Text(if (ready) "Ask anything about your money" else "Loading your transactions…", maxLines = 1, overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis) },
                    singleLine = true,
                    shape = CircleShape,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedContainerColor = surfaces.sunken,
                        unfocusedContainerColor = surfaces.sunken,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Transparent,
                    ),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                    keyboardActions = KeyboardActions(onSend = { send(input) }),
                )
                Spacer(Modifier.width(Space.sm))
                FilledIconButton(
                    onClick = { send(input) },
                    enabled = ready && input.isNotBlank() && thinking == null,
                    modifier = Modifier.size(48.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary,
                        disabledContainerColor = surfaces.sunken,
                        disabledContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                ) { Icon(Icons.AutoMirrored.Outlined.Send, "Ask") }
            }
        }
    }
}

/** One exchange: the question on the right in the accent tint, the answer in a sunken well with its payments on request. */
@Composable
private fun TurnCard(t: Turn, byId: Map<Long, Transaction>, onOpen: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Space.sm)) {
        Bubble(t.question, Modifier.align(Alignment.End))
        SoftPanel {
            // Model answers come with markdown (bold, bullets); show it formatted, not as raw asterisks.
            if (t.markdown || t.answer.byAi) MarkdownText(t.answer.text) else Text(t.answer.text, style = MaterialTheme.typography.bodyLarge)
            if (t.answer.byAi) Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.AutoAwesome, null, Modifier.size(14.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(Space.xs))
                Text(
                    if (t.answer.byOpenAi) "ChatGPT, from your FinTrack data. AI can get things wrong: check figures in Activity."
                    else "Gemini Nano, on this phone. AI can get things wrong: check figures in Activity.",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            val rows = t.answer.transactionIds.mapNotNull { byId[it] }
            if (rows.isNotEmpty()) TextAction(if (expanded) "Hide payments" else "Show ${countLabel(rows.size, "payment")}", { expanded = !expanded }, alignStart = true)
        }
        if (expanded) {
            val rows = t.answer.transactionIds.mapNotNull { byId[it] }.sortedByDescending { it.timestamp }.take(30)
            FinCard(padding = PaddingValues(vertical = Space.xs), spacing = 0.dp) {
                rows.forEachIndexed { i, r ->
                    if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                    TransactionRow(r) { onOpen(r.id) }
                }
            }
        }
    }
}

/** The question, on the right in the accent tint. */
@Composable
private fun Bubble(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = modifier.widthIn(max = 300.dp)
            .clip(RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp, bottomStart = 18.dp, bottomEnd = 6.dp))
            .background(surfaces.accentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}
