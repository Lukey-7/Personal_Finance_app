package com.pft.financetracker

import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.books.CountingRules
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * The books' promise: every figure equals the sum of the payments it lists, under either counting rule. Checked on
 * generated months of mixed payments (spend, refunds that match a purchase and refunds that don't, income, transfers
 * both ways, settlements, investments and redemptions, cash, review items).
 */
class BooksTest {
    private val now = 1_790_000_000_000L
    private val month = Periods.month(0, now)
    private val merchants = listOf("Swiggy", "Amazon", "Uber", "BigBasket", "Payment (HDFC Bank)", "Rahul Sharma", "Zerodha", "ATM")

    private fun generated(seed: Int): List<Transaction> {
        val r = Random(seed)
        return List(160) { i ->
            val flow = Flow.entries[r.nextInt(Flow.entries.size)]
            val type = when (flow) {
                Flow.EXPENSE, Flow.CASH -> TransactionType.DEBIT
                Flow.INCOME, Flow.REFUND -> TransactionType.CREDIT
                Flow.TRANSFER, Flow.INVESTMENT, Flow.SETTLEMENT -> if (r.nextBoolean()) TransactionType.DEBIT else TransactionType.CREDIT
            }
            Transaction(
                id = i + 1L, amountPaise = r.nextLong(100, 5_000_000), type = type, merchant = merchants[r.nextInt(merchants.size)],
                category = Category.entries[r.nextInt(Category.entries.size)],
                // Mostly this month, some either side of it.
                timestamp = month.start - 5 * 86_400_000L + r.nextLong(0, month.end - month.start + 10 * 86_400_000L),
                bankName = "HDFC Bank", accountRef = if (r.nextBoolean()) "1234" else null, source = Transaction.Source.SMS, flow = flow,
                needsReview = r.nextInt(20) == 0,
            )
        }
    }

    @Test fun everyFigureIsTheSumOfItsPayments() {
        for (seed in 1..40) for (cash in listOf(true, false)) {
            val books = Books.of(generated(seed), CountingRules(cashIsSpend = cash))
            val s = books.summary(month)
            fun sum(b: Bucket, c: Category? = null) = Books.total(books.payments(month, b, c), b)
            val at = "seed $seed, cash counted $cash"
            assertEquals(at, s.netSpendPaise, sum(Bucket.SPEND))
            for (c in s.byCategory) assertEquals("$at, ${c.category}", c.amountPaise, sum(Bucket.SPEND, c.category))
            assertEquals(at, s.refundsPaise, sum(Bucket.REFUNDS))
            assertEquals(at, s.incomePaise, sum(Bucket.INCOME))
            assertEquals(at, s.transfersOutPaise, sum(Bucket.TRANSFERS_OUT))
            assertEquals(at, s.transfersInPaise - s.settlementsInPaise, sum(Bucket.TRANSFERS_IN))
            assertEquals(at, s.settlementsInPaise, sum(Bucket.PAID_BACK))
            assertEquals(at, s.investmentsPaise, sum(Bucket.INVESTMENTS))
            assertEquals(at, s.cashPaise, sum(Bucket.CASH))
            assertEquals(at, s.count, books.payments(month, Bucket.ALL).size)
            assertEquals(at, s.expenseCount, books.payments(month, Bucket.SPEND).count { books.isSpend(it) })
            assertEquals(at, s.grossSpendPaise, books.payments(month, Bucket.SPEND).filter { books.isSpend(it) }.sumOf { it.amountPaise })
        }
    }

    @Test fun cashCountsAsSpendOnlyUnderThatRule() {
        val atm = Transaction(amountPaise = 200_000, type = TransactionType.DEBIT, merchant = "ATM", category = Category.ATM, timestamp = month.start + 86_400_000L,
            bankName = "SBI", accountRef = null, source = Transaction.Source.SMS, flow = Flow.CASH)
        assertEquals(200_000L, Books.of(listOf(atm)).summary(month).netSpendPaise)
        assertEquals(0L, Books.of(listOf(atm), CountingRules(cashIsSpend = false)).summary(month).netSpendPaise)
        assertEquals(200_000L, Books.of(listOf(atm), CountingRules(cashIsSpend = false)).summary(month).cashPaise)
        assertTrue(Books.of(listOf(atm)).payments(month, Bucket.SPEND).isNotEmpty())
        assertTrue(Books.of(listOf(atm), CountingRules(cashIsSpend = false)).payments(month, Bucket.SPEND).isEmpty())
    }

    @Test fun budgetsReadTheSameCategoryFiguresAsTheSummary() {
        for (seed in 1..10) {
            val books = Books.of(generated(seed), CountingRules(cashIsSpend = seed % 2 == 0))
            val limits = Category.spendCategories.map { Budget(it, 1_000_000) }
            val byCat = books.summary(month).byCategory.associate { it.category to it.amountPaise }
            for (b in books.budgets(limits, month)) assertEquals("seed $seed, ${b.budget.category}", byCat[b.budget.category] ?: 0L, b.spentPaise)
        }
    }

    @Test fun aSummaryIsWorkedOutOncePerPeriod() {
        val books = Books.of(generated(7))
        assertTrue(books.summary(month) === books.summary(month))
    }
}
