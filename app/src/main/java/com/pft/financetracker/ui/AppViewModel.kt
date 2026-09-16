package com.pft.financetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.pft.financetracker.appContainer
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.sms.ImportStats
import com.pft.financetracker.domain.ai.OpenAiClient
import com.pft.financetracker.domain.export.CsvExporter
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

class AppViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer

    val transactions: StateFlow<List<Transaction>> = c.transactions.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val budgets: StateFlow<List<Budget>> = c.budgets.all.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviewQueue: StateFlow<List<ReviewItemEntity>> = c.transactions.reviewQueue.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val reviewCount: StateFlow<Int> = c.transactions.reviewCount.stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    val hasApiKey: StateFlow<Boolean> = c.settings.hasApiKey
    val onboarded: StateFlow<Boolean> = c.settings.onboarded
    val autoImport: StateFlow<Boolean> = c.settings.autoImport
    val lastImportAt: StateFlow<Long> = c.settings.lastImportAt

    private val _importState = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val importState: StateFlow<ImportUiState> = _importState

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState

    fun hasSmsPermission() = c.importer.hasSmsPermission()

    fun scanInbox(full: Boolean = false) {
        if (_importState.value is ImportUiState.Running) return
        _importState.value = ImportUiState.Running
        viewModelScope.launch {
            val since = if (full) 0L else lastImportAt.value
            val stats = runCatching { c.importer.scanInbox(since) }.getOrElse { ImportStats(0, 0, 0, 0, 0) }
            _importState.value = ImportUiState.Done(stats)
        }
    }

    fun dismissImportResult() { _importState.value = ImportUiState.Idle }

    fun setOnboarded(v: Boolean) = c.settings.setOnboarded(v)
    fun setAutoImport(v: Boolean) = c.settings.setAutoImport(v)

    fun save(t: Transaction, onDone: () -> Unit = {}) = viewModelScope.launch {
        if (t.id == 0L) c.transactions.insert(t) else c.transactions.update(t)
        onDone()
    }

    fun delete(t: Transaction) = viewModelScope.launch { c.transactions.delete(t) }

    suspend fun getTransaction(id: Long): Transaction? = c.transactions.getById(id)
    suspend fun getReview(id: Long): ReviewItemEntity? = c.transactions.getReview(id)

    /** Save a transaction entered from a review item and remove the item from the queue. */
    fun resolveReview(reviewId: Long, t: Transaction, onDone: () -> Unit = {}) = viewModelScope.launch {
        c.transactions.insert(t)
        c.transactions.resolveReview(reviewId)
        onDone()
    }

    fun dismissReview(reviewId: Long) = viewModelScope.launch { c.transactions.resolveReview(reviewId) }

    fun setBudget(category: Category, limit: Double) = viewModelScope.launch { c.budgets.set(category, limit) }

    fun setApiKey(key: String?) = c.settings.setApiKey(key)

    /** Aggregated payload preview so users can see exactly what would be sent. */
    fun aiPayloadPreview(): String {
        val all = transactions.value
        val cur = InsightsEngine.summarize(all, Periods.month())
        val prev = InsightsEngine.summarize(all, Periods.month(-1))
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

    suspend fun exportCsv(): String = CsvExporter.toCsv(c.transactions.getAll())

    fun clearAllData(onDone: () -> Unit = {}) = viewModelScope.launch {
        c.transactions.clearAll()
        c.budgets.clearAll()
        c.settings.clearAll()
        _aiState.value = AiUiState.Idle
        _importState.value = ImportUiState.Idle
        onDone()
    }
}
