package com.pft.financetracker.data.ledger

import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.refunds.RefundLinker
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.ledger.FlowRules
import com.pft.financetracker.domain.ledger.applyEdit
import com.pft.financetracker.domain.ledger.recategorise
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext

/**
 * The ledger: every change a person makes to their payments goes through here, so each one keeps the books straight.
 *
 * Every time, without the caller asking:
 * - a payment's flow fits its direction (money in is never spend, money out never income);
 * - a person's correction is marked, so automatic rewrites (a rescan, refund pairing) leave it alone, and it is applied
 *   to the payment as stored now, so a refund pairing or split made while the editor was open is kept;
 * - an approved review item is checked for a stored twin first ([com.pft.financetracker.domain.ledger.SamePayment]);
 * - a deleted purchase gives back the refunds paired with it, merged twins hand their refund pairings to the survivor,
 *   and a deleted SMS or statement row stays deleted on the next scan or import;
 * - each change is one database transaction: it lands whole or not at all;
 * - then one follow-up (refund pairing, split detection, the widget) runs in the background. Requests that arrive while
 *   one runs are folded into a single next run, so it never runs twice at once, and it runs in [scope], which outlives
 *   any screen, so leaving a screen cannot cancel it half way.
 *
 * The follow-up here uses local rules only. The AI split judge runs through [refreshWithAi], in the caller's scope, so
 * closing the app stops it, and work that waits for the books ([catchUp]) never waits on the network for long.
 * Imports write their own rows in bulk (SMS, statements) and ask for one follow-up at the end with [followUp].
 */
class Ledger(
    private val repo: TransactionRepository,
    private val smsLog: SmsLogRepository,
    private val refunds: RefundLinker,
    private val importer: SmsImporter,
    private val statements: StatementImporter,
    /** Runs a block as one database transaction. */
    private val transactor: Transactor,
    /** Refund pairing, split detection and the widget; [useAi] lets split detection ask the AI judge. */
    private val afterChange: suspend (useAi: Boolean) -> Unit,
    private val scope: CoroutineScope,
) {
    /** Runs a block as one database transaction (Room's `withTransaction` in the app, the same in tests). */
    interface Transactor {
        suspend fun <T> run(block: suspend () -> T): T
    }

    /** A payment a person entered (the editor, quick add, a split's "my share"). Returns its id. */
    suspend fun add(t: Transaction): Long = changed { repo.insert(FlowRules.normalise(t)) }

    /**
     * A person corrected a stored payment: [edited] is the form as saved, [opened] the payment as the form first showed
     * it. Only the fields the person changed are written, onto the payment as stored now.
     */
    suspend fun correct(edited: Transaction, opened: Transaction?) = changed {
        val stored = repo.getById(edited.id)
        val row = if (stored != null && opened != null) applyEdit(stored, opened, edited) else edited
        repo.update(FlowRules.normalise(row).copy(userEdited = true))
    }

    /** The app itself reshaped a stored payment (a split shrinking it to my share): not marked as a person's correction. */
    suspend fun reshape(t: Transaction) = changed { repo.update(FlowRules.normalise(t)) }

    /** A person deleted a payment. */
    suspend fun remove(t: Transaction) = changed {
        // A refund paired with this purchase goes back to income first; the pairing itself goes with the purchase.
        tolerate { refunds.unlinkForDeletedPurchase(t.id) }
        repo.delete(t)
        importer.forgetDeleted(t)
    }

    /** Moves [ids] to [category] (Activity's multi-select), as a person's correction. Only rows that change are written. */
    suspend fun recategorise(ids: Set<Long>, category: Category) = changed {
        repo.updateAll(recategorise(repo.getByIds(ids), ids, category))
    }

    /**
     * A person confirmed review item [reviewId] as [t]. A stored twin (the payment's other alert) takes it instead of a
     * second row. The item leaves the queue and its message is logged as saved. Returns the id now holding the payment.
     */
    suspend fun approve(reviewId: Long, t: Transaction, body: String?): Long = changed {
        val id = repo.insertReviewed(FlowRules.normalise(t).copy(userEdited = true), body) { smsLog.pointsAt(it) }
        repo.resolveReview(reviewId)
        t.smsHash?.let { smsLog.updateOutcome(it, com.pft.financetracker.data.sms.Outcomes.SAVED, t.merchant, id.takeIf { v -> v > 0 }) }
        id
    }

    /**
     * Keeps one row of each pair the duplicate clean-up found; a row a split points at is the one kept. Refund pairings
     * on the dropped row move to the survivor, so the refund still remembers what it was before pairing.
     */
    suspend fun mergeTwins(pairs: List<TransactionRepository.DuplicatePair>, isLinked: suspend (Long) -> Boolean) = changed {
        repo.mergeDuplicates(pairs, beforeDrop = { dropId, keepId -> refunds.moveLinks(dropId, keepId) }, isLinked = isLinked)
    }

    /** Undoes statement import [batchId]: each purchase it added gives back its paired refunds before it goes. */
    suspend fun undoImport(batchId: Long) = changed {
        statements.undo(batchId) { id -> tolerate { refunds.unlinkForDeletedPurchase(id) } }
    }

    /**
     * Runs [block] as one change: one database transaction, so a failure part way leaves nothing half written, and a
     * single follow-up once it ends, which cannot see the change before it is complete.
     */
    suspend fun <T> together(block: suspend () -> T): T {
        if (coroutineContext[Together] != null) return block()
        try {
            return transactor.run { withContext(Together()) { block() } }
        } finally {
            followUp()
        }
    }

    /**
     * Asks for the follow-up (local rules) in the background and returns at once. Requests made while one runs are
     * folded into one next run. The returned job ends once this request is served.
     */
    fun followUp(): Job {
        pending.set(true)
        return scope.launch {
            running.withLock {
                if (pending.getAndSet(false)) runCatching { afterChange(false) }
            }
        }
    }

    /**
     * [followUp], waiting until it has run, but never longer than [CATCH_UP_WAIT_MS] (an AI run in progress may hold
     * the follow-up for a while; the request still runs after it). For a background worker and a restore.
     */
    suspend fun catchUp() {
        withTimeoutOrNull(CATCH_UP_WAIT_MS) { followUp().join() }
    }

    /**
     * The follow-up with the AI split judge, run now in the caller's coroutine (a screen's scope), so it stops when the
     * caller is cancelled. It includes everything the local follow-up does, so a pending local request is served too.
     */
    suspend fun refreshWithAi() {
        running.withLock {
            pending.set(false)
            afterChange(true)
        }
    }

    private suspend fun <T> changed(write: suspend () -> T): T {
        if (coroutineContext[Together] != null) return write()
        // Even when the write fails part way (it rolls back), the follow-up still runs: it is cheap and it is idempotent.
        try {
            return transactor.run { write() }
        } finally {
            followUp()
        }
    }

    /** Runs a best-effort step: a failure is ignored, but cancellation is not. */
    private suspend fun tolerate(step: suspend () -> Unit) {
        try { step() } catch (e: CancellationException) { throw e } catch (_: Exception) {}
    }

    private val running = Mutex()
    private val pending = AtomicBoolean(false)

    private class Together : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Together>
    }

    companion object {
        /** How long [catchUp] waits for a follow-up that may be queued behind an AI run. */
        const val CATCH_UP_WAIT_MS = 15_000L
    }
}
