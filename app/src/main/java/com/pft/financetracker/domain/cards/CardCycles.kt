package com.pft.financetracker.domain.cards

import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/**
 * A credit card the person set up: its last four digits (as card SMS show them), the day each statement is made,
 * the day payment is due, and an optional reward rate in basis points (1.5% = 150). Nothing else about the card is
 * kept: never the full number, expiry, CVV or PIN.
 */
data class Card(val id: Long = 0, val last4: String, val name: String, val statementDay: Int, val dueDay: Int, val rewardBp: Int = 0)

/** One billing cycle: purchases from [start] to [end] (the statement date) are paid by [due]. */
data class CardCycle(val start: LocalDate, val end: LocalDate, val due: LocalDate)

/**
 * A card on [today]: the open [cycle] still collecting spend, the [lastStatement] (the cycle that closed most recently,
 * whose bill is the one to pay now), and [payBy], the due date to show: the last statement's until it passes, then the
 * open cycle's. Spend and rewards are never below zero.
 */
data class CardSummary(
    val card: Card,
    val today: LocalDate,
    val cycle: CardCycle,
    val spendPaise: Long,
    val rewardPaise: Long,
    val lastStatement: CardCycle,
    val lastStatementSpendPaise: Long,
    val payBy: LocalDate,
    val daysToDue: Long,
)

/**
 * What to pay on a card and by when. From the bank's statement SMS when there is one ([fromStatement], [amountPaise]
 * is the real total due), otherwise worked out from the card's days and the spend seen in SMS (an estimate).
 * [daysLeft] is negative when overdue.
 */
data class CardDue(
    val due: LocalDate,
    val daysLeft: Long,
    val amountPaise: Long?,
    val fromStatement: Boolean,
    val overdue: Boolean = false,
    val paid: Boolean = false,
)

/** Billing-cycle arithmetic for credit cards, from the card's statement and due days. */
object CardCycles {
    private fun statementDate(c: Card, ym: YearMonth): LocalDate = ym.atDay(c.statementDay.coerceIn(1, ym.lengthOfMonth()))

    /** The due date for a statement made on [end]: the due day after it, in the same month or the next. */
    private fun dueAfter(c: Card, end: LocalDate): LocalDate {
        val endYm = YearMonth.from(end)
        val sameMonth = endYm.atDay(c.dueDay.coerceIn(1, endYm.lengthOfMonth()))
        return if (sameMonth.isAfter(end)) sameMonth else endYm.plusMonths(1).let { it.atDay(c.dueDay.coerceIn(1, it.lengthOfMonth())) }
    }

    /** The open cycle on [today]: it runs to the next statement date (today included, if the statement is today). */
    fun current(c: Card, today: LocalDate): CardCycle {
        val ym = YearMonth.from(today)
        val thisStatement = statementDate(c, ym)
        val (start, end) = if (!today.isAfter(thisStatement)) statementDate(c, ym.minusMonths(1)).plusDays(1) to thisStatement
            else thisStatement.plusDays(1) to statementDate(c, ym.plusMonths(1))
        return CardCycle(start, end, dueAfter(c, end))
    }

    /** The cycle that closed most recently: its statement is out and its bill is the one to pay now. */
    fun lastStatement(c: Card, today: LocalDate): CardCycle {
        val end = current(c, today).start.minusDays(1)
        val start = statementDate(c, YearMonth.from(end).minusMonths(1)).plusDays(1)
        return CardCycle(start, end, dueAfter(c, end))
    }

    /**
     * Spend on the card between two dates: purchases and cash withdrawals on it, minus refunds to it, never below zero.
     * Payments into the card (transfers) are not spend. Returns spend and the part that earns rewards (purchases only).
     */
    private fun spendIn(c: Card, from: LocalDate, to: LocalDate, txns: List<Transaction>, zone: ZoneId): Pair<Long, Long> {
        val onCard = txns.filter { it.accountRef == c.last4 && !it.needsReview }
            .filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().let { d -> !d.isBefore(from) && !d.isAfter(to) } }
        val purchases = onCard.filter { it.type == TransactionType.DEBIT && it.flow == Flow.EXPENSE }.sumOf { it.amountPaise }
        val cash = onCard.filter { it.type == TransactionType.DEBIT && it.flow == Flow.CASH }.sumOf { it.amountPaise }
        val refunds = onCard.filter { it.type == TransactionType.CREDIT && it.flow == Flow.REFUND }.sumOf { it.amountPaise }
        return (purchases + cash - refunds).coerceAtLeast(0) to (purchases - refunds).coerceAtLeast(0)
    }

    /** The open cycle's spend and rewards, last statement's spend, and when to pay. */
    fun summary(c: Card, today: LocalDate, txns: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): CardSummary {
        val cycle = current(c, today)
        val last = lastStatement(c, today)
        val (spend, rewardable) = spendIn(c, cycle.start, cycle.end, txns, zone)
        val (lastSpend, _) = spendIn(c, last.start, last.end, txns, zone)
        val payBy = if (today.isAfter(last.due)) cycle.due else last.due
        return CardSummary(c, today, cycle, spend, rewardable * c.rewardBp / 10_000, last, lastSpend, payBy, ChronoUnit.DAYS.between(today, payBy))
    }

    /**
     * What to pay on the card: the bank's statement bill for it (matched by last four digits) while it is upcoming,
     * overdue or just paid; otherwise the estimate from [s].
     */
    fun due(s: CardSummary, bills: List<Pair<Bill, BillState>>): CardDue {
        val match = bills.firstOrNull { it.first.cardLast4 == s.card.last4 }
        val bill = match?.first
        when (val st = match?.second) {
            is BillState.Upcoming -> return CardDue(st.due, st.daysLeft, bill?.amountPaise, fromStatement = true)
            is BillState.Overdue -> return CardDue(st.due, -st.daysLate, bill?.amountPaise, fromStatement = true, overdue = true)
            is BillState.Paid -> return CardDue(st.due, ChronoUnit.DAYS.between(s.today, st.due), bill?.amountPaise, fromStatement = true, paid = true)
            else -> Unit
        }
        // Only the closed statement has a bill to estimate; the open cycle's spend is still growing.
        val estimate = if (s.payBy == s.lastStatement.due) s.lastStatementSpendPaise.takeIf { it > 0 } else null
        return CardDue(s.payBy, s.daysToDue, estimate, fromStatement = false)
    }

    /** Another card already set up with the same last four digits as [c], if any (saving [c] would replace it). */
    fun clash(c: Card, existing: List<Card>): Card? = existing.firstOrNull { it.last4 == c.last4 && it.id != c.id }

    /**
     * Cleans what was typed or pasted into the last-4 field: digits only, and if more than four (a full card number
     * pasted), only the last four. [trimmed] is true when digits were dropped, so the editor can say so.
     */
    fun cleanLast4(input: String): Last4Input {
        val digits = input.filter { it in '0'..'9' }
        return if (digits.length > 4) Last4Input(digits.takeLast(4), trimmed = true) else Last4Input(digits, trimmed = false)
    }

    data class Last4Input(val last4: String, val trimmed: Boolean)
}
