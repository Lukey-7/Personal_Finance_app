package com.pft.financetracker.domain.refunds

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType

/**
 * A credit paired with the purchase it gives money back for.
 * [kind] REVERSAL means the payment effectively never happened (same amount back within three days, typically a
 * failed UPI payment); such pairs are hidden from lists by default. [reclassify] means the credit was booked as
 * income and should become a refund. [category] is the purchase's category, so the refund reduces the right one.
 */
data class RefundMatch(
    val refundId: Long,
    val debitId: Long,
    val amountPaise: Long,
    val kind: Kind,
    val reclassify: Boolean,
    val category: Category,
) {
    enum class Kind { REFUND, REVERSAL }
}

/**
 * Pairs refunds with purchases, on the phone, with rules a person can check:
 *  1. the same bank/UPI reference number;
 *  2. the same merchant, within 60 days, never more than what is left of the purchase (an income credit only on the
 *     exact amount, so salary from a company you once paid is never mistaken for a refund);
 *  3. a refund with no usable merchant: the exact amount, same account, within three days, and only when exactly one
 *     purchase fits.
 * Money from a person is never a refund (split intelligence handles paybacks), and a row a person edited is not touched.
 */
object RefundMatcher {
    private const val DAY = 24L * 3600 * 1000
    private const val WINDOW = 60 * DAY
    private const val REVERSAL_WINDOW = 3 * DAY
    private val generic = setOf("reversal", "refund", "upi", "neft", "imps", "unknown", "credit", "cashback")

    /**
     * @param alreadyLinkedRefunds credits that already have a decision (applied or undone) and must not be matched again.
     * @param refundedSoFar how much of each purchase earlier links have already given back, by debit id.
     */
    fun match(all: List<Transaction>, alreadyLinkedRefunds: Set<Long>, refundedSoFar: Map<Long, Long>): List<RefundMatch> {
        val debits = all.filter { it.type == TransactionType.DEBIT && it.flow == Flow.EXPENSE && !it.needsReview }
        val used = refundedSoFar.toMutableMap()
        val out = mutableListOf<RefundMatch>()
        val credits = all.filter { it.type == TransactionType.CREDIT && it.id !in alreadyLinkedRefunds && !it.needsReview }
            .filter { it.counterpartyKind != CounterpartyKind.PERSON }
            .filter { it.flow == Flow.REFUND || (it.flow == Flow.INCOME && !it.userEdited) }
            .sortedBy { it.timestamp }

        for (c in credits) {
            val isIncome = c.flow == Flow.INCOME
            fun left(d: Transaction) = d.amountPaise - (used[d.id] ?: 0L)
            val open = debits.filter { it.timestamp <= c.timestamp && c.timestamp - it.timestamp <= WINDOW && left(it) >= c.amountPaise }
            val merchant = InsightsEngine.normalizeMerchant(c.merchant)

            val byRef = c.refNumber?.takeIf { it.isNotBlank() }?.let { ref -> open.filter { it.refNumber == ref }.maxByOrNull { it.timestamp } }
            val byMerchant = if (byRef != null || isGeneric(merchant)) null else
                open.filter { InsightsEngine.normalizeMerchant(it.merchant) == merchant }
                    .filter { !isIncome || it.amountPaise == c.amountPaise }
                    .sortedWith(compareByDescending<Transaction> { it.amountPaise == c.amountPaise }.thenByDescending { it.timestamp })
                    .firstOrNull()
            val byAmount = if (byRef != null || byMerchant != null || isIncome || !isGeneric(merchant)) null else
                open.filter { it.amountPaise == c.amountPaise && c.timestamp - it.timestamp <= REVERSAL_WINDOW }
                    .filter { it.accountRef == null || c.accountRef == null || it.accountRef == c.accountRef }
                    .singleOrNull()

            val d = byRef ?: byMerchant ?: byAmount ?: continue
            used[d.id] = (used[d.id] ?: 0L) + c.amountPaise
            val kind = if (c.amountPaise == d.amountPaise && c.timestamp - d.timestamp <= REVERSAL_WINDOW) RefundMatch.Kind.REVERSAL else RefundMatch.Kind.REFUND
            out += RefundMatch(c.id, d.id, c.amountPaise, kind, reclassify = isIncome, category = d.category)
        }
        return out
    }

    private fun isGeneric(normalized: String) = normalized.length < 3 || normalized in generic
}
