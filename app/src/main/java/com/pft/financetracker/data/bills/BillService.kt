package com.pft.financetracker.data.bills

import com.pft.financetracker.data.local.BillDao
import com.pft.financetracker.data.local.BillEntity
import com.pft.financetracker.data.local.BillMarkEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.BillTracker
import com.pft.financetracker.domain.bills.CardStatement
import com.pft.financetracker.domain.bills.Loan
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.reminders.Reminder
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Bills, EMIs and card bills: stored by the person (or read from card statements), tracked against the transactions. */
class BillService(private val txDao: TransactionDao, private val dao: BillDao, private val zone: ZoneId = ZoneId.systemDefault()) {

    suspend fun all(): List<Bill> = dao.getAll().map { it.toDomain() }

    suspend fun save(b: Bill): Long = if (b.id == 0L) dao.insert(b.toEntity()) else { dao.update(b.toEntity()); b.id }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun markPaid(id: Long, due: LocalDate) = dao.mark(BillMarkEntity(billId = id, dueDay = due.toEpochDay()))
    suspend fun unmarkPaid(id: Long, due: LocalDate) = dao.unmark(id, due.toEpochDay())

    /** "This payment is not for this bill": the bill stops counting it (see [BillTracker.ignoreMark]). */
    suspend fun ignorePayment(id: Long, transactionId: Long) = dao.mark(BillMarkEntity(billId = id, dueDay = BillTracker.ignoreMark(transactionId).toEpochDay()))

    /** One bill per card: a new statement moves its due date and amount to the new cycle. */
    suspend fun fromStatement(s: CardStatement, bank: String?) {
        val last4 = s.cardLast4
        val existing = last4?.let { dao.byCard(it) }
        if (existing != null) {
            // A rescan may read last month's statement after this month's: never move the bill back in time.
            if (existing.fixedDueDay != null && existing.fixedDueDay > s.dueDate.toEpochDay()) return
            dao.update(existing.copy(amountPaise = s.totalDuePaise, fixedDueDay = s.dueDate.toEpochDay(), dueDay = s.dueDate.dayOfMonth))
        } else {
            save(Bill(name = "${bank ?: "Credit"} card" + (last4?.let { " ••$it" } ?: ""), amountPaise = s.totalDuePaise, dueDay = s.dueDate.dayOfMonth,
                keyword = null, category = Category.TRANSFER, fixedDue = s.dueDate, cardLast4 = last4))
        }
    }

    suspend fun states(today: LocalDate = LocalDate.now(zone)): List<Pair<Bill, BillState>> {
        val txns = txDao.getAll().map { it.toDomain() }
        val marks = dao.allMarks().groupBy({ it.billId }, { LocalDate.ofEpochDay(it.dueDay) })
        return statesOf(all(), txns, marks, today)
    }

    fun statesOf(bills: List<Bill>, txns: List<Transaction>, marks: Map<Long, List<LocalDate>>, today: LocalDate): List<Pair<Bill, BillState>> =
        bills.map { b -> b to BillTracker.state(b, today, txns, marks[b.id].orEmpty().toSet(), zone) }

    suspend fun reminders(now: Long): List<Reminder> {
        val today = Instant.ofEpochMilli(now).atZone(zone).toLocalDate()
        val txns = txDao.getAll().map { it.toDomain() }
        val marks = dao.allMarks().groupBy({ it.billId }, { LocalDate.ofEpochDay(it.dueDay) })
        return all().mapNotNull { BillTracker.reminder(it, today, txns, marks[it.id].orEmpty().toSet(), zone) }
    }
}

fun BillEntity.toDomain() = Bill(
    id = id, name = name, amountPaise = amountPaise, dueDay = dueDay, keyword = keyword,
    category = Category.entries.firstOrNull { it.name == category } ?: Category.BILLS,
    everyMonths = everyMonths, startMonth = startMonth, fixedDue = fixedDueDay?.let { LocalDate.ofEpochDay(it) },
    loan = if (loanPrincipalPaise != null && loanRateBp != null && loanTenureMonths != null && loanFirstDueDay != null)
        Loan(loanPrincipalPaise, loanRateBp, loanTenureMonths, LocalDate.ofEpochDay(loanFirstDueDay)) else null,
    cardLast4 = cardLast4, createdAt = createdAt,
)

/** A new bill is stamped with today; an edited one keeps the day it was added. */
fun Bill.toEntity() = BillEntity(
    id = id, name = name, amountPaise = amountPaise, dueDay = dueDay, keyword = keyword, category = category.name,
    everyMonths = everyMonths, startMonth = startMonth, fixedDueDay = fixedDue?.toEpochDay(),
    loanPrincipalPaise = loan?.principalPaise, loanRateBp = loan?.annualRateBp, loanTenureMonths = loan?.tenureMonths,
    loanFirstDueDay = loan?.firstDue?.toEpochDay(), cardLast4 = cardLast4, createdAt = createdAt ?: System.currentTimeMillis(),
)
