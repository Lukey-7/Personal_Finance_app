package com.pft.financetracker.domain.books

import com.pft.financetracker.domain.insights.BudgetStatus
import com.pft.financetracker.domain.insights.CategorySpend
import com.pft.financetracker.domain.insights.Insight
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.insights.MerchantSpend
import com.pft.financetracker.domain.insights.AccountSpend
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.insights.PeriodSummary
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.recurring.Period as RecurringPeriod
import com.pft.financetracker.domain.recurring.RecurringDetector
import java.util.Calendar
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs
import kotlin.math.roundToInt

/** What counts as spend and income. Today one choice: whether ATM cash counts as spend (Settings). */
data class CountingRules(val cashIsSpend: Boolean = true) {
    fun isSpend(t: Transaction) = t.flow == Flow.EXPENSE || (cashIsSpend && t.flow == Flow.CASH)
}

/**
 * The books: the payments read through the counting rules. Every figure the app shows (Home, Insights, budgets, the
 * drill-down, Ask, the widget, goals, the AI summary) comes from here, so no two screens can count differently, and
 * every figure can list the payments behind it ([payments]) that add up to it ([total]).
 *
 * Built once per change of payments or rules ([of]); immutable after that, so summaries are worked out once per period.
 */
class Books private constructor(
    /** Every payment, newest first, as the ledger holds them. */
    val all: List<Transaction>,
    val rules: CountingRules,
) {
    private val summaries = ConcurrentHashMap<Period, PeriodSummary>()

    /** Whether [t] counts towards spend under these books' rules. */
    fun isSpend(t: Transaction) = rules.isSpend(t)

    /** Every figure for [period]. Worked out once per period; the books never change. */
    fun summary(period: Period): PeriodSummary = summaries.getOrPut(period) { summarize(period) }

    private fun summarize(period: Period): PeriodSummary {
        val inPeriod = all.filter { it.timestamp in period && !it.needsReview }
        val spend = inPeriod.filter { isSpend(it) }
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

        val byMerchant = spend.groupBy { InsightsEngine.normalizeMerchant(it.merchant) }
            .map { (_, list) -> MerchantSpend(list.first().merchant, list.sumOf { it.amountPaise }, list.size, list.groupBy { it.category }.maxBy { it.value.size }.key) }
            .sortedByDescending { it.amountPaise }

        val byAccount = inPeriod.filter { it.accountRef != null || it.bankName != null }
            .groupBy { listOfNotNull(it.bankName, it.accountRef?.let { a -> "••$a" }).joinToString(" ") }
            .map { (label, list) ->
                AccountSpend(label, list.filter { isSpend(it) }.sumOf { it.amountPaise }, list.filter { it.flow == Flow.INCOME }.sumOf { it.amountPaise }, list.size)
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

    /** First spend's category per merchant key, for matching refunds to what they refund. */
    private fun spendCategoryByMerchant(spend: List<Transaction>): Map<String, Category> {
        val m = HashMap<String, Category>()
        for (t in spend) m.putIfAbsent(InsightsEngine.normalizeMerchant(t.merchant), t.category)
        return m
    }

    /** The category a refund reduces: the one its merchant was spent in, else its own; null when it fits none. */
    private fun refundCategory(r: Transaction, spendCats: Map<String, Category>): Category? =
        spendCats[InsightsEngine.normalizeMerchant(r.merchant)] ?: r.category.takeIf { it != Category.INCOME && it != Category.OTHER }

    /**
     * The payments behind one figure. Spend includes the refunds that reduce it (all of them, or those matched to
     * [category]), so [drillTotal] of the list is exactly the net figure Home shows.
     */
    fun payments(period: Period, bucket: Bucket, category: Category? = null): List<Transaction> {
        val inPeriod = all.filter { it.timestamp in period && !it.needsReview }
        if (bucket == Bucket.SPEND) {
            val spend = inPeriod.filter { isSpend(it) }
            val spendCats = spendCategoryByMerchant(spend)
            val refunds = inPeriod.filter { it.flow == Flow.REFUND }
            return if (category == null) inPeriod.filter { isSpend(it) || it.flow == Flow.REFUND }
            else inPeriod.filter { (isSpend(it) && it.category == category) || (it.flow == Flow.REFUND && it in refunds && refundCategory(it, spendCats) == category) }
        }
        val byBucket = when (bucket) {
            Bucket.SPEND -> inPeriod.filter { isSpend(it) }
            Bucket.REFUNDS -> inPeriod.filter { it.flow == Flow.REFUND }
            Bucket.INCOME -> inPeriod.filter { it.flow == Flow.INCOME }
            Bucket.TRANSFERS -> inPeriod.filter { it.flow == Flow.TRANSFER || it.flow == Flow.SETTLEMENT }
            Bucket.TRANSFERS_OUT -> inPeriod.filter { (it.flow == Flow.TRANSFER || it.flow == Flow.SETTLEMENT) && it.type == TransactionType.DEBIT }
            Bucket.TRANSFERS_IN -> inPeriod.filter { it.flow == Flow.TRANSFER && it.type == TransactionType.CREDIT }
            Bucket.PAID_BACK -> inPeriod.filter { it.flow == Flow.SETTLEMENT && it.type == TransactionType.CREDIT }
            Bucket.INVESTMENTS -> inPeriod.filter { it.flow == Flow.INVESTMENT && it.type == TransactionType.DEBIT }
            Bucket.CASH -> inPeriod.filter { it.flow == Flow.CASH }
            Bucket.ALL -> inPeriod
        }
        return if (category == null) byBucket else byBucket.filter { it.category == category }
    }

    fun budgets(budgets: List<Budget>, period: Period = Periods.month()): List<BudgetStatus> {
        val s = summary(period)
        return budgets.map { b -> BudgetStatus(b, s.byCategory.firstOrNull { it.category == b.category }?.amountPaise ?: 0L) }
            .sortedByDescending { it.fraction }
    }

    /**
     * Category-level comparison between two periods, e.g. "20% more on food". A category whose refunds outweigh its
     * spend (net ₹0 or less) is left out on either side, since a percentage of a negative figure means nothing. A
     * category that had spend before and none now is deliberately not listed: the rows show this period's spend, and
     * "down 100%" on a ₹0 row reads as noise.
     */
    fun trends(current: Period, previous: Period): List<Insight> {
        val cur = summary(current)
        val prev = summary(previous)
        val out = mutableListOf<Insight>()
        for (cs in cur.byCategory) {
            if (cs.amountPaise <= 0) continue
            val p = prev.byCategory.firstOrNull { it.category == cs.category }?.amountPaise ?: 0L
            if (p < 0) continue
            if (p < 20_000 && cs.amountPaise < 50_000) continue
            if (p == 0L) {
                out += Insight("New spending: ${cs.category.label}", "${InsightsEngine.rupees(cs.amountPaise)} this period, nothing last period.", Insight.Severity.INFO, cs.category, 0)
                continue
            }
            val pct = InsightsEngine.changePercent(cs.amountPaise, p) ?: continue
            if (abs(pct) < 10) continue
            if (pct > 0) out += Insight("${cs.category.label} up $pct%", "You spent ${InsightsEngine.rupees(cs.amountPaise)} vs ${InsightsEngine.rupees(p)} last period.", Insight.Severity.WARN, cs.category, pct)
            else out += Insight("${cs.category.label} down ${-pct}%", "${InsightsEngine.rupees(cs.amountPaise)} vs ${InsightsEngine.rupees(p)} last period. Nice.", Insight.Severity.GOOD, cs.category, -pct)
        }
        return out.sortedByDescending { it.magnitude }
    }

    /** Actionable "reduce spending" suggestions computed locally. */
    fun tips(budgets: List<Budget>, now: Long = System.currentTimeMillis()): List<Insight> {
        val out = mutableListOf<Insight>()
        val ninetyDays = all.filter { isSpend(it) && !it.needsReview && it.timestamp > now - 90L * 24 * 3600 * 1000 }
        if (ninetyDays.isEmpty()) return out

        // 1. Subscriptions and other repeating charges (weekly to yearly), and any that just got pricier.
        RecurringDetector.detect(all, now).filter { it.active && it.period != RecurringPeriod.UNKNOWN && it.amountPaise >= 5_000 }.forEach { r ->
            r.priceRise?.let { p ->
                out += Insight(
                    "${r.merchant} went up",
                    "Now ${InsightsEngine.rupees(p.toPaise)} instead of ${InsightsEngine.rupees(p.fromPaise)} (${r.period.label.lowercase(Locale.ROOT)}). That is ${InsightsEngine.rupees(r.yearlyPaise - r.yearlyPaise * p.fromPaise / p.toPaise)} more a year.",
                    Insight.Severity.WARN, r.category
                )
            }
            out += Insight(
                "Recurring: ${r.merchant}",
                "${InsightsEngine.rupees(r.amountPaise)} ${r.period.label.lowercase(Locale.ROOT)}, ${InsightsEngine.rupees(r.yearlyPaise)} a year. Still using it?",
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
                "${small.size} small spends add up to ${InsightsEngine.rupees(total)}",
                "Purchases under ₹300 this month" + (topCat?.let { ", mostly ${it.label.lowercase(Locale.ROOT)}" } ?: "") + ". Batching them could cut this noticeably.",
                Insight.Severity.WARN, topCat
            )
        }

        // 3. Categories trending up vs previous month.
        out += trends(month, Periods.sameSpanBefore(month, Periods.month(-1, now), now)).filter { it.severity == Insight.Severity.WARN }.take(3)

        // 4. Biggest single merchant this month.
        thisMonth.groupBy { InsightsEngine.normalizeMerchant(it.merchant) }.maxByOrNull { e -> e.value.sumOf { it.amountPaise } }?.let { (_, list) ->
            val total = list.sumOf { it.amountPaise }
            val monthTotal = thisMonth.sumOf { it.amountPaise }
            if (monthTotal > 0 && total.toDouble() / monthTotal > 0.25 && list.size > 1) {
                out += Insight(
                    "${list.first().merchant} is ${(total.toDouble() / monthTotal * 100).roundToInt()}% of this month",
                    "${list.size} transactions totalling ${InsightsEngine.rupees(total)}. Worth a second look.",
                    Insight.Severity.INFO, list.first().category
                )
            }
        }

        // 5. Budget overspend.
        budgets(budgets, month).filter { it.over }.forEach {
            out += Insight(
                "Over budget: ${it.budget.category.label}",
                "${InsightsEngine.rupees(it.spentPaise)} spent of ${InsightsEngine.rupees(it.budget.monthlyLimitPaise)} budget (${(it.fraction * 100).roundToInt()}%).",
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

    private fun isWeekend(t: Long): Boolean {
        val c = Calendar.getInstance(); c.timeInMillis = t
        val d = c.get(Calendar.DAY_OF_WEEK)
        return d == Calendar.SATURDAY || d == Calendar.SUNDAY
    }


    companion object {
        fun of(payments: List<Transaction>, rules: CountingRules = CountingRules()) = Books(payments, rules)

        /** The figure a drill-down list adds up to: for spend, payments minus the refunds listed with them. */
        fun total(list: List<Transaction>, bucket: Bucket): Long =
            if (bucket == Bucket.SPEND) list.sumOf { if (it.flow == Flow.REFUND) -it.amountPaise else it.amountPaise }
            else list.sumOf { it.amountPaise }
    }
}
