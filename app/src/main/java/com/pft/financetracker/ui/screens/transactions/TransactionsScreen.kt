package com.pft.financetracker.ui.screens.transactions

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.CallSplit
import androidx.compose.material.icons.automirrored.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material.icons.outlined.SearchOff
import androidx.compose.material.icons.outlined.SelectAll
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.ui.AppViewModel
import com.pft.financetracker.ui.components.AddButton
import com.pft.financetracker.ui.components.AppliedTag
import com.pft.financetracker.ui.components.CapsLabel
import com.pft.financetracker.ui.components.CategoriseIcon
import com.pft.financetracker.ui.components.ChipFlow
import com.pft.financetracker.ui.components.ControlShape
import com.pft.financetracker.ui.components.DateField
import com.pft.financetracker.ui.components.DayHeader
import com.pft.financetracker.ui.components.EmptyState
import com.pft.financetracker.ui.components.FabClearance
import com.pft.financetracker.ui.components.FilterButton
import com.pft.financetracker.ui.components.FinSheet
import com.pft.financetracker.ui.components.FinSnackbarHost
import com.pft.financetracker.ui.components.Gutter
import com.pft.financetracker.ui.components.Hairline
import com.pft.financetracker.ui.components.LocalBottomBarPadding
import com.pft.financetracker.ui.components.PillChip
import com.pft.financetracker.ui.components.PrimaryButton
import com.pft.financetracker.ui.components.RowTextInset
import com.pft.financetracker.ui.components.SearchField
import com.pft.financetracker.ui.components.SecondaryButton
import com.pft.financetracker.ui.components.SkeletonRows
import com.pft.financetracker.ui.components.Space
import com.pft.financetracker.ui.components.SwipeActions
import com.pft.financetracker.ui.components.TextAction
import com.pft.financetracker.ui.components.TransactionRow
import com.pft.financetracker.ui.components.avatarInk
import com.pft.financetracker.ui.components.bottomPadding
import com.pft.financetracker.ui.components.categoryColor
import com.pft.financetracker.ui.components.categoryIcon
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.displayMerchant
import com.pft.financetracker.ui.components.rememberAtTop
import com.pft.financetracker.ui.model.ActivityFilter
import com.pft.financetracker.ui.model.DayNet
import com.pft.financetracker.ui.model.DeleteWithUndo
import com.pft.financetracker.ui.model.Selection
import com.pft.financetracker.ui.theme.surfaces
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Activity: every transaction, newest first, in days with their net totals. Search and a filter sheet narrow it; swipe
 * right to categorise, left to delete (with Undo); long-press to select several and move them to another category.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun TransactionsScreen(
    vm: AppViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onOpenReview: () -> Unit,
    onOpenSmsLog: () -> Unit,
    onOpenImport: () -> Unit = {},
    onSplit: () -> Unit = {},
) {
    val txns by vm.transactions.collectAsState()
    val reviewCount by vm.reviewCount.collectAsState()
    val badges by vm.refundBadges.collectAsState()
    val loaded by vm.loaded.collectAsState()
    var query by rememberSaveable { mutableStateOf("") }
    var filter by remember { mutableStateOf(ActivityFilter()) }
    var selection by remember { mutableStateOf(Selection()) }
    var showFilters by remember { mutableStateOf(false) }
    var menu by remember { mutableStateOf(false) }
    var categorising by remember { mutableStateOf<Set<Long>?>(null) }
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()
    val deleter = remember { DeleteWithUndo { vm.delete(it) } }
    // Leaving the screen ends the chance to undo.
    DisposableEffect(Unit) { onDispose { deleter.commit() } }

    val hidden = badges.hiddenByDefault
    val shown = filter.apply(txns, query, hidden).filter { !deleter.hides(it.id) }
    val days = shown.groupBy { dayOf(it.timestamp) }

    fun delete(t: Transaction) {
        snackbar.currentSnackbarData?.dismiss()
        deleter.stage(t)
        scope.launch {
            val r = snackbar.showSnackbar("${displayMerchant(t.merchant).ifBlank { "Payment" }} deleted", actionLabel = "Undo", duration = SnackbarDuration.Short)
            // A newer delete may have replaced this one already; only settle the one this snackbar was about.
            if (deleter.pending?.id == t.id) { if (r == SnackbarResult.ActionPerformed) deleter.undo() else deleter.commit() }
        }
    }

    BackHandler(selection.active) { selection = Selection() }

    val barPad = LocalBottomBarPadding.current
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            if (selection.active) TopAppBar(
                title = { Text("${selection.ids.size} selected") },
                navigationIcon = { IconButton(onClick = { selection = Selection() }) { Icon(Icons.Outlined.Close, "End selection") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = surfaces.accentSoft),
                actions = {
                    IconButton(onClick = { selection = selection.selectAll(shown.map { it.id }) }) { Icon(Icons.Outlined.SelectAll, "Select all") }
                    TextAction("Category", { categorising = selection.ids })
                },
            ) else TopAppBar(
                title = { Text("Activity") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    if (reviewCount > 0) IconButton(onClick = onOpenReview) {
                        BadgedBox(badge = { Badge(containerColor = MaterialTheme.colorScheme.primary, contentColor = MaterialTheme.colorScheme.onPrimary) { Text(reviewCount.toString()) } }) {
                            Icon(Icons.Outlined.Inbox, "Review, $reviewCount waiting")
                        }
                    }
                    IconButton(onClick = { menu = true }) { Icon(Icons.Outlined.MoreVert, "More: import statement, SMS log") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Import statement") }, { menu = false; onOpenImport() }, leadingIcon = { Icon(Icons.Outlined.UploadFile, null) })
                        DropdownMenuItem({ Text("SMS log") }, { menu = false; onOpenSmsLog() }, leadingIcon = { Icon(Icons.Outlined.History, null) })
                        DropdownMenuItem({ Text("New split") }, { menu = false; onSplit() }, leadingIcon = { Icon(Icons.AutoMirrored.Outlined.CallSplit, null) })
                    }
                },
            )
        },
        floatingActionButton = { if (!selection.active) AddButton(onAdd, listState, Modifier.padding(bottom = barPad)) },
        snackbarHost = { FinSnackbarHost(snackbar, Modifier.padding(bottom = barPad)) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(Modifier.padding(start = Gutter, end = Gutter, top = Space.xs, bottom = Space.sm), verticalAlignment = Alignment.CenterVertically) {
                SearchField(query, { query = it }, "Merchant, bank or amount", Modifier.weight(1f))
                Spacer(Modifier.width(Space.sm))
                FilterButton(filter.count, { showFilters = true })
            }
            if (filter.isActive) ChipFlow(Modifier.padding(horizontal = Gutter).padding(bottom = Space.xs)) {
                filter.categories.forEach { c -> AppliedTag(c.label) { filter = filter.toggle(c) } }
                filter.accounts.forEach { a -> AppliedTag(a) { filter = filter.toggleAccount(a) } }
                filter.flows.forEach { f -> AppliedTag(f.label) { filter = filter.toggle(f) } }
                if (filter.hasRange) AppliedTag(rangeLabel(filter)) { filter = filter.copy(fromDay = null, toDay = null) }
                if (filter.showReversed) AppliedTag("Reversed shown") { filter = filter.copy(showReversed = false) }
            }
            when {
                !loaded -> SkeletonRows(8)
                txns.isEmpty() -> EmptyState(
                    Icons.AutoMirrored.Outlined.ReceiptLong, "No transactions yet",
                    "Payments appear here as FinTrack reads your bank SMS. Scan from Home, or add one yourself.",
                ) { SecondaryButton("Add a transaction", onAdd) }
                shown.isEmpty() -> EmptyState(
                    Icons.Outlined.SearchOff,
                    if (query.isNotBlank()) "Nothing matches “${query.trim()}”" else "Nothing matches these filters",
                    "Try fewer filters or a shorter search.",
                ) { TextAction("Clear search and filters", { query = ""; filter = ActivityFilter() }) }
                else -> LazyColumn(state = listState, contentPadding = PaddingValues(bottom = bottomPadding(FabClearance))) {
                    days.forEach { (day, list) ->
                        stickyHeader(key = "h_$day") {
                            val (name, date) = dayLabel(day)
                            DayHeader(name, date, DayNet.of(list))
                        }
                        itemsIndexed(list, key = { _, t -> t.id }) { i, t ->
                            Column(Modifier.animateItem()) {
                                if (i > 0) Hairline(startInset = RowTextInset, endInset = Gutter)
                                val row = @Composable {
                                    TransactionRow(
                                        t, showDate = false, tag = badges.tag(t.id), selected = t.id in selection,
                                        onLongClick = { selection = selection.toggle(t.id) },
                                    ) { if (selection.active) selection = selection.toggle(t.id) else onOpen(t.id) }
                                }
                                if (selection.active) row()
                                else SwipeActions(
                                    startLabel = "Categorise", startIcon = CategoriseIcon, onStartAction = { categorising = setOf(t.id) },
                                    endLabel = "Delete", onEndAction = { delete(t) },
                                ) { row() }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showFilters) FilterSheet(
        filter, txns, hiddenCount = txns.count { it.id in hidden },
        resultCount = { f -> f.apply(txns, query, hidden).size },
        onApply = { filter = it; showFilters = false },
        onDismiss = { showFilters = false },
    )

    categorising?.let { ids ->
        val current = if (ids.size == 1) txns.firstOrNull { it.id == ids.first() }?.category else null
        CategoryPickerSheet(
            title = if (ids.size == 1) "Move to a category" else "Move ${ids.size} payments to",
            selected = current,
            onPick = { c -> vm.recategorise(ids, c); categorising = null; selection = Selection() },
            onDismiss = { categorising = null },
        )
    }
}

/** Every category as a tile; tapping one picks it. Used by swipe-to-categorise and multi-select. */
@Composable
fun CategoryPickerSheet(title: String, selected: Category?, onPick: (Category) -> Unit, onDismiss: () -> Unit) {
    val s = surfaces
    FinSheet(onDismiss, title) {
        Category.entries.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(Space.sm)) {
                row.forEach { c ->
                    val on = c == selected
                    Column(
                        Modifier.weight(1f).heightIn(min = 76.dp).clip(ControlShape)
                            .background(if (on) s.accentSoft else s.card)
                            .border(if (on) 1.5.dp else 1.dp, if (on) MaterialTheme.colorScheme.primary else s.hairline, ControlShape)
                            .clickable(role = Role.RadioButton) { onPick(c) }
                            .semantics { this.selected = on; contentDescription = c.label }
                            .padding(Space.sm),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(categoryIcon(c), null, tint = avatarInk(categoryColor(c)))
                        Text(c.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis,
                            fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (on) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                    }
                }
                repeat(3 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** Category, account, kind of money, dates and reversed payments; applied together with "Show N payments". */
@Composable
private fun FilterSheet(
    start: ActivityFilter,
    txns: List<Transaction>,
    hiddenCount: Int,
    resultCount: (ActivityFilter) -> Int,
    onApply: (ActivityFilter) -> Unit,
    onDismiss: () -> Unit,
) {
    var f by remember { mutableStateOf(start) }
    val accounts = remember(txns) { ActivityFilter.accountsIn(txns) }
    FinSheet(onDismiss, "Filter") {
        CapsLabel("Category")
        ChipFlow { Category.entries.forEach { c -> PillChip(c in f.categories, c.label, icon = categoryIcon(c)) { f = f.toggle(c) } } }
        if (accounts.isNotEmpty()) {
            CapsLabel("Account")
            ChipFlow { accounts.forEach { a -> PillChip(a in f.accounts, a) { f = f.toggleAccount(a) } } }
        }
        CapsLabel("Kind of money")
        ChipFlow { Flow.entries.forEach { fl -> PillChip(fl in f.flows, fl.label) { f = f.toggle(fl) } } }
        CapsLabel("Dates")
        DateField("From", f.fromDay?.let { utcToLocalDate(it) }, { d -> f = f.copy(fromDay = startOfDay(d)) }, onClear = { f = f.copy(fromDay = null) })
        DateField("To", f.toDay?.let { utcToLocalDate(it) }, { d -> f = f.copy(toDay = startOfDay(d)) }, onClear = { f = f.copy(toDay = null) })
        if (hiddenCount > 0) Row(
            Modifier.fillMaxWidth().heightIn(min = 56.dp).clickable(role = Role.Switch) { f = f.copy(showReversed = !f.showReversed) },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Show reversed payments", style = MaterialTheme.typography.bodyLarge)
                Text("${countLabel(hiddenCount, "payment")} that failed and came straight back", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(f.showReversed, null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Space.sm), verticalAlignment = Alignment.CenterVertically) {
            TextAction("Clear all", { f = ActivityFilter() }, enabled = f.isActive)
            val n = resultCount(f)
            PrimaryButton(if (n == 0) "No payments match" else "Show ${countLabel(n, "payment")}", { onApply(f) }, Modifier.weight(1f))
        }
    }
}

private val zone: ZoneId get() = ZoneId.systemDefault()

private fun dayOf(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()
private fun startOfDay(d: LocalDate): Long = d.atStartOfDay(zone).toInstant().toEpochMilli()
private fun utcToLocalDate(ts: Long): LocalDate = Instant.ofEpochMilli(ts).atZone(zone).toLocalDate()

/** "Today", "Yesterday", or "Mon" with "5 Oct" (and the year when it is not this one). */
private fun dayLabel(d: LocalDate): Pair<String, String?> {
    val today = LocalDate.now(zone)
    val date = d.format(DateTimeFormatter.ofPattern(if (d.year == today.year) "d MMM" else "d MMM yyyy", Locale.ENGLISH))
    return when (d) {
        today -> "Today" to date
        today.minusDays(1) -> "Yesterday" to date
        else -> d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)) to date
    }
}

private fun rangeLabel(f: ActivityFilter): String {
    val fmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    val a = f.fromDay?.let { utcToLocalDate(it).format(fmt) }
    val b = f.toDay?.let { utcToLocalDate(it).format(fmt) }
    return when {
        a != null && b != null -> "$a – $b"
        a != null -> "From $a"
        else -> "Until $b"
    }
}
