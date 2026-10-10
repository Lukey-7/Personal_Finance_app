package com.pft.financetracker.domain.insights

import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.Rupees
import com.pft.financetracker.domain.model.Transaction
import java.util.Calendar
import java.util.Locale

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
        val label = String.format(Locale.ENGLISH, "Week of %1\$te %1\$tb", c)
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
        val sameDay = from.get(Calendar.YEAR) == to.get(Calendar.YEAR) && from.get(Calendar.DAY_OF_YEAR) == to.get(Calendar.DAY_OF_YEAR)
        val label = if (sameDay || to.before(from)) String.format(Locale.ENGLISH, "%1\$te %1\$tb", from)
        else if (from.get(Calendar.MONTH) == to.get(Calendar.MONTH)) String.format(Locale.ENGLISH, "%1\$te–%2\$te %2\$tb", from, to)
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
    /** Cash withdrawn when the counting rules leave cash out of spend; 0 when cash counts as spend. */
    val cashNotSpendPaise: Long = 0,
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
    /**
     * The transactions behind one number on the dashboard, so any total can be checked by hand. Each figure Home shows
     * has its own bucket, whose payments add up to exactly that figure.
     */
    enum class Bucket {
        SPEND, REFUNDS, INCOME,
        /** Money moved out: transfers, card bill payments, settlements paid. */
        TRANSFERS_OUT,
        /** Transfers in, not counting friends paying back a split. */
        TRANSFERS_IN,
        /** Friends paying back their share of a split. */
        PAID_BACK,
        /** Money put into investments (redemptions are not part of the figure). */
        INVESTMENTS,
        CASH, ALL,
    }

    /** Merchant key for grouping. Strips noise and keeps enough characters to tell "Amazon Pay" from "Amazon Prime". */
    fun normalizeMerchant(m: String) = m.lowercase(Locale.ROOT).replace(NonAlnum, "").take(20)
    private val NonAlnum = Regex("""[^a-z0-9]""")

    /** Whole-number change from [before] to [now]; null when either is nothing, since "100% less" says nothing. */
    fun changePercent(now: Long, before: Long): Int? =
        if (now <= 0 || before <= 0) null else Math.round((now - before) * 100.0 / before).toInt()

    /** A rupee figure in the app's one format, sign before the ₹: ₹1,05,000, -₹500, ₹1,234.50. */
    fun rupees(paise: Long): String = Rupees.format(paise)
}
