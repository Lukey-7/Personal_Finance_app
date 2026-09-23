package com.pft.financetracker

import com.pft.financetracker.domain.split.BillExtras
import com.pft.financetracker.domain.split.BillItem
import com.pft.financetracker.domain.split.Person
import com.pft.financetracker.domain.split.Split
import com.pft.financetracker.domain.split.SplitCalculator
import com.pft.financetracker.domain.split.SplitMode
import com.pft.financetracker.domain.split.SplitShare
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SplitCalculatorTest {

    @Test
    fun equalWithRemainderGoesToPayer() {
        val r = SplitCalculator.equal(10_000, 3, payerIndex = 1)
        assertEquals(listOf(3_333L, 3_334L, 3_333L), r.shares.map { it.amountPaise })
        assertEquals(10_000L, r.shares.sumOf { it.amountPaise })
    }

    @Test
    fun equalExact() {
        val r = SplitCalculator.equal(120_000, 4, 0)
        assertEquals(List(4) { 30_000L }, r.shares.map { it.amountPaise })
    }

    @Test
    fun sharesTwoToOneToOne() {
        val r = SplitCalculator.byShares(100_000, listOf(2, 1, 1), 0)
        assertEquals(listOf(50_000L, 25_000L, 25_000L), r.shares.map { it.amountPaise })
    }

    @Test
    fun sharesRemainderToPayer() {
        val r = SplitCalculator.byShares(100, listOf(1, 1, 1), payerIndex = 2)
        assertEquals(listOf(33L, 33L, 34L), r.shares.map { it.amountPaise })
    }

    @Test
    fun customMustSum() {
        assertEquals(500L, SplitCalculator.customDifference(10_000, listOf(4_000, 5_500)))
        val r = SplitCalculator.custom(10_000, listOf(4_500, 5_500))
        assertEquals(10_000L, r.totalPaise)
    }

    @Test(expected = IllegalArgumentException::class)
    fun customRejectsMismatch() { SplitCalculator.custom(10_000, listOf(4_000, 5_500)) }

    @Test
    fun byItemWithProportionalTax() {
        // A ordered 300, B ordered 100, shared item 200 -> A 400, B 200 of 600 items. Tax 60 -> A 40, B 20.
        val items = listOf(
            BillItem(name = "Steak", pricePaise = 30_000, assignedTo = setOf(0)),
            BillItem(name = "Salad", pricePaise = 10_000, assignedTo = setOf(1)),
            BillItem(name = "Fries", pricePaise = 20_000, assignedTo = emptySet()),
        )
        val r = SplitCalculator.byItem(items, BillExtras(taxPaise = 6_000), people = 2, payerIndex = 0)
        assertEquals(66_000L, r.totalPaise)
        assertEquals(listOf(44_000L, 22_000L), r.shares.map { it.amountPaise })
    }

    @Test
    fun byItemDiscountAndQuantity() {
        val items = listOf(BillItem(name = "Beer", quantity = 4, pricePaise = 25_000, assignedTo = setOf(0, 1)))
        val r = SplitCalculator.byItem(items, BillExtras(discountPaise = 10_000, servicePaise = 5_000), 2, 1)
        assertEquals(95_000L, r.totalPaise)
        assertEquals(listOf(47_500L, 47_500L), r.shares.map { it.amountPaise })
    }

    @Test
    fun byItemOddRemainderNeverLoses1Paisa() {
        val items = listOf(BillItem(name = "x", pricePaise = 10_001, assignedTo = setOf(0, 1, 2)))
        val r = SplitCalculator.byItem(items, BillExtras(taxPaise = 1_001), 3, 0)
        assertEquals(11_002L, r.totalPaise)
        assertEquals(11_002L, r.shares.sumOf { it.amountPaise })
    }

    /**
     * A bill that divides cleanly must look like it. Handing each item's leftover paise to the payer
     * one item at a time turned a 990 / 3 split into 330.04 / 329.98 / 329.98; the remainder is now
     * pooled and settled once.
     */
    @Test
    fun byItemStaysCleanWhenTheBillDividesEvenly() {
        val items = listOf(
            BillItem(name = "Paneer Tikka", pricePaise = 28_000),
            BillItem(name = "Veg Biryani", quantity = 2, pricePaise = 22_000),
            BillItem(name = "Masala Chaas", quantity = 3, pricePaise = 6_000),
        )
        val r = SplitCalculator.byItem(items, BillExtras(taxPaise = 4_500, servicePaise = 4_500), people = 3, payerIndex = 0)
        assertEquals(99_000L, r.totalPaise)
        assertEquals(listOf(33_000L, 33_000L, 33_000L), r.shares.map { it.amountPaise })
    }

    @Test
    fun byItemPooledRemainderStillSumsExactly() {
        // Three items that each leave a paisa behind: pooled, that is 3 paise, not 3 separate rounding hits.
        val items = (1..3).map { BillItem(name = "item$it", pricePaise = 10_000 + 1, assignedTo = setOf(0, 1, 2)) }
        val r = SplitCalculator.byItem(items, BillExtras(), people = 3, payerIndex = 1)
        assertEquals(30_003L, r.totalPaise)
        assertEquals(30_003L, r.shares.sumOf { it.amountPaise })
        // Nobody is more than a paisa off an even share.
        r.shares.forEach { assertTrue("share ${it.amountPaise}", Math.abs(it.amountPaise - 10_001L) <= 1L) }
    }

    @Test
    fun balancesAcrossSplits() {
        val me = Person(name = "Me", isMe = true); val a = Person(name = "Asha"); val b = Person(name = "Bala")
        val s1 = Split(title = "Dinner", totalPaise = 90_000, date = 0, mode = SplitMode.EQUAL, payerIndex = 0, people = listOf(me, a, b),
            shares = listOf(SplitShare(personIndex = 0, amountPaise = 30_000, settledPaise = 30_000), SplitShare(personIndex = 1, amountPaise = 30_000), SplitShare(personIndex = 2, amountPaise = 30_000, settledPaise = 10_000)))
        val s2 = Split(title = "Cab", totalPaise = 40_000, date = 0, mode = SplitMode.EQUAL, payerIndex = 1, people = listOf(me, a),
            shares = listOf(SplitShare(personIndex = 0, amountPaise = 20_000), SplitShare(personIndex = 1, amountPaise = 20_000, settledPaise = 20_000)))
        val bal = SplitCalculator.balances(listOf(s1, s2))
        assertEquals(10_000L, bal.first { it.name == "Asha" }.netPaise)   // owes 30k, I owe her 20k
        assertEquals(20_000L, bal.first { it.name == "Bala" }.netPaise)   // 30k minus 10k paid
    }
}
