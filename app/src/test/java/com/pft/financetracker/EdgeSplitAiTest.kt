package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType.CREDIT
import com.pft.financetracker.domain.model.TransactionType.DEBIT
import com.pft.financetracker.domain.split.SplitAiRequest
import com.pft.financetracker.domain.split.SplitDecider
import com.pft.financetracker.domain.split.SplitKind
import com.pft.financetracker.domain.split.SplitTx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.2.1 edge cases for AI answers and what is sent: odd JSON, many people, leaks through names. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EdgeSplitAiTest {
    private val t0 = java.util.Calendar.getInstance().apply { set(2026, 8, 19, 20, 10, 0) }.timeInMillis
    private val h = 3_600_000L
    private val dinner = SplitTx(1, 3_000_00, DEBIT, t0, "Toit Brewpub", Category.FOOD, false)
    private val cab = SplitTx(2, 600_00, DEBIT, t0 + h, "Uber", Category.TRANSPORT, false)
    private val rahul = SplitTx(10, 1_000_00, CREDIT, t0 + 15 * h, "Rahul Sharma", Category.INCOME, true)
    private val priya = SplitTx(11, 1_000_00, CREDIT, t0 + 16 * h, "Priya Nair", Category.INCOME, true)
    private val byId = listOf(dinner, cab, rahul, priya).associateBy { it.id }
    private fun req() = SplitAiRequest.build(listOf(dinner, cab), listOf(rahul, priya))
    private val good = """{"groups":[{"payment":"P1","kind":"payback","allocations":[{"incoming":"C1","amount":1000},{"incoming":"C2","amount":1000}],"people":3,"confidence":90,"reason":"Person A and Person B paid a third each"}]}"""

    @Test fun aFencedAnswerWithTextAroundItIsRead() {
        val p = req().parse("Sure! Here is the JSON:\n```json\n$good\n```\nLet me know if you need more.")
        assertNotNull(p); assertEquals(1, p!!.size)
    }

    @Test fun anAnswerWithoutAGroupsListIsNotAnAnswer() {
        assertNull(req().parse("{}"))
        assertNull(req().parse(""))
        assertNull(req().parse("[]"))
        assertNull(req().parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amo"""))
        assertEquals(emptyList<Any>(), req().parse("""{"groups":[]}"""))
    }

    @Test fun oddValuesNeverApplyAnythingWrong() {
        val answers = listOf(
            """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":1e20}],"confidence":99}]}""",
            """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":-0}],"confidence":99}]}""",
            """{"groups":[{"payment":"P99","allocations":[{"incoming":"C1","amount":1000}],"confidence":99}]}""",
            """{"groups":[{"payment":"P1","allocations":[{"incoming":"C99","amount":1000}],"confidence":99}]}""",
            """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":1000},{"incoming":"C1","amount":1000}],"confidence":99}]}""",
            """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":1000}],"confidence":99},{"payment":"P2","allocations":[{"incoming":"C1","amount":500}],"confidence":99}]}""",
            """{"groups":[{"payment":"P2","allocations":[{"incoming":"C1","amount":1000}],"confidence":99}]}""",
        )
        for (a in answers) {
            val parsed = req().parse(a).orEmpty()
            val decided = SplitDecider.decide(emptyList(), parsed, setOf(1L, 2L), byId)
            for (d in decided) {
                val pay = byId[d.proposal.paymentId]!!
                assertTrue(a, d.proposal.allocatedPaise < pay.amountPaise)
                d.proposal.allocations.forEach { al -> assertTrue(a, al.paise in 1..byId[al.txId]!!.amountPaise) }
            }
            // Every transfer used is used in full across the decisions.
            decided.flatMap { it.proposal.allocations }.groupBy { it.txId }.forEach { (id, parts) -> assertEquals(a, byId[id]!!.amountPaise, parts.sumOf { it.paise }) }
        }
    }

    @Test fun stringNumbersAndOutOfRangeConfidence() {
        val p = req().parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":"1000"},{"incoming":"C2","amount":1000}],"confidence":"250"}]}""")!!.single()
        assertEquals(2_000_00L, p.allocatedPaise)
        assertEquals(100, p.confidence)
        assertEquals(0, req().parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":1000}],"confidence":"high"}]}""")!!.single().confidence)
    }

    @Test fun twoGroupsForOnePaymentGiveAtMostOneDecision() {
        val a = """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":1000},{"incoming":"C2","amount":1000}],"confidence":90},{"payment":"P1","allocations":[{"incoming":"C1","amount":1000}],"confidence":95}]}"""
        val decided = SplitDecider.decide(emptyList(), req().parse(a), setOf(1L, 2L), byId)
        assertTrue(decided.count { it.proposal.paymentId == 1L } <= 1)
    }

    @Test fun anAiAdvanceIsNeverAppliedAutomatically() {
        val early = SplitTx(12, 1_000_00, CREDIT, t0 - 5 * h, "Amit Desai", Category.INCOME, true)
        val r = SplitAiRequest.build(listOf(dinner), listOf(early))
        val parsed = r.parse("""{"groups":[{"payment":"P1","kind":"advance","allocations":[{"incoming":"C1","amount":1000}],"confidence":100}]}""")
        val d = SplitDecider.decide(emptyList(), parsed, setOf(1L), mapOf(1L to dinner, 12L to early))
        assertTrue(d.none { it.auto })
        assertTrue(d.all { it.proposal.kind == SplitKind.ADVANCE })
    }

    @Test fun anAnswerUsingATransferOutsideTheViewIsThrownAway() {
        // C2 (Priya) exists in the request but is not in what the engine may touch (frozen, manual, settled).
        val d = SplitDecider.decide(emptyList(), req().parse(good), setOf(1L, 2L), byId - 11L)
        assertTrue(d.isEmpty())
    }

    @Test fun namesAreRestoredCorrectlyWithMoreThanTwentySixPeople() {
        val people = (0 until 28).map { i -> SplitTx(100L + i, 100_00, CREDIT, t0 + h + i * 60_000L, "Frnd" + ('a' + i / 26) + ('a' + i % 26) + "x", Category.INCOME, true) }
        val r = SplitAiRequest.build(listOf(SplitTx(1, 2_900_00, DEBIT, t0, "Hall", Category.ENTERTAINMENT, false)), people)
        assert(r.json.contains("Person AB"))
        val p = r.parse("""{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":100}],"confidence":50,"reason":"Person AB and Person A paid"}]}""")!!.single()
        assertEquals("Frndbbx and Frndaax paid", p.reasons.single())
    }

    // ---- 19. What reaches OpenAI ------------------------------------------------------------------------------

    @Test fun nothingPersonalLeaksIntoTheRequest() {
        val secrets = listOf("Rahul 9876543210", "rahul.sharma@okhdfcbank", "A/C XX1234 ACME", "priya.nair@gmail.com", "ABCDE1234F PAN", "IFSC HDFC0001234")
        val incoming = secrets.mapIndexed { i, s -> SplitTx(20L + i, 500_00, CREDIT, t0 + (i + 1) * h, s, Category.INCOME, true) }
        val pays = listOf(SplitTx(1, 3_500_00, DEBIT, t0, "Rahul's Birthday at Toit 9876543210", Category.FOOD, false), SplitTx(2, 800_00, DEBIT, t0 + h, "Anita Rao", Category.OTHER, true))
        val json = SplitAiRequest.build(pays, incoming).json.lowercase()
        for (bad in listOf("9876543210", "rahul", "priya", "okhdfcbank", "gmail", "xx1234", "acme", "abcde1234f", "hdfc0001234", "toit", "birthday", "anita", "@")) {
            assertFalse("leaked $bad: $json", json.contains(bad))
        }
    }
}
