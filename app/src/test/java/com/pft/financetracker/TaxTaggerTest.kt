package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.TaxSection
import com.pft.financetracker.domain.tax.TaxTagger
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class TaxTaggerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private var id = 1L
    private fun at(s: String) = LocalDate.parse(s).atTime(12, 0).atZone(zone).toInstant().toEpochMilli()
    private fun pay(m: String, paise: Long = 10_000_00, on: String = "2026-10-01", flow: Flow = Flow.EXPENSE, type: TransactionType = TransactionType.DEBIT) =
        Transaction(id = id++, amountPaise = paise, type = type, merchant = m, category = Category.OTHER, timestamp = at(on), bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = flow)

    @Test fun commonDeductionsAreRecognised() {
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("LIC OF INDIA PREMIUM")))
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("PPF deposit SBI", flow = Flow.INVESTMENT)))
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("Axis ELSS Tax Saver SIP", flow = Flow.INVESTMENT)))
        assertEquals(TaxSection.S80C, TaxTagger.suggest(pay("DPS school tuition fee")))
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
}
