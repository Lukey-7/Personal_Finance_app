package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.TransactionType

data class ParsedTransaction(
    val amount: Double,
    val type: TransactionType,
    val merchant: String,
    val timestamp: Long,
    val bankName: String?,
    val accountRef: String?,
    val confidence: Int,
)

sealed class ParseResult {
    /** Parsed with confidence >= threshold; safe to auto-insert. */
    data class Success(val transaction: ParsedTransaction) : ParseResult()

    /** Looks like a transaction, but one or more fields could not be confidently determined. */
    data class NeedsReview(
        val reason: String,
        val guessedAmount: Double?,
        val guessedType: TransactionType?,
    ) : ParseResult()

    /** Not a transaction (OTP, promo, balance alert, failed txn ...). Safe to drop. */
    data class Ignored(val reason: String) : ParseResult()
}

/** Raw input to the parser. */
data class SmsMessage(val sender: String, val body: String, val receivedAt: Long)
