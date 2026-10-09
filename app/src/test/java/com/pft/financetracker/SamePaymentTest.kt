package com.pft.financetracker

import com.pft.financetracker.domain.importer.StatementRow
import com.pft.financetracker.domain.ledger.SamePayment
import com.pft.financetracker.domain.ledger.SamePayment.Why
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Same payment, through its interface only: stored rows and an incoming one in, a verdict out. The stored rows sit in a
 * list that answers the same three questions the database does.
 */
class SamePaymentTest {
    private val day = SamePayment.startOfDay(1_790_000_000_000L)
    private val H = 3_600_000L
    private val M = 60_000L
    private val noon = day + 13 * H

    private class InMemory(val rows: List<Transaction>) : SamePayment.Stored {
        override suspend fun withRef(ref: String) = rows.filter { it.refNumber == ref }
        override suspend fun similar(amountPaise: Long, from: Long, to: Long) = rows.filter {
            (it.amountPaise == amountPaise || it.originalAmountPaise == amountPaise) && it.timestamp in from..to &&
                it.source in setOf(Transaction.Source.SMS, Transaction.Source.STATEMENT)
        }
        override suspend fun sameAmount(amountPaise: Long, type: TransactionType, from: Long, to: Long) = rows.filter {
            (it.amountPaise == amountPaise || it.originalAmountPaise == amountPaise) && it.type == type && it.timestamp in from..to
        }
    }

    private var nextId = 1L
    private fun tx(
        paise: Long, merchant: String, bank: String?, at: Long, ref: String? = null,
        type: TransactionType = TransactionType.DEBIT, source: Transaction.Source = Transaction.Source.SMS,
    ) = Transaction(
        id = nextId++, amountPaise = paise, type = type, merchant = merchant, category = Category.FOOD, timestamp = at, bankName = bank,
        accountRef = null, source = source, flow = if (type == TransactionType.DEBIT) Flow.EXPENSE else Flow.INCOME, smsHash = "h$nextId", refNumber = ref,
    )

    /** One row of the table: what is stored, what arrives, and which stored row (by index) it repeats, and why. */
    private data class Case(val name: String, val stored: List<Transaction>, val incoming: Transaction, val match: Int?, val why: Why? = null, val claimed: Boolean = false)

    @Test fun incomingAlertsAgainstStoredRows() = runBlocking {
        val cases = listOf(
            Case("same ref, second sender", listOf(tx(25_000, "Swiggy", "HDFC Bank", noon, ref = "4223")),
                tx(25_000, "Payment (Paytm)", "Paytm", noon + 3 * M, ref = "4223"), 0, Why.REF),
            Case("same amount, other reporter, minutes apart", listOf(tx(25_000, "Swiggy", "HDFC Bank", noon)),
                tx(25_000, "Swiggy", "Paytm", noon + 2 * M), 0, Why.WINDOW),
            Case("same bank, two merchants, minutes apart", listOf(tx(5_000, "Chai Point", "HDFC Bank", noon)),
                tx(5_000, "Auto Rickshaw", "HDFC Bank", noon + 5 * M), null),
            Case("references disagree", listOf(tx(5_000, "Swiggy", "HDFC Bank", noon, ref = "111111")),
                tx(5_000, "Swiggy", "Paytm", noon + M, ref = "222222"), null),
            Case("other merchant half an hour later", listOf(tx(25_000, "Swiggy", "HDFC Bank", noon)),
                tx(25_000, "Blinkit", "Paytm", noon + 30 * M), null),
            Case("v1.0.0 midnight row on rescan", listOf(tx(89_900, "Netflix", "HDFC Bank", day)),
                tx(89_900, "Netflix", "HDFC Bank", day + 7 * H + 45 * M, ref = "433312345678"), 0, Why.LEGACY),
            Case("claimed midnight row is not taken again", listOf(tx(50_000, "Myntra", "HDFC Bank", day)),
                tx(50_000, "Myntra", "HDFC Bank", day + 15 * H, type = TransactionType.CREDIT), null, claimed = true),
            Case("midnight row, either direction", listOf(tx(1_250_000, "Payment (HDFC Bank)", "HDFC Bank", day)),
                tx(1_250_000, "Credit (HDFC Bank)", "HDFC Bank", day + 18 * H, type = TransactionType.CREDIT), 0, Why.LEGACY),
            Case("same merchant, same day, different refs", listOf(tx(5_000, "Chai Point", "HDFC Bank", day + 9 * H, ref = "111111")),
                tx(5_000, "Chai Point", "HDFC Bank", day + 18 * H, ref = "222222"), null),
            Case("same merchant, the day before", listOf(tx(5_000, "Chai Point", "HDFC Bank", day + 9 * H)),
                tx(5_000, "Chai Point", "HDFC Bank", day - 15 * H), null),
            Case("two coffees hours apart", listOf(tx(18_000, "Starbucks", "HDFC Bank", day + 9 * H)),
                tx(18_000, "Starbucks", "HDFC Bank", day + 14 * H), null),
            Case("split-shrunk row by its original amount", listOf(tx(40_000, "Barbeque", "HDFC Bank", noon).copy(originalAmountPaise = 120_000)),
                tx(120_000, "Barbeque Nation", "Google Pay", noon + M), 0, Why.WINDOW),
            Case("same ref, different amount", listOf(tx(25_000, "Swiggy", "HDFC Bank", noon, ref = "427712345678")),
                tx(99_000, "Zomato", "HDFC Bank", noon + M, ref = "427712345678"), null),
            Case("same ref, weeks apart", listOf(tx(25_000, "Swiggy", "HDFC Bank", noon - 20 * 86_400_000L, ref = "427712345678")),
                tx(25_000, "Swiggy", "HDFC Bank", noon, ref = "427712345678"), null),
            Case("same ref, other direction", listOf(tx(25_000, "Swiggy", "HDFC Bank", noon, ref = "427712345678")),
                tx(25_000, "Swiggy", "HDFC Bank", noon + M, ref = "427712345678", type = TransactionType.CREDIT), null),
            Case("statement row, same party, SMS knows the minute", listOf(tx(100_000, "RAHUL SHARMA", null, day + 12 * H, source = Transaction.Source.STATEMENT)),
                tx(100_000, "Rahul Sharma", "HDFC Bank", day + 19 * H), 0, Why.STATEMENT),
            Case("statement row, another person", listOf(tx(100_000, "PRIYA NAIR", null, day + 12 * H, source = Transaction.Source.STATEMENT)),
                tx(100_000, "Rahul Sharma", "HDFC Bank", day + 19 * H), null),
            Case("only statement row, generic alert", listOf(tx(100_000, "PRIYA NAIR", null, day + 12 * H, source = Transaction.Source.STATEMENT)),
                tx(100_000, "Payment (HDFC Bank)", "HDFC Bank", day + 19 * H), 0, Why.STATEMENT),
            Case("two statement rows, generic alert is ambiguous", listOf(
                tx(100_000, "PRIYA NAIR", null, day + 12 * H, source = Transaction.Source.STATEMENT),
                tx(100_000, "AMIT ROY", null, day + 12 * H + 1_000, source = Transaction.Source.STATEMENT)),
                tx(100_000, "Payment (HDFC Bank)", "HDFC Bank", day + 19 * H), null),
            Case("claimed statement row", listOf(tx(100_000, "RAHUL SHARMA", null, day + 12 * H, source = Transaction.Source.STATEMENT)),
                tx(100_000, "Rahul Sharma", "HDFC Bank", day + 19 * H), null, claimed = true),
            Case("manual rows are never matched", listOf(tx(25_000, "Swiggy", null, noon, source = Transaction.Source.MANUAL)),
                tx(25_000, "Swiggy", "HDFC Bank", noon + M), null),
        )
        for (c in cases) {
            val found = SamePayment.find(c.incoming, InMemory(c.stored)) { c.claimed }
            assertEquals(c.name, c.match?.let { c.stored[it].id }, found?.row?.id)
            assertEquals(c.name, c.why, found?.why)
        }
    }

    private fun row(paise: Long, who: String, date: Long, ref: String? = null, type: TransactionType = TransactionType.DEBIT) = StatementRow(
        date = date, time = null, amountPaise = paise, type = type, narration = "UPI/$who", counterparty = who, ref = ref, balancePaise = null,
        balanceOk = null, category = Category.OTHER, flow = Flow.EXPENSE, kind = CounterpartyKind.PERSON, order = 0,
    )

    @Test fun statementRowsAgainstStoredRows() = runBlocking {
        data class S(val name: String, val stored: List<Transaction>, val row: StatementRow, val match: Int?, val consumed: Set<Int> = emptySet())
        val cases = listOf(
            S("padded ref matches the SMS ref", listOf(tx(50_000, "Swiggy", "HDFC Bank", day + 3 * 86_400_000L, ref = "427712345678")),
                row(50_000, "SWIGGY", day, ref = "000427712345678"), 0),
            S("ref in the other direction", listOf(tx(50_000, "Swiggy", "HDFC Bank", day, ref = "427712345678", type = TransactionType.CREDIT)),
                row(50_000, "SWIGGY", day, ref = "427712345678"), null),
            S("same ref, months away", listOf(tx(50_000, "Swiggy", "HDFC Bank", day + 60 * 86_400_000L, ref = "427712345678")),
                row(50_000, "SWIGGY", day, ref = "427712345678"), null),
            S("same person, booked a day late", listOf(tx(100_000, "Rahul Sharma", "HDFC Bank", day - 86_400_000L + 23 * H)),
                row(100_000, "RAHUL SHARMA", day), 0),
            S("another person, same amount", listOf(tx(100_000, "Rahul Sharma", "HDFC Bank", day + 10 * H)),
                row(100_000, "PRIYA NAIR", day), null),
            S("the only stored row is a generic alert", listOf(tx(100_000, "Payment (HDFC Bank)", "HDFC Bank", day + 10 * H)),
                row(100_000, "PRIYA NAIR", day), 0),
            S("a generic alert beside another row is ambiguous", listOf(
                tx(100_000, "Payment (HDFC Bank)", "HDFC Bank", day + 10 * H), tx(100_000, "Amit Roy", "HDFC Bank", day + 11 * H)),
                row(100_000, "PRIYA NAIR", day), null),
            S("a split's own row is never the match", listOf(tx(100_000, "Rahul Sharma", null, day + 10 * H, source = Transaction.Source.SPLIT)),
                row(100_000, "RAHUL SHARMA", day), null),
            S("a row this import already used", listOf(tx(2_000, "Tea Stall", "HDFC Bank", day + 10 * H)),
                row(2_000, "TEA STALL", day), null, consumed = setOf(0)),
        )
        for (c in cases) {
            val found = SamePayment.findForStatement(c.row, InMemory(c.stored), c.consumed.map { c.stored[it].id }.toSet())
            assertEquals(c.name, c.match?.let { c.stored[it].id }, found?.id)
        }
    }

    @Test fun storedTwinsAreFoundOncePerPair() {
        val swiggy = tx(25_000, "Swiggy", "HDFC Bank", noon, ref = "4223").copy(accountRef = "1234")
        val paytm = tx(25_000, "Payment (Paytm)", "Paytm", noon + 3 * M, ref = "4223")
        val blinkit = tx(25_000, "Blinkit", "HDFC Bank", noon + 5 * H, ref = "9999")
        val twins = SamePayment.twinsIn(listOf(paytm, blinkit, swiggy))
        assertEquals(listOf(SamePayment.Twins(keep = swiggy, drop = paytm)), twins)

        // Kept apart: two coffees hours apart, generic alerts hours apart, different refs.
        assertEquals(0, SamePayment.twinsIn(listOf(tx(18_000, "Starbucks", "HDFC Bank", day + 9 * H), tx(18_000, "Starbucks", "HDFC Bank", day + 18 * H))).size)
        assertEquals(0, SamePayment.twinsIn(listOf(tx(50_000, "Payment (ICICI Bank)", "ICICI Bank", day + 9 * H), tx(50_000, "Payment (ICICI Bank)", "ICICI Bank", day + 15 * H))).size)
        assertEquals(0, SamePayment.twinsIn(listOf(tx(5_000, "Chai Point", "HDFC Bank", day + 9 * H, ref = "111111"), tx(5_000, "Chai Point", "HDFC Bank", day + 9 * H + M, ref = "222222"))).size)
        // A v1.0.0 midnight row and its same-day twin.
        assertEquals(1, SamePayment.twinsIn(listOf(tx(89_900, "Netflix", "HDFC Bank", day), tx(89_900, "Netflix", "HDFC Bank", day + 8 * H))).size)
        // Statement rows are left to the import, not the sweep.
        assertEquals(0, SamePayment.twinsIn(listOf(tx(25_000, "Swiggy", "HDFC Bank", noon), tx(25_000, "SWIGGY", null, noon, source = Transaction.Source.STATEMENT))).size)
    }

    @Test fun combiningTwinsKeepsTheLinkedCopyAndEveryDetail() = runBlocking {
        val keep = tx(25_000, "Swiggy", "HDFC Bank", noon)
        val drop = tx(25_000, "Payment (Paytm)", "Paytm", noon + M, ref = "4223").copy(accountRef = "1234", note = "lunch")
        val plain = SamePayment.combine(SamePayment.Twins(keep, drop))
        assertEquals(keep.id, plain.keep.id)
        assertEquals("Swiggy", plain.keep.merchant)
        assertEquals("4223", plain.keep.refNumber)
        assertEquals("1234", plain.keep.accountRef)
        assertEquals("lunch", plain.keep.note)
        assertEquals(drop.id, plain.drop.id)

        // A split points at the generic copy: it survives, and takes the real merchant name.
        val linked = SamePayment.combine(SamePayment.Twins(keep, drop)) { it == drop.id }
        assertEquals(drop.id, linked.keep.id)
        assertEquals("Swiggy", linked.keep.merchant)
        assertEquals(keep.id, linked.drop.id)
    }

    @Test fun mergingASecondAlert() {
        val first = tx(25_000, "Swiggy", "HDFC Bank", noon).copy(note = "team lunch", originalAmountPaise = 75_000)
        val weaker = tx(75_000, "Payment (Paytm)", "Paytm", noon + M, ref = "424012345678").copy(category = Category.OTHER)
        val merged = SamePayment.merge(first, weaker)
        assertEquals("Swiggy", merged.merchant)
        assertEquals(Category.FOOD, merged.category)
        assertEquals("424012345678", merged.refNumber)
        assertEquals(25_000L, merged.amountPaise)
        assertEquals("team lunch", merged.note)
        assertEquals(first.id, merged.id)
        assertEquals(first.smsHash, merged.smsHash)

        // A transfer stays a transfer when the second alert reads like plain spend.
        val transfer = first.copy(flow = Flow.TRANSFER, category = Category.TRANSFER)
        assertEquals(Flow.TRANSFER, SamePayment.merge(transfer, weaker).flow)
        // A settled split transfer keeps its flow whatever the alert says.
        assertEquals(Flow.SETTLEMENT, SamePayment.merge(first.copy(flow = Flow.SETTLEMENT), weaker.copy(flow = Flow.TRANSFER)).flow)
        // A statement row learns the minute and the SMS's flow.
        val stmt = first.copy(source = Transaction.Source.STATEMENT, timestamp = day + 12 * H, flow = Flow.EXPENSE)
        val sms = weaker.copy(flow = Flow.TRANSFER, timestamp = noon + 5 * M)
        assertEquals(noon + 5 * M, SamePayment.merge(stmt, sms).timestamp)
        assertEquals(Flow.TRANSFER, SamePayment.merge(stmt, sms).flow)
        // A v1.0.0 midnight row only had a flow guessed from its category: the SMS read now decides flow and category.
        val legacy = first.copy(timestamp = day, flow = Flow.EXPENSE, category = Category.OTHER)
        val cardBill = weaker.copy(flow = Flow.TRANSFER, category = Category.TRANSFER, timestamp = day + 18 * H)
        assertEquals(Flow.TRANSFER, SamePayment.merge(legacy, cardBill).flow)
        assertEquals(Category.TRANSFER, SamePayment.merge(legacy, cardBill).category)
        assertEquals(day, SamePayment.merge(legacy, cardBill).timestamp)
        // The stored direction was wrong: the category comes from the new read, not the old guess.
        val credit = weaker.copy(type = TransactionType.CREDIT, flow = Flow.INCOME, category = Category.INCOME)
        assertEquals(Category.INCOME, SamePayment.merge(first, credit).category)
        assertEquals(TransactionType.CREDIT, SamePayment.merge(first, credit).type)
        // A row the person corrected only gains identifiers.
        val edited = first.copy(userEdited = true, category = Category.SHOPPING, merchant = "Team")
        assertEquals(edited.copy(refNumber = "424012345678"), SamePayment.merge(edited, weaker))
    }

    @Test fun genericNamesAndRicherRows() {
        assertEquals(true, SamePayment.isGeneric("Payment (HDFC Bank)"))
        assertEquals(true, SamePayment.isGeneric("Credit (SBI)"))
        assertEquals(true, SamePayment.isGeneric("Ab"))
        assertEquals(false, SamePayment.isGeneric("Swiggy"))
        val generic = tx(25_000, "Payment (Paytm)", "Paytm", noon)
        val detailed = tx(25_000, "Swiggy", "HDFC Bank", noon).copy(accountRef = "1234")
        assertEquals(detailed, SamePayment.richer(generic, detailed))
        assertEquals(detailed, SamePayment.richer(detailed, generic))
    }
}
