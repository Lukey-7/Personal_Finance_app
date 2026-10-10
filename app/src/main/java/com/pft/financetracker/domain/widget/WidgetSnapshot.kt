package com.pft.financetracker.domain.widget

import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.books.CountingRules
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
        rules: CountingRules = CountingRules(), now: Long = System.currentTimeMillis(), zone: ZoneId = ZoneId.systemDefault(),
    ): WidgetText {
        val ym = YearMonth.from(Instant.ofEpochMilli(now).atZone(zone))
        val start = ym.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = ym.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val summary = Books.of(txns, rules).summary(Period(start, end, ""))
        // Whole rupees: at 26sp on the smallest widget, paise pushed the figure off the edge.
        fun rupees(p: Long) = if (hideAmounts) "₹••••" else com.pft.financetracker.domain.model.Rupees.format(p, com.pft.financetracker.domain.model.Paise.NEVER)

        val budgetLine = budgets.takeIf { it.isNotEmpty() }?.let { bs ->
            val spentInBudgeted = bs.sumOf { b -> summary.byCategory.firstOrNull { it.category == b.category }?.amountPaise ?: 0L }
            val left = bs.sumOf { it.monthlyLimitPaise } - spentInBudgeted
            when {
                hideAmounts -> when {
                    left > 0 -> "Within budgets, some left"
                    left == 0L -> "Budgets all used"
                    else -> "Over budget"
                }
                left >= 0 -> "${rupees(left)} left of budgets"
                else -> "${rupees(-left)} over budget"
            }
        }

        // With amounts hidden, bill names stay off the home screen too: a name like "Loan EMI" says as much as a figure.
        val overdue = bills.firstOrNull { it.second is BillState.Overdue }
            ?.let { if (hideAmounts) "A bill is overdue" else "${it.first.name} · overdue" }
        val upcoming = bills.mapNotNull { (b, s) -> (s as? BillState.Upcoming)?.let { b to it } }.minByOrNull { it.second.daysLeft }
            ?.let { (b, s) -> if (hideAmounts) "A bill · ${s.due.format(dayFmt)}" else "${b.name} · ${s.due.format(dayFmt)}" }
        return WidgetText(rupees(summary.netSpendPaise), budgetLine, overdue ?: upcoming)
    }
}
