package com.pft.financetracker.domain.insights

import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class Period(val start: Long, val end: Long, val label: String)

object Periods {
    fun month(offset: Int = 0): Period {
        val c = Calendar.getInstance()
        c.add(Calendar.MONTH, offset)
        c.set(Calendar.DAY_OF_MONTH, 1); zero(c)
        val start = c.timeInMillis
        val label = String.format(Locale.ENGLISH, "%1\$tb %1\$tY", c)
        c.add(Calendar.MONTH, 1)
        return Period(start, c.timeInMillis, label)
    }

    fun week(offset: Int = 0): Period {
        val c = Calendar.getInstance()
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); zero(c)
        c.add(Calendar.WEEK_OF_YEAR, offset)
        val start = c.timeInMillis
        val label = String.format(Locale.ENGLISH, "Wk of %1\$td %1\$tb", c)
        c.add(Calendar.WEEK_OF_YEAR, 1)
        return Period(start, c.timeInMillis, label)
    }

    private fun zero(c: Calendar) {
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    }
}

data class CategorySpend(val category: Category, val amount: Double, val count: Int)

data class PeriodSummary(
    val period: Period,
    val spend: Double,
    val income: Double,
    val byCategory: List<CategorySpend>,
    val count: Int,
)

data class Insight(val title: String, val body: String, val severity: Severity, val category: Category? = null) {
    enum class Severity { INFO, WARN, GOOD }
}

data class BudgetStatus(val budget: Budget, val spent: Double) {
    val fraction: Float get() = if (budget.monthlyLimit <= 0) 0f else (spent / budget.monthlyLimit).toFloat()
    val over: Boolean get() = spent > budget.monthlyLimit
}

object InsightsEngine {

    fun summarize(all: List<Transaction>, period: Period): PeriodSummary {
        val inPeriod = all.filter { it.timestamp >= period.start && it.timestamp < period.end && !it.needsReview }
        val debits = inPeriod.filter { it.type == TransactionType.DEBIT }
        val credits = inPeriod.filter { it.type == TransactionType.CREDIT }
        val byCat = debits.groupBy { it.category }
            .map { (c, list) -> CategorySpend(c, list.sumOf { it.amount }, list.size) }
            .sortedByDescending { it.amount }
        return PeriodSummary(period, debits.sumOf { it.amount }, credits.sumOf { it.amount }, byCat, inPeriod.size)
    }

    fun budgetStatus(all: List<Transaction>, budgets: List<Budget>, period: Period = Periods.month()): List<BudgetStatus> {
        val s = summarize(all, period)
        return budgets.map { b -> BudgetStatus(b, s.byCategory.firstOrNull { it.category == b.category }?.amount ?: 0.0) }
            .sortedByDescending { it.fraction }
    }

    /** Category-level comparison between two periods, e.g. "20% more on food". */
    fun categoryTrends(all: List<Transaction>, current: Period, previous: Period): List<Insight> {
        val cur = summarize(all, current)
        val prev = summarize(all, previous)
        val out = mutableListOf<Insight>()
        for (cs in cur.byCategory) {
            val p = prev.byCategory.firstOrNull { it.category == cs.category }?.amount ?: 0.0
            if (p < 200 && cs.amount < 500) continue
            if (p == 0.0) {
                out += Insight("New spending: ${cs.category.label}", "₹${fmt(cs.amount)} this period, nothing last period.", Insight.Severity.INFO, cs.category)
                continue
            }
            val pct = ((cs.amount - p) / p * 100).roundToInt()
            if (abs(pct) < 10) continue
            if (pct > 0) out += Insight("${cs.category.label} up $pct%", "You spent ₹${fmt(cs.amount)} vs ₹${fmt(p)} last period.", Insight.Severity.WARN, cs.category)
            else out += Insight("${cs.category.label} down ${-pct}%", "₹${fmt(cs.amount)} vs ₹${fmt(p)} last period. Nice.", Insight.Severity.GOOD, cs.category)
        }
        return out.sortedByDescending { abs((it.title.filter { ch -> ch.isDigit() }).toIntOrNull() ?: 0) }
    }

    /** Actionable "reduce spending" suggestions computed locally. */
    fun suggestions(all: List<Transaction>, budgets: List<Budget>): List<Insight> {
        val out = mutableListOf<Insight>()
        val now = System.currentTimeMillis()
        val ninetyDays = all.filter { it.type == TransactionType.DEBIT && !it.needsReview && it.timestamp > now - 90L * 24 * 3600 * 1000 }
        if (ninetyDays.isEmpty()) return out

        // 1. Recurring subscriptions: same merchant, similar amount, >= 2 months.
        ninetyDays.groupBy { normalizeMerchant(it.merchant) }.forEach { (m, list) ->
            if (list.size < 2) return@forEach
            val amounts = list.map { it.amount }
            val avg = amounts.average()
            val similar = amounts.all { abs(it - avg) / avg < 0.15 }
            val months = list.map { monthKey(it.timestamp) }.distinct().size
            if (similar && months >= 2 && list.size <= 4 && avg >= 50) {
                out += Insight(
                    "Recurring: ${list.first().merchant}",
                    "≈₹${fmt(avg)} charged in $months of the last 3 months. Still using it? Cancelling saves ₹${fmt(avg * 12)}/yr.",
                    Insight.Severity.WARN, list.first().category
                )
            }
        }

        // 2. High-frequency small spends.
        val month = Periods.month()
        val thisMonth = ninetyDays.filter { it.timestamp >= month.start }
        val small = thisMonth.filter { it.amount in 1.0..300.0 }
        if (small.size >= 15) {
            val total = small.sumOf { it.amount }
            val topCat = small.groupBy { it.category }.maxByOrNull { it.value.size }?.key
            out += Insight(
                "${small.size} small spends add up to ₹${fmt(total)}",
                "Purchases under ₹300 this month" + (topCat?.let { ", mostly ${it.label.lowercase(Locale.ROOT)}" } ?: "") + ". Batching them could cut this noticeably.",
                Insight.Severity.WARN, topCat
            )
        }

        // 3. Categories trending up vs previous month.
        out += categoryTrends(all, month, Periods.month(-1)).filter { it.severity == Insight.Severity.WARN }.take(3)

        // 4. Biggest single merchant this month.
        thisMonth.groupBy { normalizeMerchant(it.merchant) }.maxByOrNull { e -> e.value.sumOf { it.amount } }?.let { (_, list) ->
            val total = list.sumOf { it.amount }
            val monthTotal = thisMonth.sumOf { it.amount }
            if (monthTotal > 0 && total / monthTotal > 0.25 && list.size > 1) {
                out += Insight(
                    "${list.first().merchant} is ${(total / monthTotal * 100).roundToInt()}% of this month",
                    "${list.size} transactions totalling ₹${fmt(total)}. Worth a second look.",
                    Insight.Severity.INFO, list.first().category
                )
            }
        }

        // 5. Budget overspend.
        budgetStatus(all, budgets).filter { it.over }.forEach {
            out += Insight(
                "Over budget: ${it.budget.category.label}",
                "₹${fmt(it.spent)} spent of ₹${fmt(it.budget.monthlyLimit)} budget (${(it.fraction * 100).roundToInt()}%).",
                Insight.Severity.WARN, it.budget.category
            )
        }

        // 6. Weekend vs weekday food.
        val food = thisMonth.filter { it.category == Category.FOOD }
        if (food.size >= 6) {
            val weekend = food.filter { isWeekend(it.timestamp) }.sumOf { it.amount }
            val total = food.sumOf { it.amount }
            if (total > 0 && weekend / total > 0.5) {
                out += Insight("Weekend food is ${(weekend / total * 100).roundToInt()}% of food spend", "Planning weekend meals could be the easiest saving.", Insight.Severity.INFO, Category.FOOD)
            }
        }

        return out.distinctBy { it.title }
    }

    fun normalizeMerchant(m: String) = m.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "").take(12)

    private fun monthKey(t: Long): Int {
        val c = Calendar.getInstance(); c.timeInMillis = t
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    private fun isWeekend(t: Long): Boolean {
        val c = Calendar.getInstance(); c.timeInMillis = t
        val d = c.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    fun fmt(v: Double): String {
        val r = v.roundToInt()
        return if (r >= 100000) String.format(Locale.ENGLISH, "%.1fL", r / 100000.0)
        else String.format(Locale.ENGLISH, "%,d", r)
    }
}
