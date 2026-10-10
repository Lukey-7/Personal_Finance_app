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
        val links = dao.getAll()
        reassert(links)
        val all = txDao.getAll().map { it.toDomain() }
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

    /**
     * A rescan writes the parser's flow and category back onto rows nobody edited, which would quietly turn a paired
     * refund back into income. Paired credits stay refunds, in the purchase's category when pairing set it.
     */
    private suspend fun reassert(links: List<RefundLinkEntity>) {
        for (l in links) {
            if (l.status != APPLIED || (l.prevFlow == null && l.prevCategory == null)) continue
            val credit = txDao.getById(l.refundTxId)?.takeIf { !it.userEdited } ?: continue
            val category = if (l.prevCategory != null) txDao.getById(l.debitTxId)?.category ?: credit.category else credit.category
            if (credit.flow != Flow.REFUND.name || credit.category != category) txDao.update(credit.copy(flow = Flow.REFUND.name, category = category))
        }
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

    /**
     * Before the purchase [debitTxId] is deleted: each credit paired with it goes back to what it was before pairing
     * (income, its own category), unless a person has corrected it since, so it stops lowering spend. The links
     * themselves go with the purchase.
     */
    suspend fun unlinkForDeletedPurchase(debitTxId: Long) {
        for (link in dao.forDebit(debitTxId)) {
            if (link.status != APPLIED || (link.prevFlow == null && link.prevCategory == null)) continue
            val credit = txDao.getById(link.refundTxId) ?: continue
            if (credit.userEdited) continue
            txDao.update(credit.copy(flow = link.prevFlow ?: credit.flow, category = link.prevCategory ?: credit.category))
        }
    }

    /**
     * Two stored rows were one payment and [fromId] is merged into [toId]: its pairings move across, keeping what each
     * refund was before pairing, so deleting the survivor later still gives the refund back.
     */
    suspend fun moveLinks(fromId: Long, toId: Long) {
        dao.moveDebit(fromId, toId)
        dao.moveRefund(fromId, toId)
    }

    companion object {
        const val APPLIED = "APPLIED"
        const val REJECTED = "REJECTED"
    }
}
