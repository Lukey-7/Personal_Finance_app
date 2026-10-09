package com.pft.financetracker

import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.books.CountingRules
import org.junit.Assert.assertTrue
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
        val s = Books.of(data).summary(month)
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
        val s = Books.of(data, CountingRules(cashIsSpend = false)).summary(month)
        assertEquals(4_500_00L, s.grossSpendPaise)
        assertEquals(2_000_00L, s.cashPaise)
    }

    @Test
    fun refundReducesMatchingCategory() {
        val s = Books.of(data).summary(month)
        assertEquals(2_000_00L, s.byCategory.first { it.category == Category.SHOPPING }.amountPaise)
        assertEquals(2_000_00L, s.byCategory.first { it.category == Category.FOOD }.amountPaise)
        assertNull(s.byCategory.firstOrNull { it.category == Category.TRANSFER })
    }

    @Test
    fun categoriesSumToNetSpend() {
        val s = Books.of(data).summary(month)
        assertEquals(s.netSpendPaise, s.byCategory.sumOf { it.amountPaise })
    }

    @Test
    fun drillDownMatchesHeadline() {
        val s = Books.of(data).summary(month)
        // Spend lists its refunds too, so the drill-down adds up to the net figure Home shows.
        val spend = Books.of(data).payments(month, InsightsEngine.Bucket.SPEND)
        assertEquals(s.netSpendPaise, Books.total(spend, InsightsEngine.Bucket.SPEND))
        for (c in s.byCategory) {
            val list = Books.of(data).payments(month, InsightsEngine.Bucket.SPEND, c.category)
            assertEquals(c.category.label, c.amountPaise, Books.total(list, InsightsEngine.Bucket.SPEND))
        }
        // Leaving cash out of spend leaves it out of the drill-down and the budgets as well.
        val noCash = Books.of(data, CountingRules(cashIsSpend = false)).summary(month)
        assertEquals(noCash.netSpendPaise, Books.total(Books.of(data, CountingRules(cashIsSpend = false)).payments(month, InsightsEngine.Bucket.SPEND), InsightsEngine.Bucket.SPEND))
        assertEquals(s.refundsPaise, Books.of(data).payments(month, InsightsEngine.Bucket.REFUNDS).sumOf { it.amountPaise })
        assertEquals(s.transfersOutPaise, Books.of(data).payments(month, InsightsEngine.Bucket.TRANSFERS).sumOf { it.amountPaise })
    }

    @Test
    fun topMerchant() {
        val s = Books.of(data).summary(month)
        assertEquals("Amazon", s.byMerchant.first().merchant)
    }

    @Test
    fun reviewItemsNeverCount() {
        val withReview = data + t(99_999_00, TransactionType.DEBIT, Flow.EXPENSE, Category.OTHER).copy(needsReview = true)
        assertEquals(Books.of(data).summary(month).netSpendPaise, Books.of(withReview).summary(month).netSpendPaise)
    }

    @Test
    fun dailyAverageAndProjection() {
        val now = month.start + 9 * 86_400_000L + 3600_000L // day 10 of the month
        val s = Books.of(data).summary(month)
        assertEquals(s.netSpendPaise / 10, s.dailyAveragePaise(now))
        val days = Calendar.getInstance().apply { timeInMillis = month.start }.getActualMaximum(Calendar.DAY_OF_MONTH)
        assertEquals(s.netSpendPaise / 10 * days, s.projectedPaise(now))
        assertNull(s.projectedPaise(month.end + 1))
    }

    // ---- Category trends and tips: the cash setting, refunds that outweigh spend, a category that stopped ----

    private val oct6 = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 6, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
    private fun on(month: Int, d: Int) = Calendar.getInstance().apply { set(2026, month, d, 12, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test
    fun categoryTrendsLeaveCashOutWhenTheSettingSaysSo() {
        val cur = Periods.month(0, oct6); val prev = Periods.month(-1, oct6)
        val cash = listOf(
            t(1_000_00, TransactionType.DEBIT, Flow.CASH, Category.ATM, "ATM", at = on(Calendar.SEPTEMBER, 3)),
            t(5_000_00, TransactionType.DEBIT, Flow.CASH, Category.ATM, "ATM", at = on(Calendar.OCTOBER, 3)),
        )
        assertTrue(Books.of(cash).trends(cur, prev).any { it.category == Category.ATM })
        assertTrue(Books.of(cash, CountingRules(cashIsSpend = false)).trends(cur, prev).none { it.category == Category.ATM })
    }

    @Test
    fun refundsThatOutweighSpendNeverReadAsMoreThanAHundredPercentDown() {
        val cur = Periods.month(0, oct6); val prev = Periods.month(-1, oct6)
        val list = listOf(
            t(1_000_00, TransactionType.DEBIT, Flow.EXPENSE, Category.FOOD, "Swiggy", at = on(Calendar.SEPTEMBER, 3)),
            t(500_00, TransactionType.DEBIT, Flow.EXPENSE, Category.FOOD, "Swiggy", at = on(Calendar.OCTOBER, 2)),
            t(1_800_00, TransactionType.CREDIT, Flow.REFUND, Category.FOOD, "Swiggy", at = on(Calendar.OCTOBER, 3)),
        )
        val trends = Books.of(list).trends(cur, prev)
        assertTrue(trends.joinToString { it.title }, trends.none { it.category == Category.FOOD })
        assertTrue(trends.all { it.magnitude in 0..10_000 && !it.title.contains("-") })
    }

    @Test
    fun aCategoryThatStoppedIsDeliberatelyNotListed() {
        val cur = Periods.month(0, oct6); val prev = Periods.month(-1, oct6)
        val list = listOf(t(2_000_00, TransactionType.DEBIT, Flow.EXPENSE, Category.ENTERTAINMENT, "Netflix", at = on(Calendar.SEPTEMBER, 3)))
        assertTrue(Books.of(list).trends(cur, prev).isEmpty())
    }

    @Test
    fun aRealDropStillReadsAsDown() {
        val cur = Periods.month(0, oct6); val prev = Periods.month(-1, oct6)
        val list = listOf(
            t(1_000_00, TransactionType.DEBIT, Flow.EXPENSE, Category.FOOD, "Swiggy", at = on(Calendar.SEPTEMBER, 3)),
            t(400_00, TransactionType.DEBIT, Flow.EXPENSE, Category.FOOD, "Swiggy", at = on(Calendar.OCTOBER, 3)),
        )
        assertEquals("Food & Dining down 60%", Books.of(list).trends(cur, prev).single().title)
    }

    @Test
    fun budgetTipsFollowTheCashSetting() {
        val list = listOf(t(2_000_00, TransactionType.DEBIT, Flow.CASH, Category.ATM, "ATM", at = on(Calendar.OCTOBER, 3)))
        val budgets = listOf(com.pft.financetracker.domain.model.Budget(Category.ATM, 1_000_00))
        assertTrue(Books.of(list).tips(budgets, oct6).any { it.title.startsWith("Over budget") })
        assertTrue(Books.of(list, CountingRules(cashIsSpend = false)).tips(budgets, oct6).none { it.title.startsWith("Over budget") })
    }
}
