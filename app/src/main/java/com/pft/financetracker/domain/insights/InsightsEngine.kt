package com.pft.financetracker.domain.insights

import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.recurring.Period as RecurringPeriod
import com.pft.financetracker.domain.recurring.RecurringDetector
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.Rupees
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

    fun custom(start: Long, endInclusive: Long, now: Long = System.currentTimeMillis()): Period {
        val c = Calendar.getInstance(); c.timeInMillis = start; zero(c)
        val s = c.timeInMillis
        c.timeInMillis = endInclusive; zero(c); c.add(Calendar.DAY_OF_MONTH, 1)
        return Period(s, c.timeInMillis, rangeLabel(s, c.timeInMillis - 1, now))
    }

    /** The same number of whole days straight before [p]: 1–10 Jul compares with 21–30 Jun. */
    fun before(p: Period, now: Long = System.currentTimeMillis()): Period {
        val c = Calendar.getInstance(); c.timeInMillis = p.start
        c.add(Calendar.DAY_OF_MONTH, -p.days)
        return Period(c.timeInMillis, p.start, rangeLabel(c.timeInMillis, p.start - 1, now))
    }

    /** "01 Jul – 31 Jul", with the year when it is not this one: "01 Jul – 31 Jul 2025". */
    private fun rangeLabel(start: Long, lastMoment: Long, now: Long): String {
        val a = Calendar.getInstance().apply { timeInMillis = start }
        val b = Calendar.getInstance().apply { timeInMillis = lastMoment }
        val year = Calendar.getInstance().apply { timeInMillis = now }.get(Calendar.YEAR)
        val base = String.format(Locale.ENGLISH, "%1\$td %1\$tb – %2\$td %2\$tb", a, b)
        return if (b.get(Calendar.YEAR) == year && a.get(Calendar.YEAR) == year) base else base + String.format(Locale.ENGLISH, " %1\$tY", b)
    }

    /**
     * The stretch of [previous] that matches how far [current] has run at [now]: six days into October compares with
     * 1–6 Sep, not all of September. A finished [current] compares with the whole of [previous].
     */
    fun sameSpanBefore(current: Period, previous: Period, now: Long): Period {
        if (now !in current) return previous
        val end = minOf(previous.end, previous.start + (now - current.start))
        val from = Calendar.getInstance().apply { timeInMillis = previous.start }
        val to = Calendar.getInstance().apply { timeInMillis = end - 1 }
        val label = if (from.get(Calendar.MONTH) == to.get(Calendar.MONTH)) String.format(Locale.ENGLISH, "%1\$te–%2\$te %2\$tb", from, to)
        else String.format(Locale.ENGLISH, "%1\$te %1\$tb – %2\$te %2\$tb", from, to)
        return Period(previous.start, end, label)
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
    /** Part of [transfersInPaise]: friends paying back their share of a split. */
    val settlementsInPaise: Long = 0,
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
        val spendCats = spendCategoryByMerchant(spend)
        for (r in refunds) {
            val cat = refundCategory(r, spendCats) ?: continue
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
            settlementsInPaise = transfersIn.filter { it.flow == Flow.SETTLEMENT }.sumOf { it.amountPaise },
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

    /** First spend's category per merchant key, for matching refunds to what they refund. */
    private fun spendCategoryByMerchant(spend: List<Transaction>): Map<String, Category> {
        val m = HashMap<String, Category>()
        for (t in spend) m.putIfAbsent(normalizeMerchant(t.merchant), t.category)
        return m
    }

    /** The category a refund reduces: the one its merchant was spent in, else its own; null when it fits none. */
    private fun refundCategory(r: Transaction, spendCats: Map<String, Category>): Category? =
        spendCats[normalizeMerchant(r.merchant)] ?: r.category.takeIf { it != Category.INCOME && it != Category.OTHER }

    /**
     * The payments behind one figure. Spend includes the refunds that reduce it (all of them, or those matched to
     * [category]), so [drillTotal] of the list is exactly the net figure Home shows.
     */
    fun drillDown(all: List<Transaction>, period: Period, bucket: Bucket, category: Category? = null, includeCash: Boolean = true): List<Transaction> {
        val inPeriod = all.filter { it.timestamp in period && !it.needsReview }
        if (bucket == Bucket.SPEND) {
            val spend = inPeriod.filter { isSpend(it, includeCash) }
            val spendCats = spendCategoryByMerchant(spend)
            val refunds = inPeriod.filter { it.flow == Flow.REFUND }
            return if (category == null) inPeriod.filter { isSpend(it, includeCash) || it.flow == Flow.REFUND }
            else inPeriod.filter { (isSpend(it, includeCash) && it.category == category) || (it.flow == Flow.REFUND && it in refunds && refundCategory(it, spendCats) == category) }
        }
        val byBucket = when (bucket) {
            Bucket.SPEND -> inPeriod.filter { isSpend(it, includeCash) }
            Bucket.REFUNDS -> inPeriod.filter { it.flow == Flow.REFUND }
            Bucket.INCOME -> inPeriod.filter { it.flow == Flow.INCOME }
            Bucket.TRANSFERS -> inPeriod.filter { it.flow == Flow.TRANSFER || it.flow == Flow.SETTLEMENT }
            Bucket.INVESTMENTS -> inPeriod.filter { it.flow == Flow.INVESTMENT }
            Bucket.CASH -> inPeriod.filter { it.flow == Flow.CASH }
            Bucket.ALL -> inPeriod
        }
        return if (category == null) byBucket else byBucket.filter { it.category == category }
    }

    /** The figure a drill-down list adds up to: for spend, payments minus the refunds listed with them. */
    fun drillTotal(list: List<Transaction>, bucket: Bucket): Long =
        if (bucket == Bucket.SPEND) list.sumOf { if (it.flow == Flow.REFUND) -it.amountPaise else it.amountPaise }
        else list.sumOf { it.amountPaise }

    fun budgetStatus(all: List<Transaction>, budgets: List<Budget>, period: Period = Periods.month(), includeCash: Boolean = true): List<BudgetStatus> {
        val s = summarize(all, period, includeCash)
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
                out += Insight("New spending: ${cs.category.label}", "${rupees(cs.amountPaise)} this period, nothing last period.", Insight.Severity.INFO, cs.category, 0)
                continue
            }
            val pct = ((cs.amountPaise - p).toDouble() / p * 100).roundToInt()
            if (abs(pct) < 10) continue
            if (pct > 0) out += Insight("${cs.category.label} up $pct%", "You spent ${rupees(cs.amountPaise)} vs ${rupees(p)} last period.", Insight.Severity.WARN, cs.category, pct)
            else out += Insight("${cs.category.label} down ${-pct}%", "${rupees(cs.amountPaise)} vs ${rupees(p)} last period. Nice.", Insight.Severity.GOOD, cs.category, -pct)
        }
        return out.sortedByDescending { it.magnitude }
    }

    /** Actionable "reduce spending" suggestions computed locally. */
    fun suggestions(all: List<Transaction>, budgets: List<Budget>, now: Long = System.currentTimeMillis()): List<Insight> {
        val out = mutableListOf<Insight>()
        val ninetyDays = all.filter { isSpend(it) && !it.needsReview && it.timestamp > now - 90L * 24 * 3600 * 1000 }
        if (ninetyDays.isEmpty()) return out

        // 1. Subscriptions and other repeating charges (weekly to yearly), and any that just got pricier.
        RecurringDetector.detect(all, now).filter { it.active && it.period != RecurringPeriod.UNKNOWN && it.amountPaise >= 5_000 }.forEach { r ->
            r.priceRise?.let { p ->
                out += Insight(
                    "${r.merchant} went up",
                    "Now ${rupees(p.toPaise)} instead of ${rupees(p.fromPaise)} (${r.period.label.lowercase(Locale.ROOT)}). That is ${rupees(r.yearlyPaise - r.yearlyPaise * p.fromPaise / p.toPaise)} more a year.",
                    Insight.Severity.WARN, r.category
                )
            }
            out += Insight(
                "Recurring: ${r.merchant}",
                "${rupees(r.amountPaise)} ${r.period.label.lowercase(Locale.ROOT)}, ${rupees(r.yearlyPaise)} a year. Still using it?",
                Insight.Severity.WARN, r.category
            )
        }

        // 2. High-frequency small spends.
        val month = Periods.month(now = now)
        val thisMonth = ninetyDays.filter { it.timestamp in month }
        val small = thisMonth.filter { it.amountPaise in 100L..30_000L }
        if (small.size >= 15) {
            val total = small.sumOf { it.amountPaise }
            val topCat = small.groupBy { it.category }.maxByOrNull { it.value.size }?.key
            out += Insight(
                "${small.size} small spends add up to ${rupees(total)}",
                "Purchases under ₹300 this month" + (topCat?.let { ", mostly ${it.label.lowercase(Locale.ROOT)}" } ?: "") + ". Batching them could cut this noticeably.",
                Insight.Severity.WARN, topCat
            )
        }

        // 3. Categories trending up vs previous month.
        out += categoryTrends(all, month, Periods.sameSpanBefore(month, Periods.month(-1, now), now)).filter { it.severity == Insight.Severity.WARN }.take(3)

        // 4. Biggest single merchant this month.
        thisMonth.groupBy { normalizeMerchant(it.merchant) }.maxByOrNull { e -> e.value.sumOf { it.amountPaise } }?.let { (_, list) ->
            val total = list.sumOf { it.amountPaise }
            val monthTotal = thisMonth.sumOf { it.amountPaise }
            if (monthTotal > 0 && total.toDouble() / monthTotal > 0.25 && list.size > 1) {
                out += Insight(
                    "${list.first().merchant} is ${(total.toDouble() / monthTotal * 100).roundToInt()}% of this month",
                    "${list.size} transactions totalling ${rupees(total)}. Worth a second look.",
                    Insight.Severity.INFO, list.first().category
                )
            }
        }

        // 5. Budget overspend.
        budgetStatus(all, budgets, month).filter { it.over }.forEach {
            out += Insight(
                "Over budget: ${it.budget.category.label}",
                "${rupees(it.spentPaise)} spent of ${rupees(it.budget.monthlyLimitPaise)} budget (${(it.fraction * 100).roundToInt()}%).",
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
    fun normalizeMerchant(m: String) = m.lowercase(Locale.ROOT).replace(NonAlnum, "").take(20)
    private val NonAlnum = Regex("""[^a-z0-9]""")

    private fun isWeekend(t: Long): Boolean {
        val c = Calendar.getInstance(); c.timeInMillis = t
        val d = c.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }

    /** Whole-number change from [before] to [now]; null when either is nothing, since "100% less" says nothing. */
    fun changePercent(now: Long, before: Long): Int? =
        if (now <= 0 || before <= 0) null else Math.round((now - before) * 100.0 / before).toInt()

    /** A rupee figure in the app's one format, sign before the ₹: ₹1,05,000, -₹500, ₹1,234.50. */
    fun rupees(paise: Long): String = Rupees.format(paise)
}
