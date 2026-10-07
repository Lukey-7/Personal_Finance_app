package com.pft.financetracker

import com.pft.financetracker.domain.insights.BudgetStatus
import com.pft.financetracker.domain.insights.CategorySpend
import com.pft.financetracker.domain.insights.MerchantSpend
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.ui.model.ChartMath
import com.pft.financetracker.ui.model.HomeLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Reading a chart by touch, and the one-line summaries on Home's collapsed cards. */
class ChartAndSummaryTest {
    @Test fun aTouchPicksTheColumnUnderTheFinger() {
        // Six bars across 300px: each owns a 50px column, gaps included, so a touch between bars still counts.
        assertEquals(0, ChartMath.indexAt(0f, 300f, 6))
        assertEquals(0, ChartMath.indexAt(49.9f, 300f, 6))
        assertEquals(1, ChartMath.indexAt(50f, 300f, 6))
        assertEquals(5, ChartMath.indexAt(299f, 300f, 6))
        assertEquals(5, ChartMath.indexAt(300f, 300f, 6))
    }

    @Test fun aDragPastTheEdgesHoldsTheEndBarAndNothingIsPickedOnAnEmptyChart() {
        assertEquals(0, ChartMath.indexAt(-40f, 300f, 6))
        assertEquals(5, ChartMath.indexAt(900f, 300f, 6))
        assertNull(ChartMath.indexAt(10f, 300f, 0))
        assertNull(ChartMath.indexAt(10f, 0f, 6))
    }

    @Test fun barsScaleToTheTallestAndAZeroBarStaysAtTheBaseline() {
        assertEquals(listOf(0.5f, 1f, 0f), ChartMath.fractions(listOf(50L, 100L, 0L)))
        assertEquals(listOf(0f, 0f), ChartMath.fractions(listOf(0L, 0L)))
        // Negative periods (refunds outweighed spend) draw as empty, never below the baseline.
        assertEquals(listOf(0f, 1f), ChartMath.fractions(listOf(-20L, 40L)))
    }

    @Test fun eachBarHasASpokenLabel() {
        assertEquals("Sep: ₹19,940", ChartMath.spoken("Sep", 19_940_00))
        assertEquals("Oct: nothing spent", ChartMath.spoken("Oct", 0))
    }

    private fun status(cat: Category, limit: Long, spent: Long) = BudgetStatus(Budget(cat, limit), spent)

    @Test fun budgetsLineLeadsWithWhatIsOver() {
        assertEquals("No budgets set", HomeLines.budgets(emptyList()))
        assertEquals("1 over · Food ₹320 over", HomeLines.budgets(listOf(status(Category.FOOD, 300_00, 620_00), status(Category.BILLS, 5_000_00, 100_00))))
        assertEquals(
            "2 over · Shopping ₹1,000 over",
            HomeLines.budgets(listOf(status(Category.FOOD, 300_00, 400_00), status(Category.SHOPPING, 1_000_00, 2_000_00))),
        )
        assertEquals("Food at 90% · 2 set", HomeLines.budgets(listOf(status(Category.FOOD, 1_000_00, 900_00), status(Category.BILLS, 5_000_00, 100_00))))
        assertEquals("All within budget · 1 set", HomeLines.budgets(listOf(status(Category.FOOD, 1_000_00, 100_00))))
    }

    @Test fun whereItWentAndMerchantsLines() {
        val cats = listOf(CategorySpend(Category.SHOPPING, 4_100_00, 3), CategorySpend(Category.FOOD, 2_200_00, 5), CategorySpend(Category.BILLS, 1_900_00, 1), CategorySpend(Category.OTHER, 1_800_00, 1))
        assertEquals("Shopping 41% · Food 22% · Bills 19%", HomeLines.whereItWent(cats))
        assertEquals("No spending yet", HomeLines.whereItWent(emptyList()))
        val ms = listOf(MerchantSpend("Amazon", 1_299_00, 1, Category.SHOPPING), MerchantSpend("zomato", 120_00, 2, Category.FOOD))
        assertEquals("Amazon ₹1,299 · zomato ₹120", HomeLines.merchants(ms))
    }

    @Test fun notCountedLineNamesTheBiggestKinds() {
        assertEquals("₹10,000 moved · ₹5,000 invested", HomeLines.notCounted(movedPaise = 10_000_00, investedPaise = 5_000_00, cashPaise = 0, paidBackPaise = 0))
        assertEquals("₹800 paid back by friends", HomeLines.notCounted(0, 0, 0, 800_00))
        assertEquals("₹2,000 cash withdrawn", HomeLines.notCounted(0, 0, 2_000_00, 0))
    }
}
