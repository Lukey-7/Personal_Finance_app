package com.pft.financetracker.data.recurring

import com.pft.financetracker.data.local.RecurringDao
import com.pft.financetracker.data.local.RecurringDecisionEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.recurring.RecurringBook
import com.pft.financetracker.domain.recurring.RecurringDecision
import com.pft.financetracker.domain.recurring.RecurringDetector
import com.pft.financetracker.domain.recurring.RecurringStatus

/** Subscriptions: detected from the stored transactions on demand, merged with the person's saved decisions. */
class RecurringService(private val txDao: TransactionDao, private val dao: RecurringDao) {

    suspend fun book(now: Long = System.currentTimeMillis()): RecurringBook =
        bookOf(txDao.getAll().map { it.toDomain() }, dao.getAll(), now)

    fun bookOf(txns: List<Transaction>, decisions: List<RecurringDecisionEntity>, now: Long = System.currentTimeMillis()): RecurringBook =
        RecurringBook.of(
            RecurringDetector.detect(txns, now),
            decisions.mapNotNull { d -> RecurringStatus.entries.firstOrNull { it.name == d.status }?.let { RecurringDecision(d.key, it, d.decidedAt) } },
        )

    /** null clears the decision, so the charge is treated as freshly detected again. */
    suspend fun decide(key: String, status: RecurringStatus?) {
        if (status == null) dao.delete(key) else dao.upsert(RecurringDecisionEntity(key, status.name))
    }
}
