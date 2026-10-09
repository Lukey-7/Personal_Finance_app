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
        now = now, zone = zone,
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

    // ---- A wider ledger for periods, merchants, transfers and investments ----
    private fun moved(paise: Long, date: String, flow: Flow, cat: Category, m: String) = Transaction(id = id++, amountPaise = paise, type = TransactionType.DEBIT, merchant = m,
        category = cat, timestamp = on(date), bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = flow)

    private val more = txns + listOf(
        spend("Swiggy", 700_00, "2025-09-10", Category.FOOD), spend("Swiggy", 100_00, "2024-12-05", Category.FOOD),
        spend("Amazon Pay", 999_00, "2026-10-05", Category.SHOPPING), spend("BookMyShow", 400_00, "2026-10-06", Category.ENTERTAINMENT),
        spend("YouTube", 129_00, "2026-10-07", Category.ENTERTAINMENT), spend("Coca-Cola", 60_00, "2026-10-08", Category.FOOD),
        spend("Ola", 250_00, "2026-10-09", Category.TRANSPORT),
        moved(10_000_00, "2026-10-04", Flow.TRANSFER, Category.TRANSFER, "Self transfer"),
        moved(5_000_00, "2026-10-02", Flow.INVESTMENT, Category.INVESTMENT, "Zerodha"),
    )
    private val wide = ctx.copy(txns = more, recurring = RecurringBook.of(RecurringDetector.detect(more, now), emptyList()))
    private fun ask2(q: String) = AskEngine.answer(q, wide)

    @Test fun lastYearIsLastYear() {
        val a = ask2("how much did I spend last year").text
        assertTrue(a, a.contains("in 2025") && a.contains("₹700"))
    }

    @Test fun aNamedMonthTakesTheYearSaid() {
        val a = ask2("food in september last year").text
        assertTrue(a, a.contains("September 2025") && a.contains("₹700"))
        val b = ask2("swiggy december 2024").text
        assertTrue(b, b.contains("December 2024") && b.contains("₹100"))
    }

    @Test fun septIsSeptember() {
        val a = ask2("spending in sept").text
        assertTrue(a, a.contains("September 2026") && a.contains("₹949"))
    }

    @Test fun mayAsAVerbIsNotTheMonth() {
        val a = ask2("how much may I spend").text
        assertTrue(a, a.contains("this month") && !a.contains("May"))
        assertTrue(ask2("spending in may").text.contains("May 2026"))
    }

    @Test fun rollingSpansCountBackFromToday() {
        val a = ask2("how much did I spend in the last 3 months").text
        assertTrue(a, a.contains("in the last 3 months") && a.contains("₹6,436"))
        val b = ask2("spending past week").text
        assertTrue(b, b.contains("in the past week") && b.contains("₹0"))
        assertTrue(ask2("spent in the last 7 days").text.contains("in the last 7 days"))
    }

    @Test fun weeklyAndWeekendAreNotThisWeek() {
        assertTrue(ask2("weekly spending").text.contains("this month"))
        assertTrue(ask2("spending on the weekend").text.contains("this month"))
    }

    @Test fun commonWordsNeverPickAMerchant() {
        val a = ask2("how much can you show me").text
        assertTrue(a, !a.contains("YouTube") && !a.contains("BookMyShow") && !a.contains("Amazon Pay"))
        val b = ask2("how much did I pay").text
        assertTrue(b, !b.contains("Amazon Pay"))
    }

    @Test fun aMerchantMustStartAWordOfTheName() {
        val a = ask2("how much on ola").text
        assertTrue(a, a.contains("at Ola") && a.contains("₹250"))
        val b = ask2("how much at amazon pay").text
        assertTrue(b, b.contains("Amazon Pay") && b.contains("₹999"))
        assertTrue(!AskEngine.merchantMatches("Coca-Cola", "ola") && !AskEngine.merchantMatches("Motorola", "ola"))
        assertTrue(!AskEngine.merchantMatches("BookMyShow", "show"))
        assertTrue(AskEngine.merchantMatches("Ola Cabs", "ola") && AskEngine.merchantMatches("Amazon Pay", "amazonpay"))
    }

    @Test fun budgetsAnswerForTheMonthAsked() {
        val a = ask2("how much budget was left in september").text
        assertTrue(a, a.contains("September 2026") && a.contains("₹4,700"))
    }

    @Test fun transfersAndInvestmentsAreAnsweredFromTheirOwnTotals() {
        val a = ask2("how much did I transfer this month").text
        assertTrue(a, a.contains("₹10,000"))
        val b = ask2("how much did I invest").text
        assertTrue(b, b.contains("₹5,000"))
    }

    @Test fun financialHealthIsNotTheHealthCategory() {
        val a = ask2("how is my financial health").text
        assertTrue(a, !a.contains("on Health"))
    }

    @Test fun earningsCountAsIncome() {
        assertTrue(ask2("what were my earnings this month").text.contains("₹85,000"))
        assertTrue(ask2("how much have I earned").text.contains("₹85,000"))
    }

    @Test fun mostComparesMerchantSpendWithSpendBeforeRefunds() {
        val a = ask("where did most of my money go?").text
        assertTrue(a, a.contains("₹2,000 of ₹3,000 spent this month"))
    }
}
