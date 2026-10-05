package com.pft.financetracker.ui.screens.transactions

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.Sms
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AddFab
import com.pft.financetracker.ui.components.ChipRow
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.SearchField
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.dateOnly
import com.pft.financetracker.ui.components.money

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionsScreen(vm: AppViewModel, onAdd: () -> Unit, onEdit: (Long) -> Unit, onOpenReview: () -> Unit, onOpenSmsLog: () -> Unit, onOpenImport: () -> Unit = {}) {
    val txns by vm.transactions.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    var query by remember { mutableStateOf("") }
    var filter by remember { mutableStateOf<Category?>(null) }
    val badges by vm.refundBadges.collectAsState()
    var showReversed by remember { mutableStateOf(false) }
    val hiddenCount = txns.count { it.id in badges.hiddenByDefault }

    val filtered = txns.filter { t ->
        (showReversed || t.id !in badges.hiddenByDefault) &&
        (filter == null || t.category == filter) &&
            (query.isBlank() || t.merchant.contains(query, true) || (t.bankName ?: "").contains(query, true) || money(t.amountPaise).contains(query) || (t.refNumber ?: "").contains(query, true))
    }
    val grouped = filtered.groupBy { dateOnly(it.timestamp) }
    val barPad = LocalBottomBarPadding.current

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                title = { Text("Activity") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    if (reviewCount > 0) IconButton(onClick = onOpenReview) {
                        BadgedBox(badge = { Badge { Text(reviewCount.toString()) } }) {
                            Icon(Icons.Outlined.Inbox, "Needs review: $reviewCount")
                        }
                    }
                    IconButton(onClick = onOpenImport) { Icon(Icons.Outlined.UploadFile, "Import statement") }
                    IconButton(onClick = onOpenSmsLog) { Icon(Icons.Outlined.Sms, "SMS log") }
                },
            )
        },
        floatingActionButton = { AddFab(onAdd, Modifier.padding(bottom = barPad)) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            SearchField(query, { query = it }, "Search merchant, bank, amount", Modifier.padding(horizontal = Gutter, vertical = 8.dp))
            ChipRow(Modifier.padding(bottom = 8.dp)) {
                PillChip(filter == null, "All") { filter = null }
                Category.entries.forEach { c ->
                    PillChip(filter == c, c.label, icon = categoryIcon(c)) { filter = if (filter == c) null else c }
                }
            }
            // Failed payments that came straight back never really happened; keep them out of the way, one tap to see.
            if (hiddenCount > 0) TextButton(onClick = { showReversed = !showReversed }, modifier = Modifier.padding(horizontal = Gutter - 12.dp)) {
                Text(if (showReversed) "Hide reversed payments" else "Show $hiddenCount reversed payment${if (hiddenCount == 1) "" else "s"}")
            }
            if (filtered.isEmpty()) {
                EmptyState(Icons.Outlined.SearchOff, if (txns.isEmpty()) "No transactions yet." else "Nothing matches this search or filter.")
            }
            LazyColumn(contentPadding = PaddingValues(bottom = bottomPadding(FabClearance))) {
                grouped.forEach { (day, list) ->
                    item(key = "h_$day") {
                        Text(day, Modifier.padding(start = Gutter, end = Gutter, top = 16.dp, bottom = 8.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Hairline(startInset = Gutter, endInset = Gutter)
                    }
                    items(list, key = { it.id }) { t -> TransactionRow(t, showDate = false, tag = badges.tag(t.id)) { onEdit(t.id) } }
                }
            }
        }
    }
}
