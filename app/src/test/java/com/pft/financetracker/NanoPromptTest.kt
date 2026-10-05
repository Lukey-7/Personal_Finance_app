package com.pft.financetracker

import com.pft.financetracker.domain.ask.AskContext
import com.pft.financetracker.domain.ask.AskEngine
import com.pft.financetracker.domain.ask.NanoPrompt
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.recurring.RecurringBook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class NanoPromptTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 10, 20).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
    private fun on(d: String) = LocalDate.parse(d).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val txns = listOf(
        Transaction(id = 1, amountPaise = 1_200_00, type = TransactionType.DEBIT, merchant = "Swiggy", category = Category.FOOD, timestamp = on("2026-10-03"),
            bankName = "HDFC Bank", accountRef = "4321", source = Transaction.Source.SMS, flow = Flow.EXPENSE, refNumber = "426298765432"),
        Transaction(id = 2, amountPaise = 900_00, type = TransactionType.DEBIT, merchant = "Swiggy", category = Category.FOOD, timestamp = on("2026-09-03"),
            bankName = "HDFC Bank", accountRef = "4321", source = Transaction.Source.SMS, flow = Flow.EXPENSE),
    )
    private val ctx = AskContext(txns, emptyList(), RecurringBook.EMPTY,
        listOf(Bill(id = 1, name = "Rent", amountPaise = 25_000_00, dueDay = 25, keyword = null) to BillState.Upcoming(LocalDate.of(2026, 10, 25), 5)),
        netWorthPaise = null, now = now, zone = zone)

    @Test fun rulesSayWhenTheyUnderstoodTheQuestion() {
        assertTrue(AskEngine.answer("food last month", ctx).understood)
        assertFalse(AskEngine.answer("should I cut down on eating out?", ctx).understood)
    }

    @Test fun theFactsCarryTotalsButNoAccountOrReferenceNumbers() {
        val facts = NanoPrompt.facts(ctx)
        assertTrue(facts, facts.contains("Food & Dining"))
        assertTrue(facts, facts.contains("1,200"))
        assertTrue(facts, facts.contains("Rent"))
        assertFalse(facts, facts.contains("4321"))
        assertFalse(facts, facts.contains("426298765432"))
    }

    @Test fun thePromptHoldsTheQuestionAndAsksForAShortAnswerFromTheFactsOnly() {
        val p = NanoPrompt.prompt("should I cut down on eating out?", "FACTS")
        assertTrue(p.contains("should I cut down on eating out?"))
        assertTrue(p.contains("FACTS"))
        assertTrue(p.lowercase().contains("only"))
    }

    @Test fun anEmptyOrRunawayReplyIsDropped() {
        assertNull(NanoPrompt.clean("   "))
        assertNull(NanoPrompt.clean(null))
        assertEquals("Spend less on food.", NanoPrompt.clean("  Spend less on food.  "))
        assertTrue(NanoPrompt.clean("x".repeat(5000))!!.length <= 1200)
    }
}
