package com.pft.financetracker

import com.pft.financetracker.domain.parser.Cashback
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.6: cashback credited with a payment is money back; a promised one is not. */
class CashbackTest {
    private fun of(body: String, paise: Long = 20_000, type: TransactionType = TransactionType.DEBIT) = Cashback.credited(body, type, paise)

    @Test fun creditedCashbackIsRead() {
        assertEquals(2_000L, of("Rs 200.00 debited from A/c XX1234 at AMAZON on 05-11-26. Cashback of Rs 20 credited to your account."))
        assertEquals(2_000L, of("You paid Rs.200 to Swiggy via UPI. Rs 20 cashback has been credited. -Paytm"))
        assertEquals(1_550L, of("INR 200 spent on card XX9012 at Zomato. Cashback of INR 15.50 is credited."))
        assertEquals(2_000L, of("Paid ₹200 to Blinkit. ₹20 cash-back credited!"))
    }

    @Test fun promisedOrOfferedCashbackIsNot() {
        assertNull(of("Rs 200 debited at AMAZON. Cashback of Rs 20 will be credited within 3 days."))
        assertNull(of("Rs 200 debited at AMAZON. You are eligible for cashback of Rs 20 credited on next bill."))
        assertNull(of("Get cashback up to Rs 100 credited on your next UPI payment! T&C apply"))
        assertNull(of("Rs 200 debited at AMAZON. Avl bal Rs 5,000."))
    }

    @Test fun onlyPartOfAPaymentOut() {
        // Money in is never a payment's cashback; a "cashback" as big as the payment is a misread.
        assertNull(of("Cashback of Rs 20 credited to your account.", type = TransactionType.CREDIT))
        assertNull(of("Rs 200 debited. Cashback of Rs 200 credited.", paise = 20_000))
        assertNull(of("Rs 200 debited. Cashback of Rs 0 credited."))
    }
}
