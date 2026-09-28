package com.pft.financetracker

import com.pft.financetracker.domain.model.CounterpartyKind.ORGANISATION
import com.pft.financetracker.domain.model.CounterpartyKind.PERSON
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.split.PayerClassifier
import org.junit.Assert.assertEquals
import org.junit.Test

/** v1.2.1: who is a person (a possible friend paying back) and who is a business. */
class PayerClassifierEdgeTest {
    private fun kind(merchant: String, text: String = "") = PayerClassifier.classify(text, merchant, CREDIT)

    @Test fun businessLikeNamesAreOrganisations() {
        for (m in listOf("Sharma Traders", "Om Sai Enterprises", "Krishna Medicals", "Hotel Saravana Bhavan", "Rahul Tech Solutions", "Balaji Motors", "Sri Lakshmi Jewellers", "Apollo Pharmacy", "Guru Kripa Electronics", "Annapurna Sweets", "Sai Travels"))
            assertEquals(m, ORGANISATION, kind(m))
    }

    @Test fun peopleWithInitialsHonorificsAndShortNames() {
        for (m in listOf("R K Sharma", "Mr. Anil Kumar", "Priya K Nair", "Om", "Md Arif", "Martin D'Souza".replace("'", ""), "Smt Lakshmi Devi"))
            assertEquals(m, PERSON, kind(m))
    }

    @Test fun merchantAndPersonalVpas() {
        assertEquals(ORGANISATION, kind("Paytmqr28100505", "UPI/CR/412345678901/paytmqr28100505@paytm"))
        assertEquals(ORGANISATION, kind("Q123456789", "UPI/CR/412345678901/q123456789@ybl"))
        assertEquals(ORGANISATION, kind("Bharatpe", "UPI/CR/412345678901/bharatpe.9000@fbl"))
        assertEquals(PERSON, kind("Rahul Sharma", "UPI/CR/412345678901/rahul.sharma@okhdfcbank"))
        assertEquals(PERSON, kind("9876543210", "UPI/CR/412345678901/9876543210@ybl"))
    }

    @Test fun markers() {
        assertEquals(ORGANISATION, kind("Ramesh Kumar", "UPI/P2M/412345678901/RAMESH KUMAR"))
        assertEquals(PERSON, kind("Ramesh Kumar", "UPI/P2A/412345678901/RAMESH KUMAR"))
        assertEquals(ORGANISATION, kind("Acme Corp Ltd", "NEFT CR-HDFC0000001-ACME CORP LTD-SALARY"))
        assertEquals(ORGANISATION, kind("Swiggy", "Refund of Rs 250 from Swiggy"))
        // "Cash back" in two words is cashback too, never a friend paying back (the categorizer no longer calls it ATM).
        assertEquals(ORGANISATION, kind("Cash Back"))
        assertEquals(ORGANISATION, kind("Rahul Sharma", "Cash back of Rs 50 credited to your account"))
    }
}
