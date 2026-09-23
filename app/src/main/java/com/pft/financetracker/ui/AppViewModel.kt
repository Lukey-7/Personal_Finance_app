package com.pft.financetracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pft.financetracker.appContainer
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed class ImportUiState {
    data object Idle : ImportUiState()
    data object Running : ImportUiState()
    data class Done(val stats: ImportStats) : ImportUiState()
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

    val transactions: StateFlow<List<Transaction>> = c.transactions.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets: StateFlow<List<Budget>> = c.budgets.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviewQueue: StateFlow<List<ReviewItemEntity>> = c.transactions.reviewQueue.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviewCount: StateFlow<Int> = c.transactions.reviewCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)
    val smsLog: StateFlow<List<SmsLogEntity>> = c.smsLog.recent.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val smsLogCounts: StateFlow<Map<String, Int>> = c.smsLog.counts.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
    val splits: StateFlow<List<Split>> = c.splits.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val recentPeople: StateFlow<List<String>> = c.splits.recentPeople.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

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

    fun hasSmsPermission() = c.importer.hasSmsPermission()

    fun scanInbox(full: Boolean = false) {
        if (_importState.value is ImportUiState.Running) return
        _importState.value = ImportUiState.Running
        viewModelScope.launch {
            val since = if (full) 0L else lastImportAt.value
            val stats = runCatching { c.importer.scanInbox(since) }.getOrElse { ImportStats(0, 0, 0, 0, 0, 0) }
            _importState.value = ImportUiState.Done(stats)
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
    }

    fun delete(t: Transaction) = viewModelScope.launch {
        c.transactions.delete(t)
        c.importer.forgetDeleted(t)
    }

    suspend fun getTransaction(id: Long): Transaction? = c.transactions.getById(id)
    suspend fun getReview(id: Long): ReviewItemEntity? = c.transactions.getReview(id)

    /** Save a transaction entered from a review item and remove the item from the queue. */
    fun resolveReview(reviewId: Long, t: Transaction, onDone: () -> Unit = {}) = viewModelScope.launch {
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
        c.transactions.mergeDuplicates(pairs)
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
    fun deleteSplit(id: Long) = viewModelScope.launch { c.splits.delete(id) }
    suspend fun splitItems(id: Long): List<BillItem> = c.splits.itemsFor(id)

    /** A credit matching an open split share can be recorded as a settlement instead of income. */
    fun markAsSettlement(t: Transaction, shareId: Long, settledPaise: Long) = viewModelScope.launch {
        c.transactions.update(t.copy(flow = Flow.SETTLEMENT, userEdited = true))
        c.splits.settle(shareId, settledPaise)
    }

    suspend fun exportCsv(): String = CsvExporter.toCsv(c.transactions.getAll())
    suspend fun exportSplitsCsv(): String = CsvExporter.splitsToCsv(splits.value)

    fun clearAllData(onDone: () -> Unit = {}) = viewModelScope.launch {
        c.transactions.clearAll()
        c.budgets.clearAll()
        c.smsLog.clearAll()
        c.splits.clearAll()
        c.settings.clearAll()
        _aiState.value = AiUiState.Idle
        _importState.value = ImportUiState.Idle
        _ocrState.value = OcrUiState.Idle
        onDone()
    }
}
