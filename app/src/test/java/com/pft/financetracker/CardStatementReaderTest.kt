package com.pft.financetracker

import com.pft.financetracker.domain.bills.CardStatementReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class CardStatementReaderTest {
    @Test fun hdfcStyle() {
        val s = CardStatementReader.read("Your HDFC Bank Credit Card XX1234 statement is generated. Total Amount Due: Rs.12,345.67, Minimum Amount Due: Rs.620.00. Payment Due Date: 18-10-2026.")!!
        assertEquals("1234", s.cardLast4); assertEquals(12_34_567L, s.totalDuePaise); assertEquals(62_000L, s.minDuePaise)
        assertEquals(LocalDate.of(2026, 10, 18), s.dueDate)
    }

    @Test fun iciciStyleWithMonthNames() {
        val s = CardStatementReader.read("ICICI Bank Credit Card XX5678: Total due INR 4,500.00, min due INR 225.00, due by 05-Nov-26.")!!
        assertEquals("5678", s.cardLast4); assertEquals(4_50_000L, s.totalDuePaise); assertEquals(22_500L, s.minDuePaise)
        assertEquals(LocalDate.of(2026, 11, 5), s.dueDate)
    }

    @Test fun sbiStyleWithSlashes() {
        val s = CardStatementReader.read("Statement for SBI Card ending 9012: Total Amt Due Rs 8,000; Min Amt Due Rs 400; Pay by 22/10/2026")!!
        assertEquals("9012", s.cardLast4); assertEquals(8_00_000L, s.totalDuePaise); assertEquals(LocalDate.of(2026, 10, 22), s.dueDate)
    }

    @Test fun aDueDateWrittenOutInWords() {
        val s = CardStatementReader.read("Axis Bank Card no. XX4321 e-statement: total amount due Rs. 2,100 by 3 Dec 2026. Min due Rs. 105.")!!
        assertEquals(LocalDate.of(2026, 12, 3), s.dueDate); assertEquals(2_10_000L, s.totalDuePaise)
    }

    @Test fun aPaymentReceiptIsNotAStatement() =
        assertNull(CardStatementReader.read("Payment of Rs 5,000.00 received towards your Credit Card XX1234. Thank you."))

    @Test fun withoutADueDateThereIsNoBill() =
        assertNull(CardStatementReader.read("Your Credit Card XX1234 statement: Total Amount Due Rs 5,000."))
}
