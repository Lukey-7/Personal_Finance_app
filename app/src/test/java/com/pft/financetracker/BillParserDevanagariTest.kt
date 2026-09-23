package com.pft.financetracker

import com.pft.financetracker.domain.ocr.BillParser
import com.pft.financetracker.domain.ocr.ParsedBill
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * Bills printed in Hindi (Devanagari), as the ML Kit Devanagari recogniser returns them: Hindi labels,
 * Devanagari digits in some amounts, "रु." for rupees, and English words mixed in, as on most real bills.
 */
class BillParserDevanagariTest {

    private val hindiBill = """
        शर्मा भोजनालय
        चांदनी चौक, दिल्ली
        बिल सं. ४४७१  दिनांक: २०/०९/२०२६
        सामान  मात्रा  राशि
        पनीर टिक्का  १  ३२०.००
        दाल मखनी  १  २८०.००
        तंदूरी रोटी  ४  ८०.००
        लस्सी  २  १२०.००
        उप योग  ८००.००
        सीजीएसटी 2.5%  २०.००
        एसजीएसटी 2.5%  २०.००
        छूट  -४०.००
        कुल योग  रु. ८००.००
        धन्यवाद, फिर पधारें
    """.trimIndent()

    /** Hinglish in practice: English labels and romanised items, with a few Hindi names and "रु.". */
    private val mixedBill = """
        GUPTA SWEETS
        Karol Bagh, New Delhi
        Inv 5521  Date 21-09-2026
        Kaju Katli 500g  रु.450.00
        समोसा  4  60.00
        Jalebi 250g  Rs.90.00
        Sub Total  600.00
        GST 5%  30.00
        Grand Total  630.00
    """.trimIndent()

    private fun items(b: ParsedBill) = b.items.map { Triple(it.name, it.quantity, it.pricePaise) }

    @Test fun hindiTotalsFromDevanagariDigits() {
        val b = BillParser.parse(hindiBill)
        assertEquals("total", 80_000L, b.totalPaise)
        assertEquals("subtotal", 80_000L, b.subtotalPaise)
        assertEquals("tax", 4_000L, b.taxPaise)
        assertEquals("discount", 4_000L, b.discountPaise)
    }

    @Test fun hindiItemsWithQuantities() = assertEquals(
        listOf(Triple("पनीर टिक्का", 1, 32_000L), Triple("दाल मखनी", 1, 28_000L), Triple("तंदूरी रोटी", 4, 2_000L), Triple("लस्सी", 2, 6_000L)),
        items(BillParser.parse(hindiBill)),
    )

    @Test fun hindiMerchantAndDate() {
        val b = BillParser.parse(hindiBill)
        assertEquals("शर्मा भोजनालय", b.merchant)
        assertNotNull("date read from Devanagari digits", b.date)
    }

    /**
     * Captured from ML Kit's Devanagari model on the emulator: the words are exact, the decimal point comes
     * back as a comma, and one Devanagari zero came back as a Bengali one ("০"). The total line must
     * still read as ₹800.
     */
    @Test fun devanagariModelCommaDecimalsAndBengaliZero() {
        val b = BillParser.parse("शर्मा भोजनालय\nलस्सी  २  120,00\nकुल योग  रु.८००.०০")
        assertEquals("total", 80_000L, b.totalPaise)
        assertEquals(listOf(Triple("लस्सी", 2, 6_000L)), items(b))
    }

    @Test fun commaThousandsAreNotDecimals() =
        assertEquals(10_000_000L, BillParser.parse("Grand Total  1,00,000").totalPaise)

    @Test fun mixedHindiEnglishBill() {
        val b = BillParser.parse(mixedBill)
        assertEquals("total", 63_000L, b.totalPaise)
        assertEquals("tax", 3_000L, b.taxPaise)
        assertEquals(
            listOf(Triple("Kaju Katli 500g", 1, 45_000L), Triple("समोसा", 4, 1_500L), Triple("Jalebi 250g", 1, 9_000L)),
            items(b),
        )
    }
}
