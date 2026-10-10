package com.pft.financetracker.domain.categorize

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType
import java.util.Locale

/**
 * Rule-based categorizer. Keyword lists live on [Category]; add words there.
 * Longer keyword matches win over shorter ones so "tata power" beats "tata".
 */
object Categorizer {
    // "cashback" is a refund (FlowClassifier), not a cash withdrawal; drop it so the ATM keyword "cash" can't match.
    private val cashback = Regex("""cash\s*back""")

    /**
     * Keywords that are also parts of everyday words or names, so they must stand alone: "rent" not in "Parent" or
     * "Current", "dine" not in "Dinesh", "chai" not in "Chaitanya", "mall" not in "small".
     */
    private val wholeWordOnly = setOf("rent", "dine", "chai", "mall", "toll", "boat", "apple", "social", "pub", "bar")

    /**
     * Matched only when the merchant is that word and nothing else: "Cash" typed by hand is a withdrawal, but
     * "Cashfree" or "Cashify" are payments to companies and must not turn into ATM cash (and so Flow.CASH).
     */
    private val exactOnly = setOf("cash")

    /** Generic words that lose to any brand: "YouTube Premium" is streaming, not an insurance premium. */
    private val weak = setOf("premium")

    fun categorize(merchant: String, type: TransactionType, bankName: String? = null): Category {
        val text = merchant.lowercase(Locale.ROOT).replace(cashback, " ")
        var best: Category? = null
        var bestScore = 0
        for (cat in Category.entries) {
            for (kw in cat.keywords) {
                // Longer matches win; a weak word only counts when nothing else matched.
                val score = if (kw in weak) 1 else kw.length
                if (score > bestScore && matches(text, kw)) {
                    best = cat
                    bestScore = score
                }
            }
        }
        // Credits with no better match are income; the Flow (refund vs salary) is decided by FlowClassifier.
        return best ?: if (type == TransactionType.CREDIT) Category.INCOME else Category.OTHER
    }

    private fun matches(text: String, kw: String): Boolean = when {
        kw in exactOnly -> text.trim() == kw
        // Short keywords (<=3 chars) must be whole words to avoid "gas" matching "vegas".
        kw.length <= 3 || kw in wholeWordOnly -> containsWholeWord(text, kw)
        else -> text.contains(kw)
    }

    /** True when [kw] appears with no letter or digit straight before or after it, anywhere in [text]. */
    private fun containsWholeWord(text: String, kw: String): Boolean {
        var idx = text.indexOf(kw)
        while (idx >= 0) {
            val before = if (idx == 0) ' ' else text[idx - 1]
            val afterIdx = idx + kw.length
            val after = if (afterIdx >= text.length) ' ' else text[afterIdx]
            if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) return true
            idx = text.indexOf(kw, idx + 1)
        }
        return false
    }
}
