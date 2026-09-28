package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.SplitEntity
import com.pft.financetracker.data.local.SplitPersonEntity
import com.pft.financetracker.data.local.SplitShareEntity
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.data.split.AiAnswerCache
import com.pft.financetracker.data.split.SplitEngine
import com.pft.financetracker.domain.importer.AppHistoryParser
import com.pft.financetracker.domain.importer.CsvReader
import com.pft.financetracker.domain.importer.ImportFormat
import com.pft.financetracker.domain.importer.ParsedStatement
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import com.pft.financetracker.domain.split.SplitAiProvider
import com.pft.financetracker.domain.split.SplitAiRequest
import com.pft.financetracker.domain.split.SplitSource
import com.pft.financetracker.domain.split.SplitStatus
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
import java.text.SimpleDateFormat
import java.util.Locale

/** Defects found by the v1.2.1 code review, against a real Room database. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class ReviewFixesDbTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var sms: SmsImporter
    private lateinit var stmt: StatementImporter
    private lateinit var engine: SplitEngine
    private var aiCalls = 0
    private var aiAnswer: ((SplitAiRequest) -> String?)? = null
    private val cacheMap = mutableMapOf<String, String>()

    private val t0 = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -9); set(java.util.Calendar.HOUR_OF_DAY, 20); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private val H = 3_600_000L
    private val D = 24 * H
    private var n = 0
    private fun d(t: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(t)
    private fun smsDate(t: Long) = SimpleDateFormat("dd-MM-yy", Locale.ENGLISH).format(t)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        val log = SmsLogRepository(db.smsLogDao())
        sms = SmsImporter(ctx, SmsParser(), repo, log, SettingsRepository(ctx))
        stmt = StatementImporter(db.transactionDao(), repo, db.importDao(), log)
        val fake = object : SplitAiProvider { override suspend fun judge(request: SplitAiRequest): String? { aiCalls++; return aiAnswer?.invoke(request) } }
        val cache = object : AiAnswerCache {
            override fun get(requestJson: String) = cacheMap[requestJson]
            override fun put(requestJson: String, answer: String) { cacheMap[requestJson] = answer }
        }
        engine = SplitEngine(db.transactionDao(), db.splitDao(), fake, aiEnabled = { aiAnswer != null }, myName = { "Me" }, cache = cache)
    }

    @After fun tearDown() { db.close() }

    private suspend fun pay(rupees: Int, merchant: String, t: Long, cat: Category = Category.FOOD) =
        repo.insert(Transaction(amountPaise = rupees * 100L, type = TransactionType.DEBIT, merchant = merchant, category = cat, timestamp = t, bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = Flow.EXPENSE, smsHash = "h${++n}", counterpartyKind = CounterpartyKind.ORGANISATION))
    private suspend fun got(rupees: Int, who: String, t: Long, ref: String? = null) =
        repo.insert(Transaction(amountPaise = rupees * 100L, type = TransactionType.CREDIT, merchant = who, category = Category.INCOME, timestamp = t, bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = Flow.INCOME, smsHash = "h${++n}", counterpartyKind = CounterpartyKind.PERSON, refNumber = ref))
    private suspend fun tx(id: Long) = repo.getById(id)!!
    private fun csv(text: String) = StatementInterpreter.interpret(CsvReader.read(text.toByteArray()), ImportFormat.CSV)
    private suspend fun manualSplit(title: String, friend: String, sharePaise: Long, date: Long): Pair<Long, Long> {
        val dao = db.splitDao()
        val id = dao.insertFull(
            SplitEntity(title = title, totalPaise = 2 * sharePaise, date = date, mode = "EQUAL", payerIndex = 0, linkedTransactionId = null, note = null),
            listOf(SplitPersonEntity(0, 0, 0, "Me", true), SplitPersonEntity(0, 0, 1, friend, false)),
            listOf(SplitShareEntity(0, 0, 0, sharePaise, 0), SplitShareEntity(0, 0, 1, sharePaise, 0)), emptyList(),
        )
        return id to dao.sharesFor(id).first { it.personIndex == 1 }.id
    }

    // ---- Imports --------------------------------------------------------------------------------------------

    @Test fun twoIdenticalScreenshotRowsAreBothStored() = runBlocking {
        fun l(text: String, x: Float, y: Float) = AppHistoryParser.Line(text, x, y, 30f)
        val day = SimpleDateFormat("d MMM", Locale.ENGLISH).format(t0)
        val rows = AppHistoryParser.parse(listOf(l("Chai Point", 180f, 300f), l("₹20", 900f, 300f), l(day, 180f, 345f), l("Chai Point", 180f, 500f), l("₹20", 900f, 500f), l(day, 180f, 545f)))
        val b = stmt.commit(stmt.preview(ParsedStatement(ImportFormat.APP_SCREENSHOT, rows, emptyList()), "shot.png"))
        assertEquals(2, b.added)
        assertEquals(2, repo.getAll().size)
    }

    @Test fun aRecentRowWithTheSameRefIsFoundEvenWhenAnOldOneSharesIt() = runBlocking {
        got(500, "Kiran Rao", t0 - 60 * D, ref = "412345678901")
        got(500, "Bundl Tech", t0, ref = "412345678901")
        val p = stmt.preview(csv("Date,Narration,Chq./Ref.No.,Withdrawal Amt.,Deposit Amt.,Closing Balance\n${d(t0)},UPI-SOMEONE ELSE,412345678901,,500.00,9500.00"), "x.csv")
        assertEquals(0, p.newRows.size)
    }

    @Test fun anSmsThatArrivesWhileThePreviewIsOpenIsNotCountedTwice() = runBlocking {
        val p = stmt.preview(csv("Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n${d(t0)},UPI-SWIGGY-swiggy@icici-PAYMENT,750.00,,9250.00"), "x.csv")
        assertEquals(1, p.newRows.size)
        sms.process(SmsMessage("VM-HDFCBK", "Rs.750.00 debited from a/c **1234 on ${smsDate(t0)} to VPA swiggy@icici.", t0))
        stmt.commit(p)
        assertEquals(1, repo.getAll().size)
    }

    @Test fun undoKeepsARowThatAnSmsAlsoReported() = runBlocking {
        val b = stmt.commit(stmt.preview(csv("Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n${d(t0)},UPI-SWIGGY-swiggy@icici-PAYMENT,750.00,,9250.00"), "x.csv"))
        sms.process(SmsMessage("VM-HDFCBK", "Rs.750.00 debited from a/c **1234 on ${smsDate(t0)} to VPA swiggy@icici.", t0 + H))
        assertEquals(1, repo.getAll().size)
        stmt.undo(b.id)
        assertEquals("the SMS still vouches for it", 1, repo.getAll().size)
        assertNull(repo.getAll().single().importBatchId)
    }

    @Test fun aRowMovedToALaterImportCountsForIt() = runBlocking {
        val text = "Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n${d(t0)},UPI-SWIGGY-swiggy@icici-PAYMENT,750.00,,9250.00"
        val first = stmt.commit(stmt.preview(csv(text), "a.csv"))
        val second = stmt.commit(stmt.preview(csv(text), "b.csv"))
        stmt.undo(first.id)
        assertEquals(1, db.importDao().get(second.id)!!.added)
    }

    // ---- Splits -------------------------------------------------------------------------------------------

    @Test fun acceptRefusesATransferAManualSplitAlreadySettled() = runBlocking {
        val p = pay(1_000, "Dominos", t0)
        val c = got(500, "Rahul Sharma", t0 + H)
        engine.run()
        val suggestion = db.splitDao().allSplits().single()
        val (manual, share) = manualSplit("Movie", "Rahul", 500_00, t0 - H)
        engine.linkSettlement(manual, share, 500_00, tx(c), 500_00)
        engine.accept(suggestion.id)
        assertEquals("the payment is not shrunk by a transfer that paid for something else", 1_000_00L, tx(p).amountPaise)
        assertEquals("the suggestion waits; the next refresh drops it", SplitStatus.SUGGESTED.name, db.splitDao().getSplit(suggestion.id)!!.status)
        assertEquals(Flow.SETTLEMENT, tx(c).flow)
    }

    @Test fun aManualSplitMadeLaterTakesItsFriendsTransferBackFromAnAutoSplit() = runBlocking {
        pay(1_200, "Myntra", t0, Category.SHOPPING)
        val bob = got(600, "Bob Mathew", t0 + H)
        engine.run()
        assertTrue(db.splitDao().allLinks().any { it.transactionId == bob })
        manualSplit("Dinner", "Bob", 600_00, t0 - 2 * H)
        engine.run(useAi = false)
        val auto = db.splitDao().allSplits().filter { it.source != SplitSource.MANUAL.name }.map { it.id }.toSet()
        assertTrue(db.splitDao().allLinks().none { it.transactionId == bob && it.splitId in auto })
    }

    @Test fun linkSettlementUsesTheRowAsItIsNow() = runBlocking {
        val c = got(500, "Rahul Sharma", t0 + H)
        val snapshot = tx(c)
        repo.update(snapshot.copy(refNumber = "REF-FILLED-LATER", category = Category.TRANSFER))
        val (manual, share) = manualSplit("Movie", "Rahul", 500_00, t0)
        engine.linkSettlement(manual, share, 500_00, snapshot, 500_00)
        assertEquals("REF-FILLED-LATER", tx(c).refNumber)
        assertEquals(Category.TRANSFER, tx(c).category)
        assertEquals(Flow.SETTLEMENT, tx(c).flow)
    }

    @Test fun anAiSplitSurvivesARunWhereTheAiCouldNotBeAsked() = runBlocking {
        pay(1_800, "Toit Brewpub", t0)
        got(500, "Kiran Rao", t0 + 15 * H); got(700, "Neha Joshi", t0 + 16 * H)
        aiAnswer = { """{"groups":[{"payment":"P1","kind":"payback","allocations":[{"incoming":"C1","amount":500},{"incoming":"C2","amount":700}],"people":3,"confidence":90,"reason":"uneven"}]}""" }
        engine.run()
        assertEquals(SplitStatus.APPLIED.name, db.splitDao().allSplits().single().status)
        got(300, "Amit Desai", t0 + 20 * H) // the week changed: the cached answer no longer matches
        aiAnswer = { null } // offline
        engine.run()
        assertEquals(1, db.splitDao().allSplits().count { it.source == SplitSource.AUTO_AI.name && it.status == SplitStatus.APPLIED.name })
    }

    @Test fun anAnswerWithNoGroupsIsCachedAndNotPaidForAgain() = runBlocking {
        pay(1_800, "Toit Brewpub", t0)
        got(500, "Kiran Rao", t0 + 15 * H); got(700, "Neha Joshi", t0 + 16 * H)
        aiAnswer = { "{}" }
        engine.run(); engine.run()
        assertEquals(1, aiCalls)
    }

    @Test fun mergingDuplicatesKeepsTheCopyASplitUses() = runBlocking {
        val settled = got(500, "Rahul Sharma", t0 + H)
        val richer = got(500, "Rahul Sharma", t0 + H + 60_000, ref = "412345678901")
        val (manual, share) = manualSplit("Movie", "Rahul", 500_00, t0)
        engine.linkSettlement(manual, share, 500_00, tx(settled), 500_00)
        repo.mergeDuplicates(listOf(TransactionRepository.DuplicatePair(keep = tx(richer), drop = tx(settled)))) { id -> db.splitDao().linksForTransaction(id).isNotEmpty() }
        engine.run(useAi = false)
        assertEquals(1, repo.getAll().size)
        assertEquals(Flow.SETTLEMENT, repo.getAll().single().flow)
        assertEquals("the ref from the other copy is kept", "412345678901", repo.getAll().single().refNumber)
        assertEquals(500_00L, db.splitDao().sharesFor(manual).first { it.id == share }.settledPaise)
    }
}
