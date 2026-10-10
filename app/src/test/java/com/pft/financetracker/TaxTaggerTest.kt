package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.PossibleKind
import com.pft.financetracker.domain.tax.TaxSection
import com.pft.financetracker.domain.tax.TaxTagger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaxTaggerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var id = 1L
    private fun at(s: String) = LocalDate.parse(s).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun pay(
        m: String, paise: Long = 10_000_00, on: String = "2026-10-01", flow: Flow = Flow.EXPENSE, type: TransactionType = TransactionType.DEBIT,
        note: String? = null, category: Category = Category.OTHER, kind: CounterpartyKind? = null,
    ) = Transaction(id = id++, amountPaise = paise, type = type, merchant = m, category = category, timestamp = at(on), bankName = null, accountRef = null,
        source = Transaction.Source.SMS, flow = flow, note = note, counterpartyKind = kind)

    @Test fun commonDeductionsAreRecognised() {
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("LIC OF INDIA PREMIUM")))
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("PPF deposit SBI", flow = Flow.INVESTMENT)))
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("Axis ELSS Tax Saver SIP", flow = Flow.INVESTMENT)))
        assertEquals(TaxSection.S80D, TaxTagger.suggest(pay("Star Health Insurance")))
        assertEquals(TaxSection.S80CCD1B, TaxTagger.suggest(pay("NPS Trust contribution", flow = Flow.INVESTMENT)))
        assertEquals(TaxSection.S80G, TaxTagger.suggest(pay("PM CARES donation")))
        assertEquals(TaxSection.S80E, TaxTagger.suggest(pay("Education loan interest")))
        assertEquals(TaxSection.HRA, TaxTagger.suggest(pay("NoBroker house rent")))
    }

    @Test fun everydaySpendingAndMoneyInAreNotDeductions() {
        assertNull(TaxTagger.suggest(pay("Swiggy")))
        assertNull(TaxTagger.suggest(pay("LIC maturity", type = TransactionType.CREDIT, flow = Flow.INCOME)))
        assertNull(TaxTagger.suggest(pay("Rent received", type = TransactionType.CREDIT, flow = Flow.INCOME)))
    }

    @Test fun theFinancialYearRunsAprilToMarch() {
        val fy = FinancialYear.of(LocalDate.of(2027, 3, 31))
        assertEquals("FY 2026-27", fy.label)
        assertEquals(FinancialYear(2027), FinancialYear.of(LocalDate.of(2027, 4, 1)))
        assertEquals(FinancialYear(2026), FinancialYear.of(LocalDate.of(2026, 4, 1)))
    }

    @Test fun theSummaryTotalsEachSectionAndCapsWhatCanBeClaimed() {
        val lic = pay("LIC premium", 1_00_000_00); val ppf = pay("PPF", 1_00_000_00, flow = Flow.INVESTMENT)
        val star = pay("Star Health", 20_000_00); val swiggy = pay("Swiggy", 500_00)
        val lastYear = pay("LIC premium", 50_000_00, on = "2026-03-20")
        val s = TaxTagger.summary(listOf(lic, ppf, star, swiggy, lastYear), FinancialYear(2026), manual = emptyMap(), zone = zone)
        val c = s.single { it.section == TaxSection.S80C }
        assertEquals(2_00_000_00L, c.totalPaise); assertEquals(1_50_000_00L, c.claimablePaise); assertEquals(listOf(lic.id, ppf.id), c.transactionIds)
        assertEquals(20_000_00L, s.single { it.section == TaxSection.S80D }.totalPaise)
    }

    @Test fun aPersonsTagWinsOverTheRules() {
        val swiggy = pay("Swiggy"); val lic = pay("LIC premium")
        val s = TaxTagger.summary(listOf(swiggy, lic), FinancialYear(2026), manual = mapOf(swiggy.id to TaxSection.S80G, lic.id to null), zone = zone)
        assertEquals(listOf(swiggy.id), s.single { it.section == TaxSection.S80G }.transactionIds)
        assertEquals(null, s.firstOrNull { it.section == TaxSection.S80C })
    }

    private fun possible(t: Transaction): PossibleKind? = TaxTagger.guess(t)?.possible

    @Test fun lifeInsurersAreFoundUnderTheirCleanedNames() {
        listOf(
            "HDFC Life", "Licindia", "SBI Life Insurance", "ICICI Pru Life", "Iciciprulife", "Max Life Insurance", "Axis Max Life",
            "Tata AIA Life", "Bajaj Allianz Life", "Kotak Life", "PNB MetLife", "Aditya Birla Sun Life Insurance",
        ).forEach { assertEquals(it, TaxSection.S80C, TaxTagger.suggest(pay(it))) }
    }

    @Test fun healthInsurersCountButMotorAndTravelCoverDoesNot() {
        listOf("Star Health", "Niva Bupa", "ManipalCigna", "Care Health Insurance", "HDFC Ergo Health")
            .forEach { assertEquals(it, TaxSection.S80D, TaxTagger.suggest(pay(it))) }
        assertNull(TaxTagger.guess(pay("ICICI Lombard Motor")))
        assertNull(TaxTagger.guess(pay("HDFC Ergo travel insurance")))
        assertNull(TaxTagger.guess(pay("Tata AIG car insurance")))
        // A general insurer with nothing else to go on may be health cover: it waits for a yes.
        assertNull(TaxTagger.suggest(pay("ICICI Lombard")))
        assertEquals(PossibleKind.INSURANCE, possible(pay("ICICI Lombard")))
        assertEquals(PossibleKind.INSURANCE, possible(pay("Acko")))
    }

    @Test fun npsTierOneCountsAndTierTwoDoesNot() {
        assertEquals(TaxSection.S80CCD1B, TaxTagger.suggest(pay("eNPS contribution", flow = Flow.INVESTMENT)))
        assertNull(TaxTagger.guess(pay("NPS Tier II", flow = Flow.INVESTMENT)))
        assertEquals(PossibleKind.NPS, possible(pay("Protean eGov", flow = Flow.INVESTMENT)))
        assertNull(TaxTagger.guess(pay("Protean PAN card", 107_00)))
    }

    @Test fun fundsAreOfferedAsPossibleElssNotCounted() {
        listOf("Indian Clearing Corp", "Groww", "Zerodha Broking", "BSE StAR MF", "ICICI Prudential MF").forEach {
            val t = pay(it, flow = Flow.INVESTMENT)
            assertNull(it, TaxTagger.suggest(t))
            assertEquals(it, PossibleKind.ELSS, possible(t))
        }
        assertNull(TaxTagger.guess(pay("Groww gold", flow = Flow.INVESTMENT)))
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("Sukanya Samriddhi", flow = Flow.TRANSFER)))
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("PPF A/c", flow = Flow.TRANSFER)))
    }

    @Test fun homeLoanEmisWaitForAYes() {
        assertEquals(PossibleKind.HOME_LOAN_EMI, possible(pay("Loan Account", 35_000_00, note = "EMI debited for loan a/c")))
        assertEquals(PossibleKind.HOME_LOAN_EMI, possible(pay("LIC Housing Finance", 25_000_00)))
        assertNull(TaxTagger.guess(pay("Car loan EMI", 15_000_00)))
        assertNull(TaxTagger.guess(pay("Bajaj Finserv EMI", 12_000_00)))
        assertEquals(TaxSection.S24B, TaxTagger.suggest(pay("Home loan interest")))
    }

    @Test fun coachingAndVehicleRentAreNotDeductions() {
        assertEquals(PossibleKind.TUITION, possible(pay("DPS school tuition fee")))
        assertNull(TaxTagger.guess(pay("Coaching classes")))
        assertNull(TaxTagger.guess(pay("Tuition classes")))
        assertNull(TaxTagger.guess(pay("Car rent")))
        assertNull(TaxTagger.guess(pay("Bike rent")))
        assertEquals(TaxSection.HRA, TaxTagger.suggest(pay("House rent")))
        // A transfer marked "rent" may be to your own account: it needs a yes.
        val own = pay("Own account", flow = Flow.TRANSFER, note = "rent")
        assertNull(TaxTagger.suggest(own))
        assertEquals(PossibleKind.RENT, possible(own))
        assertEquals(PossibleKind.DONATION, possible(pay("Ketto")))
    }

    @Test fun theSameAmountToTheSamePersonEachMonthLooksLikeRent() {
        val rent = listOf("2026-05-03", "2026-06-02", "2026-07-04", "2026-08-03").map { pay("Ramesh Kumar", 18_000_00, on = it, kind = CounterpartyKind.PERSON) }
        val friend = pay("Priya", 6_000_00, kind = CounterpartyKind.PERSON)
        val varied = listOf("2026-05-10" to 5_000_00L, "2026-06-10" to 9_000_00L, "2026-07-10" to 20_000_00L)
            .map { (d, p) -> pay("Arjun Rao", p, on = d, kind = CounterpartyKind.PERSON) }
        val r = TaxTagger.report(rent + friend + varied, FinancialYear(2026), manual = emptyMap(), zone = zone)
        val g = r.possible.single()
        assertEquals(PossibleKind.RENT, g.kind)
        assertEquals(rent.map { it.id }, g.transactionIds)
        assertEquals(72_000_00L, g.totalPaise)
        assertTrue(r.totals.isEmpty())
    }

    @Test fun rentAndMostDonationsStayOutOfTheHeadline() {
        val lic = pay("LIC premium", 50_000_00); val rent = pay("House rent", 2_00_000_00)
        val give = pay("GiveIndia donation", 10_000_00); val pm = pay("PM CARES", 5_000_00)
        val r = TaxTagger.report(listOf(lic, rent, give, pm), FinancialYear(2026), manual = emptyMap(), zone = zone)
        val hra = r.totals.single { it.section == TaxSection.HRA }
        assertEquals(2_00_000_00L, hra.totalPaise); assertEquals(0L, hra.claimablePaise)
        val g = r.totals.single { it.section == TaxSection.S80G }
        assertEquals(15_000_00L, g.totalPaise); assertEquals(5_000_00L, g.claimablePaise)
        assertEquals(55_000_00L, r.headlinePaise)
    }

    @Test fun aRefundedPremiumComesOffItsSection() {
        val premium = pay("Star Health", 30_000_00, on = "2026-06-01")
        val refund = pay("Star Health", 10_000_00, on = "2026-06-20", flow = Flow.REFUND, type = TransactionType.CREDIT)
        val s = TaxTagger.summary(listOf(premium, refund), FinancialYear(2026), manual = emptyMap(), zone = zone).single()
        assertEquals(TaxSection.S80D, s.section)
        assertEquals(20_000_00L, s.totalPaise); assertEquals(10_000_00L, s.refundedPaise); assertEquals(20_000_00L, s.claimablePaise)
        assertEquals(listOf(premium.id, refund.id), s.transactionIds)
        // A refund on its own, with nothing paid that year, is not a section.
        assertTrue(TaxTagger.summary(listOf(refund), FinancialYear(2026), manual = emptyMap(), zone = zone).isEmpty())
    }

    @Test fun aYesOrNoMovesAPossiblePaymentOutOfTheList() {
        val sip = pay("Groww", 5_000_00, flow = Flow.INVESTMENT)
        val fy = FinancialYear(2026)
        assertEquals(listOf(sip.id), TaxTagger.report(listOf(sip), fy, emptyMap(), zone).possible.single().transactionIds)
        val yes = TaxTagger.report(listOf(sip), fy, mapOf(sip.id to TaxSection.S80C), zone)
        assertTrue(yes.possible.isEmpty()); assertEquals(5_000_00L, yes.headlinePaise)
        val no = TaxTagger.report(listOf(sip), fy, mapOf(sip.id to null), zone)
        assertTrue(no.possible.isEmpty()); assertTrue(no.totals.isEmpty())
    }
}
