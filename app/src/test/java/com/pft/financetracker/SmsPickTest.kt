package com.pft.financetracker

import com.pft.financetracker.data.sms.SmsReader
import com.pft.financetracker.domain.parser.Hashing
import org.junit.Assert.assertEquals
import org.junit.Test

/** A payment's page shows its own SMS, not a neighbouring one from the same bank a minute apart. */
class SmsPickTest {
    private val at = 1_790_000_000_000L
    private val failed = SmsReader.InboxCandidate("UPI txn of Rs 500 to SWIGGY has failed. No amount has been debited from your account.", at - 40_000)
    private val netflix = SmsReader.InboxCandidate("Your a/c XX1234 is debited for 899.00 on 09-10-26 to NETFLIX. Avl Bal Rs.10,000.00", at)

    @Test fun theMessageWithTheSameFingerprintIsChosen() {
        val hash = Hashing.smsHash("HDFCBK", netflix.body, netflix.sentAt)
        assertEquals(netflix.body, SmsReader.pick(listOf(failed, netflix), "HDFCBK", at - 60_000, hash))
        assertEquals(netflix.body, SmsReader.pick(listOf(failed, netflix), "HDFCBK", at, "$hash#2"))
    }

    @Test fun withoutAFingerprintTheClosestInTimeIsChosen() {
        assertEquals(netflix.body, SmsReader.pick(listOf(failed, netflix), "HDFCBK", at + 5_000, null))
        assertEquals(failed.body, SmsReader.pick(listOf(failed, netflix), "HDFCBK", at - 45_000, null))
    }
}
