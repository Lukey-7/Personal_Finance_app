package com.pft.financetracker.domain.refunds

/** An applied refund/reversal pairing, as the screens need it. */
data class RefundPair(val linkId: Long, val refundId: Long, val debitId: Long, val kind: RefundMatch.Kind, val amountPaise: Long)

/**
 * What the lists show for paired rows: "Reversed" on both sides of a payment that came straight back (hidden unless
 * the person asks to see them, since it never really happened), "Refunded" on a purchase money came back for.
 */
class RefundBadges private constructor(private val pairs: List<RefundPair>) {
    private val byTx: Map<Long, List<RefundPair>> = pairs.flatMap { listOf(it.refundId to it, it.debitId to it) }
        .groupBy({ it.first }, { it.second })

    val hiddenByDefault: Set<Long> = pairs.filter { it.kind == RefundMatch.Kind.REVERSAL }.flatMap { listOf(it.refundId, it.debitId) }.toSet()

    fun pairsOf(txId: Long): List<RefundPair> = byTx[txId].orEmpty().sortedBy { it.linkId }

    fun refundedPaise(debitId: Long): Long = pairs.filter { it.debitId == debitId }.sumOf { it.amountPaise }

    fun tag(txId: Long): String? = when {
        txId in hiddenByDefault -> "Reversed"
        pairs.any { it.debitId == txId } -> "Refunded"
        else -> null
    }

    companion object {
        val EMPTY = RefundBadges(emptyList())
        fun of(pairs: List<RefundPair>) = RefundBadges(pairs)
    }
}
