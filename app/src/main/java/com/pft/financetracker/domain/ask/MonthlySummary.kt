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
        lines += "You spent **₹${fmt(spend)}** in ${cur.period.label}" + when {
            before <= 0 -> "."
            spend >= before -> ", ${pct(spend - before, before)}% more than ${prev.period.label} (₹${fmt(before)})."
            else -> ", ${pct(before - spend, before)}% less than ${prev.period.label} (₹${fmt(before)})."
        }
        if (cur.incomePaise > 0) {
            lines += if (cur.savingsPaise >= 0) "Income ₹${fmt(cur.incomePaise)}, so you saved **₹${fmt(cur.savingsPaise)}** (${pct(cur.savingsPaise, cur.incomePaise)}% of it)."
            else "Income ₹${fmt(cur.incomePaise)}: you spent ₹${fmt(-cur.savingsPaise)} more than came in."
        }
        val prevByCat = prev.byCategory.associate { it.category to it.amountPaise }
        cur.byCategory.mapNotNull { c -> prevByCat[c.category]?.takeIf { it > 0 && c.amountPaise > it }?.let { Triple(c.category, c.amountPaise, it) } }
            .maxByOrNull { (_, now, then) -> (now - then).toDouble() / then }
            ?.let { (cat, now, then) -> lines += "${cat.label} is up ${pct(now - then, then)}% (₹${fmt(now)} against ₹${fmt(then)})." }
        budgets.forEach { b ->
            val spent = cur.byCategory.firstOrNull { it.category == b.category }?.amountPaise ?: 0L
            if (spent > b.monthlyLimitPaise) lines += "${b.category.label} is ₹${fmt(spent - b.monthlyLimitPaise)} over its budget."
        }
        if (recurring.monthlyPaise > 0) lines += "Subscriptions and other repeating charges cost ₹${fmt(recurring.monthlyPaise)} a month."
        return lines.joinToString("\n") { "- $it" }
    }

    private fun pct(part: Long, whole: Long) = if (whole == 0L) 0 else (part.toDouble() / whole * 100).roundToInt()
    private fun fmt(p: Long) = InsightsEngine.fmt(p)
}
