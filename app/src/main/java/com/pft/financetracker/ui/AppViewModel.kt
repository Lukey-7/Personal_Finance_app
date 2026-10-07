package com.pft.financetracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pft.financetracker.appContainer
import com.pft.financetracker.data.bills.toDomain
import com.pft.financetracker.data.cards.toDomain
import com.pft.financetracker.data.networth.toDomain
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.ImportStats
import com.pft.financetracker.domain.ai.OpenAiClient
import com.pft.financetracker.domain.export.CsvExporter
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
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
import kotlinx.coroutines.flow.map
import com.pft.financetracker.data.importer.StatementFiles
import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.local.ImportBatchEntity
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

/** Reading a mutual-fund CAS PDF: (password) -> done. */
sealed class CasUiState {
    data object Idle : CasUiState()
    data object Reading : CasUiState()
    data class NeedsPassword(val uri: Uri, val wrong: Boolean) : CasUiState()
    data class Done(val count: Int) : CasUiState()
    data class Error(val message: String) : CasUiState()
}

/** Which period the dashboard shows. Kept in the view model so it survives tab switches. */
sealed class PeriodChoice {
    data object ThisMonth : PeriodChoice()
    data object LastMonth : PeriodChoice()
    data object ThisWeek : PeriodChoice()
    data class Custom(val start: Long, val endInclusive: Long) : PeriodChoice()

    fun period(): Period = when (this) {
        ThisMonth -> Periods.month()
        LastMonth -> Periods.month(-1)
        ThisWeek -> Periods.week()
        is Custom -> Periods.custom(start, endInclusive)
    }

    fun previous(): Period = when (this) {
        ThisMonth -> Periods.month(-1)
        LastMonth -> Periods.month(-2)
        ThisWeek -> Periods.week(-1)
        is Custom -> { val len = endInclusive - start + 86_400_000L; Periods.custom(start - len, start - 1) }
    }
}

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer

    val transactions: StateFlow<List<Transaction>> = c.transactions.all.stateIn(viewModelScope, SharingStarted.Eagerly, notLoaded())
    val budgets: StateFlow<List<Budget>> = c.budgets.all.stateIn(viewModelScope, SharingStarted.Eagerly, notLoaded())
    val reviewQueue: StateFlow<List<ReviewItemEntity>> = c.transactions.reviewQueue.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviewCount: StateFlow<Int> = c.transactions.reviewCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val smsLog: StateFlow<List<SmsLogEntity>> = c.smsLog.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
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
    val loaded: StateFlow<Boolean> = combine(transactions, splits, budgets) { a, b, d -> isLoaded(a) && isLoaded(b) && isLoaded(d) }
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val hasApiKey: StateFlow<Boolean> = c.settings.hasApiKey
    val apiKeyBuiltIn: StateFlow<Boolean> = c.settings.apiKeyBuiltIn
    val onboarded: StateFlow<Boolean> = c.settings.onboarded
    val autoImport: StateFlow<Boolean> = c.settings.autoImport
    val lastImportAt: StateFlow<Long> = c.settings.lastImportAt
    val countCashAsSpend: StateFlow<Boolean> = c.settings.countCashAsSpend
    val myName: StateFlow<String> = c.settings.myName

    private val _period = MutableStateFlow<PeriodChoice>(PeriodChoice.ThisMonth)
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
    fun refreshSplits(useAi: Boolean = true) = viewModelScope.launch(Dispatchers.IO) { c.afterChange(useAi) }

    fun hasSmsPermission() = c.importer.hasSmsPermission()

    fun scanInbox(full: Boolean = false) {
        if (_importState.value is ImportUiState.Running) return
        _importState.value = ImportUiState.Running
        viewModelScope.launch {
            val since = if (full) 0L else lastImportAt.value
            val stats = runCatching { c.importer.scanInbox(since) }.getOrNull()
            _importState.value = if (stats == null) ImportUiState.Failed else ImportUiState.Done(stats)
            withContext(Dispatchers.IO) { c.afterChange() }
        }
    }

    fun dismissImportResult() { _importState.value = ImportUiState.Idle }

    fun setOnboarded(v: Boolean) = c.settings.setOnboarded(v)
    fun setAutoImport(v: Boolean) = c.settings.setAutoImport(v)
    fun setCountCashAsSpend(v: Boolean) = c.settings.setCountCashAsSpend(v)
    fun setMyName(v: String) = c.settings.setMyName(v)

    fun save(t: Transaction, onDone: () -> Unit = {}) = viewModelScope.launch {
        // An existing row saved from the editor was corrected by a person: protect it from automatic rewrites.
        if (t.id == 0L) c.transactions.insert(t) else c.transactions.update(t.copy(userEdited = true))
        onDone()
        refreshSplits(useAi = false)
    }

    fun delete(t: Transaction) = viewModelScope.launch {
        c.transactions.delete(t)
        c.importer.forgetDeleted(t)
        refreshSplits(useAi = false)
    }

    /** Moves the chosen rows to [category] (Activity's multi-select). Only rows that change are written. */
    fun recategorise(ids: Set<Long>, category: Category) = viewModelScope.launch {
        com.pft.financetracker.ui.model.recategorise(transactions.value, ids, category).forEach { c.transactions.update(it) }
        refreshSplits(useAi = false)
    }

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
        c.transactions.getReview(reviewId)?.let { r -> withContext(Dispatchers.IO) { runCatching { c.templates.learn(r.sender, r.body, t) } } }
        val id = c.transactions.insert(t.copy(userEdited = true))
        c.transactions.resolveReview(reviewId)
        t.smsHash?.let { c.smsLog.updateOutcome(it, "SAVED", t.merchant, id.takeIf { v -> v > 0 }) }
        onDone()
    }

    fun dismissReview(reviewId: Long) = viewModelScope.launch {
        val r = c.transactions.getReview(reviewId)
        c.transactions.resolveReview(reviewId)
        r?.let { c.importer.recordDismissed(it.smsHash) }
    }

    fun setBudget(category: Category, limitPaise: Long) = viewModelScope.launch { c.budgets.set(category, limitPaise) }

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

    fun mergeDuplicates(onDone: (Int) -> Unit = {}) = viewModelScope.launch {
        val pairs = _duplicates.value
        c.transactions.mergeDuplicates(pairs) { id -> c.db.splitDao().linksForTransaction(id).isNotEmpty() }
        _duplicates.value = emptyList()
        onDone(pairs.size)
    }

    fun clearDuplicates() { _duplicates.value = emptyList(); _duplicatesScanned.value = false }

    // ---- SMS log ----
    suspend fun smsBody(e: SmsLogEntity): String? = withContext(Dispatchers.IO) { runCatching { c.importer.readBody(e.sender, e.receivedAt) }.getOrNull() }
    fun flagLogEntry(logId: Long, onDone: (Boolean) -> Unit) = viewModelScope.launch { onDone(c.importer.sendToReview(logId)) }

    // ---- Summary helpers ----
    fun summary(period: Period) = InsightsEngine.summarize(transactions.value, period, countCashAsSpend.value)
    fun drillDown(period: Period, bucket: InsightsEngine.Bucket, category: Category?) = InsightsEngine.drillDown(transactions.value, period, bucket, category)

    // ---- AI ----
    fun setApiKey(key: String?) = c.settings.setApiKey(key)

    /** Aggregated payload preview so users can see exactly what would be sent. */
    fun aiPayloadPreview(): String {
        val all = transactions.value
        val cur = InsightsEngine.summarize(all, Periods.month(), countCashAsSpend.value)
        val prev = InsightsEngine.summarize(all, Periods.month(-1), countCashAsSpend.value)
        return OpenAiClient().buildPayload(cur, prev, budgets.value)
    }

    fun generateAiSummary() {
        val key = c.settings.getApiKey()
        if (key.isNullOrBlank()) { _aiState.value = AiUiState.Error("No API key set."); return }
        if (_aiState.value is AiUiState.Loading) return
        _aiState.value = AiUiState.Loading
        viewModelScope.launch {
            val payload = aiPayloadPreview()
            when (val r = OpenAiClient().monthlySummary(key, payload)) {
                is OpenAiClient.Result.Ok -> _aiState.value = AiUiState.Result(r.text)
                is OpenAiClient.Result.Error -> _aiState.value = AiUiState.Error(r.message)
            }
        }
    }

    fun clearAi() { _aiState.value = AiUiState.Idle }

    // ---- OCR + splits ----
    fun runOcr(uri: Uri) {
        if (_ocrState.value is OcrUiState.Running) return
        _ocrState.value = OcrUiState.Running
        viewModelScope.launch {
            _ocrState.value = runCatching { OcrEngine.recognize(getApplication(), uri) }
                .map { text -> if (text.isBlank()) OcrUiState.Error("No text found. Try a sharper, well-lit photo.") else OcrUiState.Done(BillParser.parse(text)) }
                .getOrElse { OcrUiState.Error(it.message ?: "Could not read the image") }
        }
    }

    fun clearOcr() { _ocrState.value = OcrUiState.Idle }

    /**
     * Persist a split and reflect it in personal tracking:
     *  - I paid: my share is the expense. If an SMS debit for the full amount exists, link it and shrink it to my
     *    share (the rest is money owed to me, not spend). Otherwise record my share as a manual expense.
     *  - Someone else paid: my share is added as an expense I owe.
     */
    fun saveSplit(split: Split, items: List<BillItem>, category: Category, onDone: (Long) -> Unit = {}) = viewModelScope.launch {
        val me = split.myShare?.amountPaise ?: 0L
        var linked: Long? = split.linkedTransactionId
        if (split.iPaid && linked == null) {
            linked = transactions.value.firstOrNull {
                it.type == TransactionType.DEBIT && it.amountPaise == split.totalPaise && kotlin.math.abs(it.timestamp - split.date) < 36 * 3_600_000L && it.source == Transaction.Source.SMS
            }?.id
        }
        if (split.iPaid && linked != null) {
            c.transactions.getById(linked)?.let { t ->
                val othersPaise = split.totalPaise - me
                c.transactions.update(t.copy(amountPaise = me, originalAmountPaise = t.originalAmountPaise ?: t.amountPaise, category = category, note = listOfNotNull(t.note, "Split: ${split.title}. ₹${othersPaise / 100} owed to you.").joinToString(" ")))
            }
        } else if (me > 0) {
            linked = c.transactions.insert(
                Transaction(
                    amountPaise = me, type = TransactionType.DEBIT, merchant = split.title, category = category, timestamp = split.date,
                    bankName = null, accountRef = null, source = Transaction.Source.SPLIT, flow = Flow.EXPENSE,
                    note = if (split.iPaid) "My share of a split I paid" else "My share, paid by ${split.people.getOrNull(split.payerIndex)?.name ?: "someone else"}",
                )
            ).takeIf { it > 0 }
        }
        val id = c.splits.save(split.copy(linkedTransactionId = linked), items)
        onDone(id)
    }

    fun settleShare(shareId: Long, settledPaise: Long) = viewModelScope.launch { c.splits.settle(shareId, settledPaise) }
    fun deleteSplit(id: Long) = viewModelScope.launch {
        val s = splits.value.firstOrNull { it.id == id }
        // An automatic split is undone exactly (numbers restored, never suggested again); a manual one releases any
        // transfers linked to it before it goes.
        if (s?.isAuto == true) c.splitEngine.reject(id) else { c.splitEngine.unlinkManual(id); c.splits.delete(id) }
    }

    // ---- Split intelligence ----
    fun acceptSplit(id: Long) = viewModelScope.launch { c.splitEngine.accept(id) }
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
    fun undoRefund(linkId: Long) = viewModelScope.launch(Dispatchers.IO) { c.refunds.undo(linkId) }

    val learnedTemplates = c.db.templateDao().observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun deleteTemplate(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.templates.delete(id) }

    /** Subscriptions and other repeating charges, recomputed whenever transactions or decisions change. */
    val recurringBook: StateFlow<com.pft.financetracker.domain.recurring.RecurringBook> =
        combine(transactions, c.db.recurringDao().observeAll()) { txns, decisions ->
            withContext(Dispatchers.Default) { c.recurring.bookOf(txns, decisions) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.pft.financetracker.domain.recurring.RecurringBook.EMPTY)
    fun decideRecurring(key: String, status: com.pft.financetracker.domain.recurring.RecurringStatus?) =
        viewModelScope.launch(Dispatchers.IO) { c.recurring.decide(key, status) }

    /** Every bill with its status today, recomputed when transactions, bills or paid marks change. */
    /** Today, moving on just after midnight, so due dates and cycles update without new data. */
    private val today = com.pft.financetracker.domain.reminders.DayClock.today()
        .stateIn(viewModelScope, SharingStarted.Eagerly, java.time.LocalDate.now())

    val billStates: StateFlow<List<Pair<com.pft.financetracker.domain.bills.Bill, com.pft.financetracker.domain.bills.BillState>>> =
        combine(transactions, c.db.billDao().observeAll(), c.db.billDao().observeMarks(), today) { txns, bills, marks, day ->
            withContext(Dispatchers.Default) {
                val byBill = marks.groupBy({ it.billId }, { java.time.LocalDate.ofEpochDay(it.dueDay) })
                c.bills.statesOf(bills.map { it.toDomain() }, txns, byBill, day)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), notLoaded())
    fun saveBill(b: com.pft.financetracker.domain.bills.Bill) = viewModelScope.launch(Dispatchers.IO) { c.bills.save(b) }
    fun deleteBill(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.bills.delete(id) }
    fun markBillPaid(id: Long, due: java.time.LocalDate) = viewModelScope.launch(Dispatchers.IO) { c.bills.markPaid(id, due) }
    fun unmarkBillPaid(id: Long, due: java.time.LocalDate) = viewModelScope.launch(Dispatchers.IO) { c.bills.unmarkPaid(id, due) }

    /** Each card's current billing cycle, recomputed when transactions or cards change. */
    val cardSummaries: StateFlow<List<com.pft.financetracker.domain.cards.CardSummary>> =
        combine(transactions, c.db.cardDao().observeAll(), today) { txns, cards, day ->
            withContext(Dispatchers.Default) { c.cards.summariesOf(cards.map { it.toDomain() }, txns, day) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), notLoaded())
    suspend fun cardSuggestions(): List<String> = withContext(Dispatchers.IO) {
        c.cards.suggestions(extra = c.bills.all().mapNotNull { it.cardLast4 })
    }
    fun saveCard(card: com.pft.financetracker.domain.cards.Card) = viewModelScope.launch(Dispatchers.IO) { c.cards.save(card) }
    fun deleteCard(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.cards.delete(id) }

    val goalProgress: StateFlow<List<com.pft.financetracker.domain.goals.GoalProgress>> =
        combine(c.db.goalDao().observeAll(), c.db.goalDao().observeContributions(), today) { goals, contributions, day ->
            c.goals.progressOf(goals, contributions, day)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), notLoaded())
    /** Income minus net spend last month: the natural amount to move into a goal. */
    val lastMonthSavingsPaise: StateFlow<Long> = transactions.map { InsightsEngine.summarize(it, Periods.month(-1), c.settings.countCashAsSpend.value).savingsPaise }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)
    fun saveGoal(g: com.pft.financetracker.domain.goals.Goal) = viewModelScope.launch(Dispatchers.IO) { c.goals.save(g) }
    fun deleteGoal(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.goals.delete(id) }
    fun contributeToGoal(id: Long, paise: Long) = viewModelScope.launch(Dispatchers.IO) { c.goals.contribute(id, paise) }

    private val _taxYear = MutableStateFlow(com.pft.financetracker.domain.tax.FinancialYear.of(java.time.LocalDate.now()))
    val taxYear: StateFlow<com.pft.financetracker.domain.tax.FinancialYear> = _taxYear
    fun setTaxYear(fy: com.pft.financetracker.domain.tax.FinancialYear) { _taxYear.value = fy }
    /** The person's own tax tags by transaction id; a null value means "not a deduction". */
    val taxTags: StateFlow<Map<Long, com.pft.financetracker.domain.tax.TaxSection?>> = c.db.taxDao().observeAll()
        .map { l -> l.associate { it.transactionId to com.pft.financetracker.domain.tax.TaxSection.fromName(it.section) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val taxSummary: StateFlow<List<com.pft.financetracker.domain.tax.SectionTotal>> =
        combine(transactions, c.db.taxDao().observeAll(), _taxYear) { txns, tags, fy -> withContext(Dispatchers.Default) { c.tax.summaryOf(txns, tags, fy) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tagTax(txId: Long, s: com.pft.financetracker.domain.tax.TaxSection?) = viewModelScope.launch(Dispatchers.IO) { c.tax.tag(txId, s) }
    fun clearTaxTag(txId: Long) = viewModelScope.launch(Dispatchers.IO) { c.tax.clearTag(txId) }
    suspend fun taxCsv(fy: com.pft.financetracker.domain.tax.FinancialYear): String = withContext(Dispatchers.IO) { c.tax.csv(fy) }

    val assets: StateFlow<List<com.pft.financetracker.domain.networth.Asset>> = c.db.netWorthDao().observeAssets()
        .map { l -> l.map { it.toDomain() } }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val accountBalances = c.db.netWorthDao().observeBalances().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val holdings = c.db.netWorthDao().observeHoldings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val netWorthHistory = c.db.netWorthDao().observeSnapshots().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    val netWorth: StateFlow<com.pft.financetracker.domain.networth.NetWorthSummary> =
        combine(c.db.netWorthDao().observeAssets(), c.db.netWorthDao().observeBalances(), c.db.netWorthDao().observeHoldings(), c.db.billDao().observeAll()) { a, b, h, bills ->
            c.netWorth.summaryOf(a, b, h, bills.map { it.toDomain() }, java.time.LocalDate.now())
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), com.pft.financetracker.domain.networth.NetWorthSummary(0, 0, emptyMap()))
    fun saveAsset(a: com.pft.financetracker.domain.networth.Asset) = viewModelScope.launch(Dispatchers.IO) { c.netWorth.saveAsset(a); c.netWorth.snapshot() }
    fun deleteAsset(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.netWorth.deleteAsset(id); c.netWorth.snapshot() }

    private val _casState = MutableStateFlow<CasUiState>(CasUiState.Idle)
    val casState: StateFlow<CasUiState> = _casState
    fun resetCas() { _casState.value = CasUiState.Idle }
    fun importCas(uri: Uri, password: String?) = viewModelScope.launch {
        _casState.value = CasUiState.Reading
        _casState.value = when (val r = c.statementFiles.readPdfText(uri, password)) {
            is StatementFiles.TextRead.NeedsPassword -> CasUiState.NeedsPassword(uri, r.wrong)
            is StatementFiles.TextRead.Error -> CasUiState.Error(r.message)
            is StatementFiles.TextRead.Ok -> {
                val h = withContext(Dispatchers.Default) { com.pft.financetracker.domain.networth.CasParser.parse(r.text) }
                if (h.isEmpty()) CasUiState.Error("No fund holdings found. Use a CAMS or KFintech Consolidated Account Statement (detailed).")
                else { withContext(Dispatchers.IO) { c.netWorth.replaceHoldings(h); c.netWorth.snapshot() }; CasUiState.Done(h.size) }
            }
        }
    }

    val lastBackupAt: StateFlow<Long> = c.settings.lastBackupAt
    private val _backupBusy = MutableStateFlow<String?>(null)
    /** "Locking your backup…" / "Opening the backup…" while it runs; null otherwise. */
    val backupBusy: StateFlow<String?> = _backupBusy
    /** Seals every table with [passphrase] and writes it to the file the person picked. Returns an error message, or null. */
    suspend fun writeBackup(uri: Uri, passphrase: CharArray): String? = withContext(Dispatchers.IO) {
        _backupBusy.value = "Locking your backup…"
        runCatching {
            val bytes = c.backup.export(passphrase)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write the file.")
            c.settings.setLastBackupAt(System.currentTimeMillis())
        }.exceptionOrNull()?.let { it.message ?: "Backup failed." }.also { passphrase.fill(' '); _backupBusy.value = null }
    }
    /** Replaces all data with the backup's. Returns an error message, or null when done. */
    suspend fun restoreBackup(uri: Uri, passphrase: CharArray): String? = withContext(Dispatchers.IO) {
        _backupBusy.value = "Opening the backup…"
        runCatching {
            val bytes = getApplication<Application>().contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not open the file.")
            c.backup.restore(bytes, passphrase)
            c.templates.load()
            c.afterChange(useAi = false)
        }.exceptionOrNull()?.let { it.message ?: "Restore failed." }.also { passphrase.fill(' '); _backupBusy.value = null }
    }

    /**
     * Answers a question about the person's own numbers, on the phone: the rules first (exact figures), then, for a
     * question they do not understand, Gemini Nano with the phone's totals, where the phone has it.
     */
    suspend fun ask(question: String): com.pft.financetracker.domain.ask.AskAnswer = withContext(Dispatchers.Default) {
        // Read fresh: the netWorth flow only runs while its screen is open.
        val nw = c.netWorth.summary()
        val ctx = com.pft.financetracker.domain.ask.AskContext(
            txns = transactions.value, budgets = budgets.value, recurring = c.recurring.book(), bills = c.bills.states(),
            netWorthPaise = if (nw.ownPaise == 0L && nw.owePaise == 0L) null else nw.totalPaise, includeCash = countCashAsSpend.value,
        )
        val rules = com.pft.financetracker.domain.ask.AskEngine.answer(question, ctx)
        if (rules.understood || !c.settings.useNano.value) return@withContext rules
        c.nano.answer(question, com.pft.financetracker.domain.ask.NanoPrompt.facts(ctx))
            ?.let { com.pft.financetracker.domain.ask.AskAnswer(it, understood = true, byAi = true) } ?: rules
    }

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
        val all = transactions.value
        // A month still running is compared with the same days of the last one ("1–6 Sep"), as on Home.
        val now = System.currentTimeMillis()
        val before = Periods.sameSpanBefore(Periods.month(), Periods.month(-1), now)
        com.pft.financetracker.domain.ask.MonthlySummary.write(
            InsightsEngine.summarize(all, Periods.month(), countCashAsSpend.value), InsightsEngine.summarize(all, before, countCashAsSpend.value),
            budgets.value, c.recurring.book(),
        )
    }

    fun setSplitAi(v: Boolean) { c.settings.setSplitAi(v); if (v) refreshSplits() }

    /** Incoming money from people that could be [split]'s payback: after the split date, not already used. */
    fun settleCandidates(split: Split, remainingPaise: Long): List<Transaction> {
        val used = autoSplitOf.value.keys
        return transactions.value.filter {
            it.type == TransactionType.CREDIT && it.flow == Flow.INCOME && it.id !in used &&
                it.timestamp >= split.date - 86_400_000L && it.timestamp <= split.date + 45 * 86_400_000L && it.amountPaise <= remainingPaise &&
                (it.counterpartyKind ?: com.pft.financetracker.domain.split.PayerClassifier.classify("", it.merchant, it.type)) == com.pft.financetracker.domain.model.CounterpartyKind.PERSON
        }.sortedBy { kotlin.math.abs(it.timestamp - split.date) }
    }

    /** A friend's transfer settles their share of a manual split: it stops counting as income. */
    fun settleWithTransaction(splitId: Long, shareId: Long, newSettledPaise: Long, credit: Transaction) = viewModelScope.launch {
        c.splitEngine.linkSettlement(splitId, shareId, newSettledPaise, credit, credit.amountPaise)
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
            val batch = withContext(Dispatchers.IO) { c.statementImporter.commit(s.preview) }
            _statementState.value = StatementUiState.Saved(batch)
            withContext(Dispatchers.IO) { c.afterChange() }
        }
    }

    fun undoImport(batchId: Long) = viewModelScope.launch {
        withContext(Dispatchers.IO) { c.statementImporter.undo(batchId); c.afterChange(useAi = false) }
    }

    fun resetStatementImport() { _statementState.value = StatementUiState.Idle }
    suspend fun splitItems(id: Long): List<BillItem> = c.splits.itemsFor(id)

    /** A credit matching an open split share can be recorded as a settlement instead of income. */
    fun markAsSettlement(t: Transaction, shareId: Long, settledPaise: Long) = viewModelScope.launch {
        c.transactions.update(t.copy(flow = Flow.SETTLEMENT, userEdited = true))
        c.splits.settle(shareId, settledPaise)
    }

    suspend fun exportCsv(): String = CsvExporter.toCsv(c.transactions.getAll())
    suspend fun exportSplitsCsv(): String = CsvExporter.splitsToCsv(splits.value)

    fun clearAllData(onDone: () -> Unit = {}) = viewModelScope.launch {
        // Every table, including ones added in later versions, so nothing is left behind.
        withContext(Dispatchers.IO) { c.db.clearAllTables(); c.templates.load() }
        c.settings.clearAll()
        _aiState.value = AiUiState.Idle
        _importState.value = ImportUiState.Idle
        _ocrState.value = OcrUiState.Idle
        onDone()
    }
}
