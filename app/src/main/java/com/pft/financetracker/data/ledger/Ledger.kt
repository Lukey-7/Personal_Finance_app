package com.pft.financetracker.data.ledger

import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.refunds.RefundLinker
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.ui.model.FlowRules
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The ledger: every change a person makes to their payments goes through here, so each one keeps the books straight.
 *
 * Every time, without the caller asking:
 * - a payment's flow fits its direction (money in is never spend, money out never income);
 * - a person's correction is marked, so automatic rewrites (a rescan, refund pairing) leave it alone;
 * - an approved review item is checked for a stored twin first ([com.pft.financetracker.domain.ledger.SamePayment]);
 * - a deleted purchase gives back the refunds paired with it, and a deleted SMS or statement row stays deleted on the
 *   next scan or import;
 * - then one follow-up (refund pairing, split detection, the widget) runs in the background. Requests that arrive while
 *   one runs are folded into a single next run, so it never runs twice at once, and it runs in [scope], which outlives
 *   any screen, so leaving a screen cannot cancel it half way.
 *
 * Imports write their own rows in bulk (SMS, statements) and ask for one follow-up at the end with [followUp].
 */
class Ledger(
    private val repo: TransactionRepository,
    private val smsLog: SmsLogRepository,
    private val refunds: RefundLinker,
    private val importer: SmsImporter,
    private val statements: StatementImporter,
    /** Refund pairing, split detection and the widget; [useAi] lets split detection ask the AI judge. */
    private val afterChange: suspend (useAi: Boolean) -> Unit,
    private val scope: CoroutineScope,
) {
    /** A payment a person entered (the editor, quick add, a split's "my share"). Returns its id. */
    suspend fun add(t: Transaction): Long = changed { repo.insert(FlowRules.normalise(t)) }

    /** A person corrected a stored payment. */
    suspend fun correct(t: Transaction) = changed { repo.update(FlowRules.normalise(t).copy(userEdited = true)) }

    /** The app itself reshaped a stored payment (a split shrinking it to my share): not marked as a person's correction. */
    suspend fun reshape(t: Transaction) = changed { repo.update(FlowRules.normalise(t)) }

    /** A person deleted a payment. */
    suspend fun remove(t: Transaction) = changed {
        // A refund paired with this purchase goes back to income first; the pairing itself goes with the purchase.
        runCatching { refunds.unlinkForDeletedPurchase(t.id) }
        repo.delete(t)
        importer.forgetDeleted(t)
    }

    /** Moves [ids] to [category] (Activity's multi-select), as a person's correction. Only rows that change are written. */
    suspend fun recategorise(ids: Set<Long>, category: Category) = changed {
        val rows = ids.mapNotNull { repo.getById(it) }
        com.pft.financetracker.ui.model.recategorise(rows, ids, category).forEach { repo.update(it) }
    }

    /**
     * A person confirmed review item [reviewId] as [t]. A stored twin (the payment's other alert) takes it instead of a
     * second row. The item leaves the queue and its message is logged as saved. Returns the id now holding the payment.
     */
    suspend fun approve(reviewId: Long, t: Transaction, body: String?): Long = changed {
        val id = repo.insertReviewed(FlowRules.normalise(t).copy(userEdited = true), body) { smsLog.pointsAt(it) }
        repo.resolveReview(reviewId)
        t.smsHash?.let { smsLog.updateOutcome(it, "SAVED", t.merchant, id.takeIf { v -> v > 0 }) }
        id
    }

    /** Keeps one row of each pair the duplicate clean-up found; a row a split points at is the one kept. */
    suspend fun mergeTwins(pairs: List<TransactionRepository.DuplicatePair>, isLinked: suspend (Long) -> Boolean) = changed {
        repo.mergeDuplicates(pairs, isLinked)
    }

    /** Undoes statement import [batchId]: each purchase it added gives back its paired refunds before it goes. */
    suspend fun undoImport(batchId: Long) = changed {
        statements.undo(batchId) { id -> runCatching { refunds.unlinkForDeletedPurchase(id) } }
    }

    /**
     * Runs [block] as one change: the payments it writes get a single follow-up when it ends, after everything in it
     * (a split's own rows included) is saved.
     */
    suspend fun <T> together(block: suspend () -> T): T {
        if (coroutineContext[Together] != null) return block()
        // Even when the block fails part way, what it did write gets its follow-up.
        try {
            return withContext(Together()) { block() }
        } finally {
            followUp()
        }
    }

    /**
     * Asks for the follow-up in the background and returns at once. Requests made while one runs are folded into one
     * next run; if any of them wanted the AI judge, that run uses it. The returned job ends once this request is served.
     */
    fun followUp(useAi: Boolean = false): Job {
        synchronized(this) { wanted = maxOf(wanted, if (useAi) AI else LOCAL) }
        return scope.launch {
            running.withLock {
                val w = synchronized(this@Ledger) { wanted.also { wanted = NONE } }
                if (w != NONE) runCatching { afterChange(w == AI) }
            }
        }
    }

    /** [followUp], waiting until it has run (a background worker, a restore that reports when done). */
    suspend fun catchUp(useAi: Boolean = false) = followUp(useAi).join()

    private suspend fun <T> changed(write: suspend () -> T): T {
        val result = write()
        if (coroutineContext[Together] == null) followUp()
        return result
    }

    private val running = Mutex()
    private var wanted = NONE

    private class Together : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Together>
    }

    private companion object {
        const val NONE = 0
        const val LOCAL = 1
        const val AI = 2
    }
}
