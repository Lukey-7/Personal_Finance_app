package com.pft.financetracker.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
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
