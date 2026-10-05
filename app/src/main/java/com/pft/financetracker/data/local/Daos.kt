package com.pft.financetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE timestamp >= :from AND timestamp < :to ORDER BY timestamp DESC")
    suspend fun getBetween(from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions ORDER BY timestamp DESC")
    suspend fun getAll(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: Long): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: TransactionEntity): Long

    @Update
    suspend fun update(entity: TransactionEntity)

    @Delete
    suspend fun delete(entity: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun clear()

    @Query("SELECT EXISTS(SELECT 1 FROM transactions WHERE smsHash = :hash)")
    suspend fun hashExists(hash: String): Boolean

    @Query("SELECT * FROM transactions WHERE refNumber = :ref AND type = :type LIMIT 1")
    suspend fun findByRef(ref: String, type: String): TransactionEntity?

    /**
     * Candidates for the "same payment, two SMS" check: same amount (or the bank amount a split shrank) within a
     * time window. Direction is filtered by the caller, because a v1.0.0 row may carry the wrong one.
     */
    @Query("SELECT * FROM transactions WHERE (amountPaise = :amountPaise OR originalAmountPaise = :amountPaise) AND timestamp BETWEEN :from AND :to AND source IN ('SMS', 'STATEMENT')")
    suspend fun findSimilar(amountPaise: Long, from: Long, to: Long): List<TransactionEntity>

    /** Any stored row (SMS, statement, manual) with this bank amount in [from, to]: the duplicate check for imports. */
    @Query("SELECT * FROM transactions WHERE (amountPaise = :amountPaise OR originalAmountPaise = :amountPaise) AND type = :type AND timestamp BETWEEN :from AND :to")
    suspend fun findSameAmount(amountPaise: Long, type: String, from: Long, to: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE refNumber = :ref LIMIT 1")
    suspend fun findAnyByRef(ref: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE refNumber = :ref")
    suspend fun findAllByRef(ref: String): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE smsHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE importBatchId = :batchId")
    suspend fun getByBatch(batchId: Long): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<Long>): List<TransactionEntity>
}

@Dao
interface ReviewDao {
    @Query("SELECT * FROM review_queue ORDER BY receivedAt DESC")
    fun observeAll(): Flow<List<ReviewItemEntity>>

    @Query("SELECT COUNT(*) FROM review_queue")
    fun observeCount(): Flow<Int>

    @Query("SELECT * FROM review_queue WHERE id = :id")
    suspend fun getById(id: Long): ReviewItemEntity?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(entity: ReviewItemEntity): Long

    @Query("DELETE FROM review_queue WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM review_queue")
    suspend fun clear()

    @Query("SELECT EXISTS(SELECT 1 FROM review_queue WHERE smsHash = :hash)")
    suspend fun hashExists(hash: String): Boolean
}

@Dao
interface BudgetDao {
    @Query("SELECT * FROM budgets")
    fun observeAll(): Flow<List<BudgetEntity>>

    @Query("SELECT * FROM budgets")
    suspend fun getAll(): List<BudgetEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BudgetEntity)

    @Query("DELETE FROM budgets WHERE category = :category")
    suspend fun delete(category: String)

    @Query("DELETE FROM budgets")
    suspend fun clear()
}

@Dao
interface SmsLogDao {
    @Query("SELECT * FROM sms_log ORDER BY receivedAt DESC LIMIT 2000")
    fun observeRecent(): Flow<List<SmsLogEntity>>

    @Query("SELECT * FROM sms_log WHERE id = :id")
    suspend fun getById(id: Long): SmsLogEntity?

    @Query("SELECT * FROM sms_log WHERE smsHash = :hash LIMIT 1")
    suspend fun getByHash(hash: String): SmsLogEntity?

    @Query("SELECT outcome, COUNT(*) AS n FROM sms_log GROUP BY outcome")
    fun observeCounts(): Flow<List<OutcomeCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SmsLogEntity): Long

    /** Returns the number of rows changed: 0 means this SMS has no log row (a v1.0.0 import). */
    @Query("UPDATE sms_log SET outcome = :outcome, reason = :reason, transactionId = :transactionId WHERE smsHash = :hash")
    suspend fun updateOutcome(hash: String, outcome: String, reason: String, transactionId: Long?): Int

    /** An unused record of a deleted v1.0.0 row with this amount in [from, to). */
    @Query("SELECT * FROM sms_log WHERE smsHash LIKE 'deleted:%' AND reason = 'deleted_by_user' AND amountPaise = :amountPaise AND receivedAt >= :from AND receivedAt < :to ORDER BY receivedAt LIMIT 1")
    suspend fun findTombstone(amountPaise: Long, from: Long, to: Long): SmsLogEntity?

    /** Whether some SMS already matched this transaction (saved it, or was merged into it). */
    @Query("SELECT EXISTS(SELECT 1 FROM sms_log WHERE transactionId = :transactionId)")
    suspend fun pointsAt(transactionId: Long): Boolean

    /** Old rows go, except the user's own decisions (deleted, dismissed), which a rescan must keep honouring. */
    @Query("DELETE FROM sms_log WHERE receivedAt < :before AND reason NOT IN ('deleted_by_user', 'dismissed_by_user')")
    suspend fun pruneBefore(before: Long)

    @Query("DELETE FROM sms_log")
    suspend fun clear()
}

data class OutcomeCount(val outcome: String, val n: Int)

@Dao
interface SplitDao {
    @Query("SELECT * FROM splits ORDER BY date DESC")
    fun observeSplits(): Flow<List<SplitEntity>>

    @Query("SELECT * FROM split_people ORDER BY splitId, personIndex")
    fun observePeople(): Flow<List<SplitPersonEntity>>

    @Query("SELECT * FROM split_shares ORDER BY splitId, personIndex")
    fun observeShares(): Flow<List<SplitShareEntity>>

    @Query("SELECT * FROM split_items WHERE splitId = :splitId")
    suspend fun itemsFor(splitId: Long): List<SplitItemEntity>

    @Insert suspend fun insertSplit(e: SplitEntity): Long
    @Insert suspend fun insertPeople(e: List<SplitPersonEntity>)
    @Insert suspend fun insertShares(e: List<SplitShareEntity>)
    @Insert suspend fun insertItems(e: List<SplitItemEntity>)

    @Query("UPDATE split_shares SET settledPaise = :settledPaise WHERE id = :shareId")
    suspend fun settle(shareId: Long, settledPaise: Long)

    @Query("SELECT * FROM split_shares WHERE id = :id")
    suspend fun share(id: Long): SplitShareEntity?

    @Query("DELETE FROM split_links WHERE id = :id")
    suspend fun deleteLink(id: Long)

    @Update suspend fun updateLink(e: SplitLinkEntity)

    @Update suspend fun updateShare(e: SplitShareEntity)

    @Query("UPDATE splits SET linkedTransactionId = :txId WHERE id = :splitId")
    suspend fun link(splitId: Long, txId: Long?)

    @Query("DELETE FROM splits WHERE id = :id")
    suspend fun deleteSplit(id: Long)

    @Query("DELETE FROM splits")
    suspend fun clear()

    @Transaction
    suspend fun insertFull(split: SplitEntity, people: List<SplitPersonEntity>, shares: List<SplitShareEntity>, items: List<SplitItemEntity>): Long {
        val id = insertSplit(split)
        insertPeople(people.map { it.copy(splitId = id) })
        insertShares(shares.map { it.copy(splitId = id) })
        insertItems(items.map { it.copy(splitId = id) })
        return id
    }

    @Query("SELECT * FROM recent_people ORDER BY lastUsedAt DESC LIMIT 30")
    fun observeRecentPeople(): Flow<List<RecentPersonEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun touchPeople(e: List<RecentPersonEntity>)

    @Query("DELETE FROM recent_people")
    suspend fun clearRecentPeople()

    // ---- Split intelligence ----
    @Query("SELECT * FROM splits WHERE id = :id")
    suspend fun getSplit(id: Long): SplitEntity?

    @Query("SELECT * FROM splits WHERE source != 'MANUAL'")
    suspend fun autoSplits(): List<SplitEntity>

    @Query("SELECT * FROM splits")
    suspend fun allSplits(): List<SplitEntity>

    @Query("SELECT * FROM split_people WHERE splitId = :splitId ORDER BY personIndex")
    suspend fun peopleFor(splitId: Long): List<SplitPersonEntity>

    @Query("SELECT * FROM split_shares WHERE splitId = :splitId ORDER BY personIndex")
    suspend fun sharesFor(splitId: Long): List<SplitShareEntity>

    @Query("SELECT * FROM split_links")
    fun observeLinks(): Flow<List<SplitLinkEntity>>

    @Query("SELECT * FROM split_links")
    suspend fun allLinks(): List<SplitLinkEntity>

    @Query("SELECT * FROM split_links WHERE splitId = :splitId")
    suspend fun linksFor(splitId: Long): List<SplitLinkEntity>

    @Query("SELECT * FROM split_links WHERE transactionId = :transactionId")
    suspend fun linksForTransaction(transactionId: Long): List<SplitLinkEntity>

    @Insert suspend fun insertLinks(e: List<SplitLinkEntity>)

    @Query("DELETE FROM split_links WHERE splitId = :splitId")
    suspend fun deleteLinks(splitId: Long)

    @Update suspend fun updateSplit(e: SplitEntity)

    @Query("DELETE FROM split_shares WHERE splitId = :splitId")
    suspend fun deleteShares(splitId: Long)

    @Query("DELETE FROM split_people WHERE splitId = :splitId")
    suspend fun deletePeople(splitId: Long)

    @Query("SELECT * FROM split_decisions")
    suspend fun decisions(): List<SplitDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertDecision(e: SplitDecisionEntity)

    @Query("DELETE FROM split_links")
    suspend fun clearLinks()

    @Query("DELETE FROM split_decisions")
    suspend fun clearDecisions()
}

@Dao
interface RefundDao {
    @Query("SELECT * FROM refund_links WHERE status = 'APPLIED'")
    fun observeApplied(): Flow<List<RefundLinkEntity>>

    @Query("SELECT * FROM refund_links WHERE status = 'APPLIED'")
    suspend fun getApplied(): List<RefundLinkEntity>

    @Query("SELECT * FROM refund_links")
    suspend fun getAll(): List<RefundLinkEntity>

    @Query("SELECT * FROM refund_links WHERE id = :id")
    suspend fun get(id: Long): RefundLinkEntity?

    @Insert suspend fun insert(e: RefundLinkEntity): Long

    @Update suspend fun update(e: RefundLinkEntity)

    @Query("DELETE FROM refund_links")
    suspend fun clear()
}

@Dao
interface TemplateDao {
    @Query("SELECT * FROM parser_templates ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<ParserTemplateEntity>>

    @Query("SELECT * FROM parser_templates ORDER BY createdAt DESC")
    suspend fun getAll(): List<ParserTemplateEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun insert(e: ParserTemplateEntity): Long

    @Query("DELETE FROM parser_templates WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface RecurringDao {
    @Query("SELECT * FROM recurring_decisions")
    fun observeAll(): Flow<List<RecurringDecisionEntity>>

    @Query("SELECT * FROM recurring_decisions")
    suspend fun getAll(): List<RecurringDecisionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: RecurringDecisionEntity)

    @Query("DELETE FROM recurring_decisions WHERE `key` = :key")
    suspend fun delete(key: String)
}

@Dao
interface BillDao {
    @Query("SELECT * FROM bills ORDER BY name")
    fun observeAll(): Flow<List<BillEntity>>

    @Query("SELECT * FROM bills ORDER BY name")
    suspend fun getAll(): List<BillEntity>

    @Query("SELECT * FROM bills WHERE cardLast4 = :last4 LIMIT 1")
    suspend fun byCard(last4: String): BillEntity?

    @Insert suspend fun insert(e: BillEntity): Long
    @Update suspend fun update(e: BillEntity)

    @Query("DELETE FROM bills WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("SELECT * FROM bill_marks")
    fun observeMarks(): Flow<List<BillMarkEntity>>

    @Query("SELECT * FROM bill_marks")
    suspend fun allMarks(): List<BillMarkEntity>

    @Query("SELECT * FROM bill_marks WHERE billId = :billId")
    suspend fun marksFor(billId: Long): List<BillMarkEntity>

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun mark(e: BillMarkEntity)

    @Query("DELETE FROM bill_marks WHERE billId = :billId AND dueDay = :dueDay")
    suspend fun unmark(billId: Long, dueDay: Long)
}

@Dao
interface CardDao {
    @Query("SELECT * FROM cards ORDER BY name")
    fun observeAll(): Flow<List<CardEntity>>

    @Query("SELECT * FROM cards ORDER BY name")
    suspend fun getAll(): List<CardEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: CardEntity): Long

    @Query("DELETE FROM cards WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface GoalDao {
    @Query("SELECT * FROM goals ORDER BY id")
    fun observeAll(): Flow<List<GoalEntity>>

    @Query("SELECT * FROM goals ORDER BY id")
    suspend fun getAll(): List<GoalEntity>

    @Query("SELECT * FROM goal_contributions ORDER BY at")
    fun observeContributions(): Flow<List<GoalContributionEntity>>

    @Query("SELECT * FROM goal_contributions ORDER BY at")
    suspend fun allContributions(): List<GoalContributionEntity>

    @Insert suspend fun insert(e: GoalEntity): Long
    @Update suspend fun update(e: GoalEntity)
    @Insert suspend fun contribute(e: GoalContributionEntity): Long

    @Query("DELETE FROM goals WHERE id = :id")
    suspend fun delete(id: Long)
}

@Dao
interface TaxDao {
    @Query("SELECT * FROM tax_tags")
    fun observeAll(): Flow<List<TaxTagEntity>>

    @Query("SELECT * FROM tax_tags")
    suspend fun getAll(): List<TaxTagEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsert(e: TaxTagEntity)

    @Query("DELETE FROM tax_tags WHERE transactionId = :txId")
    suspend fun delete(txId: Long)
}

@Dao
interface NetWorthDao {
    @Query("SELECT * FROM assets ORDER BY liability, name")
    fun observeAssets(): Flow<List<AssetEntity>>
    @Query("SELECT * FROM assets ORDER BY liability, name")
    suspend fun getAssets(): List<AssetEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertAsset(e: AssetEntity): Long
    @Query("DELETE FROM assets WHERE id = :id")
    suspend fun deleteAsset(id: Long)

    @Query("SELECT * FROM account_balances")
    fun observeBalances(): Flow<List<AccountBalanceEntity>>
    @Query("SELECT * FROM account_balances")
    suspend fun getBalances(): List<AccountBalanceEntity>
    @Query("SELECT * FROM account_balances WHERE accountRef = :ref")
    suspend fun getBalance(ref: String): AccountBalanceEntity?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertBalance(e: AccountBalanceEntity)

    @Query("SELECT * FROM holdings ORDER BY valuePaise DESC")
    fun observeHoldings(): Flow<List<HoldingEntity>>
    @Query("SELECT * FROM holdings ORDER BY valuePaise DESC")
    suspend fun getHoldings(): List<HoldingEntity>
    @Query("DELETE FROM holdings")
    suspend fun clearHoldings()
    @Insert suspend fun insertHoldings(e: List<HoldingEntity>)

    @Query("SELECT * FROM networth_snapshots ORDER BY month")
    fun observeSnapshots(): Flow<List<NetWorthSnapshotEntity>>
    @Query("SELECT * FROM networth_snapshots ORDER BY month")
    suspend fun getSnapshots(): List<NetWorthSnapshotEntity>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun upsertSnapshot(e: NetWorthSnapshotEntity)
}

@Dao
interface ImportDao {
    @Query("SELECT * FROM import_batches ORDER BY importedAt DESC")
    fun observeBatches(): Flow<List<ImportBatchEntity>>

    @Insert suspend fun insert(e: ImportBatchEntity): Long

    @Update suspend fun update(e: ImportBatchEntity)

    @Query("SELECT * FROM import_batches WHERE id = :id")
    suspend fun get(id: Long): ImportBatchEntity?

    @Query("DELETE FROM import_batches WHERE id = :id")
    suspend fun delete(id: Long)

    @Query("DELETE FROM import_batches")
    suspend fun clear()

    @Insert suspend fun insertMatches(e: List<ImportMatchEntity>)

    @Query("SELECT * FROM import_matches WHERE transactionId = :transactionId")
    suspend fun matchesFor(transactionId: Long): List<ImportMatchEntity>

    @Query("DELETE FROM import_matches WHERE id = :id")
    suspend fun deleteMatch(id: Long)

    @Query("DELETE FROM import_matches WHERE batchId = :batchId")
    suspend fun deleteMatchesForBatch(batchId: Long)

    @Query("DELETE FROM import_matches")
    suspend fun clearMatches()
}
