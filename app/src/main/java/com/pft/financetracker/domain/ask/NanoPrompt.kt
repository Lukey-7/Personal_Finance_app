package com.pft.financetracker.domain.ask

import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Period
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What the on-device model (Gemini Nano) is given for a question the rules cannot answer: totals worked out on the
 * phone (by month, category and merchant, plus subscriptions, bills and budgets). Never SMS text, account numbers or
 * reference numbers. The model runs on the phone too.
 */
object NanoPrompt {
    private const val MAX_REPLY = 1_200
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    fun facts(c: AskContext): String {
        val ym = YearMonth.from(Instant.ofEpochMilli(c.now).atZone(c.zone))
        fun period(m: YearMonth) = Period(m.atDay(1).atStartOfDay(c.zone).toInstant().toEpochMilli(), m.plusMonths(1).atDay(1).atStartOfDay(c.zone).toInstant().toEpochMilli(),
            m.format(DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)))
        val lines = mutableListOf<String>()
        for ((name, m) in listOf("This month" to ym, "Last month" to ym.minusMonths(1), "Two months ago" to ym.minusMonths(2))) {
            val s = InsightsEngine.summarize(c.txns, period(m), c.includeCash)
            lines += "$name (${s.period.label}): spent ${rupees(s.netSpendPaise)}, income ${rupees(s.incomePaise)}, saved ${rupees(s.savingsPaise)}."
            if (s.byCategory.isNotEmpty()) lines += "  By category: " + s.byCategory.sortedByDescending { it.amountPaise }.take(8).joinToString(", ") { "${it.category.label} ${rupees(it.amountPaise)}" } + "."
            if (s.byMerchant.isNotEmpty()) lines += "  Top payees: " + s.byMerchant.take(6).joinToString(", ") { "${it.merchant} ${rupees(it.amountPaise)} (${it.count}x)" } + "."
        }
        c.budgets.takeIf { it.isNotEmpty() }?.let { b -> lines += "Monthly budgets: " + b.joinToString(", ") { "${it.category.label} ${rupees(it.monthlyLimitPaise)}" } + "." }
        c.recurring.shown.filter { it.counted }.takeIf { it.isNotEmpty() }?.let { r ->
            lines += "Subscriptions (${rupees(c.recurring.monthlyPaise)} a month): " + r.joinToString(", ") { "${it.item.merchant} ${rupees(it.item.amountPaise)} ${it.item.period.label.lowercase(Locale.ROOT)}" } + "."
        }
        c.bills.mapNotNull { (b, s) ->
            val amount = BillTracker.amountDue(b)?.let { " ${rupees(it)}" } ?: ""
            when (s) {
                is BillState.Upcoming -> "${b.name}$amount due ${s.due.format(dayFmt)}"
                is BillState.Overdue -> "${b.name}$amount overdue since ${s.due.format(dayFmt)}"
                else -> null
            }
        }.takeIf { it.isNotEmpty() }?.let { lines += "Bills: " + it.joinToString(", ") + "." }
        c.netWorthPaise?.let { lines += "Net worth: ${rupees(it)}." }
        return lines.joinToString("\n")
    }

    fun prompt(question: String, facts: String): String =
        "You help someone understand their own spending. Answer in at most three short sentences, using only the facts " +
            "below; if they do not answer the question, say so plainly. Quote amounts exactly as given in rupees (₹). " +
            "Do not give investment or tax advice.\n\nFacts:\n$facts\n\nQuestion: $question\nAnswer:"

    /** The model's reply, trimmed; null when there is nothing usable. */
    fun clean(reply: String?): String? = reply?.trim()?.takeIf { it.isNotEmpty() }?.take(MAX_REPLY)

    private fun rupees(p: Long) = InsightsEngine.rupees(p)
}
