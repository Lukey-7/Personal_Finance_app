package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.SplitEntity
import com.pft.financetracker.data.local.SplitPersonEntity
import com.pft.financetracker.data.local.SplitShareEntity
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.split.SplitEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.SplitSource
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

/** v1.5: manual splits put back exactly what they changed, and keep their friends' transfers from automatic splits. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ManualSplitDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var engine: SplitEngine

    private val t0 = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -9); set(java.util.Calendar.HOUR_OF_DAY, 20); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val H = 3_600_000L
    private var n = 0

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        engine = SplitEngine(db.transactionDao(), db.splitDao(), ai = null, aiEnabled = { false }, myName = { "Me" })
    }

    @After fun tearDown() { db.close() }

    private suspend fun pay(rupees: Int, merchant: String, t: Long, note: String? = null) =
        repo.insert(Transaction(amountPaise = rupees * 100L, type = TransactionType.DEBIT, merchant = merchant, category = Category.FOOD, timestamp = t, bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = Flow.EXPENSE, smsHash = "h${++n}", counterpartyKind = CounterpartyKind.ORGANISATION, note = note))
    private suspend fun got(rupees: Int, who: String, t: Long) =
        repo.insert(Transaction(amountPaise = rupees * 100L, type = TransactionType.CREDIT, merchant = who, category = Category.INCOME, timestamp = t, bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = Flow.INCOME, smsHash = "h${++n}", counterpartyKind = CounterpartyKind.PERSON))
    private suspend fun tx(id: Long) = repo.getById(id)

    /** A manual split I paid, me and [friend] equally, linked to [payment]; returns the split and the friend's share. */
    private suspend fun manualSplit(title: String, friend: String, totalPaise: Long, date: Long, payment: Long?, friendSettledPaise: Long = 0): Pair<Long, Long> {
        val dao = db.splitDao()
        val half = totalPaise / 2
        val id = dao.insertFull(
            SplitEntity(title = title, totalPaise = totalPaise, date = date, mode = "EQUAL", payerIndex = 0, linkedTransactionId = payment, note = null),
            listOf(SplitPersonEntity(0, 0, 0, "Me", true), SplitPersonEntity(0, 0, 1, friend, false)),
            listOf(SplitShareEntity(0, 0, 0, totalPaise - half, totalPaise - half), SplitShareEntity(0, 0, 1, half, friendSettledPaise)), emptyList(),
        )
        return id to dao.sharesFor(id).first { it.personIndex == 1 }.id
    }

    @Test fun deletingAManualSplitPutsThePaymentBack() = runBlocking {
        val p = pay(2_000, "Truffles", t0, note = "Team lunch")
        // What saving the split did: the payment shrank to my share and got a note.
        repo.update(tx(p)!!.copy(amountPaise = 1_000_00, originalAmountPaise = 2_000_00, note = "Team lunch " + SplitEngine.splitNote("Lunch", "₹1,000")))
        val c = got(1_000, "Rahul Sharma", t0 + H)
        val (split, share) = manualSplit("Lunch", "Rahul", 2_000_00, t0, payment = p)
        engine.linkSettlement(split, share, 1_000_00, tx(c)!!, 1_000_00)
        assertEquals(Flow.SETTLEMENT, tx(c)!!.flow)

        engine.deleteManual(split)
        assertEquals(2_000_00L, tx(p)!!.amountPaise)
        assertNull(tx(p)!!.originalAmountPaise)
        assertEquals("Team lunch", tx(p)!!.note)
        assertEquals(Flow.INCOME, tx(c)!!.flow)
        assertTrue(db.splitDao().allSplits().isEmpty())
        assertTrue(db.splitDao().allLinks().isEmpty())
    }

    @Test fun deletingAManualSplitRemovesTheShareRowItAdded() = runBlocking {
        val share = repo.insert(Transaction(amountPaise = 500_00, type = TransactionType.DEBIT, merchant = "Movie", category = Category.ENTERTAINMENT, timestamp = t0,
            bankName = null, accountRef = null, source = Transaction.Source.SPLIT, flow = Flow.EXPENSE, note = "My share, paid by Asha"))
        val (split, _) = manualSplit("Movie", "Asha", 1_000_00, t0, payment = share)
        engine.deleteManual(split)
        assertNull(tx(share))
        assertTrue(db.splitDao().allSplits().isEmpty())
    }

    @Test fun releasingAPaymentForAManualSplitDropsItsSuggestion() = runBlocking {
        val p = pay(1_000, "Dominos", t0)
        got(500, "Rahul Sharma", t0 + H)
        engine.run(useAi = false)
        assertEquals(1, db.splitDao().allSplits().size)
        engine.releaseForManual(p)
        assertTrue(db.splitDao().allSplits().isEmpty())
        assertEquals(1_000_00L, tx(p)!!.amountPaise)
        // The manual split now owns the payment: no automatic split comes back for it.
        manualSplit("Pizza", "Rahul", 1_000_00, t0, payment = p)
        engine.run(useAi = false)
        assertTrue(db.splitDao().allSplits().none { it.source != SplitSource.MANUAL.name })
    }

    @Test fun aShareMarkedPaidByHandStillKeepsTheFriendsTransfer() = runBlocking {
        // Rahul's Rs 500 share of the movie was marked paid with a typed amount, so his transfer is still income...
        manualSplit("Movie", "Rahul", 1_000_00, t0 - H, payment = null, friendSettledPaise = 500_00)
        pay(1_000, "Dominos", t0)
        got(500, "Rahul Sharma", t0 + H)
        engine.run(useAi = false)
        // ...and it is his movie money, not a payback for a pizza of the same size.
        assertTrue(db.splitDao().allSplits().none { it.source != SplitSource.MANUAL.name })
    }

    @Test fun theSplitNoteComesOffCleanly() {
        val note = SplitEngine.splitNote("Dinner. At Toit", "₹2,000")
        assertEquals("Paid by card", SplitEngine.withoutSplitNote("Paid by card $note", "Dinner. At Toit"))
        assertNull(SplitEngine.withoutSplitNote(note, "Dinner. At Toit"))
        assertEquals("other note", SplitEngine.withoutSplitNote("other note", "Dinner"))
    }
}
