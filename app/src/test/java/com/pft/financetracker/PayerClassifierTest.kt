package com.pft.financetracker

import com.pft.financetracker.domain.model.CounterpartyKind.ORGANISATION
import com.pft.financetracker.domain.model.CounterpartyKind.PERSON
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.model.TransactionType.DEBIT
import com.pft.financetracker.domain.split.PayerClassifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Only money from a person can be a split payback; everything else must be recognised as an organisation. */
class PayerClassifierTest {
    private fun kind(text: String, merchant: String, credit: Boolean = true) = PayerClassifier.classify(text, merchant, if (credit) CREDIT else DEBIT)

    @Test fun people() {
        assertEquals(PERSON, kind("Received Rs.500.00 in your Kotak Bank AC X1234 from rahul@okicici on 12-08-24.UPI Ref:422312345622.", "Rahul"))
        assertEquals(PERSON, kind("Rs 1,000 credited to a/c XX1234 from 9876543210@ybl UPI Ref 422312345611", "9876543210"))
        assertEquals(PERSON, kind("BY TRANSFER-UPI/CR/426212345611/RAHUL SH/HDFC/rahul@okhdfc/UPI", "Rahul SH"))
        assertEquals(PERSON, kind("UPI/P2A/426212345611/PRIYA NAIR/HDFC BANK/UPI", "Priya Nair"))
        assertEquals(PERSON, kind("Rahul paid you Rs 500 on PhonePe. UPI Ref 422312345677", "Rahul"))
        assertEquals(PERSON, kind("Rs 2,000 transferred to your a/c XX1234 from RAHUL via IMPS. Ref 422312345666", "Rahul"))
    }

    @Test fun organisations() {
        assertEquals(ORGANISATION, kind("your a/c no. XXXXX5678 is credited by Rs.45,000.00 on 01-09-26 by ACME CORP SALARY (IMPS Ref no 987654321). -SBI", "Acme Corp Salary"))
        assertEquals(ORGANISATION, kind("Refund of INR 450.00 credited to your ICICI Bank Credit Card XX9012 from AMAZON on 16-09-26.", "Amazon"))
        assertEquals(ORGANISATION, kind("Rs 20 cashback credited to your Paytm wallet", "Paytm"))
        assertEquals(ORGANISATION, kind("NEFT CR-HDFC0000001-INFOSYS LIMITED-SALARY SEP", "Infosys Limited"))
        assertEquals(ORGANISATION, kind("UPI/P2M/426212345611/SWIGGY/YESB", "Swiggy", credit = false))
        assertEquals(ORGANISATION, kind("Rs.250.00 debited to VPA q123456789@ybl (UPI Ref No 422312345678)", "Q123456789", credit = false))
        assertEquals(ORGANISATION, kind("Interest credited Rs 312.00 to a/c XX1234", "Interest"))
        assertEquals(ORGANISATION, kind("Rs 900 received from Sharma Enterprises via NEFT", "Sharma Enterprises"))
    }

    @Test fun namesAndParties() {
        assertTrue(PayerClassifier.looksLikeName("RAHUL SHARMA"))
        assertFalse(PayerClassifier.looksLikeName("Payment (HDFC Bank)"))
        assertFalse(PayerClassifier.looksLikeName("Q123456"))
        assertTrue(PayerClassifier.sameParty("Rahul", "RAHUL SHARMA"))
        assertFalse(PayerClassifier.sameParty("Rahul", "Priya"))
    }
}
