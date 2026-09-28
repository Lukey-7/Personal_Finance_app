package com.pft.financetracker

import com.pft.financetracker.domain.importer.AppHistoryParser
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/** v1.2.1 edge cases for payment-app screenshots: dates, misread ₹ signs, status words, overlapping screenshots. */
class EdgeAppHistoryTest {
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    private fun l(text: String, x: Float, y: Float, w: Float = 0f, page: Int = 0) = AppHistoryParser.Line(text, x, y + page * 100_000f, 30f, w, page)

    // ---- 4. Dates ---------------------------------------------------------------------------------------------

    @Test fun octoberDatesParse() {
        val now = at(2026, 10, 20)
        mapOf(
            "12 Oct" to at(2026, 10, 12, 0), "12 Oct, 8:30 PM" to at(2026, 10, 12, 20, 30), "Oct 12" to at(2026, 10, 12, 0),
            "12 Oct at 8:30 pm" to at(2026, 10, 12, 20, 30), "12 Sept, 9:05 am" to at(2026, 9, 12, 9, 5), "5 Aug at 10:00 am" to at(2026, 8, 5, 10, 0),
        ).forEach { (raw, want) -> assertEquals(raw, want, AppHistoryParser.parseWhen(raw, now)) }
    }

    @Test fun relativeDatesAroundMidnightAndNewYear() {
        val justAfterNewYear = at(2027, 1, 1, 0, 5)
        assertEquals(at(2026, 12, 31, 23, 59), AppHistoryParser.parseWhen("Yesterday, 11:59 PM", justAfterNewYear))
        assertEquals(at(2027, 1, 1, 0, 1), AppHistoryParser.parseWhen("Today, 12:01 AM", justAfterNewYear))
        assertEquals(at(2026, 12, 31, 0), AppHistoryParser.parseWhen("31 Dec", justAfterNewYear))
        assertEquals(at(2026, 9, 12, 0), AppHistoryParser.parseWhen("12 Sep", at(2027, 1, 2)))
    }

    @Test fun aMonthHeaderIsNotADate() {
        // "September 2026" used to read as 20 September, and every undated row below it took that date.
        val rows = AppHistoryParser.parse(listOf(l("September 2026", 60f, 150f), l("Paid to Swiggy", 180f, 300f), l("₹250", 900f, 300f)), at(2026, 9, 28))
        assertTrue(rows.toString(), rows.isEmpty())
    }

    @Test fun anOctoberRowGetsItsOwnDateNotTheSectionHeadersDate() {
        val rows = AppHistoryParser.parse(
            listOf(l("Yesterday", 60f, 150f), l("Paid to Swiggy", 180f, 300f), l("₹250", 900f, 300f), l("12 Oct, 8:30 PM", 180f, 345f)), at(2026, 10, 20),
        )
        assertEquals(at(2026, 10, 12, 20, 30), rows.single().time)
    }

    // ---- 5 / 6. The ₹ sign misread ----------------------------------------------------------------------------

    private fun one(amountText: String, w: Float = 0f): Long {
        val rows = AppHistoryParser.parse(listOf(l("Paid to Rahul Sharma", 180f, 300f), l(amountText, 850f, 300f, w), l("12 Sep, 8:30 PM", 180f, 345f)), at(2026, 9, 28))
        return rows.single().amountPaise
    }

    @Test fun invalidIndianGroupingMeansTheLeadingSevenWasTheRupeeSign() {
        assertEquals(12_000_00L, one("712,000"))
        assertEquals(7_300_00L, one("7,300"))
        assertEquals(7_12_000_00L, one("₹7,12,000"))
        assertEquals(7_300_00L, one("₹7,300"))
    }

    @Test fun decimalsDoNotCountAsDigitsOfTheRupeeHeuristic() {
        // No box widths: "750.00" is three digits and paise, not a misread "₹50.00".
        assertEquals(750_00L, one("- 750.00"))
        assertEquals(300_00L, one("7300"))
    }

    @Test fun anAppWithNoRupeeGlyphAtAll() {
        val g = 20f
        fun w(units: Double) = (units * g).toFloat()
        val now = at(2026, 9, 28)
        val lines = listOf(
            l("Paid to Swiggy", 180f, 300f), l("- 300.00", 850f, 300f, w(1.75 + 5.35)), l("12 Sep, 8:30 PM", 180f, 345f),
            l("Paid to Zomato", 180f, 500f), l("- 750.00", 850f, 500f, w(1.75 + 5.35)), l("12 Sep, 9:30 PM", 180f, 545f),
            l("Paid to Uber", 180f, 700f), l("- 1,250.00", 820f, 700f, w(1.75 + 6.7)), l("12 Sep, 10:30 PM", 180f, 745f),
        )
        assertEquals(listOf(300_00L, 750_00L, 1_250_00L), AppHistoryParser.parse(lines, now).map { it.amountPaise })
    }

    @Test fun oneAmountWithAKnownWidthUsesTheLineHeight() {
        // Only one amount on screen, so the glyph width can't be fitted from several. A digit is about 0.55 of the
        // line height (30): "760" in a box three glyphs wide is ₹60; four glyphs wide, ₹760 with the ₹ dropped.
        assertEquals(60_00L, one("760", w = 3 * 16.5f))
        assertEquals(760_00L, one("760", w = 4 * 16.5f))
    }

    // ---- 26. Screenshots ------------------------------------------------------------------------------------

    @Test fun eachStatusWordSkipsOnlyItsOwnRow() {
        val now = at(2026, 9, 28)
        for (status in listOf("Failed", "Declined", "Pending", "Processing", "Cancelled", "Expired", "Reversed", "Requested", "Scheduled", "Payment failed")) {
            val rows = AppHistoryParser.parse(
                listOf(
                    l("Paid to Swiggy", 180f, 300f), l("₹250", 900f, 300f), l("12 Sep, 8:30 PM", 180f, 345f),
                    l("Paid to Zomato", 180f, 500f), l("₹300", 900f, 500f), l("12 Sep, 9:30 PM", 180f, 545f), l(status, 850f, 545f),
                    l("Paid to Uber", 180f, 700f), l("₹180", 900f, 700f), l("12 Sep, 10:30 PM", 180f, 745f),
                ), now,
            )
            assertEquals(status, listOf(250_00L, 180_00L), rows.map { it.amountPaise })
        }
    }

    @Test fun aMerchantNamedWithAStatusWordIsNotSkipped() {
        val rows = AppHistoryParser.parse(listOf(l("Paid to Pending Bills Store", 180f, 300f), l("₹250", 900f, 300f), l("12 Sep, 8:30 PM", 180f, 345f)), at(2026, 9, 28))
        assertEquals(1, rows.size)
    }

    @Test fun twoIdenticalPaymentsInOneScreenshotAreBothKept() {
        val now = at(2026, 9, 28)
        val shot = listOf(
            l("Chai Point", 180f, 300f), l("₹20", 900f, 300f), l("12 Sep", 180f, 345f),
            l("Chai Point", 180f, 500f), l("₹20", 900f, 500f), l("12 Sep", 180f, 545f),
        )
        assertEquals(2, AppHistoryParser.parse(shot, now).size)
        // The same screen captured twice (overlap) still gives two, not four.
        assertEquals(2, AppHistoryParser.parse(shot + shot.map { it.copy(y = it.y + 100_000f, page = 1) }, now).size)
    }

    @Test fun aRowCutAcrossTwoScreenshotsIsNeverGivenAnotherRowsName() {
        val now = at(2026, 9, 28)
        val lines = listOf(
            l("Paid to Swiggy", 180f, 1900f), l("₹250", 900f, 1900f), l("12 Sep, 8:30 PM", 180f, 1945f),
            l("Paid to Rahul Sharma", 180f, 2300f), l("₹500", 900f, 2300f), // date cut off at the bottom
            l("12 Sep, 9:00 PM", 180f, 20f, page = 1), // ...and shown at the top of the next screenshot
            l("Paid to Uber", 180f, 200f, page = 1), l("₹180", 900f, 200f, page = 1), l("12 Sep, 10:30 PM", 180f, 245f, page = 1),
        )
        val rows = AppHistoryParser.parse(lines, now)
        assertNotNull(rows.firstOrNull { it.amountPaise == 180_00L && it.counterparty.contains("Uber") })
        assertTrue(rows.toString(), rows.none { it.amountPaise == 500_00L && !it.counterparty.contains("Rahul") })
        assertTrue(rows.none { it.amountPaise == 180_00L && !it.counterparty.contains("Uber") })
        assertEquals(TransactionType.DEBIT, rows.first().type)
    }
}
