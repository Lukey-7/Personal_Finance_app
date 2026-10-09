package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.Money
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
class SmsParser(private val threshold: Int = 60, private val templates: () -> List<LearnedTemplate> = { emptyList() }) {

    fun parse(sms: SmsMessage): ParseResult {
        val body = SmsText.normalize(sms.body)
        if (body.isBlank()) return ParseResult.Ignored("empty")
        // TRAI marks promotional headers with -P; money alerts are always service or transactional.
        if (SenderId.suffix(sms.sender) == 'P') return ParseResult.Ignored("promotional_sender")

        TextFilters.ignoreReason(body)?.let { return ParseResult.Ignored(it) }

        // A shape this person already taught us for this sender wins over the generic layers.
        for (t in templates()) {
            val hit = TemplateLearner.apply(t, sms.sender, body) ?: continue
            val bank = BankExtractor.extract(sms.sender, body)
            return ParseResult.Success(
                ParsedTransaction(
                    amountPaise = hit.amountPaise, type = hit.type, merchant = hit.merchant ?: defaultMerchant(hit.type, bank),
                    timestamp = DateExtractor.extract(body, sms.receivedAt), bankName = bank, accountRef = AccountExtractor.extract(body),
                    refNumber = RefExtractor.extract(body), confidence = 90,
                )
            )
        }
        if (!TextFilters.looksTransactional(body)) return ParseResult.Ignored("no_transaction_hint")

        val amountCandidates = AmountExtractor.candidates(body)
        val amount = amountCandidates.firstOrNull { !it.isBalance }?.value?.let { Money.toPaise(it) }
        val typeResult = TypeDetector.detect(body)

        if (amountCandidates.isEmpty() && typeResult.type == null) return ParseResult.Ignored("no_amount_no_type")
        // Only a balance or a limit was found. Saving that as the payment would record ₹10,000 for a ₹500 debit, so a
        // person decides.
        if (amount == null && amountCandidates.isNotEmpty()) return ParseResult.NeedsReview("only_balance_amount", null, typeResult.type)
        if (amount == null) return ParseResult.NeedsReview("amount_not_found", null, typeResult.type)
        if (typeResult.type == null) return ParseResult.NeedsReview("type_ambiguous", amount, null)

        val merchant = MerchantExtractor.extract(body, typeResult.type)
        val account = AccountExtractor.extract(body)
        val bank = BankExtractor.extract(sms.sender, body)
        val timestamp = DateExtractor.extract(body, sms.receivedAt)
        val ref = RefExtractor.extract(body)

        var confidence = 0
        confidence += minOf(40, 20 + typeResult.score * 5)
        confidence += 30 // a non-balance amount; balance-only messages went to review above
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
                amountPaise = amount,
                type = typeResult.type,
                merchant = merchantName,
                timestamp = timestamp,
                bankName = bank,
                accountRef = account,
                refNumber = ref,
                confidence = confidence,
            )
        )
    }

    private fun defaultMerchant(type: TransactionType, bank: String?): String =
        if (type == TransactionType.CREDIT) "Credit" + (bank?.let { " ($it)" } ?: "") else "Payment" + (bank?.let { " ($it)" } ?: "")
}

/** One clean form of an SMS body for every layer: plain spaces, one line, one spelling of the rupee sign. */
object SmsText {
    /** No-break, narrow no-break, figure, thin and other wide spaces, tabs and line breaks: all a plain space to the regexes. */
    private val oddSpace = Regex("[\u00A0\u202F\u2007\u2009\u200A\u2002\u2003\u2004\u2005\u2006\u2008\t\r\n]")
    private val zeroWidth = Regex("[\u200B\u200C\u200D\u2060\uFEFF]")
    private val runs = Regex("""\s{2,}""")

    fun normalize(raw: String): String = raw
        .replace(zeroWidth, "")
        .replace(oddSpace, " ")
        // The old rupee sign (\u20A8) and the full-width forms read as "Rs"; the ₹ sign is already understood.
        .replace("\u20A8", "Rs ")
        .replace("\uFF0E", ".")
        .replace("\uFF0C", ",")
        .replace(runs, " ")
        .trim()
}
