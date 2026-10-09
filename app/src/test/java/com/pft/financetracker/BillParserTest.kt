package com.pft.financetracker

import com.pft.financetracker.domain.ocr.BillParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BillParserTest {

    private val restaurant = """
        TRUFFLES
        Koramangala, Bengaluru
        GSTIN 29ABCDE1234F1Z5
        Bill No: 4521   Date: 12/09/2026
        Item            Qty   Rate   Amount
        Paneer Tikka     1    280.00  280.00
        Veg Biryani      2    220.00  440.00
        Masala Chaas     3     60.00  180.00
        Sub Total                     900.00
        CGST 2.5%                      22.50
        SGST 2.5%                      22.50
        Service Charge                 45.00
        Round Off                       0.00
        Grand Total                   990.00
        Thank you, visit again
    """.trimIndent()

    @Test
    fun findsGrandTotal() = assertEquals(99_000L, BillParser.parse(restaurant).totalPaise)

    @Test
    fun findsSubtotalTaxAndService() {
        val b = BillParser.parse(restaurant)
        assertEquals(90_000L, b.subtotalPaise)
        assertEquals(4_500L, b.taxPaise)
        assertEquals(4_500L, b.servicePaise)
    }

    @Test
    fun extractsItemsWithQuantities() {
        val b = BillParser.parse(restaurant)
        assertEquals(3, b.items.size)
        val biryani = b.items.first { it.name.contains("Biryani") }
        assertEquals(2, biryani.quantity)
        assertEquals(22_000L, biryani.pricePaise)
        assertEquals(0L, b.reconciliationGapPaise)
    }

    @Test
    fun merchantAndDate() {
        val b = BillParser.parse(restaurant)
        assertEquals("TRUFFLES", b.merchant)
        assertNotNull(b.date)
    }

    @Test
    fun simpleReceiptWithRupeeMarks() {
        val text = """
            Cafe Coffee Day
            Cappuccino          Rs.180
            Brownie             Rs.150/-
            Total               Rs.330
            Paid by UPI
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(33_000L, b.totalPaise)
        assertEquals(2, b.items.size)
    }

    @Test
    fun totalOnNextLineAndNoItems() {
        val text = """
            AMOUNT PAYABLE
            1,250.00
        """.trimIndent()
        assertEquals(125_000L, BillParser.parse(text).totalPaise)
    }

    @Test
    fun fallbackTotalIsLargestBottomAmountWhenNoKeyword() {
        val text = """
            Shop
            Item A   100.00
            Item B   200.00
            300.00
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(30_000L, b.totalPaise)
    }

    @Test
    fun percentOnlyTaxLinesAreNotSummedAsAmounts() {
        val text = """
            Sub Total 100.00
            GST 18%
            Total 118.00
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(0L, b.taxPaise)
        assertEquals(11_800L, b.totalPaise)
    }

    /** A five-digit price printed without decimals is an item; a PIN code or invoice number is not. */
    @Test
    fun keepsFiveDigitPricesButNotCodes() {
        val text = """
            SOUND WORLD
            MG Road 560001
            Inv 88213
            Bluetooth Speaker   12500
            Cable                 499
            Total               12999
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(listOf(1_250_000L, 49_900L), b.items.map { it.pricePaise })
    }

    // ---- v1.5: labels as whole words, the right total, exact lines, signed round-off ----------------------------

    @Test
    fun labelsInsideWordsAreNotLabels() {
        val text = """
            CAKE STUDIO
            Tax Invoice No 1234
            Table 12
            Eggless Chocolate Cake 450.00
            Boneless Chicken 380.00
            Vegetable Soup 150.00
            Taxable Amount 980.00
            CGST 2.5% 24.50
            SGST 2.5% 24.50
            Grand Total 1,029.00
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(
            listOf(Triple("Eggless Chocolate Cake", 1, 45_000L), Triple("Boneless Chicken", 1, 38_000L), Triple("Vegetable Soup", 1, 15_000L)),
            b.items.map { Triple(it.name, it.quantity, it.pricePaise) },
        )
        assertEquals("tax: not the taxable amount or the invoice number", 4_900L, b.taxPaise)
        assertEquals(0L, b.discountPaise)
        assertEquals(102_900L, b.totalPaise)
        assertEquals(0L, b.reconciliationGapPaise)
    }

    @Test
    fun aGstSummaryAfterTheGrandTotalIsNotTheTotal() {
        val text = """
            Paneer Tikka 300.00
            Dal Makhani 250.00
            Sub Total 550.00
            CGST 2.5% 13.75
            SGST 2.5% 13.75
            Grand Total 577.50
            GST Summary
            Total GST 27.50
            Total Qty 2
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(57_750L, b.totalPaise)
        assertEquals(2_750L, b.taxPaise)
    }

    @Test
    fun aBareTotalSkipsTheTotalGstLine() {
        val text = """
            Masala Dosa 120.00
            Filter Coffee 40.00
            Total 160.00
            Total GST 8.00
        """.trimIndent()
        assertEquals(16_000L, BillParser.parse(text).totalPaise)
    }

    @Test
    fun aLineThatDoesNotDivideKeepsItsTotal() {
        val text = """
            Lassi 3 33.33 100.00
            Tea 2 15.00 30.00
            Total 130.00
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(listOf(Triple("Lassi", 1, 10_000L), Triple("Tea", 2, 1_500L)), b.items.map { Triple(it.name, it.quantity, it.pricePaise) })
        assertEquals(0L, b.reconciliationGapPaise)
    }

    @Test
    fun aNegativeRoundOffTakesMoneyOff() {
        val text = """
            Veg Thali 250.00
            Sweet Lassi 80.00
            CGST 2.5% 8.25
            SGST 2.5% 8.25
            Round Off -0.50
            Grand Total 346.00
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(-50L, b.servicePaise)
        assertEquals(34_600L, b.totalPaise)
        assertEquals(0L, b.reconciliationGapPaise)
    }

    @Test
    fun cashHandedOverIsNotTheTotal() {
        val text = """
            Chai Point
            Masala Chai 40.00
            Samosa 30.00
            70.00
            Cash Tendered 100.00
            Change 30.00
        """.trimIndent()
        val b = BillParser.parse(text)
        assertEquals(7_000L, b.totalPaise)
        assertEquals(2, b.items.size)
    }

    @Test
    fun garbageIsHarmless() {
        val b = BillParser.parse("~~~ blurry ### 12 xx")
        assertTrue(b.items.isEmpty())
    }
}
