package com.pft.financetracker

import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.books.CountingRules
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.util.Calendar

/** Six days into a month is compared with the first six days of the last one, never with all of it. */
class PeriodCompareTest {
    private val now = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 6, 1, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis

    @Test fun partMonthComparesWithTheSameDaysOfThePreviousMonth() {
        val cur = Periods.month(0, now); val prev = Periods.month(-1, now)
        val span = Periods.sameSpanBefore(cur, prev, now)
        assertEquals(prev.start, span.start)
        assertEquals(prev.start + (now - cur.start), span.end)
        assertEquals("1–6 Sep", span.label)
    }

    @Test fun aFinishedPeriodComparesWithTheWholePreviousOne() {
        val cur = Periods.month(-1, now); val prev = Periods.month(-2, now)
        assertEquals(prev, Periods.sameSpanBefore(cur, prev, now))
    }

    @Test fun neverRunsPastThePreviousPeriod() {
        // 31 Mar compared with February: capped at the end of February.
        val mar31 = Calendar.getInstance().apply { set(2026, Calendar.MARCH, 31, 12, 0, 0) }.timeInMillis
        val cur = Periods.month(0, mar31); val prev = Periods.month(-1, mar31)
        assertEquals(prev.end, Periods.sameSpanBefore(cur, prev, mar31).end)
    }

    @Test fun aSpanAcrossTwoMonthsNamesBoth() {
        // Wednesday 1 Oct 2025: last week ran Mon 22 Sep – Sun 28 Sep; this week started Mon 29 Sep.
        val wed = Calendar.getInstance().apply { set(2025, Calendar.OCTOBER, 1, 12, 0, 0) }.timeInMillis
        val cur = Periods.week(0, wed); val prev = Periods.week(-1, wed)
        assertEquals("22–24 Sep", Periods.sameSpanBefore(cur, prev, wed).label)
        val sun = Calendar.getInstance().apply { set(2025, Calendar.OCTOBER, 5, 12, 0, 0) }.timeInMillis
        assertEquals("29 Sep – 5 Oct", Periods.sameSpanBefore(Periods.week(1, wed), Periods.week(0, wed), sun + 7 * 86_400_000L).label)
    }

    @Test fun noPercentAgainstNothingOrForNothing() {
        assertNull(InsightsEngine.changePercent(0, 4_218_00))
        assertNull(InsightsEngine.changePercent(800_00, 0))
        assertEquals(-81, InsightsEngine.changePercent(800_00, 4_218_00))
        assertEquals(90, InsightsEngine.changePercent(800_00, 420_00))
    }

    @Test fun reduceSpendingComparesTheSameDaysToo() {
        // 6 Oct: ₹1,000 on food so far, against ₹300 in 1–6 Sep (and ₹5,300 in all of September).
        fun food(paise: Long, y: Int, m: Int, d: Int) = com.pft.financetracker.domain.model.Transaction(
            amountPaise = paise, type = com.pft.financetracker.domain.model.TransactionType.DEBIT, merchant = "Swiggy",
            category = com.pft.financetracker.domain.model.Category.FOOD,
            timestamp = Calendar.getInstance().apply { set(y, m, d, 12, 0, 0) }.timeInMillis,
            bankName = null, accountRef = null, source = com.pft.financetracker.domain.model.Transaction.Source.SMS,
            flow = com.pft.financetracker.domain.model.Flow.EXPENSE)
        val txns = listOf(food(300_00, 2026, Calendar.SEPTEMBER, 2), food(5_000_00, 2026, Calendar.SEPTEMBER, 20), food(1_000_00, 2026, Calendar.OCTOBER, 3))
        val tips = Books.of(txns).tips(emptyList(), now)
        org.junit.Assert.assertTrue(tips.joinToString { it.title }, tips.any { it.title.startsWith("Food & Dining up") })
    }

    @Test fun theFirstOfTheMonthComparesWithOneDayNotARange() {
        val oct1 = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 9, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        assertEquals("1 Sep", Periods.sameSpanBefore(Periods.month(0, oct1), Periods.month(-1, oct1), oct1).label)
        val midnight = Calendar.getInstance().apply { set(2026, Calendar.OCTOBER, 1, 0, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        assertEquals("1 Sep", Periods.sameSpanBefore(Periods.month(0, midnight), Periods.month(-1, midnight), midnight).label)
    }

    @Test fun weeksAreNamedWithoutAZeroAndInFull() = assertEquals("Week of 5 Oct", Periods.week(0, now).label)
}
