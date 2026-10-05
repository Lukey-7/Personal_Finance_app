package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.refunds.RefundMatch
import com.pft.financetracker.domain.refunds.RefundMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RefundMatcherTest {
    private val day = 24L * 3600 * 1000
    private val t0 = 1_760_000_000_000L

    private fun debit(id: Long, paise: Long, merchant: String, at: Long, ref: String? = null, cat: Category = Category.SHOPPING, flow: Flow = Flow.EXPENSE) =
        Transaction(id = id, amountPaise = paise, type = TransactionType.DEBIT, merchant = merchant, category = cat, timestamp = at,
            bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = flow, refNumber = ref,
            counterpartyKind = CounterpartyKind.ORGANISATION)

    private fun credit(id: Long, paise: Long, merchant: String, at: Long, flow: Flow = Flow.REFUND, ref: String? = null,
                       kind: CounterpartyKind = CounterpartyKind.ORGANISATION, edited: Boolean = false, cat: Category = Category.OTHER) =
        Transaction(id = id, amountPaise = paise, type = TransactionType.CREDIT, merchant = merchant, category = cat, timestamp = at,
            bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = flow, refNumber = ref,
            counterpartyKind = kind, userEdited = edited)

    private fun match(vararg t: Transaction, known: Set<Long> = emptySet(), used: Map<Long, Long> = emptyMap()): List<RefundMatch> =
        RefundMatcher.match(t.toList(), alreadyLinkedRefunds = known, refundedSoFar = used)

    @Test
    fun aFullRefundOfAnOrderIsPairedWithIt() {
        val m = match(debit(1, 2_49_900, "Amazon", t0), credit(2, 2_49_900, "Amazon", t0 + 10 * day))
        assertEquals(1, m.size)
        assertEquals(2L, m[0].refundId); assertEquals(1L, m[0].debitId)
        assertEquals(RefundMatch.Kind.REFUND, m[0].kind)
        assertEquals(Category.SHOPPING, m[0].category)
    }

    @Test
    fun aPartialRefundIsPairedForItsOwnAmount() {
        val m = match(debit(1, 1_20_000, "Myntra", t0), credit(2, 40_000, "MYNTRA", t0 + 5 * day))
        assertEquals(1, m.size); assertEquals(40_000L, m[0].amountPaise)
    }

    @Test
    fun aFailedUpiPaymentReversedTheSameDayIsAReversal() {
        val m = match(debit(1, 50_000, "swiggy@ybl", t0, ref = "412345678901"), credit(2, 50_000, "Reversal", t0 + 3_600_000, ref = "412345678901"))
        assertEquals(RefundMatch.Kind.REVERSAL, m.single().kind)
    }

    @Test
    fun theSameAmountBackWithinThreeDaysFromTheSameMerchantIsAReversal() {
        val m = match(debit(1, 89_900, "Zomato", t0), credit(2, 89_900, "Zomato", t0 + 2 * day))
        assertEquals(RefundMatch.Kind.REVERSAL, m.single().kind)
    }

    @Test
    fun twoEqualDebitsAndOneRefundPairsTheLatestDebitOnly() {
        val m = match(debit(1, 30_000, "Uber", t0), debit(2, 30_000, "Uber", t0 + day), credit(3, 30_000, "Uber", t0 + 6 * day))
        assertEquals(2L, m.single().debitId)
    }

    @Test
    fun twoRefundsDoNotClaimMoreThanTheDebit() {
        val m = match(debit(1, 1_00_000, "Flipkart", t0), credit(2, 60_000, "Flipkart", t0 + 4 * day), credit(3, 60_000, "Flipkart", t0 + 5 * day))
        assertEquals(listOf(2L), m.map { it.refundId })
    }

    @Test
    fun anIncomeCreditThatIsReallyARefundIsReclassified() {
        // The bank wrote "credited by AMAZON" without the word refund, so the parser booked it as income.
        val m = match(debit(1, 2_49_900, "Amazon", t0, ref = "R77"), credit(2, 2_49_900, "Amazon", t0 + 8 * day, flow = Flow.INCOME, ref = "R77"))
        assertTrue(m.single().reclassify)
    }

    @Test
    fun incomeIsReclassifiedOnlyOnExactAmountFromTheSameMerchant() {
        // Salary from a company you once paid is not a refund.
        assertTrue(match(debit(1, 5_00_000, "Acme Corp", t0), credit(2, 85_00_000, "Acme Corp", t0 + 9 * day, flow = Flow.INCOME)).isEmpty())
        // Same merchant, different amount, booked as income: left alone.
        assertTrue(match(debit(1, 2_49_900, "Amazon", t0), credit(2, 1_00_000, "Amazon", t0 + 8 * day, flow = Flow.INCOME)).isEmpty())
    }

    @Test
    fun moneyFromAPersonIsNeverARefund() {
        // Paybacks from friends belong to split intelligence.
        val m = match(debit(1, 60_000, "Rahul", t0), credit(2, 60_000, "Rahul", t0 + day, flow = Flow.INCOME, kind = CounterpartyKind.PERSON))
        assertTrue(m.isEmpty())
    }

    @Test
    fun anIncomeRowAPersonEditedIsLeftAlone() {
        val m = match(debit(1, 2_49_900, "Amazon", t0, ref = "R77"), credit(2, 2_49_900, "Amazon", t0 + 8 * day, flow = Flow.INCOME, ref = "R77", edited = true))
        assertTrue(m.isEmpty())
    }

    @Test
    fun aRefundOlderThanSixtyDaysAfterTheDebitIsNotPaired() {
        assertTrue(match(debit(1, 40_000, "Nykaa", t0), credit(2, 40_000, "Nykaa", t0 + 70 * day)).isEmpty())
    }

    @Test
    fun aRefundBeforeTheDebitIsNotPaired() {
        assertTrue(match(debit(1, 40_000, "Nykaa", t0 + day), credit(2, 40_000, "Nykaa", t0)).isEmpty())
    }

    @Test
    fun alreadyLinkedRefundsAreSkippedAndEarlierRefundsReduceWhatIsLeft() {
        val d = debit(1, 1_00_000, "Flipkart", t0)
        assertTrue(match(d, credit(2, 60_000, "Flipkart", t0 + day), known = setOf(2L)).isEmpty())
        assertTrue(match(d, credit(3, 60_000, "Flipkart", t0 + 2 * day), used = mapOf(1L to 60_000L)).isEmpty())
    }

    @Test
    fun aRefundWithAnUnknownMerchantPairsByExactAmountAndAccountWithinThreeDays() {
        val m = match(debit(1, 75_000, "IRCTC", t0, cat = Category.TRANSPORT), credit(2, 75_000, "", t0 + day))
        assertEquals(1L, m.single().debitId)
        assertEquals(Category.TRANSPORT, m.single().category)
        assertFalse(m.single().reclassify)
    }

    @Test
    fun aRefundWithAnUnknownMerchantAndNoUniqueAmountMatchIsLeftAlone() {
        assertTrue(match(debit(1, 75_000, "IRCTC", t0), debit(2, 75_000, "Croma", t0 + 1000), credit(3, 75_000, "", t0 + day)).isEmpty())
    }
}
