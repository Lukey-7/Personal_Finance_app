package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.Money

/**
 * Spots messages the parser skipped that may still be a payment: no wording it recognises, but an amount (not a
 * balance) and an account reference. They are listed in the SMS log as "possible misses" with one tap to Review;
 * OTPs, promotions, failed or future payments are never listed.
 */
object MissDetector {
    private val softReasons = setOf("no_transaction_hint")
    private val balanceBefore = Regex("""\b(?:bal|balance)\b""", RegexOption.IGNORE_CASE)

    /** The amount in paise when [body], ignored for [reason], looks like a payment the parser missed; else null. */
    fun possibleMiss(body: String, reason: String): Long? {
        if (reason !in softReasons) return null
        // "Avl bal in a/c XX1234 is INR 5,000": a figure shortly after a balance word is the balance, not a payment.
        val amount = AmountExtractor.candidates(body)
            .filter { !it.isBalance && !balanceBefore.containsMatchIn(body.substring(maxOf(0, it.index - 40), it.index)) }
            .firstOrNull() ?: return null
        if (AccountExtractor.extract(body) == null) return null
        return Money.toPaise(amount.value)
    }
}
