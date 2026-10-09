package com.pft.financetracker.ui.model

import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.insights.Periods

/**
 * The period a drill-down opened from another screen covers. The route carries its bounds ([from] inclusive, [to]
 * exclusive, both millis); they are named the way people read them: a whole month as "Oct 2026", a whole week as
 * "Week of 5 Oct", anything else as its days. Null when the route carries no bounds, so the list follows Home.
 */
object DrillPeriod {
    fun of(from: Long, to: Long, now: Long = System.currentTimeMillis()): Period? {
        if (from < 0 || to <= from) return null
        val month = Periods.month(0, from)
        if (month.start == from && month.end == to) return month
        val week = Periods.week(0, from)
        if (week.start == from && week.end == to) return week
        return Periods.custom(from, to - 1, now).copy(start = from, end = to)
    }
}
