package com.pft.financetracker.data.repository

import com.pft.financetracker.data.local.BudgetDao
import com.pft.financetracker.data.local.RecentPersonEntity
import com.pft.financetracker.data.local.ReviewDao
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.local.SmsLogDao
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.local.SplitDao
import com.pft.financetracker.data.local.SplitEntity
import com.pft.financetracker.data.local.SplitPersonEntity
import com.pft.financetracker.data.local.SplitShareEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.assembleSplits
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.BillItem
import com.pft.financetracker.domain.split.Split
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

class TransactionRepository(
    private val dao: TransactionDao,
    private val reviewDao: ReviewDao,
) {
    val all: Flow<List<Transaction>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    val reviewQueue: Flow<List<ReviewItemEntity>> = reviewDao.observeAll()
    val reviewCount: Flow<Int> = reviewDao.observeCount()

    suspend fun getAll(): List<Transaction> = dao.getAll().map { it.toDomain() }
    suspend fun getBetween(from: Long, to: Long): List<Transaction> = dao.getBetween(from, to).map { it.toDomain() }
    suspend fun getById(id: Long): Transaction? = dao.getById(id)?.toDomain()

    /** Returns the new row id, or -1 if a duplicate (same smsHash) already existed. */
    suspend fun insert(t: Transaction): Long = dao.insert(t.toEntity())
    suspend fun update(t: Transaction) = dao.update(t.toEntity())
    suspend fun delete(t: Transaction) = dao.delete(t.toEntity())
    suspend fun hashSeen(hash: String): Boolean = dao.hashExists(hash) || reviewDao.hashExists(hash)

    suspend fun findByRef(ref: String, type: TransactionType): Transaction? = dao.findByRef(ref, type.name)?.toDomain()

    /**
     * The "one payment, two SMS" check. A bank alert and a UPI-app alert for the same payment share the
     * amount and land within minutes. We call it a duplicate when the reference matches, or when the
     * amount + direction match inside the window AND the two records are compatible (different bank/app
     * reporting it, or one of them has no real merchant). Two genuine payments of the same amount to two
     * different merchants from the same bank within ten minutes are kept.
     */
    suspend fun findLikelyDuplicate(candidate: Transaction, windowMillis: Long = 10 * 60_000L): Transaction? {
        candidate.refNumber?.let { ref -> findByRef(ref, candidate.type)?.let { return it } }
        val similar = dao.findSimilar(candidate.amountPaise, candidate.type.name, candidate.timestamp - windowMillis, candidate.timestamp + windowMillis)
        return similar.map { it.toDomain() }.firstOrNull { existing ->
            if (existing.refNumber != null && candidate.refNumber != null && existing.refNumber != candidate.refNumber) return@firstOrNull false
            val sameMerchant = InsightsEngine.normalizeMerchant(existing.merchant) == InsightsEngine.normalizeMerchant(candidate.merchant)
            val differentReporter = existing.bankName != candidate.bankName
            val genericMerchant = isGeneric(existing.merchant) || isGeneric(candidate.merchant)
            sameMerchant || differentReporter || genericMerchant
        }
    }

    private fun isGeneric(m: String) = m.startsWith("Payment") || m.startsWith("Credit") || m.length < 3

    /** Prefer the record with more detail (merchant, account, ref). */
    fun richer(a: Transaction, b: Transaction): Transaction {
        fun score(t: Transaction) = (if (!isGeneric(t.merchant)) 4 else 0) + (if (t.accountRef != null) 2 else 0) + (if (t.refNumber != null) 1 else 0) + (if (t.bankName != null) 1 else 0)
        return if (score(b) > score(a)) b else a
    }

    suspend fun enqueueReview(item: ReviewItemEntity): Boolean = reviewDao.insert(item) != -1L
    suspend fun getReview(id: Long): ReviewItemEntity? = reviewDao.getById(id)
    suspend fun resolveReview(id: Long) = reviewDao.deleteById(id)

    suspend fun clearAll() {
        dao.clear()
        reviewDao.clear()
    }
}

class BudgetRepository(private val dao: BudgetDao) {
    val all: Flow<List<Budget>> = dao.observeAll().map { list -> list.map { it.toDomain() } }
    suspend fun getAll(): List<Budget> = dao.getAll().map { it.toDomain() }
    suspend fun set(category: Category, limitPaise: Long) {
        if (limitPaise <= 0) dao.delete(category.name) else dao.upsert(Budget(category, limitPaise).toEntity())
    }
    suspend fun clearAll() = dao.clear()
}

class SmsLogRepository(private val dao: SmsLogDao) {
    val recent: Flow<List<SmsLogEntity>> = dao.observeRecent()
    val counts: Flow<Map<String, Int>> = dao.observeCounts().map { list -> list.associate { it.outcome to it.n } }
    suspend fun getById(id: Long) = dao.getById(id)
    suspend fun log(e: SmsLogEntity) = dao.upsert(e)
    suspend fun updateOutcome(hash: String, outcome: String, reason: String, transactionId: Long?) = dao.updateOutcome(hash, outcome, reason, transactionId)
    suspend fun prune(retainDays: Int = 365) = dao.pruneBefore(System.currentTimeMillis() - retainDays * 86_400_000L)
    suspend fun clearAll() = dao.clear()
}

class SplitRepository(private val dao: SplitDao) {
    val all: Flow<List<Split>> = combine(dao.observeSplits(), dao.observePeople(), dao.observeShares()) { s, p, sh -> assembleSplits(s, p, sh) }
    val recentPeople: Flow<List<String>> = dao.observeRecentPeople().map { list -> list.map { it.name } }

    suspend fun itemsFor(splitId: Long): List<BillItem> = dao.itemsFor(splitId).map { it.toDomain() }

    suspend fun save(split: Split, items: List<BillItem>): Long {
        val id = dao.insertFull(
            SplitEntity(0, split.title, split.totalPaise, split.date, split.mode.name, split.payerIndex, split.linkedTransactionId, split.note, split.createdAt),
            split.people.mapIndexed { i, p -> SplitPersonEntity(0, 0, i, p.name, p.isMe) },
            split.shares.map { SplitShareEntity(0, 0, it.personIndex, it.amountPaise, it.settledPaise) },
            items.map { it.toEntity(0) },
        )
        val now = System.currentTimeMillis()
        dao.touchPeople(split.people.filter { !it.isMe }.map { RecentPersonEntity(it.name, now) })
        return id
    }

    suspend fun settle(shareId: Long, settledPaise: Long) = dao.settle(shareId, settledPaise)
    suspend fun link(splitId: Long, txId: Long?) = dao.link(splitId, txId)
    suspend fun delete(splitId: Long) = dao.deleteSplit(splitId)
    suspend fun clearAll() { dao.clear(); dao.clearRecentPeople() }
}
