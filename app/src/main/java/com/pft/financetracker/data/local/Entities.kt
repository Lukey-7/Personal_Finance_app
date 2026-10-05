package com.pft.financetracker.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "transactions",
    indices = [Index(value = ["smsHash"], unique = true), Index(value = ["timestamp"]), Index(value = ["refNumber"]), Index(value = ["importBatchId"])]
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
    /** The amount the bank reported, when a split later reduced [amountPaise] to my share. Used to spot the same payment. */
    val originalAmountPaise: Long? = null,
    /** True once a person corrected this row. Automatic re-parsing and duplicate merging never overwrite it. */
    @ColumnInfo(defaultValue = "0") val userEdited: Boolean = false,
    /** PERSON / ORGANISATION / UNKNOWN, decided from the SMS or statement text at import. Null for older rows. */
    val counterpartyKind: String? = null,
    /** The statement/screenshot import that added this row ([ImportBatchEntity]); null otherwise. */
    val importBatchId: Long? = null,
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
    /** MANUAL, AUTO_LOCAL or AUTO_AI. */
    @ColumnInfo(defaultValue = "MANUAL") val source: String = "MANUAL",
    /** APPLIED or SUGGESTED. */
    @ColumnInfo(defaultValue = "APPLIED") val status: String = "APPLIED",
    val confidence: Int? = null,
    /** Newline-separated plain-language reasons, for automatic splits. */
    val reasons: String? = null,
    /** PAYBACK or ADVANCE. */
    @ColumnInfo(defaultValue = "PAYBACK") val kind: String = "PAYBACK",
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

/**
 * Which transactions an automatic (or manually linked) split is made of: the payment I made, and the paybacks or
 * advances friends sent me, with how much of each belongs to this split. [prevFlow] and [prevAmountPaise] remember
 * the row as it was, so undo restores it exactly.
 */
@Entity(
    tableName = "split_links",
    foreignKeys = [ForeignKey(entity = SplitEntity::class, parentColumns = ["id"], childColumns = ["splitId"], onDelete = ForeignKey.CASCADE)],
    indices = [Index(value = ["splitId"]), Index(value = ["transactionId"])]
)
data class SplitLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val splitId: Long,
    val transactionId: Long,
    /** PAYMENT, PAYBACK or ADVANCE. */
    val role: String,
    val allocatedPaise: Long,
    val prevFlow: String?,
    val prevAmountPaise: Long?,
)

/** "This is not a split": remembered forever so the same payment is never suggested or auto-split again. */
@Entity(tableName = "split_decisions", indices = [Index(value = ["paymentTransactionId"], unique = true)])
data class SplitDecisionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val paymentTransactionId: Long,
    val decision: String,
    val decidedAt: Long = System.currentTimeMillis(),
)

/** One statement or screenshot import, for the import history and "undo this import". */
/**
 * A refund or reversal paired with the purchase it gives money back for (v1.3). One row per credit: APPLIED while in
 * force, REJECTED after the person undid it, so the same credit is never paired again. [prevFlow]/[prevCategory] are
 * the credit's values before pairing changed them; null when pairing changed nothing.
 */
@Entity(
    tableName = "refund_links",
    foreignKeys = [
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["refundTxId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(entity = TransactionEntity::class, parentColumns = ["id"], childColumns = ["debitTxId"], onDelete = ForeignKey.CASCADE),
    ],
    indices = [Index(value = ["refundTxId"], unique = true), Index(value = ["debitTxId"])],
)
data class RefundLinkEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val refundTxId: Long,
    val debitTxId: Long,
    /** REFUND or REVERSAL. */
    val kind: String,
    val amountPaise: Long,
    /** APPLIED or REJECTED. */
    val status: String,
    val prevFlow: String?,
    val prevCategory: String?,
    val createdAt: Long = System.currentTimeMillis(),
)

/**
 * A message shape learned from Review (v1.3): the sender's core ID and its wording with amounts, merchant, numbers
 * and months masked. Holds no amounts, names or account digits. Seen and deleted in Settings.
 */
@Entity(tableName = "parser_templates", indices = [Index(value = ["senderCore", "skeleton"], unique = true)])
data class ParserTemplateEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val senderCore: String,
    val skeleton: String,
    /** DEBIT or CREDIT. */
    val type: String,
    val createdAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "import_batches")
data class ImportBatchEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val fileName: String,
    /** CSV, XLSX, XLS_HTML, PDF, PDF_SCANNED, IMAGE, APP_SCREENSHOT */
    val format: String,
    val importedAt: Long = System.currentTimeMillis(),
    val rowsFound: Int,
    val added: Int,
    val duplicates: Int,
    val needsReview: Int,
    /** Rows whose running balance did not add up (sent to review instead of guessed). */
    val balanceMismatches: Int,
    val firstDate: Long?,
    val lastDate: Long?,
)
