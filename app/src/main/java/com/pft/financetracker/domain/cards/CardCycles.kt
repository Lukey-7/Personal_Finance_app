package com.pft.financetracker.domain.cards

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
 * the day payment is due, and an optional reward rate in basis points (1.5% = 150).
 */
data class Card(val id: Long = 0, val last4: String, val name: String, val statementDay: Int, val dueDay: Int, val rewardBp: Int = 0)

/** One billing cycle: purchases from [start] to [end] (the statement date) are paid by [due]. */
data class CardCycle(val start: LocalDate, val end: LocalDate, val due: LocalDate)

data class CardSummary(val card: Card, val cycle: CardCycle, val spendPaise: Long, val rewardPaise: Long, val daysToDue: Long)

/** Billing-cycle arithmetic for credit cards, from the card's statement and due days. */
object CardCycles {
    private fun statementDate(c: Card, ym: YearMonth): LocalDate = ym.atDay(c.statementDay.coerceIn(1, ym.lengthOfMonth()))

    fun current(c: Card, today: LocalDate): CardCycle {
        val ym = YearMonth.from(today)
        val thisStatement = statementDate(c, ym)
        val (start, end) = if (!today.isAfter(thisStatement)) statementDate(c, ym.minusMonths(1)).plusDays(1) to thisStatement
            else thisStatement.plusDays(1) to statementDate(c, ym.plusMonths(1))
        val endYm = YearMonth.from(end)
        val sameMonth = endYm.atDay(c.dueDay.coerceIn(1, endYm.lengthOfMonth()))
        val due = if (sameMonth.isAfter(end)) sameMonth else endYm.plusMonths(1).let { it.atDay(c.dueDay.coerceIn(1, it.lengthOfMonth())) }
        return CardCycle(start, end, due)
    }

    /** This cycle's spend on the card (purchases minus refunds to it) and the reward it should earn. */
    fun summary(c: Card, today: LocalDate, txns: List<Transaction>, zone: ZoneId = ZoneId.systemDefault()): CardSummary {
        val cycle = current(c, today)
        val onCard = txns.filter { it.accountRef == c.last4 && !it.needsReview }
            .filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().let { d -> !d.isBefore(cycle.start) && !d.isAfter(cycle.end) } }
        val spend = onCard.filter { it.type == TransactionType.DEBIT && it.flow == Flow.EXPENSE }.sumOf { it.amountPaise } -
            onCard.filter { it.type == TransactionType.CREDIT && it.flow == Flow.REFUND }.sumOf { it.amountPaise }
        return CardSummary(c, cycle, spend, spend * c.rewardBp / 10_000, ChronoUnit.DAYS.between(today, cycle.due))
    }
}
