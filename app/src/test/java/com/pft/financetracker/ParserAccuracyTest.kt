package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.DateExtractor
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.domain.parser.Hashing
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.RefExtractor
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** Phase 1 accuracy rules: hashing, references, flows, filters that must not eat real transactions. */
class ParserAccuracyTest {
    private val parser = SmsParser()
    private val now = System.currentTimeMillis()

    private fun success(sender: String, body: String, at: Long = now) =
        (parser.parse(SmsMessage(sender, body, at)) as? ParseResult.Success)?.transaction ?: error("Expected Success, got ${parser.parse(SmsMessage(sender, body, at))}")

    // ---- 1.1 same SMS, two paths ----
    @Test
    fun hashIgnoresSecondsSkewBetweenSentAndReceived() {
        val body = "Rs.250.00 debited from a/c **1234 to VPA swiggy@ybl"
        val a = Hashing.smsHash("VM-HDFCBK", body, now)
        val b = Hashing.smsHash("VM-HDFCBK", body, now + 45_000)
        assertEquals(a, b)
    }

    @Test
    fun hashIgnoresWhitespaceAndSenderCase() {
        assertEquals(Hashing.smsHash("vm-hdfcbk", "Rs.10  debited\nfrom a/c", now), Hashing.smsHash("VM-HDFCBK", "Rs.10 debited from a/c", now))
    }

    @Test
    fun hashDiffersForDifferentBodies() {
        assertNotEquals(Hashing.smsHash("VM-HDFCBK", "Rs.10 debited ref 1", now), Hashing.smsHash("VM-HDFCBK", "Rs.10 debited ref 2", now))
    }

    // ---- 1.2 reference numbers ----
    @Test
    fun extractsUpiRef() = assertEquals("422312345678", RefExtractor.extract("Rs.250 debited to VPA x@ybl (UPI Ref No 422312345678). Not you?"))

    @Test
    fun extractsImpsRefAndTxnId() {
        assertEquals("123456789", RefExtractor.extract("credited by Rs.45,000 (IMPS Ref no 123456789). -SBI"))
        assertEquals("T2409123456", RefExtractor.extract("Paid Rs.120 to Zomato. Txn ID: T2409123456"))
    }

    @Test
    fun noRefWhenOnlyWords() = assertNull(RefExtractor.extract("Rs.500 debited. Ref: pending"))

    @Test
    fun parsedTransactionCarriesRef() {
        val t = success("VM-HDFCBK", "Rs.250.00 debited from a/c **1234 on 12-08-24 to VPA swiggy.upi@axisbank (UPI Ref No 422312345678)")
        assertEquals("422312345678", t.refNumber)
        assertEquals(25_000L, t.amountPaise)
    }

    // ---- 1.3 / 1.4 flows ----
    @Test
    fun cardBillPaymentIsTransfer() {
        val body = "Payment of Rs.12,500.00 received towards your HDFC Bank Credit Card XX3344 on 05-09-26."
        assertEquals(Flow.TRANSFER, FlowClassifier.classify(TransactionType.CREDIT, body, "HDFC Bank", Category.INCOME))
        assertEquals(Flow.TRANSFER, FlowClassifier.classify(TransactionType.DEBIT, "Rs.12,500 debited from a/c XX1234 for credit card bill payment via CRED", "Cred", Category.OTHER))
    }

    @Test
    fun selfTransferIsTransfer() =
        assertEquals(Flow.TRANSFER, FlowClassifier.classify(TransactionType.DEBIT, "Rs.5000 debited from a/c XX1234 self transfer to a/c XX9876", "Self Transfer", Category.TRANSFER))

    @Test
    fun atmIsCash() =
        assertEquals(Flow.CASH, FlowClassifier.classify(TransactionType.DEBIT, "Rs 2000.00 withdrawn from Kotak Bank a/c X5555 at ATM", "ATM", Category.ATM))

    @Test
    fun sipIsInvestment() =
        assertEquals(Flow.INVESTMENT, FlowClassifier.classify(TransactionType.DEBIT, "Rs.5000 debited towards Zerodha Broking SIP", "Zerodha", Category.INVESTMENT))

    @Test
    fun refundIsRefundNotIncome() =
        assertEquals(Flow.REFUND, FlowClassifier.classify(TransactionType.CREDIT, "Refund of INR 450.00 credited to your Card XX3344 from MYNTRA", "Myntra", Category.SHOPPING))

    @Test
    fun cashbackIsRefund() =
        assertEquals(Flow.REFUND, FlowClassifier.classify(TransactionType.CREDIT, "Cashback of Rs.25 credited to your Paytm wallet", "Paytm", Category.INCOME))

    @Test
    fun salaryIsIncome() =
        assertEquals(Flow.INCOME, FlowClassifier.classify(TransactionType.CREDIT, "a/c credited by Rs.45,000 ACME CORP SALARY", "Acme Corp Salary", Category.INCOME))

    @Test
    fun ordinaryPurchaseIsExpense() =
        assertEquals(Flow.EXPENSE, FlowClassifier.classify(TransactionType.DEBIT, "INR 1,299.00 spent on Card XX9012 at AMAZON", "Amazon", Category.SHOPPING))

    /** "towards your <own card>" is the customer's own account, not a payee to show as a merchant. */
    @Test
    fun ownCardIsNotUsedAsMerchant() {
        val t = success("VM-HDFCBK", "Payment of Rs.12,500.00 received towards your HDFC Bank Credit Card XX3344 on 05-09-26. Thank you.")
        assertTrue("merchant was ${t.merchant}", !t.merchant.lowercase().contains("your"))
        assertTrue("merchant was ${t.merchant}", !t.merchant.lowercase().contains("credit card"))
    }

    // ---- 1.6 filters must not eat real transactions ----
    @Test
    fun debitWithClickHereIsNotDroppedAsPromo() {
        val r = parser.parse(SmsMessage("VM-HDFCBK", "Rs.899.00 debited from a/c **1234 to VPA netflix@icici on 12-09-26. Click here if not you: hdfc.in/x", now))
        assertTrue("got $r", r !is ParseResult.Ignored)
    }

    @Test
    fun debitMentioningKycIsNotDroppedAsLogin() {
        val r = parser.parse(SmsMessage("JD-PAYTMB", "Paid Rs.120 to Zomato via UPI. Complete your KYC to keep enjoying wallet benefits.", now))
        assertTrue("got $r", r !is ParseResult.Ignored)
    }

    @Test
    fun debitWithBalanceReminderIsNotDroppedAsFuture() {
        val r = parser.parse(SmsMessage("VM-HDFCBK", "Rs.500.00 debited from a/c **1234 on 12-09-26 to VPA x@ybl. Reminder: maintain minimum balance.", now))
        assertTrue("got $r", r is ParseResult.Success)
    }

    @Test
    fun genuineFutureDebitStillIgnored() =
        assertTrue(parser.parse(SmsMessage("VM-HDFCBK", "Rs.999 will be debited from your a/c on 20-09-26 for Netflix autopay.", now)) is ParseResult.Ignored)

    @Test
    fun pureOtpStillIgnored() =
        assertTrue(parser.parse(SmsMessage("VM-HDFCBK", "123456 is your OTP for txn of Rs.5000 at Flipkart. Do not share.", now)) is ParseResult.Ignored)

    @Test
    fun purePromoStillIgnored() =
        assertTrue(parser.parse(SmsMessage("BZ-OFFERS", "Get up to Rs.500 cashback on your next order! Apply now. T&C apply.", now)) is ParseResult.Ignored)

    // ---- date + time merge ----
    @Test
    fun bodyDateOnSameDayKeepsSmsTimeOfDay() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 12, 14, 37, 0); set(Calendar.MILLISECOND, 0) }
        val smsAt = c.timeInMillis
        val t = DateExtractor.extract("Rs.100 debited on 12-09-26 to x@ybl", smsAt)
        assertEquals(smsAt, t)
    }

    @Test
    fun bodyDateOnEarlierDayIsUsed() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 12, 14, 37, 0); set(Calendar.MILLISECOND, 0) }
        val t = DateExtractor.extract("Rs.100 debited on 10-09-26 to x@ybl", c.timeInMillis)
        val d = Calendar.getInstance().apply { timeInMillis = t }
        assertEquals(10, d.get(Calendar.DAY_OF_MONTH))
    }

    // ---- v1.5 parser review ----
    private fun cal(t: Long) = Calendar.getInstance().apply { timeInMillis = t }

    /** An earlier day with no time in the body is stored at the SMS's time of day, never at midnight. */
    @Test
    fun bodyDateOnEarlierDayKeepsTheSmsTimeOfDay() {
        val c = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 12, 14, 37, 0); set(Calendar.MILLISECOND, 0) }
        val d = cal(DateExtractor.extract("Rs.100 debited on 10-09-26 to x@ybl", c.timeInMillis))
        assertEquals(10, d.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, d.get(Calendar.HOUR_OF_DAY))
        assertEquals(37, d.get(Calendar.MINUTE))
    }

    /** "12-10-24, 13:45:12": the time in the body wins over a date-only reading. */
    @Test
    fun bodyDateWithTimeKeepsThatTime() {
        val smsAt = Calendar.getInstance().apply { set(2024, Calendar.OCTOBER, 12, 18, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val d = cal(DateExtractor.extract("INR 120.00 debited A/c no. XX1234 12-10-24, 13:45:12 UPI/P2M/428612345678/ZOMATO LTD Not you?", smsAt))
        assertEquals(12, d.get(Calendar.DAY_OF_MONTH))
        assertEquals(13, d.get(Calendar.HOUR_OF_DAY))
        assertEquals(45, d.get(Calendar.MINUTE))
        assertEquals(12, d.get(Calendar.SECOND))
    }

    @Test
    fun isoDateWithColonBeforeTheTime() {
        val smsAt = Calendar.getInstance().apply { set(2026, Calendar.SEPTEMBER, 5, 20, 0, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
        val d = cal(DateExtractor.extract("Rs 500 debited on 2026-09-05:14:22:10 to x@ybl", smsAt))
        assertEquals(5, d.get(Calendar.DAY_OF_MONTH))
        assertEquals(14, d.get(Calendar.HOUR_OF_DAY))
        assertEquals(22, d.get(Calendar.MINUTE))
        assertEquals(10, d.get(Calendar.SECOND))
    }

    @Test
    fun refsMatchAcrossPaddingButNotPlaceholders() {
        assertTrue(RefExtractor.same("000427712345678", "427712345678"))
        assertTrue(RefExtractor.same("UPI/427712345678", "427712345678"))
        assertTrue(!RefExtractor.same("427712345678", "427712345679"))
        assertNull(RefExtractor.normalize("0000000000000000"))
        assertNull(RefExtractor.normalize("NA"))
        assertNull(RefExtractor.normalize("-"))
    }

    @Test
    fun axisUpiRefIsTheRrn() =
        assertEquals("428612345678", RefExtractor.extract("INR 120.00 debited A/c no. XX1234 12-10-24, 13:45:12 UPI/P2M/428612345678/ZOMATO LTD Not you? SMS BLOCKUPI Cust ID to 919951860002 Axis Bank"))

    @Test
    fun fdFooterIsNotAnInvestment() {
        assertEquals(Flow.EXPENSE, FlowClassifier.classify(TransactionType.DEBIT, "Rs 500.00 debited from A/c XX1234 at SWIGGY. Earn 7% on FD. T&C apply", "Swiggy", Category.FOOD))
        assertEquals(Flow.EXPENSE, FlowClassifier.classify(TransactionType.DEBIT, "Rs 500.00 debited from A/c XX1234 at SWIGGY. Invest in Mutual Funds with us today.", "Swiggy", Category.FOOD))
        // The payment itself still counts, even with a footer after it.
        assertEquals(Flow.INVESTMENT, FlowClassifier.classify(TransactionType.DEBIT, "Rs.5000 debited towards Zerodha Broking SIP. Invest in more funds today. T&C apply", "Zerodha", Category.OTHER))
    }

    @Test
    fun noBreakSpacesDoNotHideTheCardBill() =
        assertEquals(Flow.TRANSFER, FlowClassifier.classify(TransactionType.CREDIT, "Payment of Rs\u00A015,000.00 has been credited to your SBI Card ending\u00A01234", "Credit (SBI)", Category.INCOME))

    @Test
    fun aCardSpendIsNotACardBillPayment() {
        assertEquals(Flow.EXPENSE, FlowClassifier.classify(TransactionType.DEBIT, "INR 1,299.00 spent on ICICI Bank Credit Card XX9012 at AMAZON", "Amazon", Category.SHOPPING))
        assertEquals(Flow.EXPENSE, FlowClassifier.classify(TransactionType.DEBIT, "Rs 500 paid via CRED Pay using your HDFC Bank Credit Card XX4455 at Swiggy", "Swiggy", Category.FOOD))
        assertEquals(Flow.EXPENSE, FlowClassifier.classify(TransactionType.DEBIT, "Rs 500 debited from your Debit Card XX1234 at Swiggy", "Swiggy", Category.FOOD))
    }

    /** Money leaving your own account is still a debit: "transferred from your a/c ... to RAHUL". */
    @Test
    fun transferredFromYourAccountIsADebit() {
        val t = success("VM-SBIINB", "Rs 2,000 transferred from your a/c XX1234 to RAHUL via IMPS. Ref 427712345695")
        assertEquals(TransactionType.DEBIT, t.type)
    }

    @Test
    fun otpSentToYourMobileIsStillIgnored() =
        assertTrue(parser.parse(SmsMessage("VM-HDFCBK", "OTP 123456 sent to your mobile for txn of Rs 500 at Amazon. Do not share.", now)) is ParseResult.Ignored)

    @Test
    fun paymentRequestIsStillIgnored() =
        assertTrue(parser.parse(SmsMessage("VM-PAYTMB", "Rahul has requested Rs 500 from you on Paytm. Pay now.", now)) is ParseResult.Ignored)

    @Test
    fun railsAreNotMerchants() {
        val neft = success("VM-HDFCBK", "Rs 5,000.00 credited to your A/c XX1234 for NEFT transaction via HDFC Bank. Ref 427712345693")
        assertTrue("merchant was ${neft.merchant}", !neft.merchant.lowercase().contains("transaction"))
        val kotak = success("VM-HDFCBK", "Rs 1,000 credited to your A/c XX1234 by transfer from Kotak. Ref 427712345694")
        assertTrue("merchant was ${kotak.merchant}", !kotak.merchant.lowercase().contains("transfer"))
        assertTrue("merchant was ${kotak.merchant}", kotak.merchant.lowercase().contains("kotak"))
    }

    /** "to SWIGGY has failed": the merchant stops before the verb. */
    @Test
    fun merchantStopsBeforeHasFailed() =
        assertEquals("Swiggy", com.pft.financetracker.domain.parser.MerchantExtractor.extract("Rs 500 paid to SWIGGY has been debited from your a/c", TransactionType.DEBIT))
}
