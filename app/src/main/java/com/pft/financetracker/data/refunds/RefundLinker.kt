package com.pft.financetracker.data.refunds

import com.pft.financetracker.data.local.RefundDao
import com.pft.financetracker.data.local.RefundLinkEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.refunds.RefundMatcher

/**
 * Applies [RefundMatcher] to the stored transactions. A pairing may turn an "income" credit into a refund and move it
 * into the purchase's category; both are remembered so [undo] can put them back. Rows a person edited keep their
 * values. Safe to run after every import: credits that already have a decision are skipped.
 */
class RefundLinker(private val txDao: TransactionDao, private val dao: RefundDao) {

    /** Returns how many new pairs were made. */
    suspend fun run(): Int {
        val all = txDao.getAll().map { it.toDomain() }
        val links = dao.getAll()
        val used = links.filter { it.status == APPLIED }.groupBy { it.debitTxId }.mapValues { (_, l) -> l.sumOf { it.amountPaise } }
        val matches = RefundMatcher.match(all, links.map { it.refundTxId }.toSet(), used)
        for (m in matches) {
            val credit = txDao.getById(m.refundId) ?: continue
            val newFlow = if (m.reclassify) Flow.REFUND.name else credit.flow
            val newCategory = if (credit.userEdited) credit.category else m.category.name
            val changed = newFlow != credit.flow || newCategory != credit.category
            if (changed) txDao.update(credit.copy(flow = newFlow, category = newCategory))
            dao.insert(
                RefundLinkEntity(
                    refundTxId = m.refundId, debitTxId = m.debitId, kind = m.kind.name, amountPaise = m.amountPaise, status = APPLIED,
                    prevFlow = if (changed) credit.flow else null, prevCategory = if (changed) credit.category else null,
                )
            )
        }
        return matches.size
    }

    /** Unpairs and restores the credit, unless a person has corrected it since. The credit is never paired again. */
    suspend fun undo(linkId: Long) {
        val link = dao.get(linkId) ?: return
        val credit = txDao.getById(link.refundTxId)
        if (credit != null && !credit.userEdited && (link.prevFlow != null || link.prevCategory != null)) {
            txDao.update(credit.copy(flow = link.prevFlow ?: credit.flow, category = link.prevCategory ?: credit.category))
        }
        dao.update(link.copy(status = REJECTED))
    }

    companion object {
        const val APPLIED = "APPLIED"
        const val REJECTED = "REJECTED"
    }
}
