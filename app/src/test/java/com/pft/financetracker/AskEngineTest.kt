package com.pft.financetracker

import com.pft.financetracker.domain.ask.AskContext
import com.pft.financetracker.domain.ask.AskEngine
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.recurring.RecurringBook
import com.pft.financetracker.domain.recurring.RecurringDetector
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class AskEngineTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 10, 20).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
    private var id = 1L
    private fun on(date: String) = LocalDate.parse(date).atTime(13, 0).atZone(zone).toInstant().toEpochMilli()
    private fun spend(m: String, paise: Long, date: String, cat: Category) = Transaction(id = id++, amountPaise = paise, type = TransactionType.DEBIT, merchant = m,
        category = cat, timestamp = on(date), bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.EXPENSE)
    private fun income(paise: Long, date: String) = Transaction(id = id++, amountPaise = paise, type = TransactionType.CREDIT, merchant = "ACME SALARY",
        category = Category.INCOME, timestamp = on(date), bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.INCOME)

    private val txns = listOf(
        spend("Swiggy", 450_00, "2026-10-03", Category.FOOD), spend("Zomato", 550_00, "2026-10-10", Category.FOOD),
        spend("Amazon", 2_000_00, "2026-10-12", Category.SHOPPING), spend("Swiggy", 300_00, "2026-09-15", Category.FOOD),
        spend("Netflix", 649_00, "2026-08-25", Category.ENTERTAINMENT), spend("Netflix", 649_00, "2026-09-25", Category.ENTERTAINMENT),
        income(85_000_00, "2026-10-01"), income(80_000_00, "2026-09-01"),
    )
    private val ctx = AskContext(
        txns = txns, budgets = listOf(Budget(Category.FOOD, 5_000_00)),
        recurring = RecurringBook.of(RecurringDetector.detect(txns, now), emptyList()),
        bills = listOf(Bill(id = 1, name = "Rent", amountPaise = 25_000_00, dueDay = 25, keyword = null) to BillState.Upcoming(LocalDate.of(2026, 10, 25), 5)),
        netWorthPaise = null, now = now, zone = zone,
    )
    private fun ask(q: String) = AskEngine.answer(q, ctx)

    @Test fun spendOnACategoryThisMonth() {
        val a = ask("How much did I spend on food this month?")
        assertTrue(a.text, a.text.contains("₹1,000"))
        assertEquals(2, a.transactionIds.size)
    }

    @Test fun spendOnACategoryLastMonth() = assertTrue(ask("food last month").text.contains("₹300"))

    @Test fun spendAtAMerchant() {
        val a = ask("how much on swiggy")
        assertTrue(a.text, a.text.contains("₹450"))
    }

    @Test fun spendInANamedMonth() = assertTrue(ask("spending in september").text.contains("₹949"))

    @Test fun totalSpendDefaultsToThisMonth() = assertTrue(ask("how much have I spent").text.contains("₹3,000"))

    @Test fun biggestMerchant() = assertTrue(ask("where did most of my money go?").text.contains("Amazon"))

    @Test fun incomeAndSavings() {
        assertTrue(ask("what did I earn this month").text.contains("₹85,000"))
        assertTrue(ask("how much did I save").text.contains("₹82,000"))
    }

    @Test fun subscriptions() = assertTrue(ask("what subscriptions do I have").text.contains("Netflix"))

    @Test fun billsDue() = assertTrue(ask("any bills due?").text.contains("Rent"))

    @Test fun budgetLeft() = assertTrue(ask("how much budget is left").text.contains("₹4,000"))

    @Test fun anUnknownQuestionExplainsWhatCanBeAsked() {
        val a = ask("what is the meaning of life")
        assertTrue(a.text.contains("Try"))
        assertTrue(a.transactionIds.isEmpty())
    }

    @Test fun aNegativeFigurePutsTheSignBeforeTheRupee() {
        val a = AskEngine.answer("what is my net worth", ctx.copy(netWorthPaise = -2_00_000_00))
        assertTrue(a.text, a.text.contains("-₹2,00,000"))
        assertTrue(a.text, !a.text.contains("₹-"))
    }
}
