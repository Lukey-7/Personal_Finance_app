package com.pft.financetracker.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsHash"], unique = true), Index(value = ["timestamp"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val amount: Double,
    val type: String,
    val merchant: String,
    val category: String,
    val timestamp: Long,
    val bankName: String?,
    val accountRef: String?,
    val source: String,
    val note: String?,
    /** SHA-256 of sender+body+date, used only for de-duplication. The raw SMS body is NOT stored here. */
    val smsHash: String?,
    val confidence: Int,
    val needsReview: Boolean,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * Messages the parser could not confidently parse. Kept in the app-private database so the user can
 * review and enter them manually. Rows are deleted as soon as they are resolved or dismissed.
 */
@Entity(tableName = "review_queue", indices = [Index(value = ["smsHash"], unique = true)])
data class ReviewItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val body: String,
    val receivedAt: Long,
    val smsHash: String,
    val guessedAmount: Double?,
    val guessedType: String?,
    val reason: String,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimit: Double,
)
