package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.model.AmountKeys
import com.pft.financetracker.ui.model.QuickAddDraft
import com.pft.financetracker.ui.model.RecentCategories
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The quick-add sheet: the number pad's rules, what Save writes, and which categories come first. */
class QuickAddDraftTest {
    private fun typed(vararg keys: Char): String = keys.fold("") { acc, k -> AmountKeys.press(acc, k) ?: acc }

    @Test fun thePadBuildsAnAmountAndRefusesWhatCannotBeOne() {
        assertEquals("340", typed('3', '4', '0'))
        assertEquals("12.5", typed('1', '2', '.', '5'))
        // A leading zero is replaced; a dot first becomes "0."
        assertEquals("7", typed('0', '7'))
        assertEquals("0.5", typed('.', '5'))
        // Refused keys return null so the pad can buzz.
        assertNull(AmountKeys.press("12.5", '.'))
        assertNull(AmountKeys.press("12.50", '9'))
        assertNull(AmountKeys.press("99999999", '9'))
        assertNull(AmountKeys.press("", '0'))
    }

    @Test fun backspaceAndClear() {
        assertEquals("34", AmountKeys.backspace("340"))
        assertEquals("", AmountKeys.backspace(""))
        assertEquals("12", AmountKeys.backspace("12."))
    }

    @Test fun saveWritesAManualExpenseNamedByTheNoteOrTheCategory() {
        val now = 1_790_000_000_000L
        val t = QuickAddDraft(amount = "340", category = Category.FOOD, note = "  Lunch ").toTransaction(now, cashCounted = true)!!
        assertEquals(34_000L, t.amountPaise)
        assertEquals(TransactionType.DEBIT, t.type)
        assertEquals("Lunch", t.merchant)
        assertEquals(Category.FOOD, t.category)
        assertEquals(Flow.EXPENSE, t.flow)
        assertEquals(Transaction.Source.MANUAL, t.source)
        assertEquals(now, t.timestamp)
        assertNull(t.bankName)
        assertNull(t.note)
        assertEquals("Shopping", QuickAddDraft(amount = "99", category = Category.SHOPPING).toTransaction(now, true)!!.merchant)
    }

    @Test fun cashPaidForByACountedWithdrawalIsAMoveNotNewSpend() {
        val counted = QuickAddDraft(amount = "50", category = Category.FOOD, cash = true).toTransaction(0, cashCounted = true)!!
        assertEquals(Flow.TRANSFER, counted.flow)
        assertEquals("Cash", counted.bankName)
        assertEquals("Paid in cash", counted.note)
        val notCounted = QuickAddDraft(amount = "50", category = Category.FOOD, cash = true).toTransaction(0, cashCounted = false)!!
        assertEquals(Flow.EXPENSE, notCounted.flow)
    }

    @Test fun nothingIsSavedWithoutAnAmountOrACategory() {
        assertFalse(QuickAddDraft(amount = "", category = Category.FOOD).canSave)
        assertFalse(QuickAddDraft(amount = "0", category = Category.FOOD).canSave)
        assertFalse(QuickAddDraft(amount = "40").canSave)
        assertNull(QuickAddDraft(amount = "40").toTransaction(0, true))
        assertTrue(QuickAddDraft(amount = "0.5", category = Category.FOOD).canSave)
    }

    @Test fun categoriesYouAddedLastComeFirst() {
        fun manual(cat: Category, at: Long) = Transaction(amountPaise = 1, type = TransactionType.DEBIT, merchant = "x", category = cat,
            timestamp = at, bankName = null, accountRef = null, source = Transaction.Source.MANUAL, flow = Flow.EXPENSE)
        val sms = manual(Category.HEALTH, 99).copy(source = Transaction.Source.SMS)
        val order = RecentCategories.order(listOf(manual(Category.TRANSPORT, 10), manual(Category.FOOD, 30), manual(Category.TRANSPORT, 20), sms))
        assertEquals(listOf(Category.FOOD, Category.TRANSPORT), order.take(2))
        // Everything else follows in the usual order, once each, and never income or transfers.
        assertEquals(RecentCategories.choices.toSet(), order.toSet())
        assertEquals(order.size, order.distinct().size)
        assertFalse(Category.INCOME in order)
        assertFalse(Category.TRANSFER in order)
        assertEquals(RecentCategories.choices, RecentCategories.order(emptyList()))
    }
}
