package com.pft.financetracker

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/** A month with a known correct answer. If these numbers change, the dashboard is lying. */
class InsightsEngineTest {
    private val month = Periods.month()
    private val mid = month.start + 10 * 86_400_000L

    private fun t(paise: Long, type: TransactionType, flow: Flow, cat: Category, merchant: String = "M", at: Long = mid, acct: String? = null, bank: String? = null) =
        Transaction(amountPaise = paise, type = type, merchant = merchant, category = cat, timestamp = at, bankName = bank, accountRef = acct, source = Transaction.Source.SMS, flow = flow)

    private val data = listOf(
        t(45_000_00, TransactionType.CREDIT, Flow.INCOME, Category.INCOME, "Salary"),
        t(1_200_00, TransactionType.DEBIT, Flow.EXPENSE, Category.FOOD, "Swiggy"),
        t(800_00, TransactionType.DEBIT, Flow.EXPENSE, Category.FOOD, "Zomato"),
        t(2_500_00, TransactionType.DEBIT, Flow.EXPENSE, Category.SHOPPING, "Amazon"),
        t(500_00, TransactionType.CREDIT, Flow.REFUND, Category.SHOPPING, "Amazon"),          // refund matches Amazon
        t(12_000_00, TransactionType.DEBIT, Flow.TRANSFER, Category.TRANSFER, "Card bill"),    // not spend
        t(5_000_00, TransactionType.DEBIT, Flow.INVESTMENT, Category.INVESTMENT, "Zerodha"),   // not spend
        t(2_000_00, TransactionType.DEBIT, Flow.CASH, Category.ATM, "ATM"),                    // spend by default
        t(999_00, TransactionType.DEBIT, Flow.EXPENSE, Category.ENTERTAINMENT, "Netflix", at = month.start - 86_400_000L), // last month, excluded
    )

    @Test
    fun grossNetRefundsIncomeSavings() {
        val s = InsightsEngine.summarize(data, month)
        assertEquals(1_200_00 + 800_00 + 2_500_00 + 2_000_00L, s.grossSpendPaise)
        assertEquals(500_00L, s.refundsPaise)
        assertEquals(6_000_00L, s.netSpendPaise)
        assertEquals(45_000_00L, s.incomePaise)
        assertEquals(39_000_00L, s.savingsPaise)
        assertEquals(12_000_00L, s.transfersOutPaise)
        assertEquals(5_000_00L, s.investmentsPaise)
    }

    @Test
    fun cashCanBeExcluded() {
        val s = InsightsEngine.summarize(data, month, includeCash = false)
        assertEquals(4_500_00L, s.grossSpendPaise)
        assertEquals(2_000_00L, s.cashPaise)
    }

    @Test
    fun refundReducesMatchingCategory() {
        val s = InsightsEngine.summarize(data, month)
        assertEquals(2_000_00L, s.byCategory.first { it.category == Category.SHOPPING }.amountPaise)
        assertEquals(2_000_00L, s.byCategory.first { it.category == Category.FOOD }.amountPaise)
        assertNull(s.byCategory.firstOrNull { it.category == Category.TRANSFER })
    }

    @Test
    fun categoriesSumToNetSpend() {
        val s = InsightsEngine.summarize(data, month)
        assertEquals(s.netSpendPaise, s.byCategory.sumOf { it.amountPaise })
    }

    @Test
    fun drillDownMatchesHeadline() {
        val s = InsightsEngine.summarize(data, month)
        assertEquals(s.grossSpendPaise, InsightsEngine.drillDown(data, month, InsightsEngine.Bucket.SPEND).sumOf { it.amountPaise })
        assertEquals(s.refundsPaise, InsightsEngine.drillDown(data, month, InsightsEngine.Bucket.REFUNDS).sumOf { it.amountPaise })
        assertEquals(s.transfersOutPaise, InsightsEngine.drillDown(data, month, InsightsEngine.Bucket.TRANSFERS).sumOf { it.amountPaise })
    }

    @Test
    fun topMerchant() {
        val s = InsightsEngine.summarize(data, month)
        assertEquals("Amazon", s.byMerchant.first().merchant)
    }

    @Test
    fun reviewItemsNeverCount() {
        val withReview = data + t(99_999_00, TransactionType.DEBIT, Flow.EXPENSE, Category.OTHER).copy(needsReview = true)
        assertEquals(InsightsEngine.summarize(data, month).netSpendPaise, InsightsEngine.summarize(withReview, month).netSpendPaise)
    }

    @Test
    fun dailyAverageAndProjection() {
        val now = month.start + 9 * 86_400_000L + 3600_000L // day 10 of the month
        val s = InsightsEngine.summarize(data, month)
        assertEquals(s.netSpendPaise / 10, s.dailyAveragePaise(now))
        val days = Calendar.getInstance().apply { timeInMillis = month.start }.getActualMaximum(Calendar.DAY_OF_MONTH)
        assertEquals(s.netSpendPaise / 10 * days, s.projectedPaise(now))
        assertNull(s.projectedPaise(month.end + 1))
    }
}
