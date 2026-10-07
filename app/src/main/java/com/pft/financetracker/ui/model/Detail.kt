package com.pft.financetracker.ui.model

import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.refunds.RefundMatch
import com.pft.financetracker.domain.refunds.RefundPair
import com.pft.financetracker.domain.tax.TaxSection
import com.pft.financetracker.ui.components.money
import com.pft.financetracker.ui.components.shortDate

/** Something a transaction is tied to, shown as a tappable row on its detail screen. */
data class DetailLink(
    val kind: Kind,
    val title: String,
    val label: String,
    /** The split id for [Kind.SPLIT], the other transaction for refunds; null when there is nowhere to go. */
    val targetId: Long?,
    /** The refund pairing, so "Not a refund" can undo it. */
    val pairId: Long? = null,
) {
    enum class Kind { SPLIT, REFUND, REVERSAL, BILL }
}

/** Everything the read-first transaction screen says, worked out from the row and what it is linked to. */
data class TransactionDetail(
    val transaction: Transaction,
    val amount: String,
    val tone: MoneyTone,
    val countsAs: String,
    val account: String?,
    val source: String,
    val hasSms: Boolean,
    val shareNote: String?,
    val links: List<DetailLink>,
    /** The tax line for money out; null for money in. */
    val tax: String?,
) {
    companion object {
        fun countsAs(f: Flow): String = when (f) {
            Flow.EXPENSE -> "Counted in your spend"
            Flow.CASH -> "Cash withdrawal: counted in spend unless turned off in Settings"
            Flow.INCOME -> "Counted as income"
            Flow.REFUND -> "Money back: lowers your spend"
            Flow.TRANSFER -> "Not spend, not income: money moved between your own accounts"
            Flow.INVESTMENT -> "Invested: shown apart, not spend"
            Flow.SETTLEMENT -> "A split being settled: not spend, not income"
        }

        fun sourceOf(s: Transaction.Source): String = when (s) {
            Transaction.Source.SMS -> "From an SMS"
            Transaction.Source.MANUAL -> "Added by you"
            Transaction.Source.SPLIT -> "Your share of a split"
            Transaction.Source.STATEMENT -> "From an imported statement"
        }

        fun of(
            t: Transaction,
            refundPairs: List<RefundPair>,
            others: Map<Long, Transaction>,
            splitId: Long?,
            billName: String?,
            taxSection: TaxSection?,
            taxSetByYou: Boolean,
        ): TransactionDetail {
            val links = buildList {
                if (splitId != null) add(DetailLink(DetailLink.Kind.SPLIT, "Split", "Open the split", splitId))
                refundPairs.forEach { p ->
                    val isRefundSide = p.refundId == t.id
                    val otherId = if (isRefundSide) p.debitId else p.refundId
                    val other = others[otherId]
                    val reversal = p.kind == RefundMatch.Kind.REVERSAL
                    val label = when {
                        other == null -> "Paired with a payment that is no longer here"
                        isRefundSide -> "${money(p.amountPaise)} back for ${other.merchant.ifBlank { "a purchase" }} on ${shortDate(other.timestamp)}"
                        else -> "${money(p.amountPaise)} of this came back on ${shortDate(other.timestamp)}"
                    }
                    add(DetailLink(if (reversal) DetailLink.Kind.REVERSAL else DetailLink.Kind.REFUND, if (reversal) "Reversed" else "Refund", label, other?.id, p.linkId))
                }
                if (billName != null) add(DetailLink(DetailLink.Kind.BILL, "Bill", "Pays $billName", null))
            }
            return TransactionDetail(
                transaction = t,
                amount = signOf(t) + money(t.amountPaise),
                tone = toneOf(t),
                countsAs = countsAs(t.flow),
                account = accountLabel(t),
                source = sourceOf(t.source),
                hasSms = t.source == Transaction.Source.SMS && t.smsHash != null,
                shareNote = t.originalAmountPaise?.takeIf { t.type == TransactionType.DEBIT }?.let { "The bank reported ${money(it)}; only your share counts" },
                links = links,
                tax = if (t.type != TransactionType.DEBIT) null
                else (taxSection?.let { "${it.code} · ${it.label}" } ?: "Not a deduction") + if (taxSetByYou) " (set by you)" else "",
            )
        }
    }
}
