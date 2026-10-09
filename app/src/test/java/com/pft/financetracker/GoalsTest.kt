package com.pft.financetracker

import com.pft.financetracker.domain.goals.Goal
import com.pft.financetracker.domain.goals.GoalMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class GoalsTest {
    private val today = LocalDate.of(2026, 10, 15)
    private val trip = Goal(id = 1, name = "Goa trip", targetPaise = 1_20_000_00, targetDate = LocalDate.of(2027, 8, 15))

    @Test fun monthlyNeededSpreadsWhatIsLeftOverTheMonthsLeft() {
        val p = GoalMath.progress(trip, listOf(20_000_00L), today)
        assertEquals(20_000_00L, p.savedPaise)
        assertEquals(1_00_000_00L, p.remainingPaise)
        assertEquals(10, p.monthsLeft)
        assertEquals(10_000_00L, p.monthlyNeededPaise)
        assertEquals(16, p.percent)
    }

    @Test fun takingMoneyOutLowersWhatIsSaved() =
        assertEquals(15_000_00L, GoalMath.progress(trip, listOf(20_000_00L, -5_000_00L), today).savedPaise)

    @Test fun aGoalWithoutADateHasNoMonthlyFigure() =
        assertNull(GoalMath.progress(trip.copy(targetDate = null), emptyList(), today).monthlyNeededPaise)

    @Test fun aPastDateAsksForEverythingLeftNow() {
        val p = GoalMath.progress(trip.copy(targetDate = LocalDate.of(2026, 9, 1)), listOf(20_000_00L), today)
        assertTrue(p.late)
        assertEquals(1_00_000_00L, p.monthlyNeededPaise)
    }

    @Test fun aReachedGoalIsDone() {
        val p = GoalMath.progress(trip, listOf(1_30_000_00L), today)
        assertEquals(0L, p.remainingPaise); assertEquals(100, p.percent); assertTrue(p.done); assertEquals(0L, p.monthlyNeededPaise)
    }

    @Test fun theSuggestedTopUpIsLastMonthsSavingsCappedAtWhatIsLeft() {
        assertEquals(8_000_00L, GoalMath.suggestedTopUp(lastMonthSavingsPaise = 8_000_00L, remainingPaise = 1_00_000_00L))
        assertEquals(5_000_00L, GoalMath.suggestedTopUp(lastMonthSavingsPaise = 8_000_00L, remainingPaise = 5_000_00L))
        assertEquals(0L, GoalMath.suggestedTopUp(lastMonthSavingsPaise = -2_000_00L, remainingPaise = 5_000_00L))
    }

    @Test fun onTrackComparesSavedWithAStraightLineFromStartToDate() {
        val started = trip.copy(startDate = LocalDate.of(2026, 8, 15))   // 2 of 12 months gone: ₹20,000 expected
        assertTrue(GoalMath.progress(started, listOf(25_000_00L), today).onTrack)
        assertFalse(GoalMath.progress(started, listOf(10_000_00L), today).onTrack)
    }

    @Test fun noMoreCanBeTakenOutThanIsSaved() {
        assertTrue(GoalMath.canChange(-5_000_00L, savedPaise = 5_000_00L))
        assertFalse(GoalMath.canChange(-5_000_01L, savedPaise = 5_000_00L))
        assertFalse(GoalMath.canChange(-1L, savedPaise = 0L))
        assertTrue(GoalMath.canChange(1_00L, savedPaise = 0L))
        assertFalse(GoalMath.canChange(0L, savedPaise = 5_000_00L))
        assertEquals(0L, GoalMath.maxWithdrawal(-3_00L))
    }

    @Test fun savedNeverShowsBelowZero() =
        assertEquals(0L, GoalMath.progress(trip, listOf(1_000_00L, -3_000_00L), today).savedPaise)
}
