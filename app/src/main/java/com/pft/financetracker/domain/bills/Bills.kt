package com.pft.financetracker.domain.bills

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.reminders.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import kotlin.math.pow
import kotlin.math.roundToLong

/** A loan repaid in equal monthly instalments (EMI). Rate in basis points a year: 9.5% = 950. */
data class Loan(val principalPaise: Long, val annualRateBp: Int, val tenureMonths: Int, val firstDue: LocalDate)

/**
 * Something to pay on a schedule: a phone bill, rent, insurance, a card bill or a loan EMI.
 * Due on [dueDay] every [everyMonths] months (counted from [startMonth], 1-12, when not monthly), or once on
 * [fixedDue] (a card statement's due date). [amountPaise] null means "varies" (or, for a loan, the EMI).
 * [keyword] picks out the payment among the transactions, e.g. "airtel".
 */
data class Bill(
    val id: Long = 0,
    val name: String,
    val amountPaise: Long?,
    val dueDay: Int,
    val keyword: String?,
    val category: Category = Category.BILLS,
    val everyMonths: Int = 1,
    val startMonth: Int = 1,
    val fixedDue: LocalDate? = null,
    val loan: Loan? = null,
    /** Last four digits of a credit card whose statement created this bill (M5). */
    val cardLast4: String? = null,
    /** When the bill was added (epoch millis). Cycles due before that day are never shown as overdue. Null: unknown. */
    val createdAt: Long? = null,
)

sealed class BillState {
    data class Upcoming(val due: LocalDate, val daysLeft: Long) : BillState()
    data class Overdue(val due: LocalDate, val daysLate: Long) : BillState()
    /** [transactionId] is the matched payment; null when the person marked it paid by hand. */
    data class Paid(val due: LocalDate, val transactionId: Long?) : BillState()
    /** A one-off bill whose date has passed long ago, or a finished loan. */
    data object Done : BillState()
}

data class AmortizationRow(val month: Int, val interestPaise: Long, val principalPaise: Long, val balancePaise: Long)
data class LoanProgress(val paidInstalments: Int, val totalInstalments: Int, val outstandingPaise: Long)

/** Standard reducing-balance EMI arithmetic, in paise. */
object Amortization {
    fun emi(principalPaise: Long, annualRateBp: Int, months: Int): Long {
        if (months <= 0) return principalPaise
        val r = annualRateBp / 10_000.0 / 12
        if (r == 0.0) return (principalPaise.toDouble() / months).roundToLong()
        val f = (1 + r).pow(months)
        return (principalPaise * r * f / (f - 1)).roundToLong()
    }

    fun schedule(principalPaise: Long, annualRateBp: Int, months: Int): List<AmortizationRow> {
        val r = annualRateBp / 10_000.0 / 12
        val emi = emi(principalPaise, annualRateBp, months)
        var balance = principalPaise
        return (1..months).map { m ->
            val interest = (balance * r).roundToLong()
            // The last instalment clears whatever rounding left behind.
            val principal = if (m == months) balance else (emi - interest).coerceAtMost(balance)
            balance -= principal
            AmortizationRow(m, interest, principal, balance)
        }
    }
}

/**
 * Due dates, paid/overdue status and reminders for bills, worked out from the transactions on the phone.
 *
 * Each payment counts for at most one cycle. First every cycle takes the closest payment made on time (from 5 days
 * before its due date to 10 days after). Then each cycle still unpaid, oldest first, takes the earliest payment left
 * over from 7 days before its due date up to the day before the next one, so a late payment settles the cycle it was
 * late for and is not counted again for the next.
 *
 * Which payments can pay a bill: a debit (an expense, a transfer or an investment such as a SIP or RD) that
 *  - with a keyword: names the payee as a word or the start of a word ("jio" matches "Jio Prepaid", not "JioMart";
 *    "rent" never matches "Torrent Power"), and, when the bill has an amount, is within 25% of it either way, so a
 *    bank transfer to the same company for some other amount does not count;
 *  - without a keyword: is the bill's amount to within ₹1, or for a loan within ₹1 or 1% of the EMI (whichever is
 *    more), because banks debit whole rupees while the worked-out EMI has paise.
 */
object BillTracker {
    /** A payment this many days around a due date is that cycle's, before anything else is considered. */
    private const val ON_TIME_EARLY_DAYS = 5L
    private const val ON_TIME_LATE_DAYS = 10L
    /** Otherwise a payment can count from this many days before a due date up to the day before the next one. */
    private const val EARLY_DAYS = 7L
    /** How long a paid cycle shows as paid before the next one takes over. */
    private const val PAID_DAYS = 7L
    /** The first reminder goes out this many days before a due date; a missed cycle stops showing as overdue then. */
    private const val REMIND_DAYS = 3L
    /** Unpaid cycles further back than this many cycles no longer take late payments. */
    private const val LATE_CYCLES = 3L
    /** With a keyword and an amount, a payment must be within this share of the amount, either way. */
    private const val AMOUNT_SLACK_PERCENT = 25L

    fun amountDue(b: Bill): Long? = b.amountPaise ?: b.loan?.let { Amortization.emi(it.principalPaise, it.annualRateBp, it.tenureMonths) }

    /** The first due date on or after [from]; null when there is none (a one-off date already passed, a finished loan). */
    fun nextDue(b: Bill, from: LocalDate): LocalDate? {
        b.fixedDue?.let { return if (from.isAfter(it)) null else it }
        val dates = dueDates(b, YearMonth.from(from), 0..24)
        return dates.firstOrNull { !it.isBefore(from) }
    }

    /** The latest due date on or before [on]. */
    fun lastDue(b: Bill, on: LocalDate): LocalDate? {
        b.fixedDue?.let { return if (it.isAfter(on)) null else it }
        return dueDates(b, YearMonth.from(on), -24..0).lastOrNull { !it.isAfter(on) }
    }

    private fun dueDates(b: Bill, around: YearMonth, offsets: IntRange): List<LocalDate> {
        val loanEnd = b.loan?.let { YearMonth.from(it.firstDue).plusMonths(it.tenureMonths - 1L) }
        val loanStart = b.loan?.let { YearMonth.from(it.firstDue) }
        return offsets.map { around.plusMonths(it.toLong()) }
            .filter { b.everyMonths <= 1 || Math.floorMod(it.monthValue - b.startMonth, b.everyMonths) == 0 }
            .filter { (loanStart == null || !it.isBefore(loanStart)) && (loanEnd == null || !it.isAfter(loanEnd)) }
            .map { it.atDay(b.dueDay.coerceIn(1, it.lengthOfMonth())) }
    }

    /** Every due date from about two years back up to the next one on or after [today]. */
    private fun cycles(b: Bill, today: LocalDate): List<LocalDate> {
        b.fixedDue?.let { return listOf(it) }
        val all = dueDates(b, YearMonth.from(today), -25..25)
        val next = all.firstOrNull { !it.isBefore(today) }
        return if (next == null) all else all.filter { !it.isAfter(next) }
    }

    /** The due date after [due]: the next cycle's, or a cycle's length on for a one-off date or a loan's last EMI. */
    private fun followingDue(b: Bill, due: LocalDate): LocalDate {
        if (b.fixedDue == null) dueDates(b, YearMonth.from(due), 1..25).firstOrNull { it.isAfter(due) }?.let { return it }
        return due.plusMonths(b.everyMonths.coerceAtLeast(1).toLong())
    }

    /**
     * A "this payment is not for this bill" choice. It is kept with the bill's paid marks as a day before 1970 that
     * stands for the transaction, so no new table is needed; [state] tells the two apart.
     */
    fun ignoreMark(transactionId: Long): LocalDate = LocalDate.ofEpochDay(-transactionId - 1)

    private fun ignoredIds(marks: Set<LocalDate>): Set<Long> = marks.filter { it.toEpochDay() < 0 }.mapTo(mutableSetOf()) { -it.toEpochDay() - 1 }

    fun state(b: Bill, today: LocalDate, txns: List<Transaction>, markedPaid: Set<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): BillState {
        val marks = markedPaid.filterTo(mutableSetOf()) { it.toEpochDay() >= 0 }
        val paidBy = assign(b, today, txns, marks, ignoredIds(markedPaid), zone)
        val created = b.createdAt?.takeIf { b.fixedDue == null }?.let { Instant.ofEpochMilli(it).atZone(zone).toLocalDate() }
        lastDue(b, today)?.let { last ->
            val since = ChronoUnit.DAYS.between(last, today)
            val paid = last in marks || paidBy[last] != null
            if (paid && since <= PAID_DAYS) return BillState.Paid(last, paidBy[last]?.id)
            // A missed cycle stays overdue until the next one's reminders start, so a short month never hides them,
            // and a cycle due before the bill was added was never this bill's to miss.
            val nextReminds = followingDue(b, last).minusDays(REMIND_DAYS)
            val existed = created == null || !last.isBefore(created)
            if (!paid && since >= 1 && today.isBefore(nextReminds) && existed) return BillState.Overdue(last, since)
        }
        val next = nextDue(b, today) ?: return BillState.Done
        if (next in marks || paidBy[next] != null) return BillState.Paid(next, paidBy[next]?.id)
        return BillState.Upcoming(next, ChronoUnit.DAYS.between(today, next))
    }

    /** Which payment paid which cycle, each payment counting once (see the class comment). */
    private fun assign(
        b: Bill, today: LocalDate, txns: List<Transaction>, marks: Set<LocalDate>, ignored: Set<Long>, zone: ZoneId,
    ): Map<LocalDate, Transaction> {
        val cycles = cycles(b, today)
        if (cycles.isEmpty()) return emptyMap()
        fun startOf(d: LocalDate) = d.atStartOfDay(zone).toInstant().toEpochMilli()
        val from = startOf(cycles.first().minusDays(EARLY_DAYS))
        val key = keywordTokens(b.keyword)
        val amount = amountDue(b)
        val candidates = txns.filter {
            it.type == TransactionType.DEBIT && !it.needsReview && it.timestamp >= from && it.id !in ignored &&
                (it.flow == Flow.EXPENSE || it.flow == Flow.TRANSFER || it.flow == Flow.INVESTMENT) && matches(b, it, key, amount)
        }.sortedBy { it.timestamp }
        if (candidates.isEmpty()) return emptyMap()
        val used = BooleanArray(candidates.size)
        val out = mutableMapOf<LocalDate, Transaction>()

        // 1. On time: the closest payment around each due date.
        for (due in cycles) {
            val start = startOf(due.minusDays(ON_TIME_EARLY_DAYS))
            val end = startOf(due.plusDays(ON_TIME_LATE_DAYS + 1))
            val dueMillis = startOf(due)
            candidates.indices.filter { !used[it] && candidates[it].timestamp in start until end }
                .minByOrNull { kotlin.math.abs(candidates[it].timestamp - dueMillis) }
                ?.let { out[due] = candidates[it]; used[it] = true }
        }

        // 2. Early or late: unpaid cycles, oldest first, take the earliest payment left in their wider window. Only
        // cycles whose whole window is covered by the transactions on the phone, and not too far back, so a gap in
        // the history or a cycle missed long ago cannot pull every later payment one cycle back.
        val dataStart = txns.minOfOrNull { it.timestamp } ?: return out
        val oldest = today.minusMonths(LATE_CYCLES * b.everyMonths.coerceAtLeast(1))
        for (due in cycles) {
            if (due in out || due in marks || due.isBefore(oldest)) continue
            val start = startOf(due.minusDays(EARLY_DAYS))
            if (start < dataStart) continue
            val end = startOf(followingDue(b, due))
            candidates.indices.firstOrNull { !used[it] && candidates[it].timestamp in start until end }
                ?.let { out[due] = candidates[it]; used[it] = true }
        }
        return out
    }

    private fun matches(b: Bill, t: Transaction, key: List<String>?, amount: Long?): Boolean {
        if (key != null) {
            if (!keywordMatches(key, t.merchant, prefixOk = amount != null)) return false
            return amount == null || kotlin.math.abs(t.amountPaise - amount) * 100 <= amount * AMOUNT_SLACK_PERCENT
        }
        if (amount == null) return false
        val slack = if (b.loan != null) maxOf(100L, amount / 100) else 100L
        return kotlin.math.abs(t.amountPaise - amount) <= slack
    }

    /** The keyword as lowercase words; null when it is too short to pick out a payee. */
    private fun keywordTokens(keyword: String?): List<String>? = keyword?.let { tokens(it) }?.takeIf { it.joinToString("").length >= 3 }

    /**
     * True when the merchant names the keyword: as whole words ("No Broker" for "nobroker" too), or as the start of a
     * word when the keyword is 4 letters or more ("airtel" in "AIRTELPOSTPAID"), or followed only by digits.
     */
    internal fun keywordMatches(key: List<String>, merchant: String, prefixOk: Boolean = false): Boolean {
        val joined = key.joinToString("")
        val words = tokens(merchant)
        for (i in words.indices) {
            val w = words[i]
            // A 3-letter keyword may start a longer word only when the amount check guards it (JioMart's ₹50 is not
            // a ₹999 Jio bill), so "jio" still finds "JIOMOBILITY" or "JIOFIBER".
            if (w.startsWith(joined) && (joined.length >= 4 || prefixOk || w.drop(joined.length).all { it.isDigit() })) return true
            val run = StringBuilder()
            for (j in i until words.size) {
                run.append(words[j])
                if (run.length >= joined.length) {
                    if (run.toString() == joined) return true
                    break
                }
            }
        }
        return false
    }

    fun loanProgress(b: Bill, today: LocalDate): LoanProgress? {
        val loan = b.loan ?: return null
        val paid = dueDates(b, YearMonth.from(loan.firstDue), 0 until loan.tenureMonths).count { !it.isAfter(today) }
        val schedule = Amortization.schedule(loan.principalPaise, loan.annualRateBp, loan.tenureMonths)
        val outstanding = if (paid == 0) loan.principalPaise else schedule[paid - 1].balancePaise
        return LoanProgress(paid, loan.tenureMonths, outstanding)
    }

    /** A heads-up 3 days and 1 day before an unpaid bill is due. */
    fun reminder(b: Bill, today: LocalDate, txns: List<Transaction>, markedPaid: Set<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): Reminder? {
        val s = state(b, today, txns, markedPaid, zone) as? BillState.Upcoming ?: return null
        val amount = amountDue(b)?.let { "${InsightsEngine.rupees(it)} " } ?: ""
        return Reminder(
            key = "bill:${b.id}", title = "${b.name} is due soon",
            body = "${amount}due ${s.due.dayOfMonth} ${s.due.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}",
            dueAt = s.due.atStartOfDay(zone).toInstant().toEpochMilli(), leadDays = listOf(REMIND_DAYS.toInt(), 1),
        )
    }

    private val nonAlnum = Regex("[^a-z0-9]+")
    private fun tokens(s: String): List<String> = s.lowercase().split(nonAlnum).filter { it.isNotEmpty() }
}
