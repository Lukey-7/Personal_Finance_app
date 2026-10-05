package com.pft.financetracker.domain.bills

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.reminders.Reminder
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

/** Due dates, paid/overdue status and reminders for bills, worked out from the transactions on the phone. */
object BillTracker {
    /** How long before and after a due date a payment counts for it. */
    private const val EARLY_DAYS = 7L
    private const val LATE_DAYS = 20L
    /** How long a missed bill shows as overdue, and a paid one as paid, before the next cycle takes over. */
    private const val OVERDUE_DAYS = 25L
    private const val PAID_DAYS = 7L

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

    fun state(b: Bill, today: LocalDate, txns: List<Transaction>, markedPaid: Set<LocalDate>, zone: ZoneId = ZoneId.systemDefault()): BillState {
        lastDue(b, today)?.let { last ->
            val since = ChronoUnit.DAYS.between(last, today)
            val pay = payment(b, last, txns, zone)
            val paid = last in markedPaid || pay != null
            if (paid && since <= PAID_DAYS) return BillState.Paid(last, pay?.id)
            if (!paid && since in 1..OVERDUE_DAYS) return BillState.Overdue(last, since)
        }
        val next = nextDue(b, today) ?: return BillState.Done
        if (next in markedPaid) return BillState.Paid(next, null)
        payment(b, next, txns, zone)?.let { return BillState.Paid(next, it.id) }
        return BillState.Upcoming(next, ChronoUnit.DAYS.between(today, next))
    }

    /** The payment closest to [due] inside its window, by keyword or (for a fixed amount) by the exact amount. */
    fun payment(b: Bill, due: LocalDate, txns: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): Transaction? {
        val start = due.minusDays(EARLY_DAYS).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = due.plusDays(LATE_DAYS + 1).atStartOfDay(zone).toInstant().toEpochMilli()
        val key = b.keyword?.let { normalize(it) }?.takeIf { it.length >= 3 }
        val amount = amountDue(b)
        val dueMillis = due.atStartOfDay(zone).toInstant().toEpochMilli()
        return txns.asSequence()
            .filter { it.type == TransactionType.DEBIT && !it.needsReview && it.timestamp in start until end }
            .filter { it.flow == Flow.EXPENSE || it.flow == Flow.TRANSFER }
            .filter { t -> (key != null && normalize(t.merchant).contains(key)) || (amount != null && t.amountPaise == amount) }
            .minByOrNull { kotlin.math.abs(it.timestamp - dueMillis) }
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
        val amount = amountDue(b)?.let { "₹${InsightsEngine.fmt(it)} " } ?: ""
        return Reminder(
            key = "bill:${b.id}", title = "${b.name} is due soon",
            body = "${amount}due ${s.due.dayOfMonth} ${s.due.month.name.lowercase().replaceFirstChar { it.uppercase() }.take(3)}",
            dueAt = s.due.atStartOfDay(zone).toInstant().toEpochMilli(), leadDays = listOf(3, 1),
        )
    }

    private val nonAlnum = Regex("[^a-z0-9]")
    private fun normalize(s: String) = s.lowercase().replace(nonAlnum, "")
}
