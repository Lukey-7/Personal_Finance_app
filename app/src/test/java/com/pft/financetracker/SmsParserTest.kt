package com.pft.financetracker

import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SmsParserTest {
    private val parser = SmsParser()
    private val now = System.currentTimeMillis()

    private fun parse(sender: String, body: String) = parser.parse(SmsMessage(sender, body, now))
    private fun success(sender: String, body: String) =
        (parse(sender, body) as? ParseResult.Success)?.transaction ?: error("Expected Success, got ${parse(sender, body)}")

    @Test
    fun hdfcUpiDebit() {
        val t = success("VM-HDFCBK", "Rs.250.00 debited from a/c **1234 on 12-08-24 to VPA swiggy.upi@axisbank (UPI Ref No 422312345678). Not you? Call 18002586161")
        assertEquals(250.0, t.amount, 0.001)
        assertEquals(TransactionType.DEBIT, t.type)
        assertEquals("1234", t.accountRef)
        assertEquals("HDFC Bank", t.bankName)
        assertTrue(t.merchant.lowercase().contains("swiggy"))
    }

    @Test
    fun sbiCredit() {
        val t = success("AD-SBIINB", "Dear Customer, your a/c no. XXXXX5678 is credited by Rs.45,000.00 on 01Aug24 by a/c linked to mobile 9XXXXXX123-ACME CORP SALARY (IMPS Ref no 123456). -SBI")
        assertEquals(45000.0, t.amount, 0.001)
        assertEquals(TransactionType.CREDIT, t.type)
        assertEquals("5678", t.accountRef)
        assertEquals("SBI", t.bankName)
    }

    @Test
    fun iciciCardSpend() {
        val t = success("VK-ICICIB", "INR 1,299.00 spent on ICICI Bank Card XX9012 on 15-Aug-24 at AMAZON. Avl Limit: INR 98,701.00. If not you, call 18002662.")
        assertEquals(1299.0, t.amount, 0.001)
        assertEquals(TransactionType.DEBIT, t.type)
        assertEquals("9012", t.accountRef)
        assertTrue(t.merchant.lowercase().contains("amazon"))
    }

    @Test
    fun balanceIsNotPickedAsAmount() {
        val t = success("AX-AXISBK", "Your a/c XX4321 is debited for INR 500.00 on 10-08-24 towards UBER. Avl bal INR 12,345.67")
        assertEquals(500.0, t.amount, 0.001)
    }

    @Test
    fun paytmUpiPaid() {
        val t = success("JD-PAYTMB", "Paid Rs.120 to Zomato via UPI. UPI Ref: 424012345678. Balance: Rs.3,400.")
        assertEquals(120.0, t.amount, 0.001)
        assertEquals(TransactionType.DEBIT, t.type)
        assertTrue(t.merchant.lowercase().contains("zomato"))
    }

    @Test
    fun atmWithdrawal() {
        val t = success("KOTAKB", "Rs 2000.00 withdrawn from Kotak Bank a/c X5555 at ATM on 09-08-24. Avl Bal Rs 8,000.00")
        assertEquals(2000.0, t.amount, 0.001)
        assertEquals(TransactionType.DEBIT, t.type)
    }

    @Test
    fun otpIsIgnored() {
        val r = parse("VM-HDFCBK", "123456 is your OTP for txn of Rs.5000 at Flipkart. Do not share.")
        assertTrue(r is ParseResult.Ignored)
    }

    @Test
    fun promoIsIgnored() {
        val r = parse("BZ-OFFERS", "Get up to Rs.500 cashback on your next order! Apply now. T&C apply.")
        assertTrue(r is ParseResult.Ignored)
    }

    @Test
    fun futureDebitIsIgnored() {
        val r = parse("VM-HDFCBK", "Rs.999 will be debited from your a/c on 20-08-24 for Netflix autopay.")
        assertTrue(r is ParseResult.Ignored)
    }

    @Test
    fun failedTxnIsIgnored() {
        val r = parse("VM-ICICIB", "Your transaction of INR 700 at Amazon was declined due to insufficient funds.")
        assertTrue(r is ParseResult.Ignored)
    }

    @Test
    fun ambiguousGoesToReview() {
        val r = parse("XX-UNKNWN", "Transaction alert: a/c 1234 amount 500")
        assertTrue("got $r", r is ParseResult.NeedsReview || r is ParseResult.Ignored)
    }

    @Test
    fun amountWithoutTypeGoesToReview() {
        val r = parse("XX-SOMEBK", "Txn of Rs.350 on card XX1111 at STORE. Ref 9988")
        assertNotNull(r)
        // Either parses as debit (via weak 'at') or asks for review; must never be silently dropped.
        assertTrue(r !is ParseResult.Ignored)
    }

    @Test
    fun dateInsideBodyIsUsed() {
        val t = success("VM-HDFCBK", "Rs.100.00 debited from a/c **1234 on 05-01-24 to VPA test@upi (UPI Ref No 1)")
        assertTrue(t.timestamp < now)
    }

    @Test
    fun creditCardRefundIsCredit() {
        val t = success("VM-ICICIB", "Refund of INR 450.00 credited to your ICICI Bank Credit Card XX3344 from MYNTRA on 11-08-24.")
        assertEquals(TransactionType.CREDIT, t.type)
        assertEquals(450.0, t.amount, 0.001)
    }
}
