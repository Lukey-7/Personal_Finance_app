package com.pft.financetracker

import com.pft.financetracker.domain.ask.MonthlySummary
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.recurring.RecurringBook
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonthlySummaryTest {
    private val oct = Period(1_000L, 2_000L, "Oct 2026")
    private val sep = Period(0L, 1_000L, "Sep 2026")
    private var id = 1L
    private fun t(paise: Long, at: Long, cat: Category, flow: Flow = Flow.EXPENSE, type: TransactionType = TransactionType.DEBIT) =
        Transaction(id = id++, amountPaise = paise, type = type, merchant = cat.label, category = cat, timestamp = at, bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = flow)

    private val txns = listOf(
        t(6_000_00, 1_100, Category.FOOD), t(2_000_00, 1_200, Category.SHOPPING), t(50_000_00, 1_050, Category.INCOME, Flow.INCOME, TransactionType.CREDIT),
        t(4_000_00, 100, Category.FOOD), t(3_000_00, 200, Category.SHOPPING), t(50_000_00, 50, Category.INCOME, Flow.INCOME, TransactionType.CREDIT),
    )
    private val text = MonthlySummary.write(InsightsEngine.summarize(txns, oct), InsightsEngine.summarize(txns, sep), listOf(Budget(Category.FOOD, 5_000_00)), RecurringBook.EMPTY)

    @Test fun itStatesSpendIncomeAndSavingsWithTheChange() {
        assertTrue(text, text.contains("₹8,000"))      // spend
        assertTrue(text, text.contains("₹42,000"))     // saved
        assertTrue(text, text.contains("14%"))         // 8,000 vs 7,000
    }

    @Test fun itNamesTheCategoryThatGrewMost() = assertTrue(text, text.contains("Food & Dining") && text.contains("50%"))

    @Test fun itFlagsABudgetThatWasCrossed() = assertTrue(text, text.contains("over") && text.contains("₹1,000"))

    @Test fun itNeverSaysAnythingAboutMissingIncomeAsIfItWereZeroSaving() {
        val noIncome = MonthlySummary.write(InsightsEngine.summarize(txns.filter { it.flow != Flow.INCOME }, oct), InsightsEngine.summarize(emptyList(), sep), emptyList(), RecurringBook.EMPTY)
        assertFalse(noIncome, noIncome.contains("saved"))
    }

    @Test fun aMonthWithNothingSpentYetIsNotCalledAHundredPercentDrop() {
        val early = MonthlySummary.write(InsightsEngine.summarize(emptyList(), oct), InsightsEngine.summarize(txns, sep), emptyList(), RecurringBook.EMPTY)
        assertFalse(early, early.contains("100%"))
        assertTrue(early, early.contains("Nothing spent yet in Oct 2026"))
    }
}
