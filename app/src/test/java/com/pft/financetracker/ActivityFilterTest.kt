package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.ui.model.ActivityFilter
import com.pft.financetracker.ui.model.DayNet
import com.pft.financetracker.ui.model.accountLabel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Activity's filter sheet: each filter, how they combine with search, and the count on its badge. */
class ActivityFilterTest {
    private val day = 86_400_000L
    private val t0 = 1_790_000_000_000L

    private fun tx(
        id: Long, merchant: String, paise: Long, cat: Category = Category.FOOD, flow: Flow = Flow.EXPENSE,
        type: TransactionType = TransactionType.DEBIT, bank: String? = "HDFC Bank", acct: String? = "1234", at: Long = t0,
    ) = Transaction(id = id, amountPaise = paise, type = type, merchant = merchant, category = cat, timestamp = at,
        bankName = bank, accountRef = acct, source = Transaction.Source.SMS, flow = flow)

    private val zomato = tx(1, "Zomato", 120_00)
    private val amazon = tx(2, "Amazon", 1_299_00, Category.SHOPPING, bank = "ICICI Bank", acct = "9012", at = t0 - 2 * day)
    private val salary = tx(3, "Acme Ltd", 45_000_00, Category.INCOME, Flow.INCOME, TransactionType.CREDIT, bank = "SBI", acct = "5678", at = t0 - day)
    private val selfMove = tx(4, "To own account", 10_000_00, Category.TRANSFER, Flow.TRANSFER, bank = "SBI", acct = "5678", at = t0 - day)
    private val cash = tx(5, "Tea", 30_00, bank = null, acct = null)
    private val all = listOf(zomato, amazon, salary, selfMove, cash)

    @Test fun noFilterShowsEverythingExceptHiddenReversals() {
        assertEquals(all, ActivityFilter().apply(all, "", hidden = emptySet()))
        assertEquals(all - amazon, ActivityFilter().apply(all, "", hidden = setOf(2L)))
        assertEquals(all, ActivityFilter(showReversed = true).apply(all, "", hidden = setOf(2L)))
    }

    @Test fun categoriesAccountsAndFlowsEachNarrowTheList() {
        assertEquals(listOf(zomato, cash), ActivityFilter(categories = setOf(Category.FOOD)).apply(all, "", emptySet()))
        assertEquals(listOf(salary, selfMove), ActivityFilter(accounts = setOf("SBI ••5678")).apply(all, "", emptySet()))
        assertEquals(listOf(selfMove), ActivityFilter(flows = setOf(Flow.TRANSFER)).apply(all, "", emptySet()))
        // Within one group the choices widen; across groups they narrow.
        assertEquals(listOf(zomato, amazon, cash), ActivityFilter(categories = setOf(Category.FOOD, Category.SHOPPING)).apply(all, "", emptySet()))
        assertEquals(listOf(zomato), ActivityFilter(categories = setOf(Category.FOOD), accounts = setOf("HDFC Bank ••1234")).apply(all, "", emptySet()))
    }

    @Test fun dateRangeIncludesBothEndDays() {
        val f = ActivityFilter(fromDay = t0 - 2 * day, toDay = t0 - day)
        assertEquals(listOf(amazon, salary, selfMove), f.apply(all, "", emptySet()))
    }

    @Test fun searchStillMatchesNamesBanksAndAmountsAlongsideFilters() {
        assertEquals(listOf(amazon), ActivityFilter().apply(all, "amaz", emptySet()))
        assertEquals(listOf(amazon), ActivityFilter().apply(all, "1299", emptySet()))
        assertEquals(listOf(salary, selfMove), ActivityFilter().apply(all, "sbi", emptySet()))
        assertEquals(emptyList<Transaction>(), ActivityFilter(categories = setOf(Category.FOOD)).apply(all, "amazon", emptySet()))
    }

    @Test fun badgeCountsEveryChoice() {
        assertEquals(0, ActivityFilter().count)
        assertEquals(2, ActivityFilter(categories = setOf(Category.FOOD), accounts = setOf("SBI ••5678")).count)
        assertEquals(3, ActivityFilter(flows = setOf(Flow.INCOME, Flow.REFUND), fromDay = t0, toDay = t0).count)
        assertEquals(1, ActivityFilter(showReversed = true).count)
        assertTrue(ActivityFilter(showReversed = true).isActive)
    }

    @Test fun removingOneTagLeavesTheRest() {
        val f = ActivityFilter(categories = setOf(Category.FOOD, Category.SHOPPING), accounts = setOf("SBI ••5678"))
        assertEquals(setOf(Category.SHOPPING), f.toggle(Category.FOOD).categories)
        assertEquals(emptySet<String>(), f.toggleAccount("SBI ••5678").accounts)
        assertEquals(setOf(Flow.CASH), ActivityFilter().toggle(Flow.CASH).flows)
        assertEquals(ActivityFilter(), f.cleared())
    }

    @Test fun accountsAreOfferedMostUsedFirst() {
        assertEquals(listOf("SBI ••5678", "HDFC Bank ••1234", "ICICI Bank ••9012"), ActivityFilter.accountsIn(all))
        assertEquals("HDFC Bank ••1234", accountLabel(zomato))
        assertNull(accountLabel(cash))
    }

    @Test fun aDaysNetIsMoneyInMinusMoneyOutWithMovesLeftOut() {
        assertEquals(45_000_00L, DayNet.of(listOf(salary, selfMove)))
        assertEquals(-150_00L, DayNet.of(listOf(zomato, cash)))
        assertEquals(0L, DayNet.of(listOf(selfMove)))
        val refund = tx(9, "Amazon", 450_00, Category.SHOPPING, Flow.REFUND, TransactionType.CREDIT)
        assertEquals(-849_00L, DayNet.of(listOf(amazon, refund)))
    }
}
