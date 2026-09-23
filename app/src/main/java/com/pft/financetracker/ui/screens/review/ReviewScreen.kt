package com.pft.financetracker.ui.screens.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.TaskAlt
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.ui.Alignment
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.IconCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.fullDate
import com.pft.financetracker.ui.components.money
import androidx.compose.material3.TopAppBarDefaults
import com.pft.financetracker.ui.components.FinCard
import com.pft.financetracker.ui.components.Gutter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReviewScreen(vm: AppViewModel, onEnter: (Long) -> Unit, onBack: () -> Unit) {
    val queue by vm.reviewQueue.collectAsState()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("Needs review (${queue.size})") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "Back") } },
            )
        },
    ) { padding ->
        if (queue.isEmpty()) {
            EmptyState(Icons.Outlined.TaskAlt, "Nothing to review. Messages the parser is unsure about will appear here.", Modifier.padding(padding))
            return@Scaffold
        }
        LazyColumn(Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(start = Gutter, top = 8.dp, end = Gutter, bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            items(queue, key = { it.id }) { r ->
                FinCard {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            IconCircle(Icons.Outlined.Sms, tint = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(r.sender, style = MaterialTheme.typography.labelLarge)
                                Text(fullDate(r.receivedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                        Text(r.body, style = MaterialTheme.typography.bodySmall)
                        Text(
                            "Reason: ${r.reason.replace('_', ' ')}" + (r.guessedAmountPaise?.let { " · guessed ${money(it)}" } ?: "") + (r.guessedType?.let { " · $it" } ?: ""),
                            style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(onClick = { vm.dismissReview(r.id) }) { Text("Dismiss") }
                            Button(onClick = { onEnter(r.id) }) { Text("Enter details") }
                        }
                    }
                }
            }
        }
    }
}
