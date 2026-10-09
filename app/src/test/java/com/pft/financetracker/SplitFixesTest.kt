package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.Allocation
import com.pft.financetracker.domain.split.PeopleDraft
import com.pft.financetracker.domain.split.Person
import com.pft.financetracker.domain.split.SettleMatch
import com.pft.financetracker.domain.split.Split
import com.pft.financetracker.domain.split.SplitCalculator
import com.pft.financetracker.domain.split.SplitDecider
import com.pft.financetracker.domain.split.SplitDecision
import com.pft.financetracker.domain.split.SplitKind
import com.pft.financetracker.domain.split.SplitMode
import com.pft.financetracker.domain.split.SplitProposal
import com.pft.financetracker.domain.split.SplitShare
import com.pft.financetracker.domain.split.SplitSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** v1.5 split fixes that need no database: removing a person, matching payments, grouping, balances. */
class SplitFixesTest {
    private val day = 86_400_000L
    private val t0 = 1_790_000_000_000L

    // ---- Removing a person from a new split ---------------------------------------------------------------------

    private val four = PeopleDraft(
        people = listOf("Me", "Asha", "Bala", "Chitra"),
        shareWeights = listOf("1", "2", "3", "4"),
        customAmounts = listOf("100", "200", "300", "400"),
        itemAssignments = listOf(setOf(0, 2), setOf(1, 3), setOf(3), emptySet()),
        payer = 3,
    )

    @Test fun removingSomeoneKeepsEveryoneElsesNumbersAndItems() {
        val d = four.remove(1)
        assertEquals(listOf("Me", "Bala", "Chitra"), d.people)
        assertEquals(listOf("1", "3", "4"), d.shareWeights)
        assertEquals(listOf("100", "300", "400"), d.customAmounts)
        assertEquals(listOf(setOf(0, 1), setOf(2), setOf(2), emptySet<Int>()), d.itemAssignments)
        assertEquals("Chitra still paid", 2, d.payer)
    }

    @Test fun removingWhoPaidMakesMeThePayer() = assertEquals(0, four.remove(3).payer)

    @Test fun meAndOutOfRangeCantBeRemoved() {
        assertEquals(four, four.remove(0))
        assertEquals(four, four.remove(9))
    }

    // ---- Matching payments ------------------------------------------------------------------------------------

    private var nextId = 1L
    private fun tx(rupees: Double, type: TransactionType, who: String, t: Long, flow: Flow = if (type == TransactionType.DEBIT) Flow.EXPENSE else Flow.INCOME,
                   source: Transaction.Source = Transaction.Source.SMS, kind: CounterpartyKind = CounterpartyKind.PERSON) =
        Transaction(id = nextId++, amountPaise = Math.round(rupees * 100), type = type, merchant = who, category = Category.OTHER, timestamp = t,
            bankName = null, accountRef = null, source = source, flow = flow, counterpartyKind = kind)

    @Test fun aFriendsRoundUpIsOfferedForTheirShare() {
        val roundUp = tx(1_030.0, TransactionType.CREDIT, "Rahul Sharma", t0 + day)
        val tooMuch = tx(1_200.0, TransactionType.CREDIT, "Rahul Sharma", t0 + day)
        val found = SettleMatch.incoming(listOf(roundUp, tooMuch), t0, 1_028_33, emptySet())
        assertEquals(listOf(roundUp.id), found.map { it.id })
        assertEquals(roundUp.id, SettleMatch.forTypedAmount(found, 1_028_33)?.id)
        assertNull(SettleMatch.forTypedAmount(found, 500_00))
    }

    @Test fun payingBackOffersMyPaymentToThePayerFirst() {
        val other = tx(500.0, TransactionType.DEBIT, "Swiggy", t0 + day, kind = CounterpartyKind.ORGANISATION)
        val toAsha = tx(520.0, TransactionType.DEBIT, "Asha Menon", t0 + 2 * day)
        val shareRow = tx(500.0, TransactionType.DEBIT, "Dinner", t0, source = Transaction.Source.SPLIT)
        val found = SettleMatch.outgoing(listOf(other, toAsha, shareRow), t0, 500_00, "Asha", emptySet())
        assertEquals(listOf(toAsha.id, other.id), found.map { it.id })
    }

    @Test fun paidWithPicksOnlyAClearMatch() {
        val exact = tx(3_000.0, TransactionType.DEBIT, "Toit", t0 + 3_600_000L, kind = CounterpartyKind.ORGANISATION)
        val near = tx(3_040.0, TransactionType.DEBIT, "Toit", t0, kind = CounterpartyKind.ORGANISATION)
        val far = tx(3_000.0, TransactionType.DEBIT, "Toit", t0 - 5 * day, kind = CounterpartyKind.ORGANISATION)
        val used = tx(3_000.0, TransactionType.DEBIT, "Toit", t0, kind = CounterpartyKind.ORGANISATION)
        val cands = SettleMatch.paidWith(listOf(exact, near, far, used), 3_000_00, t0, setOf(used.id))
        assertEquals(listOf(exact.id, far.id, near.id), cands.map { it.id })
        assertEquals(exact.id, SettleMatch.bestPaidWith(cands, 3_000_00, t0)?.id)
        assertNull("five days off is not a clear match", SettleMatch.bestPaidWith(listOf(far), 3_000_00, t0))
        assertEquals(near.id, SettleMatch.bestPaidWith(listOf(near, far), 3_000_00, t0)?.id)
        assertNull(SettleMatch.bestPaidWith(cands, null, t0))
    }

    // ---- Splits sharing a transfer stand together ----------------------------------------------------------------

    private fun decision(payment: Long, auto: Boolean, vararg transfers: Long) = SplitDecision(
        SplitProposal(payment, SplitKind.PAYBACK, transfers.map { Allocation(it, 100_00) }, null, null, if (auto) 90 else 60, emptyList(), SplitSource.AUTO_LOCAL), auto,
    )

    @Test fun aSuggestionSharingATransferHoldsBackTheWholeChain() {
        val out = SplitDecider.groupShared(listOf(decision(1, true, 10, 11), decision(2, false, 11, 12), decision(3, true, 12), decision(4, true, 13)))
        assertEquals(mapOf(1L to false, 2L to false, 3L to false, 4L to true), out.associate { it.proposal.paymentId to it.auto })
    }

    // ---- Balances by person --------------------------------------------------------------------------------------

    private fun split(id: Long, vararg friends: String, owed: Long = 100_00) = Split(
        id = id, title = "s$id", totalPaise = owed * (friends.size + 1), date = 0, mode = SplitMode.EQUAL, payerIndex = 0,
        people = listOf(Person(name = "Me", isMe = true)) + friends.map { Person(name = it) },
        shares = listOf(SplitShare(personIndex = 0, amountPaise = owed, settledPaise = owed)) + friends.indices.map { SplitShare(personIndex = it + 1, amountPaise = owed) },
        createdAt = id,
    )

    @Test fun oneFriendIsOneBalanceHoweverTheNameWasTyped() {
        val bal = SplitCalculator.balances(listOf(split(1, "Asha"), split(2, "asha "), split(3, " ASHA")))
        assertEquals(1, bal.size)
        assertEquals("Asha", bal.single().name)
        assertEquals(300_00L, bal.single().netPaise)
    }

    @Test fun placeholderPeopleInDifferentSplitsAreDifferentPeople() {
        val bal = SplitCalculator.balances(listOf(split(1, "Person 2"), split(2, "Person 2")))
        assertEquals(2, bal.size)
        bal.forEach { assertEquals(100_00L, it.netPaise) }
    }
}
