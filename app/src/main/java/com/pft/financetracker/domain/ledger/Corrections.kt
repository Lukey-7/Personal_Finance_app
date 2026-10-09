package com.pft.financetracker.domain.ledger

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType

/** Which "counts as" choices fit money out and money in. A payment out is never income; money in is never spend. */
object FlowRules {
    private val out = listOf(Flow.EXPENSE, Flow.TRANSFER, Flow.INVESTMENT, Flow.CASH, Flow.SETTLEMENT)
    private val incoming = listOf(Flow.INCOME, Flow.REFUND, Flow.TRANSFER, Flow.INVESTMENT, Flow.SETTLEMENT)

    /** The choices the editor offers for [type], in the order it shows them. */
    fun options(type: TransactionType): List<Flow> = if (type == TransactionType.DEBIT) out else incoming

    fun default(type: TransactionType): Flow = if (type == TransactionType.DEBIT) Flow.EXPENSE else Flow.INCOME

    fun fits(type: TransactionType, flow: Flow): Boolean = flow in options(type)

    /** [flow] when it fits [type]; otherwise the usual one for that direction. */
    fun fit(type: TransactionType, flow: Flow): Flow = if (fits(type, flow)) flow else default(type)

    /** The row with a flow that fits its direction, so a credit is never saved as spend nor a debit as income. */
    fun normalise(t: Transaction): Transaction = if (fits(t.type, t.flow)) t else t.copy(flow = default(t.type))
}

/**
 * The rows to write when the chosen ones are moved to [category]: only those that change, marked as corrected by a
 * person so automatic rewrites leave them alone. Amount, flow and everything else stay as they are.
 */
fun recategorise(txns: List<Transaction>, ids: Set<Long>, category: Category): List<Transaction> =
    txns.filter { it.id in ids && it.category != category }.map { it.copy(category = category, userEdited = true) }
