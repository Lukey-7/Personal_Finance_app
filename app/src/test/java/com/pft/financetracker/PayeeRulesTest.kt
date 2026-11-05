package com.pft.financetracker

import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.insights.InsightsEngine.Bucket
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.categorize.PayeeRules
import com.pft.financetracker.domain.ledger.recategorise
import com.pft.financetracker.domain.model.Budget
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CategoryChoice
import com.pft.financetracker.domain.model.CustomCategory
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** v1.6: the category a person gives a payee sticks, and their own categories are counted apart from the built-ins. */
class PayeeRulesTest {
    private val pets = CustomCategory(3, "Pets", Category.SHOPPING)
    private val customs = mapOf(3L to pets)
    private val now = 1_790_000_000_000L
    private val month = Periods.month(0, now)

    private fun t(id: Long, merchant: String, category: Category, amount: Long = 10_000, type: TransactionType = TransactionType.DEBIT,
                  flow: Flow = if (type == TransactionType.DEBIT) Flow.EXPENSE else Flow.INCOME, custom: Long? = null, edited: Boolean = false) =
        Transaction(id = id, amountPaise = amount, type = type, merchant = merchant, category = category, timestamp = month.start + id * 60_000L,
            bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = flow, customCategoryId = custom, userEdited = edited)

    @Test fun aRuleBeatsTheKeywordGuess() {
        val rules = PayeeRules(mapOf("autowala" to "TRANSPORT"))
        assertEquals(CategoryChoice(Category.TRANSPORT), rules.categorise("AUTOWALA ", TransactionType.DEBIT))
        // No rule: the keyword lists still decide.
        assertEquals(CategoryChoice(Category.FOOD), rules.categorise("Swiggy", TransactionType.DEBIT))
    }

    @Test fun aRuleCanPointAtTheirOwnCategory() {
        val rules = PayeeRules(mapOf("supertails" to "custom:3"), customs)
        assertEquals(CategoryChoice(Category.SHOPPING, 3), rules.categorise("Supertails", TransactionType.DEBIT))
        // The category was deleted: the rule says nothing and the keywords decide.
        val gone = PayeeRules(mapOf("supertails" to "custom:9"), customs)
        assertNull(gone.choiceFor("Supertails"))
    }

    @Test fun placeholderNamesNeverMakeOrUseARule() {
        assertNull(PayeeRules.keyOf("Payment (HDFC Bank)"))
        assertNull(PayeeRules.keyOf("Credit (SBI)"))
        assertNull(PayeeRules.keyOf("Card bill (SBI)"))
        assertNull(PayeeRules.keyOf("UPI"))
        assertNull(PayeeRules.keyOf("12"))
        assertEquals("autowala", PayeeRules.keyOf("Autowala"))
    }

    @Test fun spendRulesLeaveMoneyInAlone() {
        val rules = PayeeRules(mapOf("amazon" to "SHOPPING", "acmecorp" to "INCOME"))
        // A refund from Amazon is money in: the spend rule doesn't file it, the keywords (and then the flow) do.
        assertEquals(CategoryChoice(Categorizer.categorize("Amazon", TransactionType.CREDIT)), rules.categorise("Amazon", TransactionType.CREDIT))
        // An income rule only applies to money in.
        assertEquals(CategoryChoice(Category.INCOME), rules.categorise("Acme Corp", TransactionType.CREDIT))
        assertEquals(CategoryChoice(Categorizer.categorize("Acme Corp", TransactionType.DEBIT)), rules.categorise("Acme Corp", TransactionType.DEBIT))
    }

    @Test fun alsoMovableSkipsPersonalCorrectionsOtherPayeesAndTheOtherDirection() {
        val all = listOf(
            t(1, "Autowala", Category.OTHER),
            t(2, "AUTOWALA", Category.OTHER),
            t(3, "Autowala", Category.FOOD, edited = true), // the person filed this one themselves
            t(4, "Autowala", Category.TRANSPORT),           // already there
            t(5, "Autowala", Category.OTHER, type = TransactionType.CREDIT, flow = Flow.REFUND),
            t(6, "Uber", Category.OTHER),
            t(7, "Autowala", Category.OTHER),               // the one just changed
        )
        val ids = PayeeRules.alsoMovable(all, "Autowala", CategoryChoice(Category.TRANSPORT), exceptId = 7).map { it.id }
        assertEquals(listOf(1L, 2L), ids)
    }

    @Test fun movingToTheirOwnCategoryKeepsTheParentForTheMoneyRules() {
        val moved = recategorise(listOf(t(1, "Supertails", Category.SHOPPING)), setOf(1L), CategoryChoice(Category.SHOPPING, 3))
        assertEquals(Category.SHOPPING, moved.single().category)
        assertEquals(3L, moved.single().customCategoryId)
        assertTrue(moved.single().userEdited)
    }

    @Test fun theirOwnCategoryGetsItsOwnLineAndTheTotalsDoNotMove() {
        val list = listOf(
            t(1, "Amazon", Category.SHOPPING, 50_000),
            t(2, "Supertails", Category.SHOPPING, 20_000, custom = 3),
            t(3, "Supertails", Category.SHOPPING, 5_000, custom = 3),
            // A refund from the pet shop reduces Pets, not Shopping.
            t(4, "Supertails", Category.SHOPPING, 1_000, type = TransactionType.CREDIT, flow = Flow.REFUND),
        )
        val books = Books.of(list, customs = customs)
        val s = books.summary(month)
        val shopping = s.byCategory.single { it.customId == null && it.category == Category.SHOPPING }
        val petLine = s.byCategory.single { it.customId == 3L }
        assertEquals(50_000L, shopping.amountPaise)
        assertEquals(24_000L, petLine.amountPaise)
        assertEquals("Pets", petLine.label)
        // Same net spend as with no custom categories at all.
        assertEquals(Books.of(list.map { it.copy(customCategoryId = null) }).summary(month).netSpendPaise, s.netSpendPaise)
        // Each line's drill-down adds up to it.
        assertEquals(24_000L, Books.total(books.payments(month, Bucket.SPEND, Category.SHOPPING, 3), Bucket.SPEND))
        assertEquals(50_000L, Books.total(books.payments(month, Bucket.SPEND, Category.SHOPPING), Bucket.SPEND))
    }

    @Test fun aBudgetForTheirOwnCategoryCountsOnlyItsPayments() {
        val list = listOf(t(1, "Amazon", Category.SHOPPING, 50_000), t(2, "Supertails", Category.SHOPPING, 20_000, custom = 3))
        val status = Books.of(list, customs = customs).budgets(listOf(Budget(Category.SHOPPING, 10_000, customId = 3), Budget(Category.SHOPPING, 100_000)), month)
        assertEquals(20_000L, status.single { it.budget.customId == 3L }.spentPaise)
        assertEquals(50_000L, status.single { it.budget.customId == null }.spentPaise)
    }

    @Test fun choiceKeysRoundTrip() {
        assertEquals("FOOD", CategoryChoice(Category.FOOD).key)
        assertEquals("custom:3", CategoryChoice(Category.SHOPPING, 3).key)
        assertEquals(CategoryChoice(Category.SHOPPING, 3), CategoryChoice.fromKey("custom:3", customs))
        assertEquals(CategoryChoice(Category.OTHER), CategoryChoice.fromKey("custom:44", customs))
        assertEquals(CategoryChoice(Category.FOOD), CategoryChoice.fromKey("FOOD", customs))
        assertEquals("Pets", CategoryChoice(Category.SHOPPING, 3).label(customs))
        assertEquals("Shopping", CategoryChoice(Category.SHOPPING, 9).label(customs))
    }
}
