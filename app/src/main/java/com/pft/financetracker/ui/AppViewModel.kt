package com.pft.financetracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pft.financetracker.appContainer
import com.pft.financetracker.data.readAtMost
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.ImportStats
import com.pft.financetracker.domain.ai.LlmRequest
import com.pft.financetracker.domain.ai.LlmResult
import com.pft.financetracker.domain.ai.MonthlySummary
import com.pft.financetracker.domain.ai.ProviderConfig
import com.pft.financetracker.domain.ai.ProviderKind
import com.pft.financetracker.domain.export.CsvExporter
import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CategoryChoice
import com.pft.financetracker.domain.model.CustomCategory
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.ocr.BillParser
import com.pft.financetracker.domain.ocr.ParsedBill
import com.pft.financetracker.domain.split.BillItem
import com.pft.financetracker.domain.split.Split
import com.pft.financetracker.ui.ocr.OcrEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import com.pft.financetracker.data.importer.StatementFiles
import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.local.ImportBatchEntity
import com.pft.financetracker.ui.model.saveStatement
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data object Running : ImportUiState()
    data class Done(val stats: ImportStats) : ImportUiState()
    /** The scan itself failed (permission revoked mid-scan, provider error): say so instead of "0 added". */
    data object Failed : ImportUiState()
}

sealed class AiUiState {
    data object Idle : AiUiState()
    data object Loading : AiUiState()
    data class Result(val text: String) : AiUiState()
    data class Error(val message: String) : AiUiState()
}

/** Settings › AI's "Test connection". */
sealed class AiTestState {
    data object Idle : AiTestState()
    data object Running : AiTestState()
    data class Ok(val reply: String) : AiTestState()
    data class Failed(val message: String) : AiTestState()
}

sealed class OcrUiState {
    data object Idle : OcrUiState()
    data object Running : OcrUiState()
    data class Done(val bill: ParsedBill) : OcrUiState()
    data class Error(val message: String) : OcrUiState()
}

/** Importing a statement or screenshots: read -> (password) -> preview -> saved. */
sealed class StatementUiState {
    data object Idle : StatementUiState()
    data object Reading : StatementUiState()
    data class NeedsPassword(val uri: Uri, val fileName: String, val wrong: Boolean) : StatementUiState()
    data class Preview(val preview: StatementImporter.Preview) : StatementUiState()
    data class Saved(val batch: ImportBatchEntity) : StatementUiState()
    data class Error(val message: String) : StatementUiState()
}

/**
 * Which period the dashboard shows. Kept in the view model so it survives tab switches. A month or a week is held by
 * how far back it is (0 = this one, -1 = the one before), so stepping back and forth and comparing with the period
 * before are always whole calendar months or weeks; a picked range is held as its first and last local day.
 */
sealed class PeriodChoice {
    data class Month(val offset: Int = 0) : PeriodChoice()
    data class Week(val offset: Int = 0) : PeriodChoice()
    data class Custom(val start: Long, val endInclusive: Long) : PeriodChoice()

    fun period(now: Long = System.currentTimeMillis()): Period = when (this) {
        is Month -> Periods.month(offset, now)
        is Week -> Periods.week(offset, now)
        is Custom -> Periods.custom(start, endInclusive, now)
    }

    /** The whole period just before this one: the month before, the week before, or as many days before a range. */
    fun previous(now: Long = System.currentTimeMillis()): Period = when (this) {
        is Month -> Periods.month(offset - 1, now)
        is Week -> Periods.week(offset - 1, now)
        is Custom -> Periods.before(period(now), now)
    }

    /** One step earlier (-1) or later (+1); null past the current month or week, and for a picked range. */
    fun step(delta: Int): PeriodChoice? = when (this) {
        is Month -> (offset + delta).takeIf { it <= 0 }?.let { Month(it) }
        is Week -> (offset + delta).takeIf { it <= 0 }?.let { Week(it) }
        is Custom -> null
    }

    /** "This month", "Last week", "September 2026", "Week of 21 Sep" or the picked range. */
    fun title(now: Long = System.currentTimeMillis()): String = when (this) {
        is Month -> when (offset) {
            0 -> "This month"
            -1 -> "Last month"
            else -> java.text.SimpleDateFormat("MMMM yyyy", java.util.Locale.ENGLISH).format(java.util.Date(period(now).start))
        }
        is Week -> when (offset) {
            0 -> "This week"
            -1 -> "Last week"
            else -> period(now).label.replace("Wk of", "Week of")
        }
        is Custom -> period(now).label
    }

    companion object {
        /**
         * The date-range picker gives UTC midnights; read them as calendar days and hold the local start of each, so
         * 1–31 Jul in India is 1 Jul 00:00 to 31 Jul, not 05:30 on each day.
         */
        fun fromPicker(utcStart: Long, utcEnd: Long, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): Custom {
            fun local(ms: Long) = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneOffset.UTC).toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            return Custom(local(utcStart), local(utcEnd))
        }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer

    val transactions: StateFlow<List<Transaction>> = c.transactions.all.stateIn(viewModelScope, SharingStarted.Eagerly, notLoaded())
    val budgets: StateFlow<List<Budget>> = c.budgets.all.stateIn(viewModelScope, SharingStarted.Eagerly, notLoaded())
    /** The person's own categories by id. */
    val customCategories: StateFlow<Map<Long, CustomCategory>> = c.categories.customs.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    /** Each payee the person gave a category to, and that category. */
    val payeeRules: StateFlow<List<Pair<String, CategoryChoice>>> = c.categories.payeeRules.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** The payments read through the counting rules. Every figure on every screen comes from here. */
    val books: StateFlow<Books> = combine(transactions, c.settings.countCashAsSpend, customCategories) { t, _, cc -> Books.of(t, c.settings.countingRules(), cc) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, Books.of(notLoaded()))
    val reviewQueue: StateFlow<List<ReviewItemEntity>> = c.transactions.reviewQueue.stateIn(viewModelScope, SharingStarted.Eagerly, notLoaded())
    val reviewCount: StateFlow<Int> = c.transactions.reviewCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val smsLog: StateFlow<List<SmsLogEntity>> = c.smsLog.recent.stateIn(viewModelScope, SharingStarted.Eagerly, notLoaded())
    val smsLogCounts: StateFlow<Map<String, Int>> = c.smsLog.counts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val splits: StateFlow<List<Split>> = c.splits.all.stateIn(viewModelScope, SharingStarted.Eagerly, notLoaded())
    /** Automatic splits waiting for a yes or no. */
    val splitSuggestions: StateFlow<List<Split>> = c.splits.all.map { l -> l.filter { it.isSuggestion } }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    /** Transaction id -> the applied automatic split it belongs to (for the "Auto-split" badge). */
    val autoSplitOf: StateFlow<Map<Long, Split>> = combine(c.splits.all, c.splits.links) { ss, ls ->
        val applied = ss.filter { it.isAuto && !it.isSuggestion }.associateBy { it.id }
        ls.mapNotNull { l -> applied[l.splitId]?.let { l.transactionId to it } }.toMap()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val importBatches: StateFlow<List<ImportBatchEntity>> = c.db.importDao().observeBatches().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val splitAi: StateFlow<Boolean> = c.settings.splitAi
    val remindersEnabled: StateFlow<Boolean> = c.settings.remindersEnabled
    val recentPeople: StateFlow<List<String>> = c.splits.recentPeople.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** False until the database has answered for transactions, splits and budgets, so screens show a spinner, not "nothing yet". */
    // From the books, not the raw list: once this is true the books on screen hold the loaded payments too.
    val loaded: StateFlow<Boolean> = combine(books, splits, budgets) { a, b, d -> isLoaded(a.all) && isLoaded(b) && isLoaded(d) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasApiKey: StateFlow<Boolean> = c.settings.hasApiKey
    val apiKeyBuiltIn: StateFlow<Boolean> = c.settings.apiKeyBuiltIn
    val onboarded: StateFlow<Boolean> = c.settings.onboarded
    val autoImport: StateFlow<Boolean> = c.settings.autoImport
    val lastImportAt: StateFlow<Long> = c.settings.lastImportAt
    val countCashAsSpend: StateFlow<Boolean> = c.settings.countCashAsSpend
    val myName: StateFlow<String> = c.settings.myName

    private val _period = MutableStateFlow<PeriodChoice>(PeriodChoice.Month(0))
    val period: StateFlow<PeriodChoice> = _period
    fun setPeriod(p: PeriodChoice) { _period.value = p }

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState

    private val _ocrState = MutableStateFlow<OcrUiState>(OcrUiState.Idle)
    val ocrState: StateFlow<OcrUiState> = _ocrState

    private val _statementState = MutableStateFlow<StatementUiState>(StatementUiState.Idle)
    val statementState: StateFlow<StatementUiState> = _statementState

    init {
        // Pick up anything new (and ask the AI about unclear groups) whenever the app starts.
        refreshSplits()
    }

    /** Re-run split intelligence. Cheap without AI; with AI, unchanged weeks come from the cache. */
    fun refreshSplits(useAi: Boolean = true) {
        // The AI judge runs in this screen's scope, so closing the app stops it; local rules run in the ledger's own.
        if (useAi) viewModelScope.launch(Dispatchers.IO) { c.ledger.refreshWithAi() } else c.ledger.followUp()
    }

    fun hasSmsPermission() = c.importer.hasSmsPermission()

    fun scanInbox(full: Boolean = false) {
        if (_importState.value is ImportUiState.Running) return
        _importState.value = ImportUiState.Running
        viewModelScope.launch {
            val since = if (full) 0L else lastImportAt.value
            val stats = runCatching { c.importer.scanInbox(since) }.getOrNull()
            _importState.value = if (stats == null) ImportUiState.Failed else ImportUiState.Done(stats)
            refreshSplits(useAi = true)
        }
    }

    fun dismissImportResult() { _importState.value = ImportUiState.Idle }

    fun setOnboarded(v: Boolean) = c.settings.setOnboarded(v)
    fun setAutoImport(v: Boolean) = c.settings.setAutoImport(v)
    /** The widget shows this month's spend, which changes with the setting. */
    fun setCountCashAsSpend(v: Boolean) = viewModelScope.launch {
        c.settings.setCountCashAsSpend(v)
        com.pft.financetracker.ui.widget.FinTrackWidget.refresh(getApplication())
    }
    fun setMyName(v: String) = c.settings.setMyName(v)

    /** [opened] is the payment as the editor first showed it, so only what the person changed is written. */
    fun save(t: Transaction, opened: Transaction? = null, onDone: () -> Unit = {}) = viewModelScope.launch {
        // The ledger fits the flow to the direction and marks an existing row as corrected by a person.
        if (t.id == 0L) c.ledger.add(t) else c.ledger.correct(t, opened)
        onDone()
    }

    fun delete(t: Transaction) = viewModelScope.launch {
        // The ledger gives back paired refunds and remembers the deletion for the next scan or import.
        c.ledger.remove(t)
    }

    /** Moves the chosen rows to [category] (Activity's multi-select). Only rows that change are written. */
    fun recategorise(ids: Set<Long>, category: Category) = recategorise(ids, CategoryChoice(category))

    fun recategorise(ids: Set<Long>, choice: CategoryChoice) = viewModelScope.launch {
        c.ledger.recategorise(ids, choice)
    }

    /**
     * The person's earlier payments from [t]'s payee that are filed elsewhere and that they never corrected one by one:
     * what "Also move the earlier ones?" offers after a correction.
     */
    fun alsoMovable(t: Transaction): List<Transaction> =
        com.pft.financetracker.domain.categorize.PayeeRules.alsoMovable(transactions.value, t.merchant, t.choice, t.id)

    /** Files [ids] the way the payee rule already says; the rule itself is not learned again. */
    fun moveAlso(ids: Set<Long>, choice: CategoryChoice) = viewModelScope.launch {
        c.ledger.recategorise(ids, choice, learn = false)
    }

    /** Adds a category of the person's own; [onDone] gets its id, or null when the name is blank or taken. */
    fun addCategory(name: String, parent: Category, onDone: (Long?) -> Unit = {}) = viewModelScope.launch {
        onDone(withContext(Dispatchers.IO) { c.categories.add(name, parent) })
    }

    fun updateCategory(id: Long, name: String, parent: Category, onDone: (Boolean) -> Unit = {}) = viewModelScope.launch {
        val ok = withContext(Dispatchers.IO) { c.ledger.together { c.categories.update(id, name, parent).also { if (it) c.transactions.reparent(id, parent) } } }
        onDone(ok)
    }

    /** Deletes one of the person's own categories; its payments go back to the built-in it counted as. */
    fun deleteCategory(id: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.ledger.together { c.categories.delete(id) } }
    }

    /** The category a new payment from [merchant] would get: the person's rule for that payee, else the keyword guess. */
    fun suggestCategory(merchant: String, type: TransactionType): CategoryChoice = c.categories.rules.categorise(merchant, type)

    fun forgetPayeeRule(payeeKey: String) = viewModelScope.launch { withContext(Dispatchers.IO) { c.categories.forget(payeeKey) } }

    /** The SMS a transaction was read from, while the inbox still has it; null for manual rows or a deleted message. */
    suspend fun smsTextFor(t: Transaction): String? {
        val hash = t.smsHash ?: return null
        val entry = withContext(Dispatchers.IO) { c.db.smsLogDao().getByHash(hash) } ?: return null
        return smsBody(entry)
    }

    suspend fun getTransaction(id: Long): Transaction? = c.transactions.getById(id)
    suspend fun getReview(id: Long): ReviewItemEntity? = c.transactions.getReview(id)

    /** Save a transaction entered from a review item and remove the item from the queue. */
    fun resolveReview(reviewId: Long, t: Transaction, onDone: () -> Unit = {}) = viewModelScope.launch {
        // The confirmed message teaches the parser this sender's wording, so the next one needs no review.
        val review = c.transactions.getReview(reviewId)
        review?.let { r -> withContext(Dispatchers.IO) { runCatching { c.templates.learn(r.sender, r.body, t) } } }
        // Same duplicate check as an import, so a second alert for a payment already saved is merged, not added again.
        c.ledger.approve(reviewId, t, review?.body)
        onDone()
    }

    fun dismissReview(reviewId: Long) = viewModelScope.launch {
        val r = c.transactions.getReview(reviewId)
        c.transactions.resolveReview(reviewId)
        r?.let { c.importer.recordDismissed(it.smsHash) }
    }

    fun setBudget(category: Category, limitPaise: Long, customId: Long? = null) = viewModelScope.launch { c.budgets.set(category, limitPaise, customId) }

    // ---- Duplicate cleanup ----
    private val _duplicates = MutableStateFlow<List<TransactionRepository.DuplicatePair>>(emptyList())
    val duplicates: StateFlow<List<TransactionRepository.DuplicatePair>> = _duplicates

    private val _scanningDuplicates = MutableStateFlow(false)
    val scanningDuplicates: StateFlow<Boolean> = _scanningDuplicates

    /** True once a sweep has run, so the UI can say "none found" rather than showing nothing at all. */
    private val _duplicatesScanned = MutableStateFlow(false)
    val duplicatesScanned: StateFlow<Boolean> = _duplicatesScanned

    /** Look for the same payment stored twice. Nothing is deleted until [mergeDuplicates] is called. */
    fun findDuplicates() {
        if (_scanningDuplicates.value) return
        _scanningDuplicates.value = true
        viewModelScope.launch {
            _duplicates.value = runCatching { c.transactions.findExistingDuplicates() }.getOrDefault(emptyList())
            _duplicatesScanned.value = true
            _scanningDuplicates.value = false
        }
    }

    /** Merges the pairs the scan found; [onDone] gets how many merged and how many changed since the scan. */
    fun mergeDuplicates(onDone: (merged: Int, skipped: Int) -> Unit = { _, _ -> }) = viewModelScope.launch {
        val pairs = _duplicates.value
        val outcome = runCatching { c.ledger.mergeTwins(pairs) { id -> c.db.splitDao().linksForTransaction(id).isNotEmpty() } }
            .getOrElse { TransactionRepository.MergeOutcome(merged = 0, skipped = pairs.size) }
        _duplicates.value = emptyList()
        onDone(outcome.merged, outcome.skipped)
    }

    fun clearDuplicates() { _duplicates.value = emptyList(); _duplicatesScanned.value = false }

    // ---- SMS log ----
    suspend fun smsBody(e: SmsLogEntity): String? = withContext(Dispatchers.IO) { runCatching { c.importer.readBody(e.sender, e.receivedAt, e.smsHash) }.getOrNull() }
    fun flagLogEntry(logId: Long, onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(c.importer.sendToReview(logId)) }

    // ---- AI ----
    /** The provider every AI feature uses, and its settings (key blanked). */
    val aiProvider: StateFlow<ProviderKind> = c.settings.aiProvider
    val aiConfig: StateFlow<ProviderConfig> = c.settings.aiConfig
    /** Who answers, for the screens' copy: "ChatGPT", "Claude", "your local model". */
    val aiName: StateFlow<String> = c.settings.aiProvider.map { it.assistantName }.stateIn(viewModelScope, SharingStarted.Eagerly, c.settings.aiProvider.value.assistantName)

    fun setAiProvider(kind: ProviderKind) { c.settings.setAiProvider(kind); _aiTest.value = AiTestState.Idle }
    fun setAiModel(model: String) { c.settings.setAiModel(aiProvider.value, model); _aiTest.value = AiTestState.Idle }
    fun setAiBaseUrl(url: String) { c.settings.setAiBaseUrl(aiProvider.value, url); _aiTest.value = AiTestState.Idle }
    fun setAiRegion(region: String) { c.settings.setAiRegion(region); _aiTest.value = AiTestState.Idle }
    /** Saves the active provider's model, address and region, and its key when one was typed. */
    fun saveAiSettings(model: String, url: String, region: String, key: String?) {
        val kind = aiProvider.value
        c.settings.setAiModel(kind, model)
        if (kind.editableUrl) c.settings.setAiBaseUrl(kind, url)
        if (kind == ProviderKind.BEDROCK) c.settings.setAiRegion(region)
        if (key != null) c.settings.setProviderKey(kind, key)
        _aiTest.value = AiTestState.Idle
        clearAi()
    }

    /** Saves (or, with null, removes) the key for the active provider. */
    fun setApiKey(key: String?) { c.settings.setProviderKey(aiProvider.value, key); _aiTest.value = AiTestState.Idle }

    private val _aiTest = MutableStateFlow<AiTestState>(AiTestState.Idle)
    val aiTest: StateFlow<AiTestState> = _aiTest

    /** One tiny request to the active provider, so a wrong key, model or address shows up now and not in Ask. */
    fun testAiConnection() {
        if (_aiTest.value is AiTestState.Running) return
        val llm = c.settings.activeLlm() ?: run { _aiTest.value = AiTestState.Failed("Add the key and model first."); return }
        _aiTest.value = AiTestState.Running
        viewModelScope.launch {
            _aiTest.value = when (val r = llm.complete(LlmRequest.ask("Reply with the single word: ready", "Are you there?", maxTokens = 10, temperature = 0.0))) {
                is LlmResult.Ok -> AiTestState.Ok(r.text.take(60))
                is LlmResult.Error -> AiTestState.Failed(r.message)
            }
        }
    }

    /** Aggregated payload preview so users can see exactly what would be sent. */
    fun aiPayloadPreview(): String {
        val books = books.value
        // A month still running is compared with the same days of the last one, as on Home and in monthInWords.
        val now = System.currentTimeMillis()
        val before = Periods.sameSpanBefore(Periods.month(0, now), Periods.month(-1, now), now)
        val cur = books.summary(Periods.month(0, now))
        val prev = books.summary(before)
        return MonthlySummary.buildPayload(cur, prev, budgets.value)
    }

    fun generateAiSummary() {
        val llm = c.settings.activeLlm()
        if (llm == null) { _aiState.value = AiUiState.Error("Set up an AI provider first."); return }
        if (_aiState.value is AiUiState.Loading) return
        _aiState.value = AiUiState.Loading
        viewModelScope.launch {
            // Never summarise the empty lists the screens start from.
            loaded.first { it }
            val payload = aiPayloadPreview()
            when (val r = llm.complete(MonthlySummary.request(payload))) {
                is LlmResult.Ok -> _aiState.value = AiUiState.Result(r.text)
                is LlmResult.Error -> _aiState.value = AiUiState.Error(r.message)
            }
        }
    }

    fun clearAi() { _aiState.value = AiUiState.Idle }

    // ---- OCR + splits ----
    private var ocrJob: kotlinx.coroutines.Job? = null

    fun runOcr(uri: Uri) {
        if (_ocrState.value is OcrUiState.Running) return
        _ocrState.value = OcrUiState.Running
        ocrJob = viewModelScope.launch {
            val result = runCatching { OcrEngine.recognize(getApplication(), uri) }
                .map { text -> if (text.isBlank()) OcrUiState.Error("No text found. Try a sharper, well-lit photo.") else OcrUiState.Done(BillParser.parse(text)) }
                .getOrElse { OcrUiState.Error(it.message ?: "Could not read the image") }
            // Left the new split while it was reading: the result must not land in the next one.
            if (_ocrState.value is OcrUiState.Running) _ocrState.value = result
        }
    }

    /** Forget the bill photo's result (and stop reading one), so it never fills in the next new split. */
    fun clearOcr() { ocrJob?.cancel(); ocrJob = null; _ocrState.value = OcrUiState.Idle }

    /**
     * Persist a split and reflect it in personal tracking. Never both a full payment and a "my share" row:
     *  - I paid with a payment already in the app ([Split.linkedTransactionId], picked under "Paid with"): it shrinks to
     *    my share; the rest is money owed to me, not spend. An automatic split or suggestion on it is undone first.
     *  - I paid and said it isn't in the app yet (no payment picked): my share is added as a payment.
     *  - Someone else paid: my share is added as an expense I owe.
     */
    fun saveSplit(split: Split, items: List<BillItem>, category: Category, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        // One change: the follow-up runs once the split itself is saved, so it never sees a shrunk payment without it.
        // A suggestion that shared transfers with the one undone below is worked out again then.
        // When the split takes over a payment, the split engine's lock comes before the database: an engine run writes
        // while holding that lock, so waiting for it inside an open transaction could leave each waiting on the other.
        val takesPayment = split.iPaid && split.linkedTransactionId != null
        val id = if (takesPayment) c.splitEngine.holding { c.ledger.together { saveSplitRows(split, items, category) } }
        else c.ledger.together { saveSplitRows(split, items, category) }
        onDone(id)
    }

    private suspend fun saveSplitRows(split: Split, items: List<BillItem>, category: Category): Long {
        val me = split.myShare?.amountPaise ?: 0L
        var linked: Long? = null
        val pickedId = split.linkedTransactionId?.takeIf { split.iPaid }
        if (pickedId != null && c.transactions.getById(pickedId) != null) {
            c.splitEngine.releaseForManual(pickedId)
            c.transactions.getById(pickedId)?.let { t ->
                val full = t.originalAmountPaise ?: t.amountPaise
                val othersPaise = split.totalPaise - me
                c.ledger.reshape(t.copy(
                    amountPaise = (full - othersPaise).coerceAtLeast(0L), originalAmountPaise = full, category = category,
                    note = listOfNotNull(t.note?.takeIf { it.isNotBlank() }, com.pft.financetracker.data.split.SplitEngine.splitNote(split.title, com.pft.financetracker.ui.components.money(othersPaise))).joinToString(" "),
                ))
                linked = t.id
            }
        } else if (me > 0) {
            linked = c.ledger.add(
                Transaction(
                    amountPaise = me, type = TransactionType.DEBIT, merchant = split.title, category = category, timestamp = split.date,
                    bankName = null, accountRef = null, source = Transaction.Source.SPLIT, flow = Flow.EXPENSE,
                    note = if (split.iPaid) "My share of a split I paid" else "My share, paid by ${split.people.getOrNull(split.payerIndex)?.name ?: "someone else"}",
                )
            ).takeIf { it > 0 }
        }
        return c.splits.save(split.copy(linkedTransactionId = linked), items)
    }

    /** Payments a split I paid could be ("Paid with"): near the date and the total, not already part of a split. */
    fun paidWithCandidates(totalPaise: Long?, date: Long): List<Transaction> {
        val used = autoSplitOf.value.keys + splits.value.filter { !it.isAuto }.mapNotNull { it.linkedTransactionId }
        return com.pft.financetracker.domain.split.SettleMatch.paidWith(transactions.value, totalPaise, date, used)
    }

    fun settleShare(shareId: Long, settledPaise: Long) = viewModelScope.launch { c.splits.settle(shareId, settledPaise); c.ledger.followUp() }
    fun deleteSplit(id: Long) = viewModelScope.launch {
        // Read from the database when the list hasn't got it (still loading, or just changed).
        val auto = splits.value.firstOrNull { it.id == id }?.isAuto
            ?: c.db.splitDao().getSplit(id)?.let { it.source != com.pft.financetracker.domain.split.SplitSource.MANUAL.name }
            ?: return@launch
        // An automatic split is undone exactly (numbers restored, never suggested again); a manual one puts back the
        // payment it shrank, removes the "my share" row it added and releases transfers linked to it.
        if (auto) c.splitEngine.reject(id) else c.splitEngine.deleteManual(id)
        refreshSplits(useAi = false)
    }

    // ---- Split intelligence ----
    fun acceptSplit(id: Long) = viewModelScope.launch { c.splitEngine.accept(id); c.ledger.followUp() }
    /** Undo or "not a split"; then re-check at once, so a transfer this split shared with another one is re-read. */
    fun rejectSplit(id: Long) = viewModelScope.launch { c.splitEngine.reject(id); refreshSplits(useAi = false) }
    fun setRemindersEnabled(v: Boolean) { c.settings.setRemindersEnabled(v) }
    val widgetHideAmounts: StateFlow<Boolean> = c.settings.widgetHideAmounts
    fun setWidgetHideAmounts(v: Boolean) = viewModelScope.launch { c.settings.setWidgetHideAmounts(v); com.pft.financetracker.ui.widget.FinTrackWidget.refresh(getApplication()) }
    /** Applied refund/reversal pairs, for badges and the "hide reversed payments" filter. */
    val refundBadges: StateFlow<com.pft.financetracker.domain.refunds.RefundBadges> = c.db.refundDao().observeApplied()
        .map { links ->
            com.pft.financetracker.domain.refunds.RefundBadges.of(links.map {
                com.pft.financetracker.domain.refunds.RefundPair(it.id, it.refundTxId, it.debitTxId,
                    com.pft.financetracker.domain.refunds.RefundMatch.Kind.valueOf(it.kind), it.amountPaise)
            })
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.pft.financetracker.domain.refunds.RefundBadges.EMPTY)
    fun undoRefund(linkId: Long) = viewModelScope.launch(Dispatchers.IO) { c.refunds.undo(linkId); c.ledger.followUp() }

    val learnedTemplates = c.db.templateDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun deleteTemplate(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.templates.delete(id) }

    /** Income minus net spend last month: the natural amount to move into a goal. */
    val lastMonthSavingsPaise: StateFlow<Long> = books.map { it.summary(Periods.month(-1)).savingsPaise }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    val lastBackupAt: StateFlow<Long> = c.settings.lastBackupAt
    /**
     * Answers a question about the person's own numbers, on the phone: the rules first (exact figures), then, for a
     * question they do not understand, Gemini Nano with the phone's totals, where the phone has it.
     */
    suspend fun ask(question: String, history: List<Pair<String, String>> = emptyList()): com.pft.financetracker.domain.ask.AskAnswer = withContext(Dispatchers.Default) {
        // The books the screens show, once loaded: never an answer from the empty list the app starts with.
        val snapshot = books.first { isLoaded(it.all) }
        val ctx = com.pft.financetracker.domain.ask.AskContext(
            txns = snapshot.all, budgets = budgets.value, recurring = c.recurring.book(), bills = c.bills.states(),
            rules = snapshot.rules, shared = snapshot,
        )
        val rules = com.pft.financetracker.domain.ask.AskEngine.answer(question, ctx)
        // With a provider set up and the switch on, it answers every question from a summary of the payments; the rules'
        // payments (if any) stay attached so "Show payments" still works. Offline or on error, the phone answers.
        val llm = if (c.settings.askUseOpenAi.value) c.settings.activeLlm() else null
        if (llm != null) {
            val facts = com.pft.financetracker.domain.ask.AskAiPrompt.facts(ctx)
            val turns = buildList {
                add(LlmRequest.Turn(LlmRequest.Role.USER, "Here is my money data from FinTrack:\n$facts"))
                add(LlmRequest.Turn(LlmRequest.Role.ASSISTANT, "Got it. What would you like to know?"))
                history.takeLast(6).forEach { (q, a) -> add(LlmRequest.Turn(LlmRequest.Role.USER, q)); add(LlmRequest.Turn(LlmRequest.Role.ASSISTANT, a)) }
                add(LlmRequest.Turn(LlmRequest.Role.USER, question))
            }
            val who = llm.assistantName
            when (val r = llm.complete(LlmRequest(com.pft.financetracker.domain.ask.AskAiPrompt.system, turns))) {
                is LlmResult.Ok -> return@withContext com.pft.financetracker.domain.ask.AskAnswer(
                    r.text, transactionIds = if (rules.understood) rules.transactionIds else emptyList(), byAi = true, onlineBy = who,
                )
                is LlmResult.Error -> return@withContext if (rules.understood) rules.copy(text = rules.text + "\n\n${who.replaceFirstChar { it.uppercase() }} couldn't answer (${r.message}), so this is from the phone.")
                else rules.copy(text = "${who.replaceFirstChar { it.uppercase() }} couldn't answer: ${r.message}. Check the internet connection or the provider in Settings › AI.\n\n" + rules.text)
            }
        }
        if (rules.understood || !c.settings.useNano.value) return@withContext rules
        c.nano.answer(question, com.pft.financetracker.domain.ask.NanoPrompt.facts(ctx))
            ?.let { com.pft.financetracker.domain.ask.AskAnswer(it, understood = true, byAi = true) } ?: rules
    }

    val askUseOpenAi: StateFlow<Boolean> = c.settings.askUseOpenAi
    fun setAskUseOpenAi(v: Boolean) = c.settings.setAskUseOpenAi(v)
    val useNano: StateFlow<Boolean> = c.settings.useNano
    fun setUseNano(v: Boolean) = c.settings.setUseNano(v)
    private val _nanoStatus = MutableStateFlow<com.pft.financetracker.data.ai.NanoAi.Status?>(null)
    val nanoStatus: StateFlow<com.pft.financetracker.data.ai.NanoAi.Status?> = _nanoStatus
    fun refreshNano() = viewModelScope.launch { _nanoStatus.value = c.nano.status() }
    fun downloadNano() = viewModelScope.launch {
        _nanoStatus.value = com.pft.financetracker.data.ai.NanoAi.Status.DOWNLOADING
        c.nano.download()
        _nanoStatus.value = c.nano.status()
    }
    /** This month in a few written lines, worked out on the phone (no key, no network). */
    suspend fun monthInWords(): String = withContext(Dispatchers.Default) {
        val books = books.value
        // A month still running is compared with the same days of the last one ("1–6 Sep"), as on Home.
        val now = System.currentTimeMillis()
        val before = Periods.sameSpanBefore(Periods.month(), Periods.month(-1), now)
        com.pft.financetracker.domain.ask.MonthlySummary.write(
            books.summary(Periods.month()), books.summary(before),
            budgets.value, c.recurring.book(),
        )
    }

    fun setSplitAi(v: Boolean) { c.settings.setSplitAi(v); if (v) refreshSplits() }

    /** Incoming money from people that could be [split]'s payback: after the split date, not already used, up to a round-up over what is owed. */
    fun settleCandidates(split: Split, remainingPaise: Long): List<Transaction> =
        com.pft.financetracker.domain.split.SettleMatch.incoming(transactions.value, split.date, remainingPaise, autoSplitOf.value.keys)

    /** My own payments that could be me paying back whoever paid [split]. */
    fun payoutCandidates(split: Split, remainingPaise: Long): List<Transaction> {
        val used = autoSplitOf.value.keys + splits.value.mapNotNull { it.linkedTransactionId }
        val payer = split.people.getOrNull(split.payerIndex)?.name ?: ""
        return com.pft.financetracker.domain.split.SettleMatch.outgoing(transactions.value, split.date, remainingPaise, payer, used)
    }

    /** A friend's transfer settles their share of a manual split (or my payment settles mine): it stops counting as income or spend. */
    fun settleWithTransaction(splitId: Long, shareId: Long, newSettledPaise: Long, credit: Transaction) = viewModelScope.launch {
        c.splitEngine.linkSettlement(splitId, shareId, newSettledPaise, credit, credit.amountPaise)
        c.ledger.followUp()
    }

    // ---- Statement / screenshot import ----
    fun importFile(uri: Uri, password: String? = null) {
        _statementState.value = StatementUiState.Reading
        viewModelScope.launch {
            _statementState.value = when (val r = c.statementFiles.read(uri, password)) {
                is StatementFiles.Read.Ok -> preview(r)
                is StatementFiles.Read.NeedsPassword -> StatementUiState.NeedsPassword(uri, r.fileName, r.wrong)
                is StatementFiles.Read.Error -> StatementUiState.Error(r.message)
            }
        }
    }

    fun importImages(uris: List<Uri>) {
        if (uris.isEmpty()) return
        _statementState.value = StatementUiState.Reading
        viewModelScope.launch {
            _statementState.value = when (val r = withContext(Dispatchers.IO) { c.statementFiles.readImages(uris) }) {
                is StatementFiles.Read.Ok -> preview(r)
                is StatementFiles.Read.NeedsPassword -> StatementUiState.Error("Unexpected password request")
                is StatementFiles.Read.Error -> StatementUiState.Error(r.message)
            }
        }
    }

    private suspend fun preview(r: StatementFiles.Read.Ok): StatementUiState {
        if (r.statement.rows.isEmpty() && r.statement.problems.isEmpty()) return StatementUiState.Error(r.statement.note ?: "No transactions found in ${r.fileName}.")
        return StatementUiState.Preview(withContext(Dispatchers.IO) { c.statementImporter.preview(r.statement, r.fileName) })
    }

    fun confirmImport() {
        val s = _statementState.value as? StatementUiState.Preview ?: return
        _statementState.value = StatementUiState.Reading
        viewModelScope.launch {
            val result = saveStatement { withContext(Dispatchers.IO) { c.ledger.importStatement(s.preview) } }
            _statementState.value = result
            if (result is StatementUiState.Saved) refreshSplits(useAi = true)
        }
    }

    fun undoImport(batchId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.ledger.undoImport(batchId) }
    }

    fun resetStatementImport() { _statementState.value = StatementUiState.Idle }
    suspend fun splitItems(id: Long): List<BillItem> = c.splits.itemsFor(id)

    suspend fun exportCsv(): String = CsvExporter.toCsv(c.transactions.getAll(), customCategories.value)
    suspend fun exportSplitsCsv(): String = CsvExporter.splitsToCsv(splits.value)

    fun clearAllData(onDone: () -> Unit = {}) = viewModelScope.launch {
        // Every table, including ones added in later versions, so nothing is left behind.
        withContext(Dispatchers.IO) { c.db.clearAllTables(); c.templates.load(); c.categories.load() }
        com.pft.financetracker.data.backup.AutoBackups.stop(getApplication(), c.settings)
        c.settings.clearAll()
        val app = getApplication<Application>()
        com.pft.financetracker.ui.widget.FinTrackWidget.refresh(app)
        runCatching { androidx.core.app.NotificationManagerCompat.from(app).cancelAll() }
        _aiState.value = AiUiState.Idle
        _importState.value = ImportUiState.Idle
        _ocrState.value = OcrUiState.Idle
        onDone()
    }
}
