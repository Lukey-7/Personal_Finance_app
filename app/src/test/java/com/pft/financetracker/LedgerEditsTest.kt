package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.model.DeleteWithUndo
import com.pft.financetracker.ui.model.Selection
import com.pft.financetracker.domain.ledger.recategorise
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Swipe to delete with undo, and long-press selection to recategorise several rows at once. */
class LedgerEditsTest {
    private fun tx(id: Long, cat: Category = Category.OTHER, edited: Boolean = false) = Transaction(
        id = id, amountPaise = 100_00, type = TransactionType.DEBIT, merchant = "M$id", category = cat, timestamp = id,
        bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.EXPENSE, userEdited = edited,
    )

    @Test fun aSwipedRowDisappearsAtOnceButIsOnlyDeletedWhenTheUndoChanceEnds() {
        val deleted = mutableListOf<Long>()
        val d = DeleteWithUndo { deleted += it.id }
        d.stage(tx(1))
        assertTrue(d.hides(1))
        assertEquals(emptyList<Long>(), deleted)
        d.commit()
        assertEquals(listOf(1L), deleted)
        assertFalse(d.hides(1))
        assertNull(d.pending)
    }

    @Test fun undoBringsItBackAndDeletesNothing() {
        val deleted = mutableListOf<Long>()
        val d = DeleteWithUndo { deleted += it.id }
        d.stage(tx(1))
        assertEquals(1L, d.undo()?.id)
        d.commit()
        assertEquals(emptyList<Long>(), deleted)
        assertFalse(d.hides(1))
    }

    @Test fun aSecondSwipeFinishesTheFirstSoOnlyTheLatestCanBeUndone() {
        val deleted = mutableListOf<Long>()
        val d = DeleteWithUndo { deleted += it.id }
        d.stage(tx(1))
        d.stage(tx(2))
        assertEquals(listOf(1L), deleted)
        assertTrue(d.hides(2))
        d.undo()
        assertEquals(listOf(1L), deleted)
    }

    @Test fun committingTwiceDeletesOnce() {
        val deleted = mutableListOf<Long>()
        val d = DeleteWithUndo { deleted += it.id }
        d.stage(tx(7)); d.commit(); d.commit()
        assertEquals(listOf(7L), deleted)
    }

    @Test fun recategoriseChangesOnlyTheChosenRowsThatDiffer() {
        val rows = listOf(tx(1, Category.FOOD), tx(2, Category.OTHER), tx(3, Category.OTHER), tx(4, Category.BILLS))
        val changed = recategorise(rows, setOf(1L, 2L, 3L), Category.FOOD)
        assertEquals(listOf(2L, 3L), changed.map { it.id })
        assertTrue(changed.all { it.category == Category.FOOD })
        // A person chose this, so automatic rewrites must leave these rows alone afterwards.
        assertTrue(changed.all { it.userEdited })
        // Nothing else about the row moves: same amount, same flow.
        assertEquals(rows[1].copy(category = Category.FOOD, userEdited = true), changed[0])
    }

    @Test fun selectionTogglesAndEndsWhenEmpty() {
        var s = Selection()
        assertFalse(s.active)
        s = s.toggle(3)
        assertTrue(s.active)
        s = s.toggle(5).toggle(3)
        assertEquals(setOf(5L), s.ids)
        s = s.toggle(5)
        assertFalse(s.active)
        assertEquals(setOf(1L, 2L), Selection().selectAll(listOf(1L, 2L)).ids)
    }
}
