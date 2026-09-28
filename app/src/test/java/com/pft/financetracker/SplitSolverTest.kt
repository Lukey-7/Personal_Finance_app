package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.model.TransactionType.DEBIT
import com.pft.financetracker.domain.split.SplitDecider
import com.pft.financetracker.domain.split.SplitKind
import com.pft.financetracker.domain.split.SplitProposal
import com.pft.financetracker.domain.split.SplitSolver
import com.pft.financetracker.domain.split.SplitTx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Split intelligence without AI: the local solver on real-life shapes, and the traps it must not fall into. */
class SplitSolverTest {
    private val sat = java.util.Calendar.getInstance().apply { set(2026, 8, 19, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
    private fun at(day: Int, h: Int, m: Int = 0) = sat + day * 86_400_000L + h * 3_600_000L + m * 60_000L
    private fun pay(id: Long, rupees: Int, name: String, cat: Category, t: Long, toPerson: Boolean = false) = SplitTx(id, rupees * 100L, DEBIT, t, name, cat, toPerson)
    private fun got(id: Long, rupees: Int, name: String, t: Long, person: Boolean = true) = SplitTx(id, rupees * 100L, CREDIT, t, name, Category.INCOME, person)

    /** Ronak's weekend (docs/PLAN-v1.2-split-intelligence.md, section 3): acceptance test #1. */
    private val weekend = listOf(
        pay(1, 12_000, "SBOW", Category.FOOD, at(0, 20, 10)),
        pay(2, 600, "Uber", Category.TRANSPORT, at(0, 23, 40)),
        pay(3, 180, "Rapido", Category.TRANSPORT, at(1, 10, 5)),
        got(10, 1_000, "Rahul", at(1, 11)),
        got(11, 1_200, "Priya", at(1, 11, 20)),
        got(12, 200, "Amit", at(1, 12)),
    ) + listOf("Neha", "Karan", "Sneha", "Vikram", "Anjali", "Rohan", "Pooja", "Arjun", "Meera").mapIndexed { i, n -> got(13L + i, 1_000, n, at(2, 9, i * 10)) } +
        got(30, 85_000, "Acme Corp", at(3, 10), person = false)

    @Test fun ronaksWeekendComesOutExactlyRight() {
        val ps = SplitSolver.solve(weekend).associateBy { it.paymentId }
        val sbow = ps[1]!!
        assertEquals(11_000_00L, sbow.allocatedPaise)
        assertEquals("my share of SBOW", 1_000_00L, 12_000_00L - sbow.allocatedPaise)
        assertEquals(12, sbow.people)
        assertEquals(SplitProposal.Level.HIGH, sbow.level)
        val uber = ps[2]!!
        assertEquals("Amit's 200 + Priya's 200", 400_00L, uber.allocatedPaise)
        assertEquals(SplitProposal.Level.HIGH, uber.level)
        assertNull("Rapido was just me", ps[3])
        // Priya's Rs 1,200 is split 1,000 / 200 and used in full; salary is never touched.
        assertEquals(1_000_00L, sbow.allocations.single { it.txId == 11L }.paise)
        assertEquals(200_00L, uber.allocations.single { it.txId == 11L }.paise)
        assertTrue(ps.values.none { p -> p.allocations.any { it.txId == 30L } })
        // Both apply automatically.
        val byId = weekend.associateBy { it.id }
        val decided = SplitDecider.decide(ps.values.toList(), null, emptySet(), byId)
        assertEquals(setOf(1L, 2L), decided.filter { it.auto }.map { it.proposal.paymentId }.toSet())
    }

    @Test fun oneFriendNotPaidYetLeavesTheirShareInMySpendAndSaysSo() {
        val ps = SplitSolver.solve(weekend.filter { it.id != 21L }).associateBy { it.paymentId }
        assertEquals(10_000_00L, ps[1]!!.allocatedPaise)
        assertTrue(ps[1]!!.reasons.any { it.contains("1 person hasn't paid yet") })
    }

    @Test fun paybackOnDay13JoinsDay15DoesNot() {
        val dinner = pay(1, 3_000, "Toit", Category.FOOD, at(0, 21))
        val day13 = SplitSolver.solve(listOf(dinner, got(10, 1_000, "Rahul", at(1, 10)), got(11, 1_000, "Priya", at(13, 10)))).single()
        assertEquals(2, day13.allocations.size)
        val day15 = SplitSolver.solve(listOf(dinner, got(10, 1_000, "Rahul", at(1, 10)), got(11, 1_000, "Priya", at(15, 10)))).single()
        assertEquals(1, day15.allocations.size)
    }

    private val friends = listOf("Ana", "Ben", "Cal", "Dev", "Eli", "Fay", "Gus", "Hal", "Ivy", "Jay", "Kit")

    @Test fun roundedSharesAreRecognised() {
        // Rs 12,340 for 12 = Rs 1,028.33 a head; friends send Rs 1,030.
        val txns = listOf(pay(1, 12_340, "Pop Tates", Category.FOOD, at(0, 20))) + friends.mapIndexed { i, n -> got(10L + i, 1_030, n, at(1, 9, i)) }
        val p = SplitSolver.solve(txns).single()
        assertEquals(11, p.allocations.size)
        assertEquals(12, p.people)
    }

    @Test fun aSharePaidInTwoPartsCountsOnce() {
        val txns = listOf(pay(1, 2_000, "Cafe", Category.FOOD, at(0, 20)), got(10, 500, "Rahul", at(1, 9)), got(11, 500, "Rahul", at(2, 9)))
        val p = SplitSolver.solve(txns).single()
        assertEquals(1_000_00L, p.allocatedPaise)
        assertEquals(2, p.people)
    }

    @Test fun twoDinnersInOneWeekGetTheirOwnPaybacks() {
        val txns = listOf(
            pay(1, 4_000, "Dinner Sat", Category.FOOD, at(0, 20)),
            got(10, 1_000, "Ana", at(1, 9)), got(11, 1_000, "Ben", at(1, 10)), got(12, 1_000, "Cal", at(1, 11)),
            pay(2, 3_000, "Dinner Tue", Category.FOOD, at(3, 20)),
            got(13, 1_000, "Ana", at(4, 9)), got(14, 1_000, "Ben", at(4, 10)),
        )
        val ps = SplitSolver.solve(txns).associateBy { it.paymentId }
        assertEquals(setOf(10L, 11L, 12L), ps[1]!!.allocations.map { it.txId }.toSet())
        assertEquals(setOf(13L, 14L), ps[2]!!.allocations.map { it.txId }.toSet())
    }

    @Test fun advanceCollectionIsFoundButNeverAppliedAutomatically() {
        val txns = listOf(got(10, 2_000, "Ana", at(0, 9)), got(11, 2_000, "Ben", at(0, 10)), got(12, 2_000, "Cal", at(1, 9)), pay(1, 8_000, "MakeMyTrip", Category.TRANSPORT, at(2, 12)))
        val p = SplitSolver.solve(txns).single()
        assertEquals(SplitKind.ADVANCE, p.kind)
        assertEquals(6_000_00L, p.allocatedPaise)
        val d = SplitDecider.decide(listOf(p), null, emptySet(), txns.associateBy { it.id }).single()
        assertFalse("advance is always a suggestion", d.auto)
    }

    // ---- Traps: none of these may be split ----

    @Test fun salaryAndRefundsAreNeverPaybacks() {
        val txns = listOf(pay(1, 2_000, "Swiggy", Category.FOOD, at(0, 20)), got(10, 1_000, "Swiggy Refund", at(1, 9), person = false), got(11, 1_000, "Acme Corp", at(1, 10), person = false))
        assertTrue(SplitSolver.solve(txns).isEmpty())
    }

    @Test fun flatmatesMonthlyRentWithNoPaymentBeforeIsNotASplit() {
        assertTrue(SplitSolver.solve(listOf(got(10, 15_000, "Rohan", at(0, 9)), got(11, 15_000, "Rohan", at(30, 9)))).isEmpty())
    }

    @Test fun aLoanRepaidAfterAnUnrelatedPurchaseIsNotASplit() {
        val txns = listOf(pay(1, 12_000, "Croma", Category.SHOPPING, at(0, 12)), got(10, 5_000, "Rahul", at(5, 9)))
        assertTrue(SplitSolver.solve(txns).isEmpty())
    }

    @Test fun oneFriendsThousandAfterABigPurchaseIsNotAppliedOrSuggested() {
        val txns = listOf(pay(1, 12_000, "Croma", Category.SHOPPING, at(0, 12)), got(10, 1_000, "Rahul", at(6, 9)))
        val local = SplitSolver.solve(txns)
        assertTrue(SplitDecider.decide(local, null, emptySet(), txns.associateBy { it.id }).isEmpty())
    }

    @Test fun aSingleHalfIsOnlySuggested() {
        val txns = listOf(pay(1, 1_200, "Toit", Category.FOOD, at(0, 21)), got(10, 600, "Priya", at(1, 10)))
        val d = SplitDecider.decide(SplitSolver.solve(txns), null, emptySet(), txns.associateBy { it.id }).single()
        assertFalse(d.auto)
    }

    /**
     * The whole month the emulator test imported (SMS weekend + PDF Pop Tates + Excel advance and uneven Toit). Found
     * there: Rs 8,000 / 7 "rounded" to Rs 1,200 let the hotel claim Priya's dinner-and-cab transfer.
     */
    @Test fun aWholeMonthOfSharedPaymentsStaysApart() {
        val sep5 = sat - 14 * 86_400_000L
        fun d(day: Int, h: Int, m: Int = 0) = sep5 + (day - 5) * 86_400_000L + h * 3_600_000L + m * 60_000L
        val month = weekend + listOf(
            pay(100, 12_340, "Pop Tates", Category.OTHER, d(5, 12)),
        ) + friends.mapIndexed { i, n -> got(101L + i, 1_030, "$n Kumar", d(6, 12, i)) } + listOf(
            pay(120, 150, "Rapido", Category.TRANSPORT, d(6, 12, 30)),
            got(130, 2_000, "Ana", d(10, 12)), got(131, 2_000, "Ben", d(10, 12, 1)), got(132, 2_000, "Cal", d(11, 12)),
            pay(133, 8_000, "Makemytrip", Category.TRANSPORT, d(12, 12)),
            pay(134, 1_800, "Toit Brewpub", Category.OTHER, d(13, 12), toPerson = true),
            got(135, 500, "Rahul", d(14, 12)), got(136, 700, "Priya", d(14, 12, 1)),
            // The screenshots: a Rs 900 cab on the 24th, two friends Rs 300 each 35-50 minutes later.
            pay(140, 900, "Uber", Category.TRANSPORT, d(24, 22, 15)), got(141, 300, "Karan Mehta", d(24, 22, 50)), got(142, 300, "Sneha Rao", d(24, 23, 5)),
        )
        val ps = SplitSolver.solve(month).associateBy { it.paymentId }
        assertEquals(11_000_00L, ps[1]!!.allocatedPaise)
        assertEquals(400_00L, ps[2]!!.allocatedPaise)
        assertEquals(11 * 1_030_00L, ps[100]!!.allocatedPaise)
        assertEquals(SplitKind.ADVANCE, ps[133]!!.kind)
        assertEquals(6_000_00L, ps[133]!!.allocatedPaise)
        // Uneven shares are for the AI: locally at most a low-confidence guess, which is never applied or suggested.
        assertTrue(ps[134] == null || ps[134]!!.level == SplitProposal.Level.LOW)
        val decided = SplitDecider.decide(ps.values.toList(), null, emptySet(), month.associateBy { it.id }).associateBy { it.proposal.paymentId }
        assertNull(decided[134])
        assertEquals("Rs 300 x 2 forty minutes after the cab is not ambiguous with Toit eleven days earlier", setOf(1L, 2L, 100L, 140L), decided.filterValues { it.auto }.keys)
        assertFalse("the advance waits for a yes", decided[133]!!.auto)
        // The AI's uneven answer for Toit applies despite the weak local guess.
        val ai = listOf(SplitProposal(134, SplitKind.PAYBACK, listOf(com.pft.financetracker.domain.split.Allocation(135, 500_00), com.pft.financetracker.domain.split.Allocation(136, 700_00)),
            null, 3, 85, listOf("uneven"), com.pft.financetracker.domain.split.SplitSource.AUTO_AI))
        val withAi = SplitDecider.decide(ps.values.toList(), ai, setOf(134L), month.associateBy { it.id }).associateBy { it.proposal.paymentId }
        assertTrue(withAi[134]!!.auto)
    }
}
