package com.pft.financetracker.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsHash"], unique = true), Index(value = ["timestamp"]), Index(value = ["refNumber"])]
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Whole paise. Rupee doubles are never stored. */
    val amountPaise: Long,
    val type: String,
    val merchant: String,
    val category: String,
    val timestamp: Long,
    val bankName: String?,
    val accountRef: String?,
    val source: String,
    /** See [com.pft.financetracker.domain.model.Flow]. Decides whether this counts as spend. */
    val flow: String,
    val note: String?,
    /** SHA-256 of sender+body+day, used only for de-duplication. The raw SMS body is NOT stored here. */
    val smsHash: String?,
    /** Bank / UPI reference from the SMS, if any. Same ref from two senders = one payment. */
    val refNumber: String?,
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
    val guessedAmountPaise: Long?,
    val guessedType: String?,
    val reason: String,
)

@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey val category: String,
    val monthlyLimitPaise: Long,
)

/**
 * One row per SMS the importer looked at, whatever happened to it. Lets the user audit every decision.
 * Deliberately stores NO message body: the body is re-read from the phone's inbox on demand.
 */
@Entity(tableName = "sms_log", indices = [Index(value = ["smsHash"], unique = true), Index(value = ["receivedAt"]), Index(value = ["outcome"])])
data class SmsLogEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sender: String,
    val receivedAt: Long,
    /** SAVED, REVIEW, IGNORED, DUPLICATE */
    val outcome: String,
    /** Parser reason such as "otp", "promo", "low_confidence_45", "same_ref", or the merchant when saved. */
    val reason: String,
    val amountPaise: Long?,
    val type: String?,
    val transactionId: Long?,
    val smsHash: String,
    /** Import run this row belongs to, so the import result can link to "this run" in the log. */
    val runId: Long,
    val loggedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "splits", indices = [Index(value = ["date"])])
data class SplitEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val totalPaise: Long,
    val date: Long,
    val mode: String,
    val payerIndex: Int,
    val linkedTransactionId: Long?,
    val note: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(
    tableName = "split_people",
    foreignKeys = [ForeignKey(entity = SplitEntity::class, parentColumns = ["id"], childColumns = ["splitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["splitId"])]
)
data class SplitPersonEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val personIndex: Int,
    val name: String,
    val isMe: Boolean,
)

@Entity(
    tableName = "split_shares",
    foreignKeys = [ForeignKey(entity = SplitEntity::class, parentColumns = ["id"], childColumns = ["splitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["splitId"])]
)
data class SplitShareEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val personIndex: Int,
    val amountPaise: Long,
    val settledPaise: Long,
)

@Entity(
    tableName = "split_items",
    foreignKeys = [ForeignKey(entity = SplitEntity::class, parentColumns = ["id"], childColumns = ["splitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["splitId"])]
)
data class SplitItemEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val name: String,
    val quantity: Int,
    val pricePaise: Long,
    /** Comma-separated person indices. */
    val assignedTo: String,
)

/** Names you have split with before, so they can be picked instead of retyped. Local only, no contacts access. */
@Entity(tableName = "recent_people")
data class RecentPersonEntity(
    @PrimaryKey val name: String,
    val lastUsedAt: Long,
)
