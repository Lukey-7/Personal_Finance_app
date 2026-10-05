package com.pft.financetracker

import com.pft.financetracker.domain.parser.MissDetector
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MissDetectorTest {
    private fun reason(sender: String, body: String) = (SmsParser().parse(SmsMessage(sender, body, 1L)) as ParseResult.Ignored).reason

    @Test fun anUnusualAlertWithAnAmountAndAnAccountIsAPossibleMiss() {
        val body = "INR 2,000.00 moved from XX1234 to RAHUL 03-10-26"
        assertEquals("no_transaction_hint", reason("VM-ZETABK-S", body))
        assertEquals(2_00_000L, MissDetector.possibleMiss(body, reason("VM-ZETABK-S", body)))
    }

    @Test fun otpMessagesAreNeverMisses() {
        val body = "OTP 482913 for txn of INR 2,000.00 on a/c XX1234. Do not share."
        assertNull(MissDetector.possibleMiss(body, reason("VM-HDFCBK-S", body)))
    }

    @Test fun promotionsAreNeverMisses() {
        val body = "Get up to Rs 5000 cashback on your HDFC card a/c XX1234! T&C apply"
        assertNull(MissDetector.possibleMiss(body, reason("VM-HDFCBK-S", body)))
    }

    @Test fun anAmountWithoutAnAccountIsNotEnough() {
        val body = "Thanks for shopping. Bill INR 450 at ZETA MART"
        assertNull(MissDetector.possibleMiss(body, "no_transaction_hint"))
    }

    @Test fun aBalanceOnlyFigureIsNotAMiss() {
        val body = "Avl bal in a/c XX1234 is INR 5,000.00 as of today"
        assertNull(MissDetector.possibleMiss(body, "no_transaction_hint"))
    }
}
