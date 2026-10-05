package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.data.refunds.RefundLinker
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class RefundLinkerTest {
    private lateinit var db: AppDatabase
    private lateinit var linker: RefundLinker
    private val day = 86_400_000L
    private val t0 = 1_760_000_000_000L

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        linker = RefundLinker(db.transactionDao(), db.refundDao())
    }

    @After fun tearDown() = db.close()

    private fun insert(t: Transaction): Long = runBlocking { db.transactionDao().insert(t.toEntity()) }
    private fun tx(id: Long) = runBlocking { db.transactionDao().getById(id)!!.toDomain() }

    private fun order(paise: Long = 2_49_900) = insert(Transaction(amountPaise = paise, type = TransactionType.DEBIT, merchant = "Amazon", category = Category.SHOPPING,
        timestamp = t0, bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = Flow.EXPENSE, refNumber = "R77",
        counterpartyKind = CounterpartyKind.ORGANISATION))

    private fun refundBookedAsIncome() = insert(Transaction(amountPaise = 2_49_900, type = TransactionType.CREDIT, merchant = "Amazon", category = Category.OTHER,
        timestamp = t0 + 8 * day, bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = Flow.INCOME, refNumber = "R77",
        counterpartyKind = CounterpartyKind.ORGANISATION))

    @Test
    fun anIncomeRefundBecomesARefundInThePurchasesCategory() = runBlocking {
        val d = order(); val c = refundBookedAsIncome()
        assertEquals(1, linker.run())
        assertEquals(Flow.REFUND, tx(c).flow)
        assertEquals(Category.SHOPPING, tx(c).category)
        val link = db.refundDao().getAll().single()
        assertEquals(d, link.debitTxId); assertEquals("APPLIED", link.status)
    }

    @Test
    fun runningAgainAddsNothing() = runBlocking {
        order(); refundBookedAsIncome()
        linker.run()
        assertEquals(0, linker.run())
        assertEquals(1, db.refundDao().getAll().size)
    }

    @Test
    fun undoRestoresTheCreditAndIsRemembered() = runBlocking {
        order(); val c = refundBookedAsIncome()
        linker.run()
        linker.undo(db.refundDao().getAll().single().id)
        assertEquals(Flow.INCOME, tx(c).flow)
        assertEquals(Category.OTHER, tx(c).category)
        assertEquals(0, linker.run())
        assertEquals("REJECTED", db.refundDao().getAll().single().status)
        assertTrue(db.refundDao().getApplied().isEmpty())
    }

    @Test
    fun undoKeepsAPersonsLaterCorrection() = runBlocking {
        order(); val c = refundBookedAsIncome()
        linker.run()
        db.transactionDao().update(tx(c).copy(category = Category.HEALTH, userEdited = true).toEntity())
        linker.undo(db.refundDao().getAll().single().id)
        assertEquals(Category.HEALTH, tx(c).category)
    }

    @Test
    fun deletingThePurchaseDropsTheLink() = runBlocking {
        val d = order(); refundBookedAsIncome()
        linker.run()
        db.transactionDao().delete(db.transactionDao().getById(d)!!)
        assertTrue(db.refundDao().getAll().isEmpty())
    }
}
