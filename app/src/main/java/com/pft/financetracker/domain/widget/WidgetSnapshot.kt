package com.pft.financetracker.domain.widget

import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Transaction
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** The three lines the home-screen widget shows. Amounts become "••••" when the person hides them. */
data class WidgetText(val spent: String, val budgetLine: String?, val nextBill: String?)

/** What the widget says, worked out from the same numbers as the Home tab. */
object WidgetSnapshot {
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun build(
        txns: List<Transaction>, budgets: List<Budget>, bills: List<Pair<Bill, BillState>>, hideAmounts: Boolean,
        includeCash: Boolean = true, now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
    ): WidgetText {
        val ym = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val summary = InsightsEngine.summarize(txns, Period(start, end, ""), includeCash)
        fun rupees(p: Long) = if (hideAmounts) "₹••••" else "₹${InsightsEngine.fmt(p)}"

        val budgetLine = budgets.takeIf { it.isNotEmpty() }?.let { bs ->
            val spentInBudgeted = bs.sumOf { b -> summary.byCategory.firstOrNull { it.category == b.category }?.amountPaise ?: 0L }
            val left = bs.sumOf { it.monthlyLimitPaise } - spentInBudgeted
            when {
                hideAmounts -> if (left >= 0) "Within budgets, some left" else "Over budget"
                left >= 0 -> "${rupees(left)} left of budgets"
                else -> "${rupees(-left)} over budget"
            }
        }

        val overdue = bills.firstOrNull { it.second is BillState.Overdue }?.let { "${it.first.name} · overdue" }
        val upcoming = bills.mapNotNull { (b, s) -> (s as? BillState.Upcoming)?.let { b to it } }.minByOrNull { it.second.daysLeft }
            ?.let { (b, s) -> "${b.name} · ${s.due.format(dayFmt)}" }
        return WidgetText(rupees(summary.netSpendPaise), budgetLine, overdue ?: upcoming)
    }
}
