package com.pft.financetracker.domain.categorize

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CategoryChoice
import com.pft.financetracker.domain.model.CustomCategory
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType

/**
 * The categories a person gave their payees: once "Autowala" is moved to Transport, the next Autowala payment lands
 * there too, before the keyword lists are tried. [byKey] maps a payee key ([keyOf]) to a [CategoryChoice] key.
 */
class PayeeRules(private val byKey: Map<String, String>, private val customs: Map<Long, CustomCategory> = emptyMap()) {

    /** The person's choice for [merchant], or null when they never gave one (or it points at a deleted category). */
    fun choiceFor(merchant: String): CategoryChoice? {
        val key = keyOf(merchant) ?: return null
        val stored = byKey[key] ?: return null
        if (CategoryChoice.isCustomKey(stored)) return CategoryChoice.fromKey(stored, customs).takeIf { it.customId != null }
        return CategoryChoice.fromKey(stored, customs)
    }

    /**
     * The category for a new payment from [merchant]: the person's rule when there is one, else the keyword guess.
     * Money in only takes a rule made for money in (Income), so a payee's spend category never files a refund
     * from them as spend, and the other way round.
     */
    fun categorise(merchant: String, type: TransactionType, bankName: String? = null): CategoryChoice {
        val rule = choiceFor(merchant)?.takeIf { fitsDirection(it.category, type) }
        return rule ?: CategoryChoice(Categorizer.categorize(merchant, type, bankName))
    }

    val isEmpty: Boolean get() = byKey.isEmpty()

    companion object {
        val NONE = PayeeRules(emptyMap())

        /**
         * The payee as a rule key, or null for a name that says nothing about who was paid: the parsers' placeholders
         * ("Payment (HDFC Bank)", "Credit (SBI)"), and names too short to mean anything.
         */
        fun keyOf(merchant: String): String? {
            val m = merchant.trim()
            if (Generic.matches(m)) return null
            val key = InsightsEngine.normalizeMerchant(m)
            return key.takeIf { it.length >= 3 && !it.all(Char::isDigit) }
        }

        private fun fitsDirection(category: Category, type: TransactionType) =
            (category == Category.INCOME) == (type == TransactionType.CREDIT)

        /**
         * The earlier payments a new rule could also move: same payee, filed elsewhere, same direction, and not
         * individually corrected by the person (their own correction always wins).
         */
        fun alsoMovable(all: List<Transaction>, merchant: String, choice: CategoryChoice, exceptId: Long): List<Transaction> {
            val key = keyOf(merchant) ?: return emptyList()
            return all.filter {
                it.id != exceptId && !it.userEdited && !it.needsReview && it.choice != choice &&
                    fitsDirection(choice.category, it.type) && keyOf(it.merchant) == key
            }
        }

        private val Generic = Regex("""^(payment|credit|debit|transfer|upi|upi payment|card bill|card payment|atm withdrawal|cash|refund)( \(.*\))?$""", RegexOption.IGNORE_CASE)
    }
}
