package com.pft.financetracker.ui.model

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.components.matchesAmount

/*
 * Activity's list logic, kept apart from the screen so it can be tested: the filter sheet, a day's net, deleting with
 * a chance to undo, and selecting several rows to recategorise.
 */

/** "HDFC Bank ••1234", "SBI", or null when the row has no account (cash, a split share). */
fun accountLabel(t: Transaction): String? =
    listOfNotNull(t.bankName?.takeIf { it.isNotBlank() }, t.accountRef?.takeIf { it.isNotBlank() }?.let { "••$it" })
        .joinToString(" ").ifBlank { null }

/**
 * What the filter sheet has chosen. Within a group the choices widen ("Food or Shopping"); across groups they narrow
 * ("Food, on HDFC"). Days are start-of-day millis; both ends are included.
 */
data class ActivityFilter(
    val categories: Set<Category> = emptySet(),
    val accounts: Set<String> = emptySet(),
    val flows: Set<Flow> = emptySet(),
    val fromDay: Long? = null,
    val toDay: Long? = null,
    /** Failed payments that came straight back are hidden unless this is on. */
    val showReversed: Boolean = false,
) {
    val hasRange: Boolean get() = fromDay != null || toDay != null

    /** The number on the Filter button's badge: one per choice. */
    val count: Int get() = categories.size + accounts.size + flows.size + (if (hasRange) 1 else 0) + (if (showReversed) 1 else 0)
    val isActive: Boolean get() = count > 0

    fun toggle(c: Category) = copy(categories = categories.flip(c))
    fun toggle(f: Flow) = copy(flows = flows.flip(f))
    fun toggleAccount(a: String) = copy(accounts = accounts.flip(a))
    fun cleared() = ActivityFilter()

    fun matches(t: Transaction, query: String, hidden: Set<Long>): Boolean {
        if (!showReversed && t.id in hidden) return false
        if (categories.isNotEmpty() && t.category !in categories) return false
        if (accounts.isNotEmpty() && accountLabel(t) !in accounts) return false
        if (flows.isNotEmpty() && t.flow !in flows) return false
        if (fromDay != null && t.timestamp < fromDay) return false
        if (toDay != null && t.timestamp >= toDay + DAY) return false
        return matchesQuery(t, query)
    }

    fun apply(txns: List<Transaction>, query: String, hidden: Set<Long>): List<Transaction> = txns.filter { matches(it, query, hidden) }

    companion object {
        private const val DAY = 86_400_000L

        /** Accounts that appear in [txns], most used first (ties alphabetical), for the sheet's account choices. */
        fun accountsIn(txns: List<Transaction>): List<String> =
            txns.mapNotNull { accountLabel(it) }.groupingBy { it }.eachCount().entries
                .sortedWith(compareByDescending<Map.Entry<String, Int>> { it.value }.thenBy { it.key }).map { it.key }

        /** Search: a name, a bank, a reference number, or an amount with or without commas. */
        fun matchesQuery(t: Transaction, query: String): Boolean {
            val q = query.trim()
            if (q.isEmpty()) return true
            return t.merchant.contains(q, true) || (t.bankName ?: "").contains(q, true) || matchesAmount(t.amountPaise, q) ||
                (t.refNumber ?: "").contains(q, true) || (t.note ?: "").contains(q, true)
        }
    }
}

private fun <T> Set<T>.flip(v: T): Set<T> = if (v in this) this - v else this + v

/** A day's net for its sticky header: money in minus money out. Moves between your own pockets are left out. */
object DayNet {
    fun signed(t: Transaction): Long = when (t.flow) {
        Flow.INCOME, Flow.REFUND -> t.amountPaise
        Flow.EXPENSE, Flow.CASH -> -t.amountPaise
        Flow.TRANSFER, Flow.INVESTMENT, Flow.SETTLEMENT -> 0L
    }

    fun of(day: List<Transaction>): Long = day.sumOf { signed(it) }
}

/**
 * Swipe to delete: the row goes at once, the delete happens when the chance to undo ends (the snackbar times out, or
 * another row is swiped). Only the latest delete can be undone. State is Compose state so the list redraws.
 */
class DeleteWithUndo(private val delete: (Transaction) -> Unit) {
    var pending: Transaction? by mutableStateOf(null)
        private set

    fun hides(id: Long): Boolean = pending?.id == id

    /** Takes [t] off the list; finishes any earlier pending delete first. */
    fun stage(t: Transaction) {
        commit()
        pending = t
    }

    /** Puts the pending row back and deletes nothing. Returns it. */
    fun undo(): Transaction? = pending.also { pending = null }

    /** The undo chance is over: delete for real. Safe to call twice. */
    fun commit() {
        val t = pending ?: return
        pending = null
        delete(t)
    }
}

/** Long-press selection in Activity. */
data class Selection(val ids: Set<Long> = emptySet()) {
    val active: Boolean get() = ids.isNotEmpty()
    fun toggle(id: Long) = Selection(if (id in ids) ids - id else ids + id)
    fun selectAll(all: List<Long>) = Selection(all.toSet())
    operator fun contains(id: Long) = id in ids
}

/**
 * The rows to write when the chosen ones are moved to [category]: only those that change, marked as corrected by a
 * person so automatic rewrites leave them alone. Amount, flow and everything else stay as they are.
 */
fun recategorise(txns: List<Transaction>, ids: Set<Long>, category: Category): List<Transaction> =
    txns.filter { it.id in ids && it.category != category }.map { it.copy(category = category, userEdited = true) }

/** Which way a figure points, for its colour and its words. */
enum class MoneyTone { IN, OUT, MOVED }

fun toneOf(t: Transaction): MoneyTone = when (t.flow) {
    Flow.EXPENSE, Flow.CASH -> MoneyTone.OUT
    Flow.INCOME, Flow.REFUND -> MoneyTone.IN
    Flow.TRANSFER, Flow.INVESTMENT, Flow.SETTLEMENT -> MoneyTone.MOVED
}

/** "+" for money in, "-" for money out (the formatter's one minus glyph), as every list shows it. */
fun signOf(t: Transaction): String = if (t.type == TransactionType.CREDIT) "+" else "-"
