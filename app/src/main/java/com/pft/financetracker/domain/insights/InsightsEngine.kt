package com.pft.financetracker.domain.insights

import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.Transaction
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class Period(val start: Long, val end: Long, val label: String) {
    operator fun contains(t: Long) = t >= start && t < end
    val days: Int get() = maxOf(1, ((end - start) / 86_400_000L).toInt())
}

object Periods {
    fun month(offset: Int = 0, now: Long = System.currentTimeMillis()): Period {
        val c = Calendar.getInstance(); c.timeInMillis = now
        c.add(Calendar.MONTH, offset)
        c.set(Calendar.DAY_OF_MONTH, 1); zero(c)
        val start = c.timeInMillis
        val label = String.format(Locale.ENGLISH, "%1\$tb %1\$tY", c)
        c.add(Calendar.MONTH, 1)
        return Period(start, c.timeInMillis, label)
    }

    fun week(offset: Int = 0, now: Long = System.currentTimeMillis()): Period {
        val c = Calendar.getInstance(); c.timeInMillis = now
        c.firstDayOfWeek = Calendar.MONDAY
        c.set(Calendar.DAY_OF_WEEK, Calendar.MONDAY); zero(c)
        c.add(Calendar.WEEK_OF_YEAR, offset)
        val start = c.timeInMillis
        val label = String.format(Locale.ENGLISH, "Wk of %1\$td %1\$tb", c)
        c.add(Calendar.WEEK_OF_YEAR, 1)
        return Period(start, c.timeInMillis, label)
    }

    fun custom(start: Long, endInclusive: Long): Period {
        val c = Calendar.getInstance(); c.timeInMillis = start; zero(c)
        val s = c.timeInMillis
        c.timeInMillis = endInclusive; zero(c); c.add(Calendar.DAY_OF_MONTH, 1)
        val label = String.format(Locale.ENGLISH, "%1\$td %1\$tb – %2\$td %2\$tb", Calendar.getInstance().apply { timeInMillis = s }, Calendar.getInstance().apply { timeInMillis = endInclusive })
        return Period(s, c.timeInMillis, label)
    }

    private fun zero(c: Calendar) {
        c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
    }
}

/** Net spend per category: expenses minus refunds matched to that category. All paise. */
data class CategorySpend(val category: Category, val amountPaise: Long, val count: Int) {
    val amount: Double get() = Money.toRupees(amountPaise)
}

data class MerchantSpend(val merchant: String, val amountPaise: Long, val count: Int, val category: Category)
data class AccountSpend(val label: String, val spendPaise: Long, val incomePaise: Long, val count: Int)

/**
 * Everything the dashboard needs for one period, with the arithmetic visible:
 *   netSpend = grossSpend - refunds
 *   savings  = income - netSpend
 * Transfers, investments and settlements are reported separately and never inside spend or income.
 */
data class PeriodSummary(
    val period: Period,
    val grossSpendPaise: Long,
    val refundsPaise: Long,
    val incomePaise: Long,
    val transfersOutPaise: Long,
    val transfersInPaise: Long,
    val investmentsPaise: Long,
    val cashPaise: Long,
    val byCategory: List<CategorySpend>,
    val byMerchant: List<MerchantSpend>,
    val byAccount: List<AccountSpend>,
    val count: Int,
    val expenseCount: Int,
) {
    val netSpendPaise: Long get() = grossSpendPaise - refundsPaise
    val savingsPaise: Long get() = incomePaise - netSpendPaise
    val spend: Double get() = Money.toRupees(netSpendPaise)
    val income: Double get() = Money.toRupees(incomePaise)

    /** Average per elapsed day (for the current period) or per period day (for closed periods). */
    fun dailyAveragePaise(now: Long = System.currentTimeMillis()): Long {
        val elapsedDays = if (now in period) maxOf(1, ((now - period.start) / 86_400_000L).toInt() + 1) else period.days
        return netSpendPaise / elapsedDays
    }

    /** Straight-line projection to the end of the period. Null once the period is over. */
    fun projectedPaise(now: Long = System.currentTimeMillis()): Long? =
        if (now in period) dailyAveragePaise(now) * period.days else null
}

data class Insight(val title: String, val body: String, val severity: Severity, val category: Category? = null, val magnitude: Int = 0) {
    enum class Severity { INFO, WARN, GOOD }
}

data class BudgetStatus(val budget: Budget, val spentPaise: Long) {
    val spent: Double get() = Money.toRupees(spentPaise)
    val fraction: Float get() = if (budget.monthlyLimitPaise <= 0) 0f else (spentPaise.toDouble() / budget.monthlyLimitPaise).toFloat()
    val over: Boolean get() = spentPaise > budget.monthlyLimitPaise
}

object InsightsEngine {

    /** Flows that count towards spend. Cash is included by default (Settings can exclude it later). */
    fun isSpend(t: Transaction, includeCash: Boolean = true) = t.flow == Flow.EXPENSE || (includeCash && t.flow == Flow.CASH)

    fun summarize(all: List<Transaction>, period: Period, includeCash: Boolean = true): PeriodSummary {
        val inPeriod = all.filter { it.timestamp in period && !it.needsReview }
        val spend = inPeriod.filter { isSpend(it, includeCash) }
        val refunds = inPeriod.filter { it.flow == Flow.REFUND }
        val income = inPeriod.filter { it.flow == Flow.INCOME }
        val transfersOut = inPeriod.filter { (it.flow == Flow.TRANSFER || it.flow == Flow.SETTLEMENT) && it.type == com.pft.financetracker.domain.model.TransactionType.DEBIT }
        val transfersIn = inPeriod.filter { (it.flow == Flow.TRANSFER || it.flow == Flow.SETTLEMENT) && it.type == com.pft.financetracker.domain.model.TransactionType.CREDIT }
        val investments = inPeriod.filter { it.flow == Flow.INVESTMENT && it.type == com.pft.financetracker.domain.model.TransactionType.DEBIT }
        val cash = inPeriod.filter { it.flow == Flow.CASH }

        // Refunds reduce the category they came from when the merchant matches a spend in this period,
        // otherwise they reduce the total only.
        val refundByCat = mutableMapOf<Category, Long>()
        for (r in refunds) {
            val key = normalizeMerchant(r.merchant)
            val match = spend.firstOrNull { normalizeMerchant(it.merchant) == key }
            val cat = match?.category ?: r.category.takeIf { it != Category.INCOME && it != Category.OTHER } ?: continue
            refundByCat[cat] = (refundByCat[cat] ?: 0L) + r.amountPaise
        }
        val byCat = spend.groupBy { it.category }
            .map { (c, list) -> CategorySpend(c, list.sumOf { it.amountPaise } - (refundByCat[c] ?: 0L), list.size) }
            .filter { it.amountPaise != 0L }
            .sortedByDescending { it.amountPaise }

        val byMerchant = spend.groupBy { normalizeMerchant(it.merchant) }
            .map { (_, list) -> MerchantSpend(list.first().merchant, list.sumOf { it.amountPaise }, list.size, list.groupBy { it.category }.maxBy { it.value.size }.key) }
            .sortedByDescending { it.amountPaise }

        val byAccount = inPeriod.filter { it.accountRef != null || it.bankName != null }
            .groupBy { listOfNotNull(it.bankName, it.accountRef?.let { a -> "••$a" }).joinToString(" ") }
            .map { (label, list) ->
                AccountSpend(label, list.filter { isSpend(it, includeCash) }.sumOf { it.amountPaise }, list.filter { it.flow == Flow.INCOME }.sumOf { it.amountPaise }, list.size)
            }
            .sortedByDescending { it.spendPaise }

        return PeriodSummary(
            period = period,
            grossSpendPaise = spend.sumOf { it.amountPaise },
            refundsPaise = refunds.sumOf { it.amountPaise },
            incomePaise = income.sumOf { it.amountPaise },
            transfersOutPaise = transfersOut.sumOf { it.amountPaise },
            transfersInPaise = transfersIn.sumOf { it.amountPaise },
            investmentsPaise = investments.sumOf { it.amountPaise },
            cashPaise = cash.sumOf { it.amountPaise },
            byCategory = byCat,
            byMerchant = byMerchant,
            byAccount = byAccount,
            count = inPeriod.size,
            expenseCount = spend.size,
        )
    }

    /** The transactions behind one number on the dashboard, so any total can be checked by hand. */
    enum class Bucket { SPEND, REFUNDS, INCOME, TRANSFERS, INVESTMENTS, CASH, ALL }

    fun drillDown(all: List<Transaction>, period: Period, bucket: Bucket, category: Category? = null): List<Transaction> {
        val inPeriod = all.filter { it.timestamp in period && !it.needsReview }
        val byBucket = when (bucket) {
            Bucket.SPEND -> inPeriod.filter { isSpend(it) }
            Bucket.REFUNDS -> inPeriod.filter { it.flow == Flow.REFUND }
            Bucket.INCOME -> inPeriod.filter { it.flow == Flow.INCOME }
            Bucket.TRANSFERS -> inPeriod.filter { it.flow == Flow.TRANSFER || it.flow == Flow.SETTLEMENT }
            Bucket.INVESTMENTS -> inPeriod.filter { it.flow == Flow.INVESTMENT }
            Bucket.CASH -> inPeriod.filter { it.flow == Flow.CASH }
            Bucket.ALL -> inPeriod
        }
        return if (category == null) byBucket else byBucket.filter { it.category == category }
    }

    fun budgetStatus(all: List<Transaction>, budgets: List<Budget>, period: Period = Periods.month()): List<BudgetStatus> {
        val s = summarize(all, period)
        return budgets.map { b -> BudgetStatus(b, s.byCategory.firstOrNull { it.category == b.category }?.amountPaise ?: 0L) }
            .sortedByDescending { it.fraction }
    }

    /** Category-level comparison between two periods, e.g. "20% more on food". */
    fun categoryTrends(all: List<Transaction>, current: Period, previous: Period): List<Insight> {
        val cur = summarize(all, current)
        val prev = summarize(all, previous)
        val out = mutableListOf<Insight>()
        for (cs in cur.byCategory) {
            val p = prev.byCategory.firstOrNull { it.category == cs.category }?.amountPaise ?: 0L
            if (p < 20_000 && cs.amountPaise < 50_000) continue
            if (p == 0L) {
                out += Insight("New spending: ${cs.category.label}", "₹${fmt(cs.amountPaise)} this period, nothing last period.", Insight.Severity.INFO, cs.category, 0)
                continue
            }
            val pct = ((cs.amountPaise - p).toDouble() / p * 100).roundToInt()
            if (abs(pct) < 10) continue
            if (pct > 0) out += Insight("${cs.category.label} up $pct%", "You spent ₹${fmt(cs.amountPaise)} vs ₹${fmt(p)} last period.", Insight.Severity.WARN, cs.category, pct)
            else out += Insight("${cs.category.label} down ${-pct}%", "₹${fmt(cs.amountPaise)} vs ₹${fmt(p)} last period. Nice.", Insight.Severity.GOOD, cs.category, -pct)
        }
        return out.sortedByDescending { it.magnitude }
    }

    /** Actionable "reduce spending" suggestions computed locally. */
    fun suggestions(all: List<Transaction>, budgets: List<Budget>, now: Long = System.currentTimeMillis()): List<Insight> {
        val out = mutableListOf<Insight>()
        val ninetyDays = all.filter { isSpend(it) && !it.needsReview && it.timestamp > now - 90L * 24 * 3600 * 1000 }
        if (ninetyDays.isEmpty()) return out

        // 1. Recurring subscriptions: same merchant, similar amount, >= 2 months.
        ninetyDays.groupBy { normalizeMerchant(it.merchant) }.forEach { (_, list) ->
            if (list.size < 2) return@forEach
            val amounts = list.map { it.amountPaise.toDouble() }
            val avg = amounts.average()
            val similar = amounts.all { abs(it - avg) / avg < 0.15 }
            val months = list.map { monthKey(it.timestamp) }.distinct().size
            if (similar && months >= 2 && list.size <= 4 && avg >= 5_000) {
                out += Insight(
                    "Recurring: ${list.first().merchant}",
                    "≈₹${fmt(avg.toLong())} charged in $months of the last 3 months. Still using it? Cancelling saves ₹${fmt((avg * 12).toLong())}/yr.",
                    Insight.Severity.WARN, list.first().category
                )
            }
        }

        // 2. High-frequency small spends.
        val month = Periods.month(now = now)
        val thisMonth = ninetyDays.filter { it.timestamp in month }
        val small = thisMonth.filter { it.amountPaise in 100L..30_000L }
        if (small.size >= 15) {
            val total = small.sumOf { it.amountPaise }
            val topCat = small.groupBy { it.category }.maxByOrNull { it.value.size }?.key
            out += Insight(
                "${small.size} small spends add up to ₹${fmt(total)}",
                "Purchases under ₹300 this month" + (topCat?.let { ", mostly ${it.label.lowercase(Locale.ROOT)}" } ?: "") + ". Batching them could cut this noticeably.",
                Insight.Severity.WARN, topCat
            )
        }

        // 3. Categories trending up vs previous month.
        out += categoryTrends(all, month, Periods.month(-1, now)).filter { it.severity == Insight.Severity.WARN }.take(3)

        // 4. Biggest single merchant this month.
        thisMonth.groupBy { normalizeMerchant(it.merchant) }.maxByOrNull { e -> e.value.sumOf { it.amountPaise } }?.let { (_, list) ->
            val total = list.sumOf { it.amountPaise }
            val monthTotal = thisMonth.sumOf { it.amountPaise }
            if (monthTotal > 0 && total.toDouble() / monthTotal > 0.25 && list.size > 1) {
                out += Insight(
                    "${list.first().merchant} is ${(total.toDouble() / monthTotal * 100).roundToInt()}% of this month",
                    "${list.size} transactions totalling ₹${fmt(total)}. Worth a second look.",
                    Insight.Severity.INFO, list.first().category
                )
            }
        }

        // 5. Budget overspend.
        budgetStatus(all, budgets, month).filter { it.over }.forEach {
            out += Insight(
                "Over budget: ${it.budget.category.label}",
                "₹${fmt(it.spentPaise)} spent of ₹${fmt(it.budget.monthlyLimitPaise)} budget (${(it.fraction * 100).roundToInt()}%).",
                Insight.Severity.WARN, it.budget.category
            )
        }

        // 6. Weekend vs weekday food.
        val food = thisMonth.filter { it.category == Category.FOOD }
        if (food.size >= 6) {
            val weekend = food.filter { isWeekend(it.timestamp) }.sumOf { it.amountPaise }
            val total = food.sumOf { it.amountPaise }
            if (total > 0 && weekend.toDouble() / total > 0.5) {
                out += Insight("Weekend food is ${(weekend.toDouble() / total * 100).roundToInt()}% of food spend", "Planning weekend meals could be the easiest saving.", Insight.Severity.INFO, Category.FOOD)
            }
        }

        return out.distinctBy { it.title }
    }

    /** Merchant key for grouping. Strips noise and keeps enough characters to tell "Amazon Pay" from "Amazon Prime". */
    fun normalizeMerchant(m: String) = m.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "").take(20)

    private fun monthKey(t: Long): Int {
        val c = Calendar.getInstance(); c.timeInMillis = t
        return c.get(Calendar.YEAR) * 12 + c.get(Calendar.MONTH)
    }

    private fun isWeekend(t: Long): Boolean {
        val c = Calendar.getInstance(); c.timeInMillis = t
        val d = c.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    /** Compact rupee formatting from paise: 1,234 or 1.2L. */
    fun fmt(paise: Long): String {
        val r = Math.round(paise / 100.0)
        return if (r >= 100000) String.format(Locale.ENGLISH, "%.1fL", r / 100000.0)
        else String.format(Locale.ENGLISH, "%,d", r)
    }
}
