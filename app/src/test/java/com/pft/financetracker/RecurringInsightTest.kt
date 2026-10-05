package com.pft.financetracker

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The "reduce spending" suggestions read subscriptions from RecurringDetector. */
class RecurringInsightTest {
    private val day = 86_400_000L
    private val now = 1_760_000_000_000L
    private var id = 1L
    private fun charge(m: String, paise: Long, daysAgo: Int) = Transaction(id = id++, amountPaise = paise, type = TransactionType.DEBIT, merchant = m,
        category = Category.ENTERTAINMENT, timestamp = now - daysAgo * day, bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.EXPENSE)

    @Test fun aYearlySubscriptionIsSuggestedWithItsYearlyCost() {
        // The old rule needed two charges inside 90 days, so yearly plans were never mentioned.
        val s = InsightsEngine.suggestions(listOf(charge("Amazon Prime", 1_49_900, 370), charge("Amazon Prime", 1_49_900, 5)), emptyList(), now)
        val prime = s.single { it.title.startsWith("Recurring") }
        assertTrue(prime.body, prime.body.contains("1,499"))
    }

    @Test fun aPriceRiseGetsItsOwnSuggestion() {
        val s = InsightsEngine.suggestions(listOf(charge("Netflix", 49_900, 75), charge("Netflix", 49_900, 45), charge("Netflix", 64_900, 15)), emptyList(), now)
        assertEquals(1, s.count { it.title == "Netflix went up" })
    }
}
