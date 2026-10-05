package com.pft.financetracker.domain.goals

import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Something to save toward, optionally by a date. [startDate] is when the goal was set, for the on-track line. */
data class Goal(
    val id: Long = 0,
    val name: String,
    val targetPaise: Long,
    val targetDate: LocalDate?,
    val startDate: LocalDate = LocalDate.now(),
)

data class GoalProgress(
    val goal: Goal,
    val savedPaise: Long,
    val remainingPaise: Long,
    val percent: Int,
    /** Whole months until the target date (at least 1 while it is ahead); 0 once it has passed or with no date. */
    val monthsLeft: Int,
    /** What to set aside each month to arrive on time; null without a date, everything left once the date has passed. */
    val monthlyNeededPaise: Long?,
    val done: Boolean,
    val late: Boolean,
    /** At least as much saved as a straight line from the start date to the target date asks for. */
    val onTrack: Boolean,
)

object GoalMath {
    fun progress(g: Goal, contributions: List<Long>, today: LocalDate): GoalProgress {
        val saved = contributions.sum()
        val remaining = (g.targetPaise - saved).coerceAtLeast(0)
        val done = g.targetPaise > 0 && saved >= g.targetPaise
        val percent = if (g.targetPaise <= 0) 0 else (saved.coerceAtLeast(0) * 100 / g.targetPaise).toInt().coerceAtMost(100)
        val date = g.targetDate
        val late = !done && date != null && date.isBefore(today)
        val monthsLeft = if (date == null || late) 0 else ChronoUnit.MONTHS.between(YearMonth.from(today), YearMonth.from(date)).toInt().coerceAtLeast(1)
        val monthly = when {
            done -> 0L
            date == null -> null
            late -> remaining
            else -> (remaining + monthsLeft - 1) / monthsLeft
        }
        val onTrack = done || date == null || late.not() && run {
            val total = ChronoUnit.DAYS.between(g.startDate, date).coerceAtLeast(1)
            val elapsed = ChronoUnit.DAYS.between(g.startDate, today).coerceIn(0, total)
            saved >= g.targetPaise * elapsed / total
        }
        return GoalProgress(g, saved, remaining, percent, monthsLeft, monthly, done, late, onTrack)
    }

    /** Last month's savings as the natural top-up, never negative and never more than what is left. */
    fun suggestedTopUp(lastMonthSavingsPaise: Long, remainingPaise: Long): Long = lastMonthSavingsPaise.coerceIn(0, remainingPaise.coerceAtLeast(0))
}
