package com.pft.financetracker.domain.ledger

import com.pft.financetracker.domain.importer.Dates
import com.pft.financetracker.domain.importer.StatementRow
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.RefExtractor
import com.pft.financetracker.domain.split.PayerClassifier
import kotlin.math.abs

/**
 * Same payment: whether two records are one payment, and what survives when they merge.
 *
 * One payment often reaches the app twice: a bank alert and a UPI-app alert minutes apart, a statement row for a payment
 * an SMS already reported, a v1.0.0 row (time of day lost) re-read by a rescan, a review item approved after its twin was
 * saved. Every one of those checks, and every merge, lives here so they cannot drift apart.
 *
 * The rules, in short:
 * - A shared reference is the strongest sign, but only for the same direction, the same amount (a few paise, or the bank
 *   amount a split shrank) and at most [REF_MATCH_DAYS] apart: some banks reuse short refs.
 * - Two references that both exist and disagree are two payments.
 * - Minutes apart ([WINDOW_MILLIS]), same direction and amount: one payment when a different bank/app reported each, the
 *   merchant matches, or one alert names no merchant. Hours apart is two payments: two coffees are two coffees.
 * - A v1.0.0 row sits at midnight; it matches a same-day row of the same merchant (or a generic one), either direction.
 * - A statement row only knows its day: same direction, a day either side, and the names must agree (or it is the only
 *   generic candidate): ten Rs 1,000 paybacks on one day are ten different people.
 */
object SamePayment {
    /** How far apart two alerts for one payment can land. */
    const val WINDOW_MILLIS = 10 * 60_000L

    /** A shared reference only proves one payment for rows at most this many days apart. */
    const val REF_MATCH_DAYS = 3L

    private const val DAY = 86_400_000L

    /** The stored payments the checks look through: the database in the app, a list in tests. */
    interface Stored {
        /** Every stored row carrying exactly this reference. */
        suspend fun withRef(ref: String): List<Transaction>

        /** SMS and statement rows whose amount (or the bank amount a split shrank) is [amountPaise], in [from]..[to], either direction. */
        suspend fun similar(amountPaise: Long, from: Long, to: Long): List<Transaction>

        /** Rows from any source with this amount (or original amount) and direction, in [from]..[to]. */
        suspend fun sameAmount(amountPaise: Long, type: TransactionType, from: Long, to: Long): List<Transaction>
    }

    /** Why a stored row was taken for the incoming one. */
    enum class Why { REF, STATEMENT, LEGACY, WINDOW }

    data class Found(val row: Transaction, val why: Why)

    /** A pair of stored rows that are one payment recorded twice. */
    data class Twins(val keep: Transaction, val drop: Transaction)

    /**
     * The stored row an incoming SMS or approved review item repeats, or null when it is a new payment.
     * [isClaimed] says a statement or v1.0.0 row already stands for another SMS (each stands for exactly one).
     */
    suspend fun find(
        incoming: Transaction,
        stored: Stored,
        windowMillis: Long = WINDOW_MILLIS,
        isClaimed: suspend (Long) -> Boolean = { false },
    ): Found? {
        incoming.refNumber?.let { ref ->
            stored.withRef(ref)
                .filter { it.type == incoming.type && sameAmount(it, incoming.amountPaise) && abs(it.timestamp - incoming.timestamp) <= REF_MATCH_DAYS * DAY }
                .minByOrNull { abs(it.timestamp - incoming.timestamp) }
                ?.let { return Found(it, Why.REF) }
        }

        // Search the whole calendar day as well as the window: a transaction imported by v1.0.0 sits at
        // midnight (its parser dropped the time of day), so the same message re-parsed now lands hours away.
        val dayStart = startOfDay(incoming.timestamp)
        val dayEnd = dayStart + DAY
        val from = minOf(dayStart, incoming.timestamp - windowMillis)
        val to = maxOf(dayEnd, incoming.timestamp + windowMillis)

        // Same direction first, so a same-day refund never takes a legacy row its own debit should have matched.
        val similar = stored.similar(incoming.amountPaise, minOf(from, dayStart - DAY), to)
        val sameDayUnique = similar.count { it.type == incoming.type && it.source == Transaction.Source.STATEMENT } == 1
        for (existing in similar.sortedBy { it.type != incoming.type }) {
            // Two references that both exist and disagree mean two genuinely different payments.
            if (refsDiffer(existing.refNumber, incoming.refNumber)) continue
            val sameMerchant = sameMerchant(existing, incoming)
            val genericMerchant = eitherGeneric(existing, incoming)
            val why = when {
                // A row imported from a statement only knows its day (and the statement may book it a day late). Same
                // direction, and the names must agree: ten Rs 1,000 paybacks on one day are ten different people.
                existing.source == Transaction.Source.STATEMENT -> Why.STATEMENT.takeIf {
                    existing.type == incoming.type && abs(startOfDay(existing.timestamp) - dayStart) <= DAY &&
                        !isClaimed(existing.id) && (PayerClassifier.sameParty(existing.merchant, incoming.merchant) || (genericMerchant && sameDayUnique))
                }
                // A v1.0.0 row: exactly midnight on this day, possibly with the direction the old parser guessed. Each
                // stands for one SMS, so once one has matched it (its own debit, scanned first), a same-day refund of
                // the same amount is a second payment and must not merge into it and flip it.
                isMidnight(existing) && existing.timestamp in dayStart until dayEnd ->
                    Why.LEGACY.takeIf { !isClaimed(existing.id) && (sameMerchant || genericMerchant) }
                // Minutes apart, same direction: a second sender reporting the same payment, or a generic alert.
                existing.type == incoming.type && abs(existing.timestamp - incoming.timestamp) <= windowMillis ->
                    Why.WINDOW.takeIf { sameMerchant || existing.bankName != incoming.bankName || genericMerchant }
                // Hours apart is two payments, even to the same merchant: two coffees are two coffees.
                else -> null
            }
            if (why != null) return Found(existing, why)
        }
        return null
    }

    /**
     * The stored row a statement row repeats, or null. [consumed] rows already stand for another row of this import.
     * (A row from the same file imported before is found by its hash, before this is asked.)
     */
    suspend fun findForStatement(row: StatementRow, stored: Stored, consumed: Set<Long>): Transaction? {
        // The same reference number, direction, amount and (within a week) date. The date guard matters: some banks
        // reuse cheque-style numbers, and a ref alone once matched a payment months away. Statements pad or prefix refs
        // ("000427712345678" for the SMS's "427712345678"), so refs are compared normalised.
        if (RefExtractor.normalize(row.ref) != null) {
            stored.sameAmount(row.amountPaise, row.type, row.date - 7 * DAY, row.date + 7 * DAY)
                .filter { it.id !in consumed && RefExtractor.same(it.refNumber, row.ref) }
                .minByOrNull { abs(it.timestamp - row.date) }
                ?.let { return it }
        }
        // Same amount and direction within a day either side (statements book some payments a day late). Names
        // must agree: ten Rs 1,000 paybacks on one day are ten different people.
        val cands = stored.sameAmount(row.amountPaise, row.type, row.date - DAY, row.date + 2 * DAY - 1)
            .filter { it.id !in consumed && it.source != Transaction.Source.SPLIT }
        cands.firstOrNull { PayerClassifier.sameParty(it.merchant, row.counterparty) }?.let { return it }
        val generic = cands.filter { isGeneric(it.merchant) }
        return generic.singleOrNull()?.takeIf { cands.size == 1 }
    }

    /**
     * Stored SMS rows already counted twice: the retrospective counterpart to [find], for rows imported before the
     * rules existed. Largest amounts first. Each row is in at most one pair; the richer one is kept.
     */
    fun twinsIn(rows: List<Transaction>, windowMillis: Long = WINDOW_MILLIS): List<Twins> {
        val all = rows.filter { it.source == Transaction.Source.SMS && !it.needsReview }
        val pairs = mutableListOf<Twins>()
        val consumed = mutableSetOf<Long>()
        val refWindow = REF_MATCH_DAYS * DAY
        for ((_, group) in all.groupBy { it.amountPaise to it.type }) {
            if (group.size < 2) continue
            val ordered = group.sortedBy { it.timestamp }
            for (i in ordered.indices) {
                val a = ordered[i]
                if (a.id in consumed) continue
                for (j in i + 1 until ordered.size) {
                    val b = ordered[j]
                    val gap = b.timestamp - a.timestamp
                    if (gap > refWindow) break
                    if (b.id in consumed) continue
                    if (refsDiffer(a.refNumber, b.refNumber)) continue
                    val sameMerchant = sameMerchant(a, b)
                    val differentReporter = a.bankName != b.bankName
                    val generic = eitherGeneric(a, b)
                    val sameRef = refsAgree(a.refNumber, b.refNumber)
                    val legacy = (isMidnight(a) || isMidnight(b)) && startOfDay(a.timestamp) == startOfDay(b.timestamp)
                    val paired = when {
                        sameRef -> true
                        legacy -> sameMerchant || generic
                        gap <= windowMillis -> sameMerchant || differentReporter || generic
                        else -> false
                    }
                    if (!paired) continue
                    val keep = richer(a, b)
                    val drop = if (keep === a) b else a
                    pairs += Twins(keep, drop)
                    consumed += drop.id
                    consumed += keep.id
                    break
                }
            }
        }
        return pairs.sortedByDescending { it.drop.amountPaise }
    }

    /**
     * One record for a payment a second message reported. A row a person corrected only gains the identifiers it
     * lacked. Otherwise: the richer row's descriptive fields, with what only the other had filled in; never the
     * incoming amount or note (a split may have shrunk the amount on purpose). A weaker second alert must not undo
     * what the first one knew: a specific category stays unless it was Other or the direction turned out wrong, and a
     * transfer, investment, cash or refund flow stays when this parse only fell back to expense/income.
     */
    fun merge(existing: Transaction, incoming: Transaction): Transaction {
        if (existing.userEdited) return addIdentifiers(existing, incoming)
        val base = richer(existing, incoming)
        val other = if (base === existing) incoming else existing
        val sameDirection = existing.type == incoming.type
        // A v1.0.0 row (midnight, flow guessed from its category) or a statement row (flow guessed from the narration)
        // knew less than the SMS we hold now: this parse decides.
        val weakExisting = existing.source == Transaction.Source.STATEMENT || isMidnight(existing)
        val incomingFellBack = incoming.flow == Flow.EXPENSE || incoming.flow == Flow.INCOME
        val flowFitsDirection = sameDirection || existing.flow == Flow.TRANSFER || existing.flow == Flow.INVESTMENT
        val flow = when {
            // A split owns a settled transfer's flow: re-reading its SMS must not make it income again.
            existing.flow == Flow.SETTLEMENT -> existing.flow
            !weakExisting && incomingFellBack && flowFitsDirection &&
                existing.flow in setOf(Flow.TRANSFER, Flow.INVESTMENT, Flow.CASH, Flow.REFUND) -> existing.flow
            else -> incoming.flow
        }
        val category = when {
            weakExisting || !sameDirection -> incoming.category
            existing.category == Category.OTHER -> incoming.category
            else -> existing.category
        }
        val merchant = nameOf(base, other)
        return base.copy(
            id = existing.id, smsHash = existing.smsHash, type = incoming.type, flow = flow, category = category, merchant = merchant,
            amountPaise = existing.amountPaise, originalAmountPaise = existing.originalAmountPaise, note = existing.note,
            refNumber = base.refNumber ?: other.refNumber, accountRef = base.accountRef ?: other.accountRef, bankName = base.bankName ?: other.bankName,
            counterpartyKind = existing.counterpartyKind ?: incoming.counterpartyKind, importBatchId = existing.importBatchId,
            source = existing.source, userEdited = existing.userEdited, confidence = existing.confidence, needsReview = existing.needsReview,
            // A statement row only knew the day; the SMS knows the minute.
            timestamp = if (existing.source == Transaction.Source.STATEMENT) incoming.timestamp else existing.timestamp,
        )
    }

    /** The stored row, gaining only the reference and account it lacked (a corrected row, an approved review item). */
    fun addIdentifiers(existing: Transaction, incoming: Transaction): Transaction =
        existing.copy(refNumber = existing.refNumber ?: incoming.refNumber, accountRef = existing.accountRef ?: incoming.accountRef)

    /**
     * The survivor of two stored twins: [keep], with any detail only [drop] had. [isLinked] says a split points at a row
     * (a settled transfer, a shrunk payment); that copy survives so the split's numbers and links stay whole.
     * Returns the row to write and the row to delete.
     */
    suspend fun combine(twins: Twins, isLinked: suspend (Long) -> Boolean = { false }): Twins {
        val p = if (isLinked(twins.drop.id) && !isLinked(twins.keep.id)) Twins(keep = twins.drop, drop = twins.keep) else twins
        val merged = p.keep.copy(
            merchant = nameOf(p.keep, p.drop),
            accountRef = p.keep.accountRef ?: p.drop.accountRef,
            refNumber = p.keep.refNumber ?: p.drop.refNumber,
            bankName = p.keep.bankName ?: p.drop.bankName,
            note = p.keep.note ?: p.drop.note,
            counterpartyKind = p.keep.counterpartyKind ?: p.drop.counterpartyKind,
        )
        return Twins(keep = merged, drop = p.drop)
    }

    /** Prefer the record with more detail (merchant, account, ref). */
    fun richer(a: Transaction, b: Transaction): Transaction {
        fun score(t: Transaction) = (if (!isGeneric(t.merchant)) 4 else 0) + (if (t.accountRef != null) 2 else 0) + (if (t.refNumber != null) 1 else 0) + (if (t.bankName != null) 1 else 0)
        return if (score(b) > score(a)) b else a
    }

    /** "Payment (HDFC Bank)", "Credit (SBI)": the placeholder the parser uses when an SMS names no merchant. */
    fun isGeneric(merchant: String) = merchant.startsWith("Payment") || merchant.startsWith("Credit") || merchant.length < 3

    fun startOfDay(t: Long): Long = Dates.startOfDay(t)

    /** A v1.0.0 row: its parser dropped the time of day, so it sits at exactly midnight. */
    private fun isMidnight(t: Transaction) = !Dates.hasTime(t.timestamp)

    private fun sameMerchant(a: Transaction, b: Transaction) = InsightsEngine.normalizeMerchant(a.merchant) == InsightsEngine.normalizeMerchant(b.merchant)

    private fun eitherGeneric(a: Transaction, b: Transaction) = isGeneric(a.merchant) || isGeneric(b.merchant)

    /** The name a merged payment keeps: [base]'s, unless it is a placeholder and [other] has a real one. */
    private fun nameOf(base: Transaction, other: Transaction) = if (isGeneric(base.merchant) && !isGeneric(other.merchant)) other.merchant else base.merchant

    /** Same amount, or the bank amount a split shrank this row from; a few paise of rounding allowed. */
    private fun sameAmount(t: Transaction, paise: Long) = abs(t.amountPaise - paise) <= 5 || t.originalAmountPaise == paise

    private fun refsAgree(a: String?, b: String?) = a != null && b != null && (a == b || RefExtractor.same(a, b))

    /** Two references that both exist and name different payments. */
    private fun refsDiffer(a: String?, b: String?) = a != null && b != null && !refsAgree(a, b)
}
