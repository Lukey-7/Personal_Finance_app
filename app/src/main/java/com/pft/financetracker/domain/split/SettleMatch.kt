package com.pft.financetracker.domain.split

import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import kotlin.math.abs

/**
 * Which payments already in the app belong to a manual split: the bill itself ("Paid with"), a friend's transfer that
 * settles their share, or my own payment to whoever paid. Pure, so the screens' choices are tested.
 */
object SettleMatch {
    private const val DAY = 86_400_000L

    /** How far over the amount owed a transfer may be and still be the payment for it: friends round up (Rs 1,030 for Rs 1,028.33). */
    fun roundUpPaise(owedPaise: Long): Long = maxOf(50_00L, owedPaise * 5 / 100)

    private fun isPerson(t: Transaction) =
        (t.counterpartyKind ?: PayerClassifier.classify("", t.merchant, t.type)) == CounterpartyKind.PERSON

    /**
     * Money in from people that could settle [remainingPaise] of a split dated [date]: from a day before to 45 days
     * after, still counted as income, not already used by a split ([used]), and at most what is owed plus a round-up.
     * Closest in time first.
     */
    fun incoming(txns: List<Transaction>, date: Long, remainingPaise: Long, used: Set<Long>): List<Transaction> =
        txns.filter {
            it.type == TransactionType.CREDIT && it.flow == Flow.INCOME && it.id !in used &&
                it.timestamp >= date - DAY && it.timestamp <= date + 45 * DAY &&
                it.amountPaise <= remainingPaise + roundUpPaise(remainingPaise) && isPerson(it)
        }.sortedBy { abs(it.timestamp - date) }

    /**
     * My own payments that could be me paying back [payerName], who paid the bill: money out counted as spend, not
     * already used by a split, at most what I owe plus a round-up. Payments to that person first, then the closest
     * amount, then the closest in time.
     */
    fun outgoing(txns: List<Transaction>, date: Long, remainingPaise: Long, payerName: String, used: Set<Long>): List<Transaction> =
        txns.filter {
            it.type == TransactionType.DEBIT && it.flow == Flow.EXPENSE && it.source != Transaction.Source.SPLIT && it.id !in used &&
                it.timestamp >= date - DAY && it.timestamp <= date + 45 * DAY &&
                it.amountPaise <= remainingPaise + roundUpPaise(remainingPaise)
        }.sortedWith(
            compareByDescending<Transaction> { PayerClassifier.sameParty(it.merchant, payerName) }
                .thenBy { abs(it.amountPaise - remainingPaise) }
                .thenBy { abs(it.timestamp - date) }
        )

    /** The candidate whose amount is the typed [typedPaise] (to the paisa, or a friend's round-up of it), if any. */
    fun forTypedAmount(candidates: List<Transaction>, typedPaise: Long): Transaction? =
        candidates.filter { it.amountPaise >= typedPaise && it.amountPaise - typedPaise <= roundUpPaise(typedPaise) }
            .minByOrNull { it.amountPaise - typedPaise }

    /**
     * The payment a split I paid could be ("Paid with"): money out counted as spend, a week either side of [date],
     * not a split's own row and not already used by a split ([used]). With a total, only bills within a quarter of
     * it; the closest amount, then the closest day, first.
     */
    fun paidWith(txns: List<Transaction>, totalPaise: Long?, date: Long, used: Set<Long>, limit: Int = 6): List<Transaction> =
        txns.filter {
            it.type == TransactionType.DEBIT && it.flow == Flow.EXPENSE && it.source != Transaction.Source.SPLIT && it.id !in used &&
                abs(it.timestamp - date) <= 7 * DAY &&
                (totalPaise == null || abs(it.amountPaise - totalPaise) <= totalPaise / 4)
        }.sortedWith(
            compareBy<Transaction> { if (totalPaise == null) 0L else abs(it.amountPaise - totalPaise) }
                .thenBy { abs(it.timestamp - date) }
        ).take(limit)

    /** The payment to pick for the person without asking: within 2% of the total and 3 days of the date, the closest. */
    fun bestPaidWith(candidates: List<Transaction>, totalPaise: Long?, date: Long): Transaction? {
        if (totalPaise == null || totalPaise <= 0) return null
        return candidates.filter { abs(it.amountPaise - totalPaise) * 100 <= totalPaise * 2 && abs(it.timestamp - date) <= 3 * DAY }
            .minWithOrNull(compareBy<Transaction> { abs(it.amountPaise - totalPaise) }.thenBy { abs(it.timestamp - date) })
    }
}
