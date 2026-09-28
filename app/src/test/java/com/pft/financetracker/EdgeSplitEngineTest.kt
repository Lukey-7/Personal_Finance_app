package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.split.AiAnswerCache
import com.pft.financetracker.data.split.SplitEngine
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.SplitAiProvider
import com.pft.financetracker.domain.split.SplitAiRequest
import com.pft.financetracker.domain.split.SplitStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.2.1 edge cases for the split engine: user edits, rejects, deletes, re-runs, AI failures and the cache. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EdgeSplitEngineTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var engine: SplitEngine
    private var aiCalls = 0
    private var aiAnswer: ((SplitAiRequest) -> String?)? = null
    private val cacheMap = mutableMapOf<String, String>()
    private var cacheDefault: String? = null

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
        val fake = object : SplitAiProvider {
            override suspend fun judge(request: SplitAiRequest): String? { aiCalls++; return aiAnswer?.invoke(request) }
        }
        val cache = object : AiAnswerCache {
            override fun get(requestJson: String) = cacheMap[requestJson] ?: cacheDefault
            override fun put(requestJson: String, answer: String) { cacheMap[requestJson] = answer }
        }
        engine = SplitEngine(db.transactionDao(), db.splitDao(), fake, aiEnabled = { aiAnswer != null }, myName = { "Me" }, cache = cache)
    }

    @After fun tearDown() { db.close() }

    private suspend fun pay(rupees: Int, merchant: String, t: Long, cat: Category = Category.FOOD) =
        repo.insert(Transaction(amountPaise = rupees * 100L, type = TransactionType.DEBIT, merchant = merchant, category = cat, timestamp = t, bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = Flow.EXPENSE, smsHash = "h${++n}", counterpartyKind = CounterpartyKind.ORGANISATION))
    private suspend fun got(rupees: Int, who: String, t: Long, kind: CounterpartyKind? = CounterpartyKind.PERSON, flow: Flow = Flow.INCOME) =
        repo.insert(Transaction(amountPaise = rupees * 100L, type = TransactionType.CREDIT, merchant = who, category = Category.INCOME, timestamp = t, bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = flow, smsHash = "h${++n}", counterpartyKind = kind))
    private suspend fun tx(id: Long) = repo.getById(id)!!
    private suspend fun splits() = db.splitDao().allSplits()

    /** The numbers always agree with the links: a settled transfer is used in full by applied splits, a payment is its bank amount minus friends' shares. */
    private suspend fun assertConsistent() {
        val applied = splits().filter { it.status == SplitStatus.APPLIED.name && it.source != "MANUAL" }.map { it.id }.toSet()
        val links = db.splitDao().allLinks().filter { it.splitId in applied }
        for (t in repo.getAll()) {
            val mine = links.filter { it.transactionId == t.id }
            if (t.type == TransactionType.CREDIT && t.flow == Flow.SETTLEMENT) assertEquals("credit ${t.merchant}", t.amountPaise, mine.sumOf { it.allocatedPaise })
            if (t.type == TransactionType.CREDIT && mine.isNotEmpty()) assertEquals("credit ${t.merchant}", Flow.SETTLEMENT, t.flow)
            if (t.type == TransactionType.DEBIT && mine.isEmpty()) assertTrue("payment ${t.merchant} restored", t.originalAmountPaise == null || t.userEdited)
        }
    }

    // ---- 7. User edits survive accept and reject ----------------------------------------------------------------

    @Test fun acceptAfterTheUserEditedThePaymentKeepsTheEdit() = runBlocking {
        val p = pay(1_000, "Dominos", t0)
        got(500, "Rahul Sharma", t0 + H)
        assertEquals(1, engine.run().suggested)
        repo.update(tx(p).copy(amountPaise = 800_00, userEdited = true))
        engine.accept(splits().single().id)
        assertEquals(800_00L, tx(p).amountPaise)
    }

    @Test fun rejectAfterTheUserEditedRowsKeepsTheEdits() = runBlocking {
        val p = pay(4_000, "Toit", t0)
        val a = got(1_000, "Rahul Sharma", t0 + H); val b = got(1_000, "Priya Nair", t0 + 2 * H); val c = got(1_000, "Amit Desai", t0 + 3 * H)
        assertEquals(1, engine.run().applied)
        assertEquals(1_000_00L, tx(p).amountPaise)
        repo.update(tx(p).copy(amountPaise = 1_500_00, userEdited = true))
        repo.update(tx(b).copy(flow = Flow.TRANSFER, userEdited = true))
        engine.reject(splits().single().id)
        assertEquals(1_500_00L, tx(p).amountPaise)
        assertEquals(Flow.TRANSFER, tx(b).flow)
        assertEquals(Flow.INCOME, tx(a).flow); assertEquals(Flow.INCOME, tx(c).flow)
    }

    // ---- 11. Lifecycle ---------------------------------------------------------------------------------------

    @Test fun rejectedTransfersAreNotAutoAppliedToAnotherPayment() = runBlocking {
        pay(3_000, "Toit", t0)
        pay(3_000, "Myntra", t0 + H, Category.SHOPPING)
        val a = got(1_000, "Rahul Sharma", t0 + 24 * H); val b = got(1_000, "Priya Nair", t0 + 25 * H)
        engine.run()
        val first = splits().single()
        engine.reject(first.id)
        engine.run(useAi = false)
        val appliedLinks = db.splitDao().allLinks().filter { l -> splits().any { it.id == l.splitId && it.status == SplitStatus.APPLIED.name } }
        assertTrue(appliedLinks.toString(), appliedLinks.none { it.transactionId == a || it.transactionId == b })
        assertConsistent()
    }

    @Test fun reRunsAreStable() = runBlocking {
        pay(3_000, "Toit", t0); pay(600, "Uber", t0 + 2 * H, Category.TRANSPORT)
        got(1_200, "Rahul Sharma", t0 + 24 * H); got(1_000, "Priya Nair", t0 + 25 * H); got(200, "Priya Nair", t0 + 25 * H + 60_000)
        pay(1_000, "Dominos", t0 + 72 * H); got(500, "Kiran Rao", t0 + 73 * H)
        val first = engine.run()
        assertTrue(first.applied + first.suggested >= 2)
        val snap = repo.getAll().sortedBy { it.id }
        repeat(2) {
            val r = engine.run()
            assertEquals(0, r.applied + r.suggested + r.undone)
        }
        assertEquals(snap, repo.getAll().sortedBy { it.id })
        assertConsistent()
    }

    @Test fun deletingThePaymentRestoresEveryTransfer() = runBlocking {
        val p = pay(4_000, "Toit", t0)
        val cs = listOf(got(1_000, "Rahul Sharma", t0 + H), got(1_000, "Priya Nair", t0 + 2 * H), got(1_000, "Amit Desai", t0 + 3 * H))
        engine.run()
        repo.delete(tx(p))
        engine.run(useAi = false)
        cs.forEach { assertEquals(Flow.INCOME, tx(it).flow) }
        assertTrue(splits().isEmpty())
    }

    @Test fun deletingATransferThatCoversTwoSplitsLeavesNothingHalfSettled() = runBlocking {
        pay(3_000, "Toit", t0); pay(600, "Uber", t0 + 2 * H, Category.TRANSPORT)
        val r = got(1_200, "Rahul Sharma", t0 + 24 * H); got(1_000, "Priya Nair", t0 + 25 * H); got(200, "Priya Nair", t0 + 25 * H + 60_000)
        assertEquals(2, engine.run().applied)
        repo.delete(tx(r))
        engine.run(useAi = false)
        assertConsistent()
    }

    @Test fun acceptRunRejectRunReturnsTheBankNumbers() = runBlocking {
        val p = pay(4_000, "Toit", t0)
        val cs = listOf(got(1_000, "Rahul Sharma", t0 + H), got(1_000, "Priya Nair", t0 + 2 * H), got(1_000, "Amit Desai", t0 + 3 * H))
        engine.run()
        val s = splits().single()
        engine.accept(s.id); engine.run(); engine.reject(s.id); engine.run()
        assertEquals(4_000_00L, tx(p).amountPaise)
        assertEquals(null, tx(p).originalAmountPaise)
        cs.forEach { assertEquals(Flow.INCOME, tx(it).flow) }
    }

    @Test fun anOldRefundRowIsNeverAPayback() = runBlocking {
        pay(1_000, "Swiggy", t0)
        got(500, "Rahul Sharma", t0 + H, kind = null, flow = Flow.REFUND)
        engine.run()
        assertTrue(splits().isEmpty())
    }

    @Test fun acceptWhileARefreshRunsHolds() = runBlocking {
        pay(1_000, "Dominos", t0); got(500, "Rahul Sharma", t0 + H)
        engine.run()
        val s = splits().single()
        listOf(async(Dispatchers.Default) { engine.run() }, async(Dispatchers.Default) { engine.accept(s.id) }, async(Dispatchers.Default) { engine.run() }).awaitAll()
        engine.run()
        assertEquals(SplitStatus.APPLIED.name, splits().single().status)
        assertConsistent()
    }

    // ---- 22. The AI can't be asked, or answers badly -------------------------------------------------------------

    private suspend fun unevenDinner(base: Long = t0) {
        pay(1_800, "Toit Brewpub", base)
        got(500, "Kiran Rao", base + 15 * H); got(700, "Neha Joshi", base + 16 * H)
    }
    private val unevenAnswer = """{"groups":[{"payment":"P1","kind":"payback","allocations":[{"incoming":"C1","amount":500},{"incoming":"C2","amount":700}],"people":3,"confidence":90,"reason":"uneven shares"}]}"""

    @Test fun aCutOffAnswerIsNotCachedAndTheNextRunAsksAgain() = runBlocking {
        unevenDinner()
        aiAnswer = { """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amo""" }
        engine.run()
        assertTrue("nothing applied from a broken answer", splits().isEmpty())
        aiAnswer = { unevenAnswer }
        engine.run()
        assertEquals(2, aiCalls)
        assertEquals(1, splits().size)
    }

    @Test fun aCorruptCacheEntryIsIgnored() = runBlocking {
        unevenDinner()
        cacheDefault = "not json at all"
        aiAnswer = { unevenAnswer }
        engine.run()
        assertEquals(1, aiCalls)
        assertEquals(1, splits().size)
    }

    @Test fun noAnswerLeavesLocalResultsAndCachesNothing() = runBlocking {
        pay(4_000, "Toit", t0)
        got(1_000, "Rahul Sharma", t0 + H); got(1_000, "Priya Nair", t0 + 2 * H); got(1_000, "Amit Desai", t0 + 3 * H)
        unevenDinner(t0 + 96 * H)
        aiAnswer = { null } // timeout, 429, bad key: the provider returns null
        val r = engine.run()
        assertEquals(false, r.askedAi)
        assertEquals(1, r.applied)
        assertTrue(cacheMap.isEmpty())
    }
}
