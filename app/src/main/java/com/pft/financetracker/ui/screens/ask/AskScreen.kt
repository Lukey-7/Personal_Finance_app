package com.pft.financetracker.ui.screens.ask

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.Send
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.ask.AskAnswer
import com.pft.financetracker.domain.ask.AskEngine
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.MarkdownText
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.SoftPanel
import com.pft.financetracker.ui.components.TransactionRow
import kotlinx.coroutines.launch

private data class Turn(val question: String, val answer: AskAnswer, val markdown: Boolean = false)

/** Ask about your own numbers in plain words. Answered by rules on this phone; nothing is sent anywhere. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AskScreen(vm: AppViewModel, onOpenTransaction: (Long) -> Unit, onBack: () -> Unit) {
    val turns = remember { mutableStateListOf<Turn>() }
    var input by remember { mutableStateOf("") }
    val txns by vm.transactions.collectAsState()
    val byId = remember(txns) { txns.associateBy { it.id } }
    val scope = rememberCoroutineScope()
    val list = rememberLazyListState()
    LaunchedEffect(turns.size) { if (turns.isNotEmpty()) list.animateScrollToItem(turns.size) }

    fun send(q: String) {
        if (q.isBlank()) return
        input = ""
        scope.launch { turns += Turn(q.trim(), vm.ask(q)) }
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
            LazyColumn(Modifier.weight(1f), state = list, contentPadding = PaddingValues(start = Gutter, top = 8.dp, end = Gutter, bottom = 8.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    Text("Answers come from your transactions on this phone. Nothing is sent anywhere.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                items(turns) { t -> TurnCard(t, byId, onOpenTransaction) }
            }
            ChipRow(Modifier.padding(vertical = 8.dp)) {
                PillChip(false, "This month in words") { scope.launch { turns += Turn("This month in words", AskAnswer(vm.monthInWords()), markdown = true) } }
                AskEngine.examples.forEach { e -> PillChip(false, e) { send(e) } }
            }
            Row(Modifier.fillMaxWidth().padding(start = Gutter, end = 8.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    input, { input = it }, Modifier.weight(1f), placeholder = { Text("e.g. food last month") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send), keyboardActions = KeyboardActions(onSend = { send(input) }),
                )
                IconButton(onClick = { send(input) }, enabled = input.isNotBlank()) { Icon(Icons.AutoMirrored.Outlined.Send, "Ask") }
            }
        }
    }
}

@Composable
private fun TurnCard(t: Turn, byId: Map<Long, com.pft.financetracker.domain.model.Transaction>, onOpen: (Long) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(t.question, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.align(Alignment.End))
        SoftPanel {
            if (t.markdown) MarkdownText(t.answer.text) else Text(t.answer.text)
            if (t.answer.byAi) Text("Gemini Nano, on this phone. AI can get things wrong: check figures in Activity.", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val rows = t.answer.transactionIds.mapNotNull { byId[it] }
            if (rows.isNotEmpty()) TextButton(onClick = { expanded = !expanded }) { Text(if (expanded) "Hide payments" else "Show ${rows.size} payment${if (rows.size == 1) "" else "s"}") }
            if (expanded) FinCard(padding = PaddingValues(vertical = 4.dp)) {
                Column { rows.sortedByDescending { it.timestamp }.take(30).forEach { r -> TransactionRow(r) { onOpen(r.id) } } }
            }
        }
    }
}
