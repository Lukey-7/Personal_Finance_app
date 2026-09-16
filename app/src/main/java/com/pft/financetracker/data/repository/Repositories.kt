package com.pft.financetracker.data.repository

import com.pft.financetracker.data.local.BudgetDao
import com.pft.financetracker.data.local.ReviewDao
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
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

    /** Returns true if inserted, false if a duplicate (same smsHash) already existed. */
    suspend fun insert(t: Transaction): Boolean = dao.insert(t.toEntity()) != -1L
    suspend fun update(t: Transaction) = dao.update(t.toEntity())
    suspend fun delete(t: Transaction) = dao.delete(t.toEntity())
    suspend fun hashSeen(hash: String): Boolean = dao.hashExists(hash) || reviewDao.hashExists(hash)

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
    suspend fun set(category: Category, limit: Double) {
        if (limit <= 0) dao.delete(category.name) else dao.upsert(Budget(category, limit).toEntity())
    }
    suspend fun clearAll() = dao.clear()
}
