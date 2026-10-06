package com.pft.financetracker

import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.widget.WidgetSnapshot
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class WidgetSnapshotTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val now = LocalDate.of(2026, 10, 15).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()
    private fun spend(paise: Long, cat: Category = Category.FOOD, daysAgo: Long = 1) = Transaction(amountPaise = paise, type = TransactionType.DEBIT,
        merchant = "X", category = cat, timestamp = now - daysAgo * 86_400_000L, bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.EXPENSE)
    private val rent = Bill(id = 1, name = "Rent", amountPaise = 25_000_00, dueDay = 20, keyword = null)

    @Test fun showsMonthToDateSpendBudgetLeftAndTheNextBill() {
        val s = WidgetSnapshot.build(listOf(spend(1_200_00), spend(300_00, daysAgo = 40)), listOf(Budget(Category.FOOD, 5_000_00)),
            listOf(rent to BillState.Upcoming(LocalDate.of(2026, 10, 20), 5)), hideAmounts = false, now = now, zone = zone)
        assertEquals("₹1,200", s.spent)
        assertEquals("₹3,800 left of budgets", s.budgetLine)
        assertEquals("Rent · 20 Oct", s.nextBill)
    }

    @Test fun overBudgetSaysSo() {
        val s = WidgetSnapshot.build(listOf(spend(6_000_00)), listOf(Budget(Category.FOOD, 5_000_00)), emptyList(), hideAmounts = false, now = now, zone = zone)
        assertEquals("₹1,000 over budget", s.budgetLine)
    }

    @Test fun withoutBudgetsThereIsNoBudgetLine() =
        assertNull(WidgetSnapshot.build(listOf(spend(100_00)), emptyList(), emptyList(), hideAmounts = false, now = now, zone = zone).budgetLine)

    @Test fun overdueBillsComeFirst() {
        val phone = Bill(id = 2, name = "Airtel", amountPaise = 799_00, dueDay = 10, keyword = "airtel")
        val s = WidgetSnapshot.build(emptyList(), emptyList(), listOf(rent to BillState.Upcoming(LocalDate.of(2026, 10, 20), 5), phone to BillState.Overdue(LocalDate.of(2026, 10, 10), 5)),
            hideAmounts = false, now = now, zone = zone)
        assertEquals("Airtel · overdue", s.nextBill)
    }

    @Test fun hiddenAmountsShowNoFigures() {
        val s = WidgetSnapshot.build(listOf(spend(1_200_00)), listOf(Budget(Category.FOOD, 5_000_00)), emptyList(), hideAmounts = true, now = now, zone = zone)
        assertEquals("₹••••", s.spent)
        assertFalse(s.budgetLine!!.any { it.isDigit() })
        assertTrue(s.budgetLine!!.contains("left"))
    }

    @Test fun theWidgetShowsWholeRupeesSoTheFigureFitsTheSmallestWidget() {
        val s = WidgetSnapshot.build(listOf(spend(1_23_456_78)), emptyList(), emptyList(), hideAmounts = false, now = now, zone = zone)
        assertEquals("₹1,23,457", s.spent)
    }
}
