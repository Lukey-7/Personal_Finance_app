package com.pft.financetracker

import com.pft.financetracker.domain.cards.Card
import com.pft.financetracker.domain.cards.CardCycles
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

    @Test fun theCycleRunsFromTheDayAfterOneStatementToTheNext() {
        val c = CardCycles.current(card, d("2026-10-04"))
        assertEquals(d("2026-09-16"), c.start); assertEquals(d("2026-10-15"), c.end); assertEquals(d("2026-11-05"), c.due)
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
        assertEquals(32L, s.daysToDue)
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
