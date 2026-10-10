package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.model.ActivityFilter
import com.pft.financetracker.ui.model.AmountInput
import com.pft.financetracker.domain.ledger.FlowRules
import com.pft.financetracker.ui.model.PickerDate
import com.pft.financetracker.ui.model.ReviewBank
import com.pft.financetracker.ui.model.Selection
import com.pft.financetracker.ui.model.TypedAmount
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset

/** The editor's rules: money in and out, the date picker's days, the amount field, quick-add's figure, Review's bank. */
class EditFormTest {
    private val india = ZoneId.of("Asia/Kolkata")

    private fun tx(type: TransactionType, flow: Flow) = Transaction(
        amountPaise = 500_00, type = type, merchant = "Someone", category = Category.OTHER, timestamp = 0L,
        bankName = null, accountRef = null, source = Transaction.Source.MANUAL, flow = flow,
    )

    // ---- Money in / out ----

    @Test fun moneyInNeverCountsAsSpendNorMoneyOutAsIncome() {
        assertEquals(Flow.INCOME, FlowRules.normalise(tx(TransactionType.CREDIT, Flow.EXPENSE)).flow)
        assertEquals(Flow.INCOME, FlowRules.normalise(tx(TransactionType.CREDIT, Flow.CASH)).flow)
        assertEquals(Flow.EXPENSE, FlowRules.normalise(tx(TransactionType.DEBIT, Flow.INCOME)).flow)
        assertEquals(Flow.EXPENSE, FlowRules.normalise(tx(TransactionType.DEBIT, Flow.REFUND)).flow)
    }

    @Test fun choicesThatFitBothDirectionsAreKept() {
        val refund = tx(TransactionType.CREDIT, Flow.REFUND)
        assertSame(refund, FlowRules.normalise(refund))
        assertEquals(Flow.TRANSFER, FlowRules.fit(TransactionType.CREDIT, Flow.TRANSFER))
        assertEquals(Flow.SETTLEMENT, FlowRules.fit(TransactionType.DEBIT, Flow.SETTLEMENT))
        assertEquals(Flow.CASH, FlowRules.fit(TransactionType.DEBIT, Flow.CASH))
        assertTrue(FlowRules.options(TransactionType.DEBIT).none { it == Flow.INCOME || it == Flow.REFUND })
        assertTrue(FlowRules.options(TransactionType.CREDIT).none { it == Flow.EXPENSE || it == Flow.CASH })
    }

    // ---- Date picker ----

    private fun local(y: Int, m: Int, d: Int, h: Int, min: Int) = LocalDateTime.of(y, m, d, h, min).atZone(india).toInstant().toEpochMilli()
    private fun utcMidnight(y: Int, m: Int, d: Int) = LocalDateTime.of(y, m, d, 0, 0).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test fun anEarlyMorningPaymentOpensThePickerOnItsOwnDay() {
        // 02:30 in India is still the day before in UTC; the picker must show 6 Oct, not 5 Oct.
        assertEquals(utcMidnight(2026, 10, 6), PickerDate.toPicker(local(2026, 10, 6, 2, 30), india))
        assertEquals(utcMidnight(2026, 10, 6), PickerDate.toPicker(local(2026, 10, 6, 23, 50), india))
    }

    @Test fun pickingADayKeepsTheTimeOfDay() {
        val original = local(2026, 10, 6, 2, 30)
        assertEquals(local(2026, 10, 8, 2, 30), PickerDate.fromPicker(utcMidnight(2026, 10, 8), original, india))
        // Picking the same day changes nothing.
        assertEquals(original, PickerDate.fromPicker(PickerDate.toPicker(original, india), original, india))
    }

    // ---- Amount field ----

    @Test fun moreThanTwoDecimalsIsRefusedNotRounded() {
        assertEquals(AmountInput.Parsed.TooManyDecimals, AmountInput.parse("12.345"))
        assertNull(AmountInput.paiseOf("12.345"))
        assertEquals("Use at most two digits after the point.", AmountInput.problem("12.345"))
    }

    @Test fun aMalformedAmountSaysSo() {
        assertEquals(AmountInput.Parsed.Invalid, AmountInput.parse("1.2.3"))
        assertEquals("Enter a valid amount.", AmountInput.problem("1.2.3"))
        assertEquals(AmountInput.Parsed.Invalid, AmountInput.parse("."))
        assertEquals("Enter an amount above ₹0.", AmountInput.problem("0"))
        assertEquals("Enter an amount above ₹0.", AmountInput.problem("0.00"))
        assertNull(AmountInput.problem(""))
    }

    @Test fun amountsAreReadExactly() {
        assertEquals(1250L, AmountInput.paiseOf("12.5"))
        assertEquals(50L, AmountInput.paiseOf(".5"))
        assertEquals(1_05_000_00L, AmountInput.paiseOf("1,05,000"))
        assertEquals(10L, AmountInput.paiseOf("0.10"))
    }

    @Test fun commasDoNotCountTowardTheLimit() {
        val pasted = "12,34,56,789.12"
        assertEquals(pasted, AmountInput.clean(pasted))
        assertEquals(12_34_56_789_12L, AmountInput.paiseOf(AmountInput.clean(pasted)))
        assertEquals(AmountInput.MAX_CHARS, AmountInput.clean("12345678901234567890").length)
        assertEquals("1,500.5", AmountInput.clean("₹1,500.5 "))
    }

    // ---- Quick-add figure ----

    @Test fun theQuickAddFigureShowsWhatWasTyped() {
        assertEquals("₹0", TypedAmount.show(""))
        assertEquals("₹0.", TypedAmount.show("0."))
        assertEquals("₹12.0", TypedAmount.show("12.0"))
        assertEquals("₹12.", TypedAmount.show("12."))
        assertEquals("₹1,23,456", TypedAmount.show("123456"))
        assertEquals("₹1,00,000.5", TypedAmount.show("100000.5"))
    }

    // ---- Review's bank ----

    @Test fun aReviewMessageNamesItsBankNotItsSenderCode() {
        assertEquals("HDFC Bank", ReviewBank.of("AD-HDFCBK", "Rs 500 debited from a/c 1234"))
        assertNull(ReviewBank.of("AD-QWERTY", "Amount 500 received"))
    }

    // ---- Activity's filter and selection ----

    @Test fun theActivityFilterSurvivesBeingSaved() {
        val f = ActivityFilter(
            categories = setOf(Category.FOOD, Category.SHOPPING), accounts = setOf("HDFC Bank ••1234", "SBI"),
            flows = setOf(Flow.REFUND), fromDay = 1_790_000_000_000L, toDay = null, showReversed = true,
        )
        assertEquals(f, ActivityFilter.decode(f.encode()))
        assertEquals(ActivityFilter(), ActivityFilter.decode(ActivityFilter().encode()))
        assertEquals(ActivityFilter(), ActivityFilter.decode(listOf("broken")))
    }

    @Test fun aSelectionKeepsOnlyRowsStillShown() {
        val s = Selection(setOf(1L, 2L, 3L))
        assertEquals(setOf(1L, 3L), s.keepOnly(setOf(1L, 3L, 4L)).ids)
        assertSame(s, s.keepOnly(setOf(1L, 2L, 3L, 4L)))
        assertFalse(s.keepOnly(emptySet()).active)
    }
}
