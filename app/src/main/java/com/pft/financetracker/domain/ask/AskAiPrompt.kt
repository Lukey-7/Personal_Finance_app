package com.pft.financetracker.domain.ask

import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.model.Flow
import java.time.Instant
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * What ChatGPT is given to answer a question in Ask, when the owner has switched that on: a year of monthly totals,
 * this month's and last month's categories, the main payees, the latest payments (date, payee, category, amount),
 * subscriptions, bills and budgets, all worked out on the phone. Never SMS text, account numbers, bank reference
 * numbers or notes.
 */
object AskAiPrompt {
    /** How many recent payments are listed one by one. Enough for "when did I last pay X" without a huge prompt. */
    const val RECENT_ROWS = 120
    private const val MONTHS = 12
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH)
    private val shortDay = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val monthFmt = DateTimeFormatter.ofPattern("MMM yyyy", Locale.ENGLISH)

    val system: String =
        "You are FinTrack's assistant. You help one person in India understand and manage their own money, using the " +
            "data from their phone below (worked out from their bank, UPI and card SMS). Answer the actual question " +
            "directly and specifically: add up, compare and spot trends in the data yourself, and name payees, months " +
            "and amounts. Give practical opinions and suggestions on spending, saving and budgeting when they help. " +
            "For tax or investment questions, explain the general Indian rules that apply and how their payments fit, " +
            "and say when something depends on details you do not have. If the data does not cover what they ask, " +
            "say what is missing in one line and answer as far as you can. Write amounts with ₹ and Indian grouping " +
            "(₹1,05,000). Keep it short: a sentence or two, then bullets only if they help. Use plain words."

    fun facts(c: AskContext): String {
        val zone = c.zone
        val ym = YearMonth.from(Instant.ofEpochMilli(c.now).atZone(zone))
        fun period(m: YearMonth) = Period(
            m.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            m.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),
            m.format(monthFmt),
        )
        val out = StringBuilder()
        out.append("Today is ").append(Instant.ofEpochMilli(c.now).atZone(zone).toLocalDate().format(dayFmt)).append(".\n")
        out.append(if (c.includeCash) "ATM cash counts as spend.\n" else "ATM cash is not counted as spend.\n")

        out.append("\nMonthly totals (spent is after refunds; transfers between own accounts, card bill payments and investments are not spend):\n")
        for (i in 0 until MONTHS) {
            val m = ym.minusMonths(i.toLong())
            val s = InsightsEngine.summarize(c.txns, period(m), c.includeCash)
            if (i > 0 && s.count == 0) continue
            out.append("- ").append(m.format(monthFmt)).append(if (i == 0) " (so far)" else "")
                .append(": spent ").append(r(s.netSpendPaise))
                .append(", income ").append(r(s.incomePaise))
                .append(", saved ").append(r(s.savingsPaise))
                .append(", invested ").append(r(s.investmentsPaise))
                .append(", ").append(s.expenseCount).append(" payments\n")
        }

        for ((name, m) in listOf("This month" to ym, "Last month" to ym.minusMonths(1))) {
            val s = InsightsEngine.summarize(c.txns, period(m), c.includeCash)
            if (s.byCategory.isEmpty()) continue
            out.append("\n").append(name).append(" by category: ")
                .append(s.byCategory.joinToString(", ") { "${it.category.label} ${r(it.amountPaise)} (${it.count})" }).append(".\n")
        }

        val threeMonths = Period(period(ym.minusMonths(2)).start, period(ym).end, "last three months")
        val payees = InsightsEngine.summarize(c.txns, threeMonths, c.includeCash).byMerchant.take(25)
        if (payees.isNotEmpty()) {
            out.append("\nMain payees over the last three months: ")
                .append(payees.joinToString(", ") { "${payee(it.merchant)} ${r(it.amountPaise)} (${it.count}x, ${it.category.label})" }).append(".\n")
        }

        if (c.budgets.isNotEmpty()) {
            out.append("\nMonthly budgets: ").append(c.budgets.joinToString(", ") { "${it.category.label} ${r(it.monthlyLimitPaise)}" }).append(".\n")
        }
        c.recurring.shown.filter { it.counted }.takeIf { it.isNotEmpty() }?.let { l ->
            out.append("\nLooks like subscriptions (").append(r(c.recurring.monthlyPaise)).append(" a month): ")
                .append(l.joinToString(", ") { "${payee(it.item.merchant)} ${r(it.item.amountPaise)} ${it.item.period.label.lowercase(Locale.ROOT)}" }).append(".\n")
        }
        c.bills.mapNotNull { (b, s) ->
            val amount = (b.amountPaise ?: BillTracker.amountDue(b))?.let { " ${r(it)}" } ?: ""
            when (s) {
                is BillState.Upcoming -> "${b.name}$amount due ${s.due.format(shortDay)}"
                is BillState.Overdue -> "${b.name}$amount overdue since ${s.due.format(shortDay)}"
                else -> null
            }
        }.takeIf { it.isNotEmpty() }?.let { out.append("\nBills: ").append(it.joinToString(", ")).append(".\n") }

        val recent = c.txns.asSequence().filter { !it.needsReview }.sortedByDescending { it.timestamp }.take(RECENT_ROWS).toList()
        if (recent.isNotEmpty()) {
            out.append("\nLatest payments, newest first (date | payee | category | kind | amount):\n")
            for (t in recent) {
                val d = Instant.ofEpochMilli(t.timestamp).atZone(zone).toLocalDate().format(dayFmt)
                val sign = if (t.type == com.pft.financetracker.domain.model.TransactionType.CREDIT) "+" else "-"
                out.append(d).append(" | ").append(payee(t.merchant)).append(" | ").append(t.category.label)
                    .append(" | ").append(kind(t.flow)).append(" | ").append(sign).append(r(t.amountPaise)).append('\n')
            }
        }
        return out.toString().trim()
    }

    private fun kind(f: Flow) = when (f) {
        Flow.EXPENSE -> "spend"
        Flow.INCOME -> "income"
        Flow.REFUND -> "refund"
        Flow.CASH -> "cash"
        Flow.INVESTMENT -> "investment"
        else -> "transfer"
    }

    private fun r(p: Long) = InsightsEngine.rupees(p)

    private val longDigits = Regex("""\d[\d\s-]{5,}\d""")
    private val handle = Regex("""\S+@\S+""")
    private val control = Regex("""[\p{Cntrl}|]""")

    /** A payee as sent: no runs of 7+ digits (phone or account numbers), no UPI handles, one line. */
    fun payee(raw: String): String = raw
        .replace(control, " ")
        .replace(handle, "a UPI ID")
        .replace(longDigits, "••••")
        .replace(Regex("""\s+"""), " ")
        .trim()
        .take(40)
        .ifEmpty { "Unknown" }
}
