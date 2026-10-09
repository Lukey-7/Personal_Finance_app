package com.pft.financetracker

import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.cards.Card
import com.pft.financetracker.domain.cards.CardCycles
import com.pft.financetracker.domain.cards.CardDue
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class CardCycleTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun d(s: String) = LocalDate.parse(s)
    private fun at(s: String) = d(s).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private val card = Card(id = 1, last4 = "1234", name = "HDFC Millennia", statementDay = 15, dueDay = 5, rewardBp = 150)

    @Test fun theOpenCycleRunsFromTheDayAfterOneStatementToTheNext() {
        val c = CardCycles.current(card, d("2026-10-04"))
        assertEquals(d("2026-09-16"), c.start); assertEquals(d("2026-10-15"), c.end); assertEquals(d("2026-11-05"), c.due)
    }

    @Test fun theLastStatementIsTheCycleThatClosedMostRecently() {
        val c = CardCycles.lastStatement(card, d("2026-10-04"))
        assertEquals(d("2026-08-16"), c.start); assertEquals(d("2026-09-15"), c.end); assertEquals(d("2026-10-05"), c.due)
        val after = CardCycles.lastStatement(card, d("2026-10-16"))
        assertEquals(d("2026-09-16"), after.start); assertEquals(d("2026-10-15"), after.end); assertEquals(d("2026-11-05"), after.due)
    }

    @Test fun payByIsTheLastStatementsDueDateUntilItPasses() {
        // Statement on the 15th, due on the 5th: on 4 Oct the September statement is due tomorrow, not 5 Nov.
        val before = CardCycles.summary(card, d("2026-10-04"), emptyList(), zone)
        assertEquals(d("2026-10-05"), before.payBy); assertEquals(1L, before.daysToDue)
        val onTheDay = CardCycles.summary(card, d("2026-10-05"), emptyList(), zone)
        assertEquals(d("2026-10-05"), onTheDay.payBy); assertEquals(0L, onTheDay.daysToDue)
        // Once it passes, the next bill (the open cycle's) is the one to pay.
        val after = CardCycles.summary(card, d("2026-10-06"), emptyList(), zone)
        assertEquals(d("2026-11-05"), after.payBy); assertEquals(30L, after.daysToDue)
        // After the next statement, its bill is due 5 Nov.
        val nextStatement = CardCycles.summary(card, d("2026-10-16"), emptyList(), zone)
        assertEquals(d("2026-11-05"), nextStatement.payBy)
    }

    @Test fun aDueDayAfterTheStatementDayIsPaidInTheSameMonthAsTheStatement() {
        val s = CardCycles.summary(card.copy(statementDay = 2, dueDay = 22), d("2026-10-10"), emptyList(), zone)
        assertEquals(d("2026-10-02"), s.lastStatement.end); assertEquals(d("2026-10-22"), s.payBy)
    }

    @Test fun afterTheStatementDayANewCycleStarts() {
        val c = CardCycles.current(card, d("2026-10-16"))
        assertEquals(d("2026-10-16"), c.start); assertEquals(d("2026-11-15"), c.end); assertEquals(d("2026-12-05"), c.due)
    }

    @Test fun aStatementOnThe30thFallsOnFebruary28th() {
        val c = CardCycles.current(card.copy(statementDay = 30, dueDay = 20), d("2027-02-10"))
        assertEquals(d("2027-01-31"), c.start); assertEquals(d("2027-02-28"), c.end); assertEquals(d("2027-03-20"), c.due)
    }

    @Test fun aDueDayAfterTheStatementDayIsInTheSameMonth() {
        val c = CardCycles.current(card.copy(statementDay = 2, dueDay = 22), d("2026-10-10"))
        assertEquals(d("2026-11-02"), c.end); assertEquals(d("2026-11-22"), c.due)
    }

    private fun tx(paise: Long, on: String, ref: String? = "1234", type: TransactionType = TransactionType.DEBIT, flow: Flow = Flow.EXPENSE) =
        Transaction(amountPaise = paise, type = type, merchant = "X", category = Category.SHOPPING, timestamp = at(on), bankName = null, accountRef = ref,
            source = Transaction.Source.SMS, flow = flow)

    @Test fun spendInTheCycleIsThisCardsPurchasesMinusItsRefunds() {
        val txns = listOf(
            tx(1_000_00, "2026-09-20"), tx(2_500_00, "2026-10-01"),
            tx(500_00, "2026-10-02", type = TransactionType.CREDIT, flow = Flow.REFUND),
            tx(9_999_00, "2026-10-01", ref = "9999"),                 // another account
            tx(1_000_00, "2026-09-10"),                               // previous cycle
            tx(3_000_00, "2026-10-03", type = TransactionType.CREDIT, flow = Flow.TRANSFER),   // bill payment in
        )
        val s = CardCycles.summary(card, d("2026-10-04"), txns, zone)
        assertEquals(3_000_00L, s.spendPaise)
        assertEquals(4_500L, s.rewardPaise)       // 1.5% of ₹3,000
        assertEquals(1_000_00L, s.lastStatementSpendPaise)   // 10 Sep, in the cycle that closed on 15 Sep
        assertEquals(1L, s.daysToDue)             // that statement is due 5 Oct
    }

    @Test fun cashWithdrawalsOnTheCardCountAsSpendButEarnNoRewards() {
        val txns = listOf(tx(1_000_00, "2026-09-20"), tx(2_000_00, "2026-09-21", flow = Flow.CASH))
        val s = CardCycles.summary(card, d("2026-10-04"), txns, zone)
        assertEquals(3_000_00L, s.spendPaise)
        assertEquals(1_500L, s.rewardPaise)
    }

    @Test fun refundsLargerThanSpendNeverShowNegativeSpendOrRewards() {
        val txns = listOf(tx(500_00, "2026-09-20"), tx(2_000_00, "2026-09-21", type = TransactionType.CREDIT, flow = Flow.REFUND))
        val s = CardCycles.summary(card, d("2026-10-04"), txns, zone)
        assertEquals(0L, s.spendPaise)
        assertEquals(0L, s.rewardPaise)
    }

    private fun statementBill(state: BillState, last4: String = "1234") =
        Bill(id = 9, name = "HDFC Bank card ••$last4", amountPaise = 12_345_67, dueDay = 6, keyword = null,
            fixedDue = d("2026-10-06"), cardLast4 = last4) to state

    @Test fun theBanksStatementBillBeatsTheEstimate() {
        val s = CardCycles.summary(card, d("2026-10-04"), listOf(tx(1_000_00, "2026-09-10")), zone)
        val due = CardCycles.due(s, listOf(statementBill(BillState.Upcoming(d("2026-10-06"), 2))))
        assertEquals(CardDue(d("2026-10-06"), 2, 12_345_67, fromStatement = true), due)
        val overdue = CardCycles.due(s, listOf(statementBill(BillState.Overdue(d("2026-10-01"), 3))))
        assertEquals(true, overdue.overdue); assertEquals(-3L, overdue.daysLeft)
        assertEquals(true, CardCycles.due(s, listOf(statementBill(BillState.Paid(d("2026-10-06"), 1)))).paid)
    }

    @Test fun withoutAStatementBillTheEstimateIsLastStatementsSpend() {
        val s = CardCycles.summary(card, d("2026-10-04"), listOf(tx(1_000_00, "2026-09-10")), zone)
        // Another card's bill, or a finished one, does not count.
        val due = CardCycles.due(s, listOf(statementBill(BillState.Upcoming(d("2026-10-06"), 2), last4 = "9999"), statementBill(BillState.Done)))
        assertEquals(CardDue(d("2026-10-05"), 1, 1_000_00, fromStatement = false), due)
        // Once that bill's date passes, the open cycle has no bill yet, so no amount.
        val later = CardCycles.summary(card, d("2026-10-06"), listOf(tx(1_000_00, "2026-09-10")), zone)
        assertEquals(null, CardCycles.due(later, emptyList()).amountPaise)
    }

    @Test fun aPastedCardNumberKeepsOnlyItsLastFourDigits() {
        assertEquals(CardCycles.Last4Input("1234", trimmed = true), CardCycles.cleanLast4("4111 1111 1111 1234"))
        assertEquals(CardCycles.Last4Input("1234", trimmed = false), CardCycles.cleanLast4("12-34"))
        assertEquals(CardCycles.Last4Input("12", trimmed = false), CardCycles.cleanLast4("12"))
    }

    @Test fun anotherCardWithTheSameLastFourClashesButTheSameCardDoesNot() {
        val other = card.copy(id = 2, last4 = "5678", name = "Amazon Pay")
        assertEquals(other, CardCycles.clash(card.copy(id = 0, last4 = "5678"), listOf(card, other)))
        assertEquals(other, CardCycles.clash(card.copy(last4 = "5678"), listOf(card, other)))
        assertEquals(null, CardCycles.clash(card.copy(name = "Renamed"), listOf(card, other)))
    }

    @Test fun rupayCreditCardSpendOverUpiIsACardDebit() {
        for (body in listOf(
            "INR 250.00 spent on HDFC Bank RuPay Credit Card XX1234 at swiggy@ybl on 04-10-26 via UPI. Avl Limit: INR 45,000.00",
            "Rs.250.00 debited from your RuPay Credit Card ending 1234 for UPI txn to SWIGGY on 04/10/26. Ref 426212349999",
        )) {
            val t = (SmsParser().parse(SmsMessage("VM-HDFCBK-S", body, at("2026-10-04"))) as ParseResult.Success).transaction
            assertEquals(body, TransactionType.DEBIT, t.type)
            assertEquals(body, 25_000L, t.amountPaise)
            assertEquals(body, "1234", t.accountRef)
        }
    }

    @Test fun rupayCreditCardSpendCountsAsSpendNotATransfer() {
        for ((body, merchant) in listOf(
            "INR 250.00 spent on HDFC Bank RuPay Credit Card XX1234 at swiggy@ybl on 04-10-26 via UPI. Avl Limit: INR 45,000.00" to "swiggy@ybl",
            "Rs.250.00 debited from your RuPay Credit Card ending 1234 for UPI txn to SWIGGY on 04/10/26. Ref 426212349999" to "SWIGGY",
        )) {
            val cat = com.pft.financetracker.domain.categorize.Categorizer.categorize(merchant, TransactionType.DEBIT, "HDFC Bank")
            assertEquals(body, Flow.EXPENSE, com.pft.financetracker.domain.parser.FlowClassifier.classify(TransactionType.DEBIT, body, merchant, cat))
        }
    }
}
