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
import com.pft.financetracker.domain.ledger.SamePayment
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.RefExtractor
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

    /** The stored payments [SamePayment] looks through. */
    val stored: SamePayment.Stored = object : SamePayment.Stored {
        override suspend fun withRef(ref: String) = dao.findAllByRef(ref).map { it.toDomain() }
        override suspend fun similar(amountPaise: Long, from: Long, to: Long) = dao.findSimilar(amountPaise, from, to).map { it.toDomain() }
        override suspend fun sameAmount(amountPaise: Long, type: TransactionType, from: Long, to: Long) =
            dao.findSameAmount(amountPaise, type.name, from, to).map { it.toDomain() }
    }

    /** The stored row [candidate] repeats (see [SamePayment.find]), or null. */
    suspend fun findLikelyDuplicate(candidate: Transaction, windowMillis: Long = SamePayment.WINDOW_MILLIS, isClaimed: suspend (Long) -> Boolean = { false }): Transaction? =
        SamePayment.find(candidate, stored, windowMillis, isClaimed)?.row

    companion object {
        fun startOfDay(t: Long): Long = SamePayment.startOfDay(t)
    }

    /**
     * A message the person confirmed from the review queue. Runs the same duplicate check as an import, so approving a
     * bank alert whose UPI-app twin was already saved does not count the payment twice, and fills in the reference
     * and account the review screen does not ask for from [body]. Returns the id of the row now holding the payment:
     * the new one, or the stored one it matched (which only gains identifiers it lacked).
     */
    suspend fun insertReviewed(t: Transaction, body: String? = null, isClaimed: suspend (Long) -> Boolean = { false }): Long {
        val text = body?.let { com.pft.financetracker.domain.parser.SmsText.normalize(it) }
        val filled = t.copy(
            refNumber = t.refNumber ?: text?.let { RefExtractor.extract(it) },
            accountRef = t.accountRef ?: text?.let { com.pft.financetracker.domain.parser.AccountExtractor.extract(it) },
        )
        val existing = findLikelyDuplicate(filled, isClaimed = isClaimed)
        if (existing != null) {
            val merged = SamePayment.addIdentifiers(existing, filled)
            if (merged != existing) update(merged)
            return existing.id
        }
        return insert(filled)
    }

    /** A pair of stored transactions that look like the same payment recorded twice. */
    data class DuplicatePair(val keep: Transaction, val drop: Transaction) {
        val amountPaise: Long get() = drop.amountPaise
    }

    /** Stored SMS rows already counted twice (see [SamePayment.twinsIn]). Nothing is deleted here. */
    suspend fun findExistingDuplicates(windowMillis: Long = SamePayment.WINDOW_MILLIS): List<DuplicatePair> =
        SamePayment.twinsIn(dao.getAll().map { it.toDomain() }, windowMillis).map { DuplicatePair(it.keep, it.drop) }

    /** Delete the redundant row of each pair, keeping any detail it had that the survivor lacked. */
    suspend fun mergeDuplicates(pairs: List<DuplicatePair>, isLinked: suspend (Long) -> Boolean = { false }) {
        for (pair in pairs) {
            val (merged, drop) = SamePayment.combine(SamePayment.Twins(pair.keep, pair.drop), isLinked)
            val before = if (merged.id == pair.keep.id) pair.keep else pair.drop
            if (merged != before) dao.update(merged.toEntity())
            dao.delete(drop.toEntity())
        }
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
    suspend fun getByHash(hash: String) = dao.getByHash(hash)
    suspend fun log(e: SmsLogEntity) = dao.upsert(e)
    suspend fun updateOutcome(hash: String, outcome: String, reason: String, transactionId: Long?) = dao.updateOutcome(hash, outcome, reason, transactionId)
    suspend fun findTombstone(amountPaise: Long, at: Long): SmsLogEntity? =
        TransactionRepository.startOfDay(at).let { day -> dao.findTombstone(amountPaise, day, day + 86_400_000L) }
    suspend fun pointsAt(transactionId: Long) = dao.pointsAt(transactionId)
    suspend fun prune(retainDays: Int = 365) = dao.pruneBefore(System.currentTimeMillis() - retainDays * 86_400_000L)
    suspend fun clearAll() = dao.clear()
}

class SplitRepository(private val dao: SplitDao) {
    val all: Flow<List<Split>> = combine(dao.observeSplits(), dao.observePeople(), dao.observeShares()) { s, p, sh -> assembleSplits(s, p, sh) }
    val recentPeople: Flow<List<String>> = dao.observeRecentPeople().map { list -> list.map { it.name } }
    val links: Flow<List<com.pft.financetracker.data.local.SplitLinkEntity>> = dao.observeLinks()

    suspend fun itemsFor(splitId: Long): List<BillItem> = dao.itemsFor(splitId).map { it.toDomain() }

    suspend fun save(split: Split, items: List<BillItem>): Long {
        val id = dao.insertFull(
            SplitEntity(
                0, split.title, split.totalPaise, split.date, split.mode.name, split.payerIndex, split.linkedTransactionId, split.note, split.createdAt,
                source = split.source.name, status = split.status.name, confidence = split.confidence,
                reasons = split.reasons.joinToString("\n").ifBlank { null }, kind = split.kind.name,
            ),
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
    suspend fun clearAll() { dao.clearLinks(); dao.clear(); dao.clearRecentPeople(); dao.clearDecisions() }
}
