package com.pft.financetracker

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
}
