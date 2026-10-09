package com.pft.financetracker

import com.pft.financetracker.ui.model.DrillPeriod
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.PeriodChoice
import com.pft.financetracker.ui.components.RollDirection
import com.pft.financetracker.ui.components.RollMemo
import com.pft.financetracker.ui.components.rollMask
import com.pft.financetracker.ui.screens.dashboard.HomeFigures
import com.pft.financetracker.ui.screens.dashboard.sparkChoices
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Moving between months on Home: every figure must belong to the month shown, and compare with the one before. */
class HomePeriodTest {
    private val zone = ZoneId.systemDefault()
    private fun at(y: Int, m: Int, d: Int, h: Int = 12) = LocalDate.of(y, m, d).atTime(h, 0).atZone(zone).toInstant().toEpochMilli()
    private fun day(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(zone).toInstant().toEpochMilli()
    private val now = at(2026, 10, 9)

    private fun spend(paise: Long, at: Long, cat: Category = Category.FOOD, merchant: String = "Swiggy") =
        Transaction(amountPaise = paise, type = TransactionType.DEBIT, merchant = merchant, category = cat, timestamp = at, bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.EXPENSE)
    private fun refund(paise: Long, at: Long, merchant: String = "Swiggy") =
        Transaction(amountPaise = paise, type = TransactionType.CREDIT, merchant = merchant, category = Category.FOOD, timestamp = at, bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.REFUND)

    @Test fun anOlderMonthComparesWithTheWholeMonthBeforeIt() {
        val july = PeriodChoice.Month(-3)
        assertEquals(day(2026, 7, 1), july.period(now).start)
        assertEquals(day(2026, 8, 1), july.period(now).end)
        val before = july.previous(now)
        assertEquals(day(2026, 6, 1), before.start)
        assertEquals(day(2026, 7, 1), before.end)
        assertEquals("Jun 2026", before.label)
    }

    @Test fun weeksCompareWithTheWeekBefore() {
        val w = PeriodChoice.Week(-4)
        val p = w.period(now)
        val prev = w.previous(now)
        assertEquals(p.start, prev.end)
        assertEquals(7, prev.days)
    }

    @Test fun aPickedRangeIsReadAsCalendarDaysAndComparesWithTheSameNumberOfDaysBefore() {
        // The date picker hands back UTC midnights.
        fun utc(y: Int, m: Int, d: Int) = LocalDate.of(y, m, d).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        val c = PeriodChoice.fromPicker(utc(2026, 7, 1), utc(2026, 7, 10))
        val p = c.period(now)
        assertEquals(day(2026, 7, 1), p.start)
        assertEquals(day(2026, 7, 11), p.end)
        val prev = c.previous(now)
        assertEquals(day(2026, 6, 21), prev.start)
        assertEquals(p.start, prev.end) // no overlap, no gap
        assertEquals("21 Jun – 30 Jun", prev.label)
    }

    @Test fun aRangeInAnotherYearSaysWhichYear() {
        val p = PeriodChoice.Custom(day(2025, 7, 1), day(2025, 7, 31)).period(now)
        assertEquals("01 Jul – 31 Jul 2025", p.label)
    }

    @Test fun steppingStopsAtTheCurrentPeriod() {
        assertNull(PeriodChoice.Month(0).step(1))
        assertEquals(PeriodChoice.Month(-1), PeriodChoice.Month(0).step(-1))
        assertEquals(PeriodChoice.Week(-2), PeriodChoice.Week(-1).step(-1))
        assertNull(PeriodChoice.Custom(0, 0).step(-1))
    }

    @Test fun titlesSayWhichPeriod() {
        assertEquals("This month", PeriodChoice.Month(0).title(now))
        assertEquals("Last month", PeriodChoice.Month(-1).title(now))
        assertEquals("July 2026", PeriodChoice.Month(-3).title(now))
        assertEquals("This week", PeriodChoice.Week(0).title(now))
    }

    @Test fun theSparkBarsStayPutWithinTheLastSixMonths() {
        val a = sparkChoices(PeriodChoice.Month(0))
        val b = sparkChoices(PeriodChoice.Month(-1))
        val c = sparkChoices(PeriodChoice.Month(-5))
        assertEquals(a, b)
        assertEquals(a, c)
        assertEquals(PeriodChoice.Month(-5), a.first())
        assertEquals(PeriodChoice.Month(0), a.last())
        // Further back, the window ends at the chosen month.
        assertEquals(PeriodChoice.Month(-8), sparkChoices(PeriodChoice.Month(-8)).last())
        // Weeks stay weeks.
        assertTrue(sparkChoices(PeriodChoice.Week(-7)).all { it is PeriodChoice.Week })
    }

    @Test fun everyHomeFigureBelongsToTheChosenMonthAndAgrees() {
        val txns = listOf(
            spend(1_000_00, at(2026, 10, 2)),
            spend(3_000_00, at(2026, 7, 5)),
            refund(500_00, at(2026, 7, 6)),
            spend(2_000_00, at(2026, 6, 10)),
        ).sortedByDescending { it.timestamp }
        val budgets = listOf(Budget(Category.FOOD, 2_000_00))
        val f = HomeFigures.of(txns, budgets, PeriodChoice.Month(-3), includeCash = true, now = now)
        assertEquals(2_500_00L, f.summary.netSpendPaise)       // July only, net of the refund
        assertEquals(2_000_00L, f.previous.netSpendPaise)      // all of June
        assertEquals(day(2026, 7, 1), f.budgetPeriod.start)    // July's budget, not October's
        assertEquals(2_500_00L, f.budgetStatus.single().spentPaise)
        assertTrue(f.recent.all { it.timestamp in f.period }) // July's payments, not this week's
        assertEquals(2, f.recent.size)
        assertEquals(1, f.sparkBars.count { it.current })
    }

    @Test fun aWeekShowsTheBudgetOfTheMonthItEndsIn() {
        val f = HomeFigures.of(emptyList(), emptyList(), PeriodChoice.Week(0), includeCash = true, now = now)
        assertEquals(Periods.month(0, now).start, f.budgetPeriod.start)
    }

    @Test fun digitsRollOnlyWhenTheSeparatorsStayPut() {
        assertEquals("0,00,000", rollMask("1,23,456"))
        val m = RollMemo("12,345")
        assertTrue(m.sameShapeAs("12,890"))
        assertFalse(m.sameShapeAs("1,234.5")) // same length, different shape: fade, don't roll
        assertFalse(m.sameShapeAs("1,234.5")) // a recomposition with the same text keeps the answer
        assertTrue(m.sameShapeAs("9,999.9"))
    }

    @Test fun theRollDirectionFollowsTheValue() {
        val d = RollDirection(100)
        assertFalse(d.upTo(50))
        assertFalse(d.upTo(50)) // same value again keeps its direction
        assertTrue(d.upTo(80))
    }

    // ---- Week labels and drill-downs opened from another screen ----

    @Test fun weekTitlesReadAsWords() {
        assertEquals("Week of 21 Sep", PeriodChoice.Week(-2).title(now))
        assertEquals("Week of 5 Oct", Periods.week(0, now).label)
    }

    @Test fun aDrillWithoutBoundsFollowsHome() {
        assertNull(DrillPeriod.of(-1L, -1L, now))
        assertNull(DrillPeriod.of(day(2026, 10, 5), day(2026, 10, 5), now))
    }

    @Test fun aDrillNamesAWholeMonthOrWeekTheUsualWay() {
        val sep = DrillPeriod.of(day(2026, 9, 1), day(2026, 10, 1), now)!!
        assertEquals("Sep 2026", sep.label)
        assertEquals(day(2026, 9, 1), sep.start)
        val week = DrillPeriod.of(day(2026, 10, 5), day(2026, 10, 12), now)!!
        assertEquals("Week of 5 Oct", week.label)
    }

    @Test fun aDrillOverAnyOtherSpanKeepsItsExactBounds() {
        val from = day(2026, 9, 1); val to = day(2026, 9, 7) + 3_600_000L
        val p = DrillPeriod.of(from, to, now)!!
        assertEquals(from, p.start)
        assertEquals(to, p.end)
        assertTrue(p.label, p.label.contains("Sep"))
    }
}
