package com.pft.financetracker.domain.ask

import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.recurring.RecurringBook
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.Month
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAdjusters
import java.util.Locale

/** Everything Ask can look at. All of it is already on the phone. */
data class AskContext(
    val txns: List<Transaction>,
    val budgets: List<Budget>,
    val recurring: RecurringBook,
    val bills: List<Pair<Bill, BillState>>,
    val netWorthPaise: Long?,
    val includeCash: Boolean = true,
    val now: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * An answer in plain words, and the transactions behind it (for "show me"). [understood] is false when the rules did
 * not recognise the question, so an on-device model may try; [byAi] marks an answer the model wrote.
 */
data class AskAnswer(val text: String, val transactionIds: List<Long> = emptyList(), val understood: Boolean = true, val byAi: Boolean = false)

/**
 * Answers everyday questions about your money with rules that run on the phone: spend on a category or at a
 * merchant in a period, income, savings, the biggest merchant, subscriptions, bills due, budget left and net worth.
 * Nothing is sent anywhere. Questions it does not understand get a short list of ones it does.
 */
object AskEngine {
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)
    private val periodWords = setOf("this", "last", "month", "week", "year", "today", "yesterday", "days") + Month.entries.map { it.name.lowercase(Locale.ROOT) }

    val examples = listOf(
        "How much on food this month?", "Swiggy last month", "Spending in September", "Where did most of my money go?",
        "How much did I save?", "What subscriptions do I have?", "Any bills due?", "How much budget is left?",
    )

    fun answer(question: String, c: AskContext): AskAnswer {
        val q = question.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()
        val period = period(q, c)
        val s = InsightsEngine.summarize(c.txns, period, c.includeCash)
        val category = category(q)
        val words = q.split(' ')
        fun has(vararg w: String) = w.any { x -> if (x.contains(' ')) q.contains(x) else x in words || words.any { it.startsWith(x) && x.length >= 5 } }

        return when {
            has("subscription", "subscriptions", "recurring", "autopay") -> subscriptions(c)
            has("due", "emi", "emis", "upcoming") || (has("bill", "bills") && category == null && !has("spend", "spent")) -> bills(c)
            has("budget", "budgets") -> budget(c, s)
            has("net worth", "worth") -> c.netWorthPaise?.let { AskAnswer("Your net worth is ${rupees(it)} (what you own minus what you owe).") }
                ?: AskAnswer("Set up Net worth in Money tools first: bank balances come from SMS, funds from a CAS statement.")
            has("earn", "earned", "income", "salary") -> AskAnswer("You received ${rupees(s.incomePaise)} of income ${period.label}.")
            has("save", "saved", "saving", "savings") -> AskAnswer(
                "You saved ${rupees(s.savingsPaise)} ${period.label}: income ${rupees(s.incomePaise)} minus spend ${rupees(s.netSpendPaise)}."
            )
            has("most", "biggest", "top", "where did") -> s.byMerchant.firstOrNull()?.let { m ->
                AskAnswer("Most went to ${m.merchant}: ${rupees(m.amountPaise)} of ${rupees(s.netSpendPaise)} ${period.label} (${m.count} payment${if (m.count == 1) "" else "s"}).")
            } ?: AskAnswer("No spending ${period.label}.")
            category != null -> {
                val list = spends(c, period).filter { it.category == category }
                val net = s.byCategory.firstOrNull { it.category == category }?.amountPaise ?: 0L
                AskAnswer("You spent ${rupees(net)} on ${category.label} ${period.label} (${list.size} payment${if (list.size == 1) "" else "s"}).", list.map { it.id })
            }
            merchant(q, c) != null -> {
                val m = merchant(q, c)!!
                val list = spends(c, period).filter { norm(it.merchant).contains(m) }
                AskAnswer("You spent ${rupees(list.sumOf { it.amountPaise })} at ${list.firstOrNull()?.merchant ?: m} ${period.label} (${list.size} payment${if (list.size == 1) "" else "s"}).", list.map { it.id })
            }
            has("spend", "spent", "spending", "how much", "expenses", "cost") ->
                AskAnswer("You spent ${rupees(s.netSpendPaise)} ${period.label}.", spends(c, period).map { it.id })
            else -> AskAnswer("I can answer questions about your own numbers. Try: " + examples.take(4).joinToString(" · ") { "\"$it\"" }, understood = false)
        }
    }

    private fun subscriptions(c: AskContext): AskAnswer {
        val counted = c.recurring.shown.filter { it.counted }
        if (counted.isEmpty()) return AskAnswer("No repeating charges found yet. They show up after a service has charged you twice.")
        val list = counted.joinToString("; ") { v -> "${v.item.merchant} ${rupees(v.item.amountPaise)} ${v.item.period.label.lowercase(Locale.ROOT)}" }
        return AskAnswer("${counted.size} repeating charge${if (counted.size == 1) "" else "s"}, ${rupees(c.recurring.monthlyPaise)} a month: $list.",
            counted.flatMap { it.item.transactionIds })
    }

    private fun bills(c: AskContext): AskAnswer {
        val open = c.bills.filter { it.second is BillState.Upcoming || it.second is BillState.Overdue }
            .sortedBy { (_, s) -> (s as? BillState.Upcoming)?.daysLeft ?: -1L }
        if (open.isEmpty()) return AskAnswer(if (c.bills.isEmpty()) "No bills set up. Add them in Money tools > Bills & EMIs." else "Nothing due: every bill is paid for now.")
        return AskAnswer(open.joinToString("; ") { (b, s) ->
            val amount = (b.amountPaise ?: com.pft.financetracker.domain.bills.BillTracker.amountDue(b))?.let { " ${rupees(it)}" } ?: ""
            when (s) {
                is BillState.Overdue -> "${b.name}$amount was due ${s.due.format(dayFmt)} (overdue)"
                is BillState.Upcoming -> "${b.name}$amount due ${s.due.format(dayFmt)}"
                else -> b.name
            }
        } + ".")
    }

    private fun budget(c: AskContext, s: com.pft.financetracker.domain.insights.PeriodSummary): AskAnswer {
        if (c.budgets.isEmpty()) return AskAnswer("No budgets set. Set them in Insights > Budgets.")
        val spent = c.budgets.sumOf { b -> s.byCategory.firstOrNull { it.category == b.category }?.amountPaise ?: 0L }
        val left = c.budgets.sumOf { it.monthlyLimitPaise } - spent
        return AskAnswer(if (left >= 0) "${rupees(left)} left of your budgets this month." else "${rupees(-left)} over your budgets this month.")
    }

    private fun spends(c: AskContext, p: Period) = c.txns.filter { it.timestamp in p && !it.needsReview && InsightsEngine.isSpend(it, c.includeCash) }

    private fun category(q: String): Category? = Category.entries.filter { it != Category.OTHER && it != Category.INCOME }.firstOrNull { cat ->
        val names = setOf(cat.name.lowercase(Locale.ROOT)) + cat.label.lowercase(Locale.ROOT).split(Regex("""[^a-z]+""")).filter { it.length >= 4 }
        names.any { n -> Regex("""\b${Regex.escape(n)}\b""").containsMatchIn(q) }
    }

    /** A merchant named after "on" / "at" / "for", or any word matching a merchant seen in the transactions. */
    private fun merchant(q: String, c: AskContext): String? {
        val known = c.txns.map { norm(it.merchant) }.filter { it.length >= 3 }.toSet()
        val candidates = q.split(' ').filter { it.length >= 3 && it !in periodWords && it !in stop }
        return candidates.firstOrNull { w -> known.any { it.contains(w) } }
    }

    private val stop = setOf("how", "much", "did", "spend", "spent", "spending", "the", "and", "for", "what", "have", "money", "was", "with", "from", "all", "total")

    private fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9]"""), "")

    private fun period(q: String, c: AskContext): Period {
        val today = Instant.ofEpochMilli(c.now).atZone(c.zone).toLocalDate()
        fun range(from: LocalDate, toExcl: LocalDate, label: String) =
            Period(from.atStartOfDay(c.zone).toInstant().toEpochMilli(), toExcl.atStartOfDay(c.zone).toInstant().toEpochMilli(), label)
        val ym = YearMonth.from(today)
        Month.entries.firstOrNull { m ->
            val full = m.name.lowercase(Locale.ROOT)
            Regex("""\b$full\b""").containsMatchIn(q) || (m != Month.MAY && Regex("""\b${full.take(3)}\b""").containsMatchIn(q))
        }
            ?.let { m ->
                var y = YearMonth.of(today.year, m)
                if (y.isAfter(ym)) y = y.minusYears(1)
                return range(y.atDay(1), y.plusMonths(1).atDay(1), "in ${m.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }} ${y.year}")
            }
        return when {
            q.contains("last month") -> range(ym.minusMonths(1).atDay(1), ym.atDay(1), "last month")
            q.contains("last week") -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { range(it.minusWeeks(1), it, "last week") }
            q.contains("this week") || q.contains(" week") -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { range(it, today.plusDays(1), "this week") }
            q.contains("yesterday") -> range(today.minusDays(1), today, "yesterday")
            q.contains("today") -> range(today, today.plusDays(1), "today")
            q.contains("this year") || q.contains(" year") -> range(LocalDate.of(today.year, 1, 1), today.plusDays(1), "this year")
            else -> range(ym.atDay(1), ym.plusMonths(1).atDay(1), "this month")
        }
    }

    private fun rupees(p: Long) = InsightsEngine.rupees(p)

}
