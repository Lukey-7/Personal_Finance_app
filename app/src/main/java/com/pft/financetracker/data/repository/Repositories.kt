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
    suspend fun findLikelyDuplicate(candidate: Transaction, windowMillis: Long = 10 * 60_000L, isClaimed: suspend (Long) -> Boolean = { false }): Transaction? {
        candidate.refNumber?.let { ref -> findByRef(ref, candidate.type)?.let { return it } }

        // Search the whole calendar day as well as the window: a transaction imported by v1.0.0 sits at
        // midnight (its parser dropped the time of day), so the same message re-parsed now lands hours away.
        val dayStart = startOfDay(candidate.timestamp)
        val dayEnd = dayStart + 86_400_000L
        val from = minOf(dayStart, candidate.timestamp - windowMillis)
        val to = maxOf(dayEnd, candidate.timestamp + windowMillis)

        // Same direction first, so a same-day refund never takes a legacy row its own debit should have matched.
        return dao.findSimilar(candidate.amountPaise, from, to).map { it.toDomain() }.sortedBy { it.type != candidate.type }.firstOrNull { existing ->
            // Two references that both exist and disagree mean two genuinely different payments.
            if (existing.refNumber != null && candidate.refNumber != null && existing.refNumber != candidate.refNumber) return@firstOrNull false
            val sameMerchant = InsightsEngine.normalizeMerchant(existing.merchant) == InsightsEngine.normalizeMerchant(candidate.merchant)
            val genericMerchant = isGeneric(existing.merchant) || isGeneric(candidate.merchant)
            when {
                // A v1.0.0 row: exactly midnight on this day, possibly with the direction the old parser guessed. Each
                // stands for one SMS, so once one has matched it (its own debit, scanned first), a same-day refund of
                // the same amount is a second payment and must not merge into it and flip it.
                existing.timestamp == startOfDay(existing.timestamp) && existing.timestamp in dayStart until dayEnd ->
                    !isClaimed(existing.id) && (sameMerchant || genericMerchant)
                // Minutes apart, same direction: a second sender reporting the same payment, or a generic alert.
                existing.type == candidate.type && kotlin.math.abs(existing.timestamp - candidate.timestamp) <= windowMillis ->
                    sameMerchant || existing.bankName != candidate.bankName || genericMerchant
                // Hours apart is two payments, even to the same merchant: two coffees are two coffees.
                else -> false
            }
        }
    }

    companion object {
        fun startOfDay(t: Long): Long = java.util.Calendar.getInstance().apply {
            timeInMillis = t
            set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    private fun isGeneric(m: String) = m.startsWith("Payment") || m.startsWith("Credit") || m.length < 3

    /** Prefer the record with more detail (merchant, account, ref). */
    fun richer(a: Transaction, b: Transaction): Transaction {
        fun score(t: Transaction) = (if (!isGeneric(t.merchant)) 4 else 0) + (if (t.accountRef != null) 2 else 0) + (if (t.refNumber != null) 1 else 0) + (if (t.bankName != null) 1 else 0)
        return if (score(b) > score(a)) b else a
    }

    /** A pair of stored transactions that look like the same payment recorded twice. */
    data class DuplicatePair(val keep: Transaction, val drop: Transaction) {
        val amountPaise: Long get() = drop.amountPaise
    }

    /**
     * Sweep already-stored transactions for the same payment counted twice. This is the retrospective
     * counterpart to [findLikelyDuplicate]: rows imported before the duplicate rules existed are still
     * sitting in the database, and no amount of re-importing removes them.
     *
     * Two rows pair up when the amount, direction and calendar day match and either their references
     * agree, a different bank/app reported each, or one carries no real merchant. References that both
     * exist and disagree mean two genuine payments, so those are never paired.
     */
    suspend fun findExistingDuplicates(): List<DuplicatePair> {
        val all = dao.getAll().map { it.toDomain() }.filter { it.source == Transaction.Source.SMS && !it.needsReview }
        val pairs = mutableListOf<DuplicatePair>()
        val consumed = mutableSetOf<Long>()
        val byKey = all.groupBy { Triple(it.amountPaise, it.type, startOfDay(it.timestamp)) }
        for ((_, group) in byKey) {
            if (group.size < 2) continue
            val ordered = group.sortedBy { it.timestamp }
            for (i in ordered.indices) {
                val a = ordered[i]
                if (a.id in consumed) continue
                for (j in i + 1 until ordered.size) {
                    val b = ordered[j]
                    if (b.id in consumed) continue
                    if (a.refNumber != null && b.refNumber != null && a.refNumber != b.refNumber) continue
                    val sameMerchant = InsightsEngine.normalizeMerchant(a.merchant) == InsightsEngine.normalizeMerchant(b.merchant)
                    val differentReporter = a.bankName != b.bankName
                    val generic = isGeneric(a.merchant) || isGeneric(b.merchant)
                    val sameRef = a.refNumber != null && a.refNumber == b.refNumber
                    if (!(sameRef || sameMerchant || differentReporter || generic)) continue
                    val keep = richer(a, b)
                    val drop = if (keep === a) b else a
                    pairs += DuplicatePair(keep, drop)
                    consumed += drop.id
                    consumed += keep.id
                    break
                }
            }
        }
        return pairs.sortedByDescending { it.amountPaise }
    }

    /** Delete the redundant row of each pair, keeping any detail it had that the survivor lacked. */
    suspend fun mergeDuplicates(pairs: List<DuplicatePair>) {
        for (p in pairs) {
            val merged = p.keep.copy(
                merchant = if (isGeneric(p.keep.merchant) && !isGeneric(p.drop.merchant)) p.drop.merchant else p.keep.merchant,
                accountRef = p.keep.accountRef ?: p.drop.accountRef,
                refNumber = p.keep.refNumber ?: p.drop.refNumber,
                bankName = p.keep.bankName ?: p.drop.bankName,
                note = p.keep.note ?: p.drop.note,
            )
            if (merged != p.keep) dao.update(merged.toEntity())
            dao.delete(p.drop.toEntity())
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
