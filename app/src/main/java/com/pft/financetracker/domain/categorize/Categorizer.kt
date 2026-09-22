package com.pft.financetracker.domain.categorize

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType
import java.util.Locale

/**
 * Rule-based categorizer. Keyword lists live on [Category]; add words there.
 * Longer keyword matches win over shorter ones so "tata power" beats "tata".
 */
object Categorizer {
    fun categorize(merchant: String, type: TransactionType, bankName: String? = null): Category {
        val text = merchant.lowercase(Locale.ROOT)
        var best: Category? = null
        var bestLen = 0
        for (cat in Category.entries) {
            for (kw in cat.keywords) {
                if (kw.length > bestLen && containsWord(text, kw)) {
                    best = cat
                    bestLen = kw.length
                }
            }
        }
        // Credits with no better match are income; the Flow (refund vs salary) is decided by FlowClassifier.
        return best ?: if (type == TransactionType.CREDIT) Category.INCOME else Category.OTHER
    }

    private fun containsWord(text: String, kw: String): Boolean {
        val idx = text.indexOf(kw)
        if (idx < 0) return false
        // Short keywords (<=3 chars) must be whole words to avoid "gas" matching "vegas".
        if (kw.length <= 3) {
            val before = if (idx == 0) ' ' else text[idx - 1]
            val afterIdx = idx + kw.length
            val after = if (afterIdx >= text.length) ' ' else text[afterIdx]
            return !before.isLetterOrDigit() && !after.isLetterOrDigit()
        }
        return true
    }
}
