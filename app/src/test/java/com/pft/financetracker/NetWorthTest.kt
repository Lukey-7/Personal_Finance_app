package com.pft.financetracker

import com.pft.financetracker.domain.networth.Asset
import com.pft.financetracker.domain.networth.AssetKind
import com.pft.financetracker.domain.networth.BalanceExtractor
import com.pft.financetracker.domain.networth.CasParser
import com.pft.financetracker.domain.networth.Holding
import com.pft.financetracker.domain.networth.NetWorth
import com.pft.financetracker.domain.networth.AccountBalance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

class NetWorthTest {
    // ---- Balance from SMS ----

    @Test fun availableBalanceIsRead() {
        assertEquals(5_000_00L, BalanceExtractor.extract("Rs.450.00 debited from a/c **1234 on 03-10-26 to VPA x@ybl. Avl Bal INR 5,000.00"))
        assertEquals(12_345_67L, BalanceExtractor.extract("INR 100 credited to A/c XX1234. Available balance: Rs. 12,345.67"))
        assertEquals(1_000_00L, BalanceExtractor.extract("Txn of Rs 50 on a/c XX9876. Bal: Rs 1,000"))
        assertEquals(98_765_43L, BalanceExtractor.extract("Your a/c XX1234 balance is INR 98,765.43 as on 03-Oct"))
    }

    @Test fun aCardLimitIsNotABalance() =
        assertNull(BalanceExtractor.extract("INR 250.00 spent on Credit Card XX1234 at Swiggy. Avl Limit: INR 45,000.00"))

    @Test fun noBalanceMeansNull() = assertNull(BalanceExtractor.extract("Rs.450.00 debited from a/c **1234 to VPA x@ybl"))

    // ---- CAS statements (CAMS / KFintech) ----

    private val cams = """
        Consolidated Account Statement
        01-Apr-2026 To 30-Sep-2026
        Folio No: 12345678 / 90   PAN: ABCDE1234F
        Axis Long Term Equity Fund - Direct Growth (Advisor: DIRECT) Registrar : KFINTECH
        Opening Unit Balance: 1,000.000
        15-Apr-2026 Purchase - SIP 5,000.00 60.123 83.1600 1,060.123
        Closing Unit Balance: 1,234.567 NAV on 30-Sep-2026: INR 85.4321 Cost Value: INR 90,000.00 Market Value on 30-Sep-2026: INR 1,05,467.89
        Folio No: 99887766   PAN: ABCDE1234F
        Parag Parikh Flexi Cap Fund - Direct Plan - Growth Registrar : CAMS
        Closing Unit Balance: 500.000 NAV on 30-Sep-2026: INR 80.0000 Cost Value: INR 30,000.00 Market Value on 30-Sep-2026: INR 40,000.00
        HDFC Liquid Fund - Direct Growth Registrar : CAMS
        Closing Unit Balance: 0.000 NAV on 30-Sep-2026: INR 4,800.0000 Market Value on 30-Sep-2026: INR 0.00
    """.trimIndent()

    @Test fun casHoldingsAreReadWithTheirMarketValue() {
        val h = CasParser.parse(cams)
        assertEquals(2, h.size)    // the zero-unit fund is left out
        assertEquals("Axis Long Term Equity Fund - Direct Growth", h[0].scheme)
        assertEquals("12345678 / 90", h[0].folio)
        assertEquals(1_05_467_89L, h[0].valuePaise)
        assertEquals(LocalDate.of(2026, 9, 30), h[0].asOf)
        assertEquals("Parag Parikh Flexi Cap Fund - Direct Plan - Growth", h[1].scheme)
        assertEquals(40_000_00L, h[1].valuePaise)
    }

    @Test fun withoutAMarketValueUnitsTimesNavIsUsed() {
        val h = CasParser.parse("Folio No: 1\nSBI Bluechip Fund - Regular Growth\nClosing Unit Balance: 100.000 NAV on 30-Sep-2026: INR 75.5000")
        assertEquals(7_550_00L, h.single().valuePaise)
    }

    @Test fun aFileThatIsNotACasHasNoHoldings() = assertEquals(emptyList<Holding>(), CasParser.parse("Bank statement\n01/10/26 UPI-SWIGGY 450.00"))

    // ---- Net worth ----

    @Test fun netWorthIsWhatYouOwnMinusWhatYouOwe() {
        val nw = NetWorth.compute(
            assets = listOf(Asset(name = "FD", kind = AssetKind.FD, valuePaise = 2_00_000_00), Asset(name = "Credit card dues", kind = AssetKind.OTHER, valuePaise = 15_000_00, liability = true)),
            balances = listOf(AccountBalance("1234", "HDFC Bank", 50_000_00, 1L), AccountBalance("5678", "SBI", 10_000_00, 2L)),
            holdings = listOf(Holding("1", "Axis ELSS", 1_00_000_00, LocalDate.of(2026, 9, 30))),
            loansOutstandingPaise = 4_00_000_00,
        )
        assertEquals(3_60_000_00L, nw.ownPaise)       // 2L FD + 60k banks + 1L funds
        assertEquals(4_15_000_00L, nw.owePaise)       // 4L loan + 15k card
        assertEquals(-55_000_00L, nw.totalPaise)
        assertEquals(60_000_00L, nw.byKind[AssetKind.BANK])
        assertEquals(1_00_000_00L, nw.byKind[AssetKind.MUTUAL_FUND])
    }

    @Test fun aBankAccountTypedInByHandReplacesItsSmsBalance() {
        val nw = NetWorth.compute(
            assets = listOf(Asset(name = "HDFC savings", kind = AssetKind.BANK, valuePaise = 70_000_00, accountRef = "1234")),
            balances = listOf(AccountBalance("1234", "HDFC Bank", 50_000_00, 1L)), holdings = emptyList(), loansOutstandingPaise = 0,
        )
        assertEquals(70_000_00L, nw.totalPaise)
    }
}
