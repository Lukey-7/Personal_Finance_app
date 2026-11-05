package com.pft.financetracker.ui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.pft.financetracker.appContainer
import com.pft.financetracker.data.bills.toDomain
import com.pft.financetracker.data.cards.toDomain
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.cards.Card
import com.pft.financetracker.domain.cards.CardSummary
import com.pft.financetracker.domain.goals.Goal
import com.pft.financetracker.domain.goals.GoalProgress
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.recurring.RecurringBook
import com.pft.financetracker.domain.recurring.RecurringStatus
import com.pft.financetracker.domain.reminders.DayClock
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.SectionTotal
import com.pft.financetracker.domain.tax.TaxSection
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate

/**
 * The money tools: subscriptions, bills and EMIs, credit cards, goals and the tax helper. Reads the same payments as
 * [AppViewModel] ([transactions] is its flow, not a second database read), so every tool agrees with Home.
 */
class MoneyToolsViewModel(app: Application, transactions: StateFlow<List<Transaction>>) : AndroidViewModel(app) {
    private val c = app.appContainer

    /** Subscriptions and other repeating charges, recomputed whenever transactions or decisions change. */
    val recurringBook: StateFlow<RecurringBook> =
        combine(transactions, c.db.recurringDao().observeAll()) { txns, decisions ->
            if (!isLoaded(txns)) RecurringBook.EMPTY
            else withContext(Dispatchers.Default) { c.recurring.bookOf(txns, decisions) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecurringBook.EMPTY)
    fun decideRecurring(key: String, status: RecurringStatus?) =
        viewModelScope.launch(Dispatchers.IO) { c.recurring.decide(key, status) }

    /** Today, moving on just after midnight, so due dates and cycles update without new data. */
    private val today = DayClock.today().stateIn(viewModelScope, SharingStarted.Eagerly, LocalDate.now())

    /** Every bill with its status today, recomputed when transactions, bills or paid marks change. */
    val billStates: StateFlow<List<Pair<Bill, BillState>>> =
        combine(transactions, c.db.billDao().observeAll(), c.db.billDao().observeMarks(), today) { txns, bills, marks, day ->
            withContext(Dispatchers.Default) {
                val byBill = marks.groupBy({ it.billId }, { LocalDate.ofEpochDay(it.dueDay) })
                c.bills.statesOf(bills.map { it.toDomain() }, txns, byBill, day)
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), notLoaded())
    fun saveBill(b: Bill) = viewModelScope.launch(Dispatchers.IO) { c.bills.save(b) }
    fun deleteBill(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.bills.delete(id) }
    fun markBillPaid(id: Long, due: LocalDate) = viewModelScope.launch(Dispatchers.IO) { c.bills.markPaid(id, due) }
    fun unmarkBillPaid(id: Long, due: LocalDate) = viewModelScope.launch(Dispatchers.IO) { c.bills.unmarkPaid(id, due) }

    /** Each card's current billing cycle, recomputed when transactions or cards change. */
    val cardSummaries: StateFlow<List<CardSummary>> =
        combine(transactions, c.db.cardDao().observeAll(), today) { txns, cards, day ->
            withContext(Dispatchers.Default) { c.cards.summariesOf(cards.map { it.toDomain() }, txns, day) }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), notLoaded())
    suspend fun cardSuggestions(): List<String> = withContext(Dispatchers.IO) {
        c.cards.suggestions(extra = c.bills.all().mapNotNull { it.cardLast4 })
    }
    fun saveCard(card: Card) = viewModelScope.launch(Dispatchers.IO) { c.cards.save(card) }
    fun deleteCard(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.cards.delete(id) }

    val goalProgress: StateFlow<List<GoalProgress>> =
        combine(c.db.goalDao().observeAll(), c.db.goalDao().observeContributions(), today) { goals, contributions, day ->
            c.goals.progressOf(goals, contributions, day)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), notLoaded())
    fun saveGoal(g: Goal) = viewModelScope.launch(Dispatchers.IO) { c.goals.save(g) }
    fun deleteGoal(id: Long) = viewModelScope.launch(Dispatchers.IO) { c.goals.delete(id) }
    fun contributeToGoal(id: Long, paise: Long) = viewModelScope.launch(Dispatchers.IO) { c.goals.contribute(id, paise) }

    private val _taxYear = MutableStateFlow(FinancialYear.of(LocalDate.now()))
    val taxYear: StateFlow<FinancialYear> = _taxYear
    fun setTaxYear(fy: FinancialYear) { _taxYear.value = fy }
    /** The person's own tax tags by transaction id; a null value means "not a deduction". */
    val taxTags: StateFlow<Map<Long, TaxSection?>> = c.db.taxDao().observeAll()
        .map { l -> l.associate { it.transactionId to TaxSection.fromName(it.section) } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())
    val taxSummary: StateFlow<List<SectionTotal>> =
        combine(transactions, c.db.taxDao().observeAll(), _taxYear) { txns, tags, fy -> withContext(Dispatchers.Default) { c.tax.summaryOf(txns, tags, fy) } }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())
    fun tagTax(txId: Long, s: TaxSection?) = viewModelScope.launch(Dispatchers.IO) { c.tax.tag(txId, s) }
    fun clearTaxTag(txId: Long) = viewModelScope.launch(Dispatchers.IO) { c.tax.clearTag(txId) }
    suspend fun taxCsv(fy: FinancialYear): String = withContext(Dispatchers.IO) { c.tax.csv(fy) }

    companion object {
        /** Built once, for the whole app, from the payments [AppViewModel] already holds. */
        fun factory(app: Application, transactions: StateFlow<List<Transaction>>): ViewModelProvider.Factory =
            viewModelFactory { initializer { MoneyToolsViewModel(app, transactions) } }
    }
}
