package com.pft.financetracker.domain.ask

import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Period
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
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
    val includeCash: Boolean = true,
    val now: Long = System.currentTimeMillis(),
    val zone: ZoneId = ZoneId.systemDefault(),
)

/**
 * An answer in plain words, and the transactions behind it (for "show me"). [understood] is false when the rules did
 * not recognise the question, so an on-device model may try; [byAi] marks an answer the model wrote.
 */
data class AskAnswer(
    val text: String,
    val transactionIds: List<Long> = emptyList(),
    val understood: Boolean = true,
    val byAi: Boolean = false,
    /** Written by ChatGPT (OpenAI), from the summary in [AskAiPrompt]; otherwise by the rules or Gemini Nano on the phone. */
    val byOpenAi: Boolean = false,
)

/**
 * Answers everyday questions about your money with rules that run on the phone: spend on a category or at a
 * merchant in a period, income, savings, the biggest merchant, subscriptions, bills due and budget left.
 * Nothing is sent anywhere. Questions it does not understand get a short list of ones it does.
 */
object AskEngine {
    private val dayFmt = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

    /** Month words: full names, three-letter forms and "sept". "may" is only a month in context (see [monthAt]). */
    private val monthWords: Map<String, Month> = buildMap {
        for (m in Month.entries) {
            val full = m.name.lowercase(Locale.ROOT)
            put(full, m)
            if (m != Month.MAY) put(full.take(3), m)
        }
        put("sept", Month.SEPTEMBER)
    }

    private val periodWords: Set<String> = setOf(
        "this", "last", "past", "previous", "month", "months", "week", "weeks", "weekly", "weekend", "year", "years",
        "today", "yesterday", "day", "days", "may",
    ) + monthWords.keys

    val examples = listOf(
        "How much on food this month?", "Swiggy last month", "Spending in September", "Where did most of my money go?",
        "How much did I save?", "What subscriptions do I have?", "Any bills due?", "How much budget is left?",
    )

    fun answer(question: String, c: AskContext): AskAnswer {
        val q = question.lowercase(Locale.ROOT).replace(Regex("""[^a-z0-9 ]"""), " ").replace(Regex("""\s+"""), " ").trim()
        val period = period(q, c)
        val s = InsightsEngine.summarize(c.txns, period, c.includeCash)
        // "Financial health" is about money overall, not the Health category.
        val category = category(q.replace("financial health", " "))
        val words = q.split(' ')
        fun has(vararg w: String) = w.any { x -> if (x.contains(' ')) q.contains(x) else x in words || words.any { it.startsWith(x) && x.length >= 5 } }
        val merchantKey = if (category == null) merchant(q, c) else null

        return when {
            has("subscription", "subscriptions", "recurring", "autopay") -> subscriptions(c)
            has("due", "emi", "emis", "upcoming") || (has("bill", "bills") && category == null && !has("spend", "spent")) -> bills(c)
            has("budget", "budgets") -> budget(c, period)
            has("transfer") -> {
                val list = c.txns.filter { it.timestamp in period && !it.needsReview && it.type == TransactionType.DEBIT && (it.flow == Flow.TRANSFER || it.flow == Flow.SETTLEMENT) }
                AskAnswer("${rupees(s.transfersOutPaise)} went out in transfers and card bill payments ${period.label}. These don't count as spend.", list.map { it.id })
            }
            has("invest", "sip", "sips") -> {
                val list = c.txns.filter { it.timestamp in period && !it.needsReview && it.type == TransactionType.DEBIT && it.flow == Flow.INVESTMENT }
                AskAnswer("You invested ${rupees(s.investmentsPaise)} ${period.label} (${count(list.size)}).", list.map { it.id })
            }
            has("earn", "earned", "earning", "earnings", "income", "salary") -> AskAnswer("You received ${rupees(s.incomePaise)} of income ${period.label}.")
            has("save", "saved", "saving", "savings") -> AskAnswer(
                "You saved ${rupees(s.savingsPaise)} ${period.label}: income ${rupees(s.incomePaise)} minus spend ${rupees(s.netSpendPaise)}."
            )
            // Merchant totals are before refunds, so they are set against spend before refunds too.
            has("most", "biggest", "top", "where did") -> s.byMerchant.firstOrNull()?.let { m ->
                AskAnswer("Most went to ${m.merchant}: ${rupees(m.amountPaise)} of ${rupees(s.grossSpendPaise)} spent ${period.label} (${count(m.count)}).")
            } ?: AskAnswer("No spending ${period.label}.")
            category != null -> {
                val list = spends(c, period).filter { it.category == category }
                val net = s.byCategory.firstOrNull { it.category == category }?.amountPaise ?: 0L
                AskAnswer("You spent ${rupees(net)} on ${category.label} ${period.label} (${count(list.size)}).", list.map { it.id })
            }
            merchantKey != null -> {
                val list = spends(c, period).filter { merchantMatches(it.merchant, merchantKey) }
                AskAnswer("You spent ${rupees(list.sumOf { it.amountPaise })} at ${list.firstOrNull()?.merchant ?: merchantKey} ${period.label} (${count(list.size)}).", list.map { it.id })
            }
            has("spend", "spent", "spending", "how much", "expenses", "cost") ->
                AskAnswer("You spent ${rupees(s.netSpendPaise)} ${period.label}.", spends(c, period).map { it.id })
            else -> AskAnswer("I can answer questions about your own numbers. Try: " + examples.take(4).joinToString(" · ") { "\"$it\"" }, understood = false)
        }
    }

    private fun count(n: Int) = "$n payment${if (n == 1) "" else "s"}"

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

    /** Budgets are monthly: a question about a whole month uses that month, anything else this month. */
    private fun budget(c: AskContext, asked: Period): AskAnswer {
        if (c.budgets.isEmpty()) return AskAnswer("No budgets set. Set them in Insights > Budgets.")
        val month = if (isWholeMonth(asked, c)) asked else period("", c)
        val s = InsightsEngine.summarize(c.txns, month, c.includeCash)
        val spent = c.budgets.sumOf { b -> s.byCategory.firstOrNull { it.category == b.category }?.amountPaise?.coerceAtLeast(0) ?: 0L }
        val left = c.budgets.sumOf { it.monthlyLimitPaise } - spent
        val running = c.now in month
        return AskAnswer(
            when {
                left < 0 -> "${rupees(-left)} over your budgets ${month.label}."
                running -> "${rupees(left)} left of your budgets ${month.label}."
                else -> "${rupees(left)} under your budgets ${month.label}."
            }
        )
    }

    private fun isWholeMonth(p: Period, c: AskContext): Boolean {
        val from = Instant.ofEpochMilli(p.start).atZone(c.zone).toLocalDate()
        return from.dayOfMonth == 1 && from.plusMonths(1).atStartOfDay(c.zone).toInstant().toEpochMilli() == p.end
    }

    private fun spends(c: AskContext, p: Period) = c.txns.filter { it.timestamp in p && !it.needsReview && InsightsEngine.isSpend(it, c.includeCash) }

    private fun category(q: String): Category? = Category.entries.filter { it != Category.OTHER && it != Category.INCOME }.firstOrNull { cat ->
        val names = setOf(cat.name.lowercase(Locale.ROOT)) + cat.label.lowercase(Locale.ROOT).split(Regex("""[^a-z]+""")).filter { it.length >= 4 }
        names.any { n -> Regex("""\b${Regex.escape(n)}\b""").containsMatchIn(q) }
    }

    private val lead = setOf("on", "at", "for", "to", "from", "with", "in")

    /**
     * A merchant key from the question, matched against the merchants in the transactions. Words straight after on /
     * at / for / to / from are tried first, and two words before one ("amazon pay" before "amazon"). A word must
     * start a word of the merchant's name: "ola" finds Ola, not Coca-Cola; common words ("pay", "show", "you") never
     * count, so they cannot pick Amazon Pay, BookMyShow or YouTube.
     */
    private fun merchant(q: String, c: AskContext): String? {
        val names = c.txns.map { it.merchant }.distinct()
        val words = q.split(' ').filter { it.isNotEmpty() }
        fun usable(w: String) = w.length >= 3 && w !in periodWords && w !in stop && w.any { it.isLetter() }
        fun known(key: String) = names.any { merchantMatches(it, key) }
        val afterLead = words.indices.filter { i -> i > 0 && words[i - 1] in lead }
        val order = afterLead + words.indices.filter { it !in afterLead }
        for (i in order) {
            val w = words[i]
            if (!usable(w)) continue
            val next = words.getOrNull(i + 1)
            if (next != null && next.length >= 2 && next !in periodWords && known(w + next)) return w + next
            if (known(w)) return w
        }
        return null
    }

    /** True when [key] (letters and digits, no spaces) starts at the beginning of one of the words in [merchant]. */
    internal fun merchantMatches(merchant: String, key: String): Boolean {
        val tokens = merchant.lowercase(Locale.ROOT).split(Regex("""[^a-z0-9]+""")).filter { it.isNotEmpty() }
        return tokens.indices.any { i -> tokens.drop(i).joinToString("").startsWith(key) }
    }

    private val stop = setOf(
        "how", "much", "many", "did", "does", "spend", "spent", "spending", "the", "and", "for", "what", "whats", "have", "has",
        "money", "was", "were", "with", "from", "all", "total", "pay", "paid", "paying", "payment", "payments", "show", "you",
        "your", "can", "could", "may", "might", "will", "would", "should", "tell", "give", "list", "get", "got", "there",
        "that", "these", "those", "which", "where", "when", "why", "who", "any", "are", "about", "more", "most", "less",
        "than", "over", "under", "left", "far", "till", "until", "since", "during", "per", "average", "daily", "each",
        "every", "time", "times", "cost", "costs", "expense", "expenses", "buy", "bought", "went", "out", "only", "just",
        "not", "mine", "please", "see", "category", "merchant", "biggest", "top",
    )

    private val numberWords = mapOf(
        "one" to 1, "two" to 2, "three" to 3, "four" to 4, "five" to 5, "six" to 6, "seven" to 7, "eight" to 8,
        "nine" to 9, "ten" to 10, "eleven" to 11, "twelve" to 12,
    )
    private val rolling = Regex("""\b(?:last|past|previous)\s+(\d{1,3}|${numberWords.keys.joinToString("|")})\s+(day|week|month|year)s?\b""")
    private val pastOne = Regex("""\bpast\s+(day|week|month|year)\b""")
    private val yearNumber = Regex("""\b(19\d{2}|20\d{2})\b""")
    private val lastYearWords = Regex("""\b(?:last|previous) year\b""")
    private val lastMonthWords = Regex("""\b(?:last|previous) month\b""")
    private val lastWeekWords = Regex("""\b(?:last|previous) week\b""")
    private val mayContext = setOf("in", "for", "during", "of", "since", "last", "this", "from", "through", "till", "until")

    /** The month named at word [i], or null. "may" counts only after "in", "for", "since"… or before a year. */
    private fun monthAt(words: List<String>, i: Int): Month? {
        val w = words[i]
        if (w == "may") {
            val before = words.getOrNull(i - 1)
            val after = words.getOrNull(i + 1)
            val inContext = (before != null && before in mayContext) || (after != null && yearNumber.matches(after)) || words.size == 1
            return if (inContext) Month.MAY else null
        }
        return monthWords[w]
    }

    /**
     * Which stretch of time the question is about, in this order:
     * - "last 3 months", "past 7 days", "past week": that many days, weeks, months or years up to today;
     * - a month by name ("sept", "december 2024", "september last year"): the year said, else the latest one that has started;
     * - "last year" or a year ("2025"): that calendar year;
     * - "last month", "last week", "yesterday", "today", "this week", "this year" as written;
     * - anything else: this month.
     * Words are matched whole, so "weekly" and "weekend" are not "week".
     */
    private fun period(q: String, c: AskContext): Period {
        val today = Instant.ofEpochMilli(c.now).atZone(c.zone).toLocalDate()
        fun range(from: LocalDate, toExcl: LocalDate, label: String) =
            Period(from.atStartOfDay(c.zone).toInstant().toEpochMilli(), toExcl.atStartOfDay(c.zone).toInstant().toEpochMilli(), label)
        val ym = YearMonth.from(today)
        val words = q.split(' ').filter { it.isNotEmpty() }
        val tomorrow = today.plusDays(1)

        fun back(n: Long, unit: String): LocalDate = when (unit) {
            "day" -> today.minusDays(n - 1)
            "week" -> today.minusWeeks(n).plusDays(1)
            "month" -> today.minusMonths(n).plusDays(1)
            else -> today.minusYears(n).plusDays(1)
        }
        rolling.find(q)?.let { m ->
            val n = (m.groupValues[1].toLongOrNull() ?: numberWords[m.groupValues[1]]?.toLong() ?: 1L).coerceIn(1L, 1200L)
            val unit = m.groupValues[2]
            return range(back(n, unit), tomorrow, if (n == 1L) "in the past $unit" else "in the last $n ${unit}s")
        }
        pastOne.find(q)?.let { m -> return range(back(1, m.groupValues[1]), tomorrow, "in the past ${m.groupValues[1]}") }

        val lastYear = lastYearWords.containsMatchIn(q)
        val named = words.indices.firstNotNullOfOrNull { i -> monthAt(words, i)?.let { m -> i to m } }
        if (named != null) {
            val (i, m) = named
            val saidYear = words.getOrNull(i + 1)?.takeIf { yearNumber.matches(it) }?.toInt()
                ?: yearNumber.find(q)?.value?.toInt()
            val y = when {
                saidYear != null -> YearMonth.of(saidYear, m)
                lastYear -> YearMonth.of(today.year - 1, m)
                else -> YearMonth.of(today.year, m).let { if (it.isAfter(ym)) it.minusYears(1) else it }
            }
            return range(y.atDay(1), y.plusMonths(1).atDay(1), "in ${m.name.lowercase(Locale.ROOT).replaceFirstChar { it.uppercase() }} ${y.year}")
        }
        if (lastYear) return range(LocalDate.of(today.year - 1, 1, 1), LocalDate.of(today.year, 1, 1), "in ${today.year - 1}")
        val saidYear = yearNumber.find(q)?.value?.toInt()
        if (saidYear != null) return range(LocalDate.of(saidYear, 1, 1), LocalDate.of(saidYear + 1, 1, 1), "in $saidYear")
        return when {
            lastMonthWords.containsMatchIn(q) -> range(ym.minusMonths(1).atDay(1), ym.atDay(1), "last month")
            lastWeekWords.containsMatchIn(q) ->
                today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { range(it.minusWeeks(1), it, "last week") }
            "yesterday" in words -> range(today.minusDays(1), today, "yesterday")
            "today" in words -> range(today, tomorrow, "today")
            "week" in words -> today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).let { range(it, tomorrow, "this week") }
            "year" in words -> range(LocalDate.of(today.year, 1, 1), tomorrow, "this year")
            else -> range(ym.atDay(1), ym.plusMonths(1).atDay(1), "this month")
        }
    }

    private fun rupees(p: Long) = InsightsEngine.rupees(p)
}
