package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.TransactionType

/**
 * Orchestrates the extractor layers. Pure Kotlin, no Android dependencies, fully unit-testable.
 *
 * Confidence model (0-100):
 *   +40 type detected (scaled by keyword strength)
 *   +30 amount found (non-balance)
 *   +15 merchant found
 *   +10 account ref found
 *   +5  bank identified
 * Result >= [threshold] -> Success, else NeedsReview. Messages that fail the pre-filter -> Ignored.
 */
class SmsParser(private val threshold: Int = 60) {

    fun parse(sms: SmsMessage): ParseResult {
        val body = sms.body.replace('\n', ' ').replace(Regex("""\s{2,}"""), " ").trim()
        if (body.isBlank()) return ParseResult.Ignored("empty")

        TextFilters.ignoreReason(body)?.let { return ParseResult.Ignored(it) }
        if (!TextFilters.looksTransactional(body)) return ParseResult.Ignored("no_transaction_hint")

        val amountCandidates = AmountExtractor.candidates(body)
        val amount = (amountCandidates.firstOrNull { !it.isBalance } ?: amountCandidates.firstOrNull())?.value
        val typeResult = TypeDetector.detect(body)

        if (amount == null && typeResult.type == null) return ParseResult.Ignored("no_amount_no_type")
        if (amount == null) return ParseResult.NeedsReview("amount_not_found", null, typeResult.type)
        if (typeResult.type == null) return ParseResult.NeedsReview("type_ambiguous", amount, null)

        val merchant = MerchantExtractor.extract(body, typeResult.type)
        val account = AccountExtractor.extract(body)
        val bank = BankExtractor.extract(sms.sender, body)
        val timestamp = DateExtractor.extract(body, sms.receivedAt)

        var confidence = 0
        confidence += minOf(40, 20 + typeResult.score * 5)
        confidence += if (amountCandidates.any { !it.isBalance }) 30 else 15
        if (merchant != null) confidence += 15
        if (account != null) confidence += 10
        if (bank != null && bank.length > 2) confidence += 5
        confidence = confidence.coerceIn(0, 100)

        if (confidence < threshold) {
            return ParseResult.NeedsReview("low_confidence_$confidence", amount, typeResult.type)
        }

        val merchantName = merchant ?: defaultMerchant(typeResult.type, bank)
        return ParseResult.Success(
            ParsedTransaction(
                amount = amount,
                type = typeResult.type,
                merchant = merchantName,
                timestamp = timestamp,
                bankName = bank,
                accountRef = account,
                confidence = confidence,
            )
        )
    }

    private fun defaultMerchant(type: TransactionType, bank: String?): String =
        if (type == TransactionType.CREDIT) "Credit" + (bank?.let { " ($it)" } ?: "") else "Payment" + (bank?.let { " ($it)" } ?: "")
}
