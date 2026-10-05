package com.pft.financetracker

import com.pft.financetracker.domain.refunds.RefundPair
import com.pft.financetracker.domain.refunds.RefundBadges
import com.pft.financetracker.domain.refunds.RefundMatch
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RefundBadgesTest {
    private val links = listOf(
        RefundPair(linkId = 1, refundId = 10, debitId = 20, kind = RefundMatch.Kind.REVERSAL, amountPaise = 50_000),
        RefundPair(linkId = 2, refundId = 11, debitId = 21, kind = RefundMatch.Kind.REFUND, amountPaise = 40_000),
        RefundPair(linkId = 3, refundId = 12, debitId = 21, kind = RefundMatch.Kind.REFUND, amountPaise = 10_000),
    )
    private val b = RefundBadges.of(links)

    @Test fun bothSidesOfAReversalAreHiddenByDefault() = assertEquals(setOf(10L, 20L), b.hiddenByDefault)

    @Test fun aReversalIsTaggedOnBothSides() {
        assertEquals("Reversed", b.tag(10)); assertEquals("Reversed", b.tag(20))
    }

    @Test fun aRefundedPurchaseShowsHowMuchCameBack() = assertEquals(50_000L, b.refundedPaise(21))

    @Test fun aRefundedPurchaseIsTagged() = assertEquals("Refunded", b.tag(21))

    @Test fun anUnlinkedRowHasNoTag() = assertNull(b.tag(99))

    @Test fun eachTransactionKnowsItsPairs() {
        assertEquals(listOf(2L, 3L), b.pairsOf(21).map { it.linkId })
        assertEquals(listOf(2L), b.pairsOf(11).map { it.linkId })
    }
}
