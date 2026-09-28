package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.model.TransactionType.DEBIT
import com.pft.financetracker.domain.split.SplitAiRequest
import com.pft.financetracker.domain.split.SplitDecider
import com.pft.financetracker.domain.split.SplitSolver
import com.pft.financetracker.domain.split.SplitSource
import com.pft.financetracker.domain.split.SplitTx
import com.pft.financetracker.domain.split.SplitVerifier
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The AI judge's contract: what is sent (anonymised), how answers are read, and that wrong answers are harmless. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SplitAiTest {
    private val t0 = java.util.Calendar.getInstance().apply { set(2026, 8, 19, 20, 10, 0) }.timeInMillis
    private val h = 3_600_000L
    private val dinner = SplitTx(1, 1_800_00, DEBIT, t0, "Toit Brewpub", Category.FOOD, false)
    // Uneven shares of Rs 1,800 (one had a starter, one didn't): no "bill / k" explains Rs 500 or Rs 700.
    private val rahul = SplitTx(10, 500_00, CREDIT, t0 + 15 * h, "Rahul Sharma", Category.INCOME, true)
    private val priya = SplitTx(11, 700_00, CREDIT, t0 + 16 * h, "Priya Nair", Category.INCOME, true)
    private val salary = SplitTx(30, 85_000_00, CREDIT, t0 + 40 * h, "Acme Corp", Category.INCOME, false)
    private val byId = listOf(dinner, rahul, priya, salary).associateBy { it.id }

    @Test fun requestIsAnonymised() {
        val req = SplitAiRequest.build(listOf(dinner), listOf(rahul, priya))
        assertFalse(req.json.contains("Rahul")); assertFalse(req.json.contains("Priya")); assertFalse(req.json.contains("Toit"))
        assertTrue(req.json.contains("Person A")); assertTrue(req.json.contains("restaurant, food"))
        val o = JSONObject(req.json)
        assertEquals(1800.0, o.getJSONArray("payments").getJSONObject(0).getDouble("amount"), 0.001)
        assertEquals(0, o.getJSONArray("payments").getJSONObject(0).getInt("day"))
    }

    @Test fun answerIsMappedBackAndNamesRestoredInReasons() {
        val req = SplitAiRequest.build(listOf(dinner), listOf(rahul, priya))
        val answer = """{"groups":[{"payment":"P1","kind":"payback","allocations":[{"incoming":"C1","amount":500},{"incoming":"C2","amount":700}],"people":3,"confidence":88,"reason":"Person A and Person B paid uneven shares the next morning"}]}"""
        val p = req.parse(answer)!!.single()
        assertEquals(1L, p.paymentId)
        assertEquals(setOf(10L to 500_00L, 11L to 700_00L), p.allocations.map { it.txId to it.paise }.toSet())
        assertEquals(SplitSource.AUTO_AI, p.source)
        assertTrue(p.reasons.single().contains("Rahul Sharma"))
    }

    @Test fun garbageAnswersAreIgnored() {
        val req = SplitAiRequest.build(listOf(dinner), listOf(rahul))
        assertNull(req.parse("I think P1 is a split"))
        assertTrue(req.parse("""{"groups":[{"payment":"P9","allocations":[{"incoming":"C1","amount":500}]}]}""")!!.isEmpty())
    }

    /** Uneven shares the local rules cannot see: the AI finds them, the verifier checks them, and they apply. */
    @Test fun unevenSharesFoundByAiApply() {
        val local = SplitSolver.solve(byId.values.toList())
        assertTrue("local rules see no equal shares", local.isEmpty())
        val req = SplitAiRequest.build(listOf(dinner), listOf(rahul, priya))
        val ai = req.parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":500},{"incoming":"C2","amount":700}],"confidence":86}]}""")!!
        val d = SplitDecider.decide(local, ai, req.payments, byId).single()
        assertTrue(d.auto)
        assertEquals(1_200_00L, d.proposal.allocatedPaise)
    }

    @Test fun wrongOrMaliciousAiAnswersAreThrownAway() {
        val req = SplitAiRequest.build(listOf(dinner), listOf(rahul, priya, salary))
        fun decide(json: String) = SplitDecider.decide(emptyList(), req.parse(json), req.payments, byId)
        // Salary as a payback (C3 is the organisation's transfer).
        assertTrue(decide("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C3","amount":900}],"confidence":99}]}""").isEmpty())
        // More than the transfer.
        assertTrue(decide("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":5000}],"confidence":99}]}""").isEmpty())
        // More than the bill.
        val big = SplitTx(12, 1_700_00, CREDIT, t0 + 17 * h, "Ravi", Category.INCOME, true)
        val req2 = SplitAiRequest.build(listOf(dinner), listOf(rahul, big))
        assertTrue(SplitDecider.decide(emptyList(), req2.parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":500},{"incoming":"C2","amount":1700}],"confidence":99}]}"""), req2.payments, byId + (12L to big)).isEmpty())
        // A transfer only partly used (Rs 700 counted as Rs 500) would hide Rs 200 of income: rejected.
        assertTrue(decide("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C2","amount":500}],"confidence":99}]}""").isEmpty())
    }

    @Test fun disagreementIsOnlySuggested() {
        val a = SplitTx(12, 600_00, CREDIT, t0 + 15 * h, "Ana", Category.INCOME, true)
        val b = SplitTx(13, 600_00, CREDIT, t0 + 16 * h, "Ben", Category.INCOME, true)
        val ids = byId + mapOf(12L to a, 13L to b)
        val local = SplitSolver.solve(listOf(dinner, a, b))
        assertEquals(1, local.size)
        val req = SplitAiRequest.build(listOf(dinner), listOf(a, b))
        val ai = req.parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":600}],"confidence":90}]}""")!!
        val d = SplitDecider.decide(local, ai, req.payments, ids).single()
        assertFalse(d.auto)
        // Agreement applies.
        val agree = req.parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":600},{"incoming":"C2","amount":600}],"confidence":90}]}""")!!
        assertTrue(SplitDecider.decide(local, agree, req.payments, ids).single().auto)
    }

    @Test fun verifierListsProblems() {
        val bad = SplitSolver.solve(listOf(dinner)).firstOrNull()
        assertNull(bad)
        val p = com.pft.financetracker.domain.split.SplitProposal(1, com.pft.financetracker.domain.split.SplitKind.PAYBACK,
            listOf(com.pft.financetracker.domain.split.Allocation(30, 100_00)), null, null, 99, emptyList(), SplitSource.AUTO_AI)
        assertTrue(SplitVerifier.problems(p, byId).any { it.contains("not from a person") })
    }
}
