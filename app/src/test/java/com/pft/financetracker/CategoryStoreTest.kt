package com.pft.financetracker

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.categories.CategoryStore
import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.ledger.Ledger
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.refunds.RefundLinker
import com.pft.financetracker.data.repository.BudgetRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CategoryChoice
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.6, through the real database: a correction teaches its payee, and deleting a category of their own loses no payment. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CategoryStoreTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var store: CategoryStore
    private lateinit var ledger: Ledger
    private val t0 = 1_760_000_000_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        store = CategoryStore(db.customCategoryDao(), db.payeeRuleDao(), db.budgetDao())
        val smsLog = SmsLogRepository(db.smsLogDao())
        val transactor = object : Ledger.Transactor {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
        }
        ledger = Ledger(
            repo, smsLog, RefundLinker(db.transactionDao(), db.refundDao()),
            SmsImporter(context, SmsParser(), repo, smsLog, SettingsRepository(context)),
            StatementImporter(db.transactionDao(), repo, db.importDao(), smsLog), transactor,
            afterChange = {}, scope = CoroutineScope(Dispatchers.Unconfined),
            learnPayee = { m, c -> store.learn(m, c) },
        )
    }

    @After fun tearDown() = db.close()

    private fun tx(merchant: String, category: Category = Category.OTHER, at: Long = t0) =
        Transaction(amountPaise = 5_000, type = TransactionType.DEBIT, merchant = merchant, category = category, timestamp = at, bankName = "HDFC Bank",
            accountRef = "1234", source = Transaction.Source.SMS, flow = Flow.EXPENSE, smsHash = "h$merchant$at")

    @Test fun aCorrectionTeachesThePayeeAndAPlaceholderDoesNot() = runBlocking {
        val id = ledger.add(tx("Autowala"))
        val opened = repo.getById(id)!!
        ledger.correct(opened.copy(category = Category.TRANSPORT), opened)
        assertEquals(CategoryChoice(Category.TRANSPORT), store.rules.choiceFor("AUTOWALA"))

        val generic = ledger.add(tx("Payment (HDFC Bank)", at = t0 + 1))
        ledger.recategorise(setOf(generic), CategoryChoice(Category.FOOD))
        assertNull(store.rules.choiceFor("Payment (HDFC Bank)"))
    }

    @Test fun aRuleSurvivesARestartOfTheStore() = runBlocking {
        store.learn("Autowala", CategoryChoice(Category.TRANSPORT))
        val again = CategoryStore(db.customCategoryDao(), db.payeeRuleDao(), db.budgetDao()).also { it.load() }
        assertEquals(CategoryChoice(Category.TRANSPORT), again.rules.choiceFor("Autowala"))
    }

    @Test fun namesAreUniqueIgnoringCaseAndNeverABuiltInOne() = runBlocking {
        assertTrue(store.add("Pets", Category.SHOPPING) != null)
        assertNull(store.add("pets", Category.SHOPPING))
        assertNull(store.add("Food & Dining", Category.FOOD))
        assertNull(store.add("   ", Category.FOOD))
    }

    @Test fun deletingTheirCategoryReturnsItsPaymentsAndDropsItsBudgetAndRules() = runBlocking {
        val pets = store.add("Pets", Category.SHOPPING)!!
        val choice = CategoryChoice(Category.SHOPPING, pets)
        val id = ledger.add(tx("Supertails", Category.SHOPPING))
        ledger.recategorise(setOf(id), choice)
        val budgets = BudgetRepository(db.budgetDao(), store.customs)
        budgets.set(Category.SHOPPING, 100_000, pets)
        assertEquals(pets, repo.getById(id)!!.customCategoryId)
        assertEquals(choice, store.rules.choiceFor("Supertails"))
        assertEquals(pets, budgets.all.first().single().customId)

        db.withTransaction { store.delete(pets) }

        val after = repo.getById(id)!!
        assertNull(after.customCategoryId)
        assertEquals(Category.SHOPPING, after.category)
        assertTrue(budgets.all.first().isEmpty())
        assertNull(store.rules.choiceFor("Supertails"))
    }
}
