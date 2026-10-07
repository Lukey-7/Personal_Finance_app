package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.refunds.RefundMatch
import com.pft.financetracker.domain.refunds.RefundPair
import com.pft.financetracker.domain.tax.TaxSection
import com.pft.financetracker.ui.model.DetailLink
import com.pft.financetracker.ui.model.MoneyTone
import com.pft.financetracker.ui.model.TransactionDetail
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** What the read-first transaction screen shows for a row, worked out from the row and what it is linked to. */
class TransactionDetailTest {
    private val at = 1_790_000_000_000L
    private fun tx(id: Long, paise: Long, flow: Flow, type: TransactionType = TransactionType.DEBIT, source: Transaction.Source = Transaction.Source.SMS,
                   merchant: String = "Amazon", original: Long? = null, bank: String? = "ICICI Bank", acct: String? = "9012") =
        Transaction(id = id, amountPaise = paise, type = type, merchant = merchant, category = Category.SHOPPING, timestamp = at,
            bankName = bank, accountRef = acct, source = source, flow = flow, originalAmountPaise = original, smsHash = if (source == Transaction.Source.SMS) "h$id" else null)

    private fun detail(t: Transaction, pairs: List<RefundPair> = emptyList(), others: List<Transaction> = emptyList(),
                       splitId: Long? = null, billName: String? = null, tax: TaxSection? = null, taxByYou: Boolean = false) =
        TransactionDetail.of(t, pairs, others.associateBy { it.id }, splitId, billName, tax, taxByYou)

    @Test fun aPurchaseReadsAsMoneyOutCountedInSpend() {
        val d = detail(tx(1, 1_299_00, Flow.EXPENSE))
        assertEquals("-₹1,299", d.amount)
        assertEquals(MoneyTone.OUT, d.tone)
        assertEquals("Counted in your spend", d.countsAs)
        assertEquals("ICICI Bank ••9012", d.account)
        assertEquals("From an SMS", d.source)
        assertTrue(d.hasSms)
    }

    @Test fun moneyInAndMovesGetTheirOwnToneAndWords() {
        val salary = detail(tx(2, 45_000_00, Flow.INCOME, TransactionType.CREDIT))
        assertEquals("+₹45,000", salary.amount)
        assertEquals(MoneyTone.IN, salary.tone)
        assertEquals("Counted as income", salary.countsAs)
        val move = detail(tx(3, 10_000_00, Flow.TRANSFER, source = Transaction.Source.MANUAL))
        assertEquals(MoneyTone.MOVED, move.tone)
        assertEquals("Not spend, not income: money moved between your own accounts", move.countsAs)
        assertEquals("Added by you", move.source)
        assertTrue(!move.hasSms)
    }

    @Test fun aSharedPaymentSaysWhatTheBankReportedAndLinksTheSplit() {
        val d = detail(tx(4, 800_00, Flow.EXPENSE, merchant = "Dinner", original = 2_400_00), splitId = 9)
        assertEquals("The bank reported ₹2,400; only your share counts", d.shareNote)
        assertEquals(listOf(DetailLink(DetailLink.Kind.SPLIT, "Split", "Open the split", 9)), d.links)
    }

    @Test fun refundsLinkBothWays() {
        val buy = tx(5, 1_299_00, Flow.EXPENSE)
        val back = tx(6, 450_00, Flow.REFUND, TransactionType.CREDIT)
        val pair = RefundPair(linkId = 1, refundId = 6, debitId = 5, kind = RefundMatch.Kind.REFUND, amountPaise = 450_00)
        val onPurchase = detail(buy, listOf(pair), listOf(back))
        val link = onPurchase.links.single()
        assertEquals(DetailLink.Kind.REFUND, link.kind)
        assertEquals("Refund", link.title)
        assertTrue(link.label.startsWith("₹450 of this came back on "))
        assertEquals(6L, link.targetId)
        assertEquals(1L, link.pairId)
        val onRefund = detail(back, listOf(pair), listOf(buy))
        assertEquals(5L, onRefund.links.single().targetId)
        assertTrue(onRefund.links.single().label.startsWith("₹450 back for Amazon on "))
        val reversal = detail(buy, listOf(pair.copy(kind = RefundMatch.Kind.REVERSAL)), listOf(back))
        assertEquals("Reversed", reversal.links.single().title)
    }

    @Test fun aPairWhoseOtherSideIsGoneSaysSo() {
        val pair = RefundPair(linkId = 1, refundId = 6, debitId = 5, kind = RefundMatch.Kind.REFUND, amountPaise = 450_00)
        val d = detail(tx(5, 1_299_00, Flow.EXPENSE), listOf(pair))
        assertEquals("Paired with a payment that is no longer here", d.links.single().label)
        assertNull(d.links.single().targetId)
    }

    @Test fun billsAndTaxShowWhenTheyApply() {
        val d = detail(tx(7, 25_000_00, Flow.EXPENSE, merchant = "Landlord"), billName = "Rent", tax = TaxSection.entries.first(), taxByYou = true)
        assertEquals(DetailLink(DetailLink.Kind.BILL, "Bill", "Pays Rent", null), d.links.single())
        assertEquals("${TaxSection.entries.first().code} · ${TaxSection.entries.first().label} (set by you)", d.tax)
        assertEquals("Not a deduction", detail(tx(8, 100_00, Flow.EXPENSE)).tax)
        // Money in has no tax line.
        assertNull(detail(tx(9, 100_00, Flow.INCOME, TransactionType.CREDIT)).tax)
    }

    @Test fun paiseShowOnlyWhenPresent() {
        assertEquals("-₹2,400.50", detail(tx(10, 2_400_50, Flow.EXPENSE)).amount)
    }
}
