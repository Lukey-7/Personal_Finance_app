package com.pft.financetracker.domain.ask

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.PeriodSummary
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.recurring.RecurringBook
import kotlin.math.roundToInt

/**
 * A short written summary of a month, put together on the phone from the same numbers as the Home tab: spend and how
 * it moved, savings, the category that grew most, budgets crossed and what subscriptions cost. No key, no network.
 */
object MonthlySummary {
    fun write(cur: PeriodSummary, prev: PeriodSummary, budgets: List<Budget>, recurring: RecurringBook): String {
        val lines = mutableListOf<String>()
        val spend = cur.netSpendPaise
        val before = prev.netSpendPaise
        lines += if (spend <= 0) "Nothing spent yet in ${cur.period.label}" + (if (before > 0) " (${prev.period.label}: ${rupees(before)})." else ".")
        else "You spent **${rupees(spend)}** in ${cur.period.label}" + when {
            before <= 0 -> "."
            spend >= before -> ", ${pct(spend - before, before)}% more than ${prev.period.label} (${rupees(before)})."
            else -> ", ${pct(before - spend, before)}% less than ${prev.period.label} (${rupees(before)})."
        }
        if (cur.incomePaise > 0) {
            lines += if (cur.savingsPaise >= 0) "Income ${rupees(cur.incomePaise)}, so you saved **${rupees(cur.savingsPaise)}** (${pct(cur.savingsPaise, cur.incomePaise)}% of it)."
            else "Income ${rupees(cur.incomePaise)}: you spent ${rupees(-cur.savingsPaise)} more than came in."
        }
        val prevByCat = prev.byCategory.associate { it.category to it.amountPaise }
        cur.byCategory.mapNotNull { c -> prevByCat[c.category]?.takeIf { it > 0 && c.amountPaise > it }?.let { Triple(c.category, c.amountPaise, it) } }
            .maxByOrNull { (_, now, then) -> (now - then).toDouble() / then }
            ?.let { (cat, now, then) -> lines += "${cat.label} is up ${pct(now - then, then)}% (${rupees(now)} against ${rupees(then)})." }
        budgets.forEach { b ->
            val spent = cur.byCategory.firstOrNull { it.category == b.category }?.amountPaise ?: 0L
            if (spent > b.monthlyLimitPaise) lines += "${b.category.label} is ${rupees(spent - b.monthlyLimitPaise)} over its budget."
        }
        if (recurring.monthlyPaise > 0) lines += "Subscriptions and other repeating charges cost ${rupees(recurring.monthlyPaise)} a month."
        return lines.joinToString("\n") { "- $it" }
    }

    private fun pct(part: Long, whole: Long) = if (whole == 0L) 0 else (part.toDouble() / whole * 100).roundToInt()
    private fun rupees(p: Long) = InsightsEngine.rupees(p)
}
