package com.pft.financetracker

import com.pft.financetracker.domain.ocr.BillParser
import com.pft.financetracker.domain.ocr.ParsedBill
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Real ML Kit output, captured on the emulator from photographed test receipts (clean, faded, angled, dim)
 * run through New split -> Gallery. Unlike the hand-typed fixtures in BillParserTest, these carry what the
 * recogniser actually produces: "0" read as "e", a stray space inside an amount, a dropped quantity, and
 * "Rs." / "RS." prefixes.
 */
class BillParserRealOcrTest {

    private val restaurantClean = """
        TRUFFLES CAFE
        22, St Marks Road, Bengaluru
        GSTIN 29ABCDE1234F1Z5
        Bill No: 4471  Date: 20/09/2026
        Item  Qty  Amount
        Paneer Tikka  1  320.00
        Dal Makhani  1  280.00
        Butter Naan  4  240.00
        Jeera Rice  2  360.0e
        Masala Chaas  3  210.00
        Sub Total  1,410.00
        CGST 2.5%  35.25
        SGST 2.5%  35.25
        Service Charge 5%  70.50
        Grand Total  1,551.00
        Thank you! Visit again
    """.trimIndent()

    private val restaurantFaded = """
        TRUFFLES CAFE
        22, St Marks Road, Bengaluru
        GSTIN 29ABCDE1234F1Z5
        Bill No: 4471  Date: 20/09/2026
        Item  Qty  Amount
        Paneer Tikka  1  320.00
        Dal Makhani  280.0
        Butter Naan  4  240.00
        Jeera Rice  2  360.00
        Masala Chaas  3  210.00
        Sub Total  1,410.0
        CGST 2.5%  35.25
        SGST 2.5%  35.25
        Service Charge 5%  70.50
        Grand Total  1,551. 00
        Thank you! Visit again
    """.trimIndent()

    private val groceryAngled = """
        FRESHMART SUPERSTORE
        Koramangala, Bengaluru
        Inv 88213
        Dt 21-09-2026
        Amul Butter 500g  RS.285.00
        Aashirvaad Atta 5kg  RS.265.00
        2 x Tata Salt 1kg  Rs.56.00
        6 x Maggi Noodles  RS.84.00
        Surf Excel 1kg  Rs.135.00
        Item Total
        Rs.825.00
        Discount
        -50.00
        Net Payable  Rs.775.00
        You saved RS.50.00
    """.trimIndent()

    private val groceryDim = """
        FRESHMART SUPERSTORE
        Koramangala, Bengaluru
        Iny 88213  Dt 21-09-2026
        Amul Butter 500g  RS.285.00
        Aashirvaad Atta 5kg  Rs.265.00
        2 X Tata Salt 1kg  Rs.56.00
        6 x Maggi Noodles  RS.84.00
        Surf Excel 1kg  Rs.135.00
        Item Total  RS.825.00
        Discount  -50.00
        Net Payable  RS.775.00
        You saved Rs.50.00
    """.trimIndent()

    /** name, quantity, unit price in paise */
    private val restaurantItems = listOf(
        Triple("Paneer Tikka", 1, 32_000L), Triple("Dal Makhani", 1, 28_000L), Triple("Butter Naan", 4, 6_000L),
        Triple("Jeera Rice", 2, 18_000L), Triple("Masala Chaas", 3, 7_000L),
    )
    private val groceryItems = listOf(
        Triple("Amul Butter 500g", 1, 28_500L), Triple("Aashirvaad Atta 5kg", 1, 26_500L), Triple("Tata Salt 1kg", 2, 2_800L),
        Triple("Maggi Noodles", 6, 1_400L), Triple("Surf Excel 1kg", 1, 13_500L),
    )

    private fun items(b: ParsedBill) = b.items.map { Triple(it.name, it.quantity, it.pricePaise) }

    private fun checkRestaurant(text: String) {
        val b = BillParser.parse(text)
        assertEquals("total", 155_100L, b.totalPaise)
        assertEquals("tax", 7_050L, b.taxPaise)
        assertEquals("service", 7_050L, b.servicePaise)
        assertEquals("discount", 0L, b.discountPaise)
        assertEquals("items", restaurantItems, items(b))
    }

    private fun checkGrocery(text: String) {
        val b = BillParser.parse(text)
        assertEquals("total", 77_500L, b.totalPaise)
        assertEquals("tax", 0L, b.taxPaise)
        assertEquals("service", 0L, b.servicePaise)
        assertEquals("discount", 5_000L, b.discountPaise)
        assertEquals("items", groceryItems, items(b))
    }

    @Test fun restaurantClean() = checkRestaurant(restaurantClean)
    @Test fun restaurantFaded() = checkRestaurant(restaurantFaded)
    @Test fun groceryAngled() = checkGrocery(groceryAngled)
    @Test fun groceryDim() = checkGrocery(groceryDim)
}
