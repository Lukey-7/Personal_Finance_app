package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.model.TransactionType.DEBIT
import com.pft.financetracker.domain.split.SplitKind
import com.pft.financetracker.domain.split.SplitProposal
import com.pft.financetracker.domain.split.SplitSolver
import com.pft.financetracker.domain.split.SplitTx
import com.pft.financetracker.domain.split.SplitVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.2.1 edge cases for the local split solver: boundaries, big groups, repeat friends, twins, paise, speed. */
class EdgeSplitSolverTest {
    private val t0 = java.util.Calendar.getInstance().apply { set(2026, 8, 5, 20, 0, 0); set(java.util.Calendar.MILLISECOND, 0) }.timeInMillis
    private val H = 3_600_000L
    private val D = SplitSolver.DAY
    private fun pay(id: Long, paise: Long, name: String, cat: Category, t: Long) = SplitTx(id, paise, DEBIT, t, name, cat, false)
    private fun got(id: Long, paise: Long, name: String, t: Long) = SplitTx(id, paise, CREDIT, t, name, Category.INCOME, true)
    private fun names(n: Int) = (0 until n).map { i -> "Frnd" + ('a' + i / 26) + ('a' + i % 26) + "x" }

    // ---- 8. Window boundaries -------------------------------------------------------------------------------

    @Test fun paybackExactlyFourteenDaysLaterCountsOneMillisecondMoreDoesNot() {
        val dinner = pay(1, 3_000_00, "Toit", Category.FOOD, t0)
        val a = got(10, 1_000_00, "Rahul Sharma", t0 + 14 * D)
        val b = got(11, 1_000_00, "Priya Nair", t0 + 14 * D + 1)
        val p = SplitSolver.solve(listOf(dinner, a, b)).single()
        assertEquals(listOf(10L), p.allocations.map { it.txId })
        val byId = listOf(dinner, a, b).associateBy { it.id }
        assertTrue(SplitVerifier.problems(p, byId).isEmpty())
        assertTrue(SplitVerifier.problems(p.copy(allocations = p.allocations + com.pft.financetracker.domain.split.Allocation(11, 1_000_00)), byId).isNotEmpty())
    }

    @Test fun moneyOneMillisecondBeforeThePaymentIsNeverAPayback() {
        val dinner = pay(1, 3_000_00, "Toit", Category.FOOD, t0)
        val ps = SplitSolver.solve(listOf(dinner, got(10, 1_000_00, "Rahul Sharma", t0 - 1), got(11, 1_000_00, "Priya Nair", t0 + 60_000)))
        assertTrue(ps.none { p -> p.kind == SplitKind.PAYBACK && p.allocations.any { it.txId == 10L } })
    }

    // ---- 9. Groups and people -------------------------------------------------------------------------------

    @Test(timeout = 20_000) fun fiftyPeople() {
        val txs = listOf(pay(1, 10_000_00, "Office Party Hall", Category.ENTERTAINMENT, t0)) + names(49).mapIndexed { i, n -> got(100L + i, 200_00, n, t0 + D + i * 60_000L) }
        val p = SplitSolver.solve(txs).single()
        assertEquals(50, p.people)
        assertEquals(49 * 200_00L, p.allocatedPaise)
        assertEquals(SplitProposal.Level.HIGH, p.level)
    }

    @Test(timeout = 20_000) fun seventyFivePeopleAreFoundWithTheRightHeadCount() {
        val txs = listOf(pay(1, 15_000_00, "Annual Day Venue", Category.ENTERTAINMENT, t0)) + names(74).mapIndexed { i, n -> got(100L + i, 200_00, n, t0 + D + i * 60_000L) }
        val p = SplitSolver.solve(txs).singleOrNull()
        assertNotNull("a 75-person event is found", p)
        assertEquals(75, p!!.people)
        assertEquals(74 * 200_00L, p.allocatedPaise)
    }

    private val dinner = pay(1, 3_000_00, "Toit", Category.FOOD, t0)
    private val cab = pay(2, 600_00, "Uber", Category.TRANSPORT, t0 + 2 * H)

    @Test fun oneFriendInTwoGroupsPayingSeparately() {
        val ps = SplitSolver.solve(
            listOf(
                dinner, cab, got(10, 1_000_00, "Rahul Sharma", t0 + D), got(11, 200_00, "Rahul Sharma", t0 + D + 5 * 60_000),
                got(12, 1_000_00, "Priya Nair", t0 + D + H), got(13, 200_00, "Priya Nair", t0 + D + H + 60_000),
            )
        ).associateBy { it.paymentId }
        assertEquals(setOf(10L, 12L), ps[1]!!.allocations.map { it.txId }.toSet())
        assertEquals(setOf(11L, 13L), ps[2]!!.allocations.map { it.txId }.toSet())
    }

    @Test fun oneFriendPaysBothSharesInOneTransferTheOtherSeparately() {
        val ps = SplitSolver.solve(
            listOf(dinner, cab, got(10, 1_200_00, "Rahul Sharma", t0 + D), got(12, 1_000_00, "Priya Nair", t0 + D + H), got(13, 200_00, "Priya Nair", t0 + D + H + 60_000))
        ).associateBy { it.paymentId }
        assertEquals(2_000_00L, ps[1]!!.allocatedPaise)
        assertEquals(400_00L, ps[2]!!.allocatedPaise)
        assertEquals(1_000_00L, ps[1]!!.allocations.single { it.txId == 10L }.paise)
        assertEquals(200_00L, ps[2]!!.allocations.single { it.txId == 10L }.paise)
    }

    @Test fun twinPaymentsEachGetTheirOwnPaybacks() {
        val p1 = pay(1, 3_000_00, "Toit", Category.FOOD, t0)
        val p2 = pay(2, 3_000_00, "Toit", Category.FOOD, t0 + 2 * D)
        val ps = SplitSolver.solve(
            listOf(p1, p2, got(10, 1_000_00, "Rahul Sharma", t0 + D), got(11, 1_000_00, "Priya Nair", t0 + D + H),
                got(12, 1_000_00, "Rahul Sharma", t0 + 3 * D), got(13, 1_000_00, "Priya Nair", t0 + 3 * D + H))
        ).associateBy { it.paymentId }
        assertEquals(setOf(10L, 11L), ps[1]!!.allocations.map { it.txId }.toSet())
        assertEquals(setOf(12L, 13L), ps[2]!!.allocations.map { it.txId }.toSet())
    }

    @Test fun sharesWithPaise() {
        val bill = pay(1, 1_333_35, "Toit", Category.FOOD, t0)
        val p = SplitSolver.solve(listOf(bill, got(10, 333_34, "Rahul Sharma", t0 + H), got(11, 333_33, "Priya Nair", t0 + H), got(12, 333_00, "Amit Desai", t0 + H))).single()
        assertEquals(4, p.people)
        assertEquals(999_67L, p.allocatedPaise)
        assertTrue(p.reasons.toString(), p.reasons.any { it == "Your share so far: ₹333.68" })
    }

    @Test fun aSenderWithNoNameWordsIsStillOnePerson() {
        // "AK" and a bare phone number have no word of 3+ letters: two parts from the same sender are one share.
        for (who in listOf("AK", "9876543210")) {
            val p = SplitSolver.solve(listOf(pay(1, 2_000_00, "Toit", Category.FOOD, t0), got(10, 500_00, who, t0 + H), got(11, 500_00, who, t0 + D))).singleOrNull()
            assertNotNull(who, p)
            assertEquals(who, 2, p!!.people)
            assertEquals(who, 1_000_00L, p.allocatedPaise)
        }
    }

    // ---- 10. Not paybacks -----------------------------------------------------------------------------------

    @Test fun moneyFromOrganisationsIsNeverAllocated() {
        val food = pay(1, 1_000_00, "Swiggy", Category.FOOD, t0)
        val txs = listOf(food, SplitTx(10, 500_00, CREDIT, t0 + H, "Swiggy", Category.INCOME, false), SplitTx(11, 500_00, CREDIT, t0 + 2 * H, "Google Pay", Category.INCOME, false))
        assertTrue(SplitSolver.solve(txs).isEmpty())
    }

    // ---- 11. Speed -----------------------------------------------------------------------------------------

    @Test(timeout = 20_000) fun aBusyFortnightSolvesQuickly() {
        val r = java.util.Random(7)
        val txs = (0 until 300).map { i -> pay(i.toLong(), (100 + r.nextInt(5_000)) * 100L, "Shop$i", Category.FOOD, t0 + r.nextInt(14 * 24) * H) } +
            (0 until 300).map { i -> got(1_000L + i, (50 + r.nextInt(1_500)) * 100L, names(300)[i], t0 + r.nextInt(14 * 24) * H) }
        val start = System.nanoTime()
        SplitSolver.solve(txs)
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < 2_000)
    }

    @Test fun solvingIsStable() {
        val txs = listOf(dinner, cab, got(10, 1_200_00, "Rahul Sharma", t0 + D), got(12, 1_000_00, "Priya Nair", t0 + D + H), got(13, 200_00, "Priya Nair", t0 + D + H + 60_000))
        val a = SplitSolver.solve(txs); val b = SplitSolver.solve(txs.reversed())
        assertEquals(a.map { it.paymentId to it.allocations.sortedBy { x -> x.txId } }.toSet(), b.map { it.paymentId to it.allocations.sortedBy { x -> x.txId } }.toSet())
        assertNull(SplitSolver.solve(emptyList()).firstOrNull())
    }
}
