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

    @Test
    fun garbageIsHarmless() {
        val b = BillParser.parse("~~~ blurry ### 12 xx")
        assertTrue(b.items.isEmpty())
    }
}
