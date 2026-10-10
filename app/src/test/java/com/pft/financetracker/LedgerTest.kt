package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.ledger.Ledger
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.ImportBatchEntity
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.local.SmsLogEntity
import androidx.room.withTransaction
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.refunds.RefundLinker
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The ledger, through its interface only: each rule it promises runs on every change, and the follow-up runs once. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class LedgerTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var refunds: RefundLinker
    private lateinit var ledger: Ledger
    private val followUps = mutableListOf<Boolean>()
    private var gate: CompletableDeferred<Unit>? = null
    private val t0 = 1_760_000_000_000L
    private val day = 86_400_000L

    @Before fun setUp() {
        val context = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        val smsLog = SmsLogRepository(db.smsLogDao())
        refunds = RefundLinker(db.transactionDao(), db.refundDao())
        val importer = SmsImporter(context, SmsParser(), repo, smsLog, SettingsRepository(context))
        val statements = StatementImporter(db.transactionDao(), repo, db.importDao(), smsLog)
        // Unconfined: the follow-up runs inside the call that asked for it, until something makes it wait.
        val transactor = object : Ledger.Transactor {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
        }
        ledger = Ledger(repo, smsLog, refunds, importer, statements, transactor, afterChange = { ai -> followUps += ai; gate?.await() }, scope = CoroutineScope(Dispatchers.Unconfined))
    }

    @After fun tearDown() = db.close()

    private fun tx(paise: Long, merchant: String, type: TransactionType = TransactionType.DEBIT, flow: Flow = Flow.EXPENSE, at: Long = t0, ref: String? = null,
                   source: Transaction.Source = Transaction.Source.SMS, hash: String? = "h$merchant$at$type") =
        Transaction(amountPaise = paise, type = type, merchant = merchant, category = Category.SHOPPING, timestamp = at, bankName = "HDFC Bank", accountRef = "1234",
            source = source, flow = flow, smsHash = hash, refNumber = ref, counterpartyKind = CounterpartyKind.ORGANISATION)

    @Test fun moneyInIsNeverSavedAsSpend() = runBlocking {
        val id = ledger.add(tx(50_000, "Salary", type = TransactionType.CREDIT, flow = Flow.EXPENSE, source = Transaction.Source.MANUAL, hash = null))
        assertEquals(Flow.INCOME, repo.getById(id)!!.flow)
        assertEquals(listOf(false), followUps)
    }

    @Test fun aCorrectionIsMarkedAndFitsItsDirection() = runBlocking {
        val id = repo.insert(tx(50_000, "Swiggy"))
        val opened = repo.getById(id)!!
        ledger.correct(opened.copy(flow = Flow.REFUND, merchant = "Swiggy Instamart"), opened)
        val row = repo.getById(id)!!
        assertTrue(row.userEdited)
        assertEquals(Flow.EXPENSE, row.flow)
        assertEquals("Swiggy Instamart", row.merchant)
        assertEquals(1, followUps.size)
    }

    @Test fun aSplitReshapingAPaymentIsNotAPersonsCorrection() = runBlocking {
        val id = repo.insert(tx(120_000, "Barbeque Nation"))
        ledger.reshape(repo.getById(id)!!.copy(amountPaise = 40_000, originalAmountPaise = 120_000))
        assertEquals(false, repo.getById(id)!!.userEdited)
        assertEquals(40_000L, repo.getById(id)!!.amountPaise)
    }

    @Test fun removingARefundedPurchaseGivesTheRefundBack() = runBlocking {
        val purchase = repo.insert(tx(249_900, "Amazon", ref = "R77"))
        val credit = repo.insert(tx(249_900, "Amazon", type = TransactionType.CREDIT, flow = Flow.INCOME, at = t0 + 8 * day, ref = "R77").copy(category = Category.OTHER))
        refunds.run()
        assertEquals(Flow.REFUND, repo.getById(credit)!!.flow)

        ledger.remove(repo.getById(purchase)!!)
        assertEquals(null, repo.getById(purchase))
        assertEquals(Flow.INCOME, repo.getById(credit)!!.flow)
        assertEquals(Category.OTHER, repo.getById(credit)!!.category)
        assertEquals(1, followUps.size)
    }

    @Test fun aRemovedSmsPaymentIsRemembered() = runBlocking {
        val id = repo.insert(tx(50_000, "Swiggy", hash = "sms-1"))
        ledger.remove(repo.getById(id)!!)
        assertNotNull(SmsLogRepository(db.smsLogDao()).getByHash("deleted:sms-1"))
    }

    @Test fun recategorisingOnlyWritesRowsThatChange() = runBlocking {
        val a = repo.insert(tx(50_000, "Swiggy").copy(category = Category.FOOD))
        val b = repo.insert(tx(60_000, "Zomato", at = t0 + day).copy(category = Category.OTHER))
        ledger.recategorise(setOf(a, b), Category.FOOD)
        assertEquals(false, repo.getById(a)!!.userEdited)
        assertEquals(Category.FOOD, repo.getById(b)!!.category)
        assertTrue(repo.getById(b)!!.userEdited)
    }

    @Test fun approvingAReviewItemWhoseTwinIsStoredMergesIt() = runBlocking {
        repo.insert(tx(25_000, "Swiggy", at = t0))
        val body = "Paid Rs.250 to Swiggy via UPI. UPI Ref: 424012345678"
        val reviewId = db.reviewDao().insert(ReviewItemEntity(sender = "JD-PAYTMB", body = body, receivedAt = t0 + 60_000,
            smsHash = "review-1", guessedAmountPaise = 25_000, guessedType = "DEBIT", reason = "unknown_format"))
        val id = ledger.approve(reviewId, tx(25_000, "Swiggy", at = t0 + 60_000, hash = "review-1").copy(bankName = "Paytm"), body)
        assertEquals(1, repo.getAll().size)
        assertEquals("424012345678", repo.getById(id)!!.refNumber)
        assertEquals(null, db.reviewDao().getById(reviewId))
        assertEquals(1, followUps.size)
    }

    @Test fun undoingAnImportGivesBackRefundsOfItsPurchases() = runBlocking {
        val batch = db.importDao().insert(ImportBatchEntity(fileName = "may.csv", format = "CSV", rowsFound = 1, added = 1, duplicates = 0, needsReview = 0,
            balanceMismatches = 0, firstDate = t0, lastDate = t0))
        repo.insert(tx(249_900, "Amazon", ref = "R77", source = Transaction.Source.STATEMENT, hash = "stmt:1").copy(importBatchId = batch))
        val credit = repo.insert(tx(249_900, "Amazon", type = TransactionType.CREDIT, flow = Flow.INCOME, at = t0 + 8 * day, ref = "R77").copy(category = Category.OTHER))
        refunds.run()
        assertEquals(Flow.REFUND, repo.getById(credit)!!.flow)

        ledger.undoImport(batch)
        assertEquals(1, repo.getAll().size)
        assertEquals(Flow.INCOME, repo.getById(credit)!!.flow)
    }

    @Test fun oneChangeWithSeveralWritesGetsOneFollowUp() = runBlocking {
        ledger.together {
            ledger.add(tx(10_000, "Chai", source = Transaction.Source.MANUAL, hash = null))
            ledger.add(tx(20_000, "Lunch", source = Transaction.Source.MANUAL, hash = null))
        }
        assertEquals(2, repo.getAll().size)
        assertEquals(1, followUps.size)
    }

    @Test fun aChangeThatFailsPartWayLeavesNothingHalfWrittenAndStillGetsItsFollowUp() = runBlocking {
        val id = repo.insert(tx(120_000, "Barbeque Nation"))
        followUps.clear()
        runCatching {
            ledger.together {
                ledger.reshape(repo.getById(id)!!.copy(amountPaise = 40_000, originalAmountPaise = 120_000))
                error("the split could not be saved")
            }
        }
        // The shrink to my share is rolled back with the split that failed.
        assertEquals(120_000L, repo.getById(id)!!.amountPaise)
        assertEquals(null, repo.getById(id)!!.originalAmountPaise)
        assertEquals(1, followUps.size)
    }

    @Test fun aCorrectionKeepsWhatTheAppChangedWhileTheEditorWasOpen() = runBlocking {
        val id = repo.insert(tx(249_900, "Amazon", type = TransactionType.CREDIT, flow = Flow.INCOME).copy(category = Category.OTHER))
        val opened = repo.getById(id)!!
        // While the editor is open, refund pairing turns the credit into a refund.
        repo.update(opened.copy(flow = Flow.REFUND, category = Category.SHOPPING))
        // The person only changed the note.
        ledger.correct(opened.copy(note = "returned shoes"), opened)
        val row = repo.getById(id)!!
        assertEquals("returned shoes", row.note)
        assertEquals(Flow.REFUND, row.flow)
        assertEquals(Category.SHOPPING, row.category)
        assertTrue(row.userEdited)
    }

    @Test fun mergingTwinsHandsTheRefundPairingToTheSurvivor() = runBlocking {
        val keep = repo.insert(tx(249_900, "Amazon", ref = "R77"))
        val twin = repo.insert(tx(249_900, "Payment (Paytm)", ref = "R77", at = t0 + 60_000).copy(bankName = "Paytm", accountRef = null))
        val credit = repo.insert(tx(249_900, "Amazon", type = TransactionType.CREDIT, flow = Flow.INCOME, at = t0 + 8 * day, ref = "R77").copy(category = Category.OTHER))
        refunds.run()
        val link = db.refundDao().getAll().single()
        val (survivor, dropped) = if (link.debitTxId == twin) keep to twin else twin to keep
        ledger.mergeTwins(listOf(TransactionRepository.DuplicatePair(keep = repo.getById(survivor)!!, drop = repo.getById(dropped)!!))) { false }
        assertEquals(survivor, db.refundDao().getAll().single().debitTxId)
        // Deleting the surviving purchase still gives the refund back.
        ledger.remove(repo.getById(survivor)!!)
        assertEquals(Flow.INCOME, repo.getById(credit)!!.flow)
        assertEquals(Category.OTHER, repo.getById(credit)!!.category)
    }

    @Test fun undoingAnImportDoesNotOverwriteARefundItGaveBack() = runBlocking {
        val batch = db.importDao().insert(ImportBatchEntity(fileName = "may.csv", format = "CSV", rowsFound = 2, added = 2, duplicates = 0, needsReview = 0,
            balanceMismatches = 0, firstDate = t0, lastDate = t0))
        repo.insert(tx(249_900, "Amazon", ref = "R77", source = Transaction.Source.STATEMENT, hash = "stmt:1").copy(importBatchId = batch))
        val credit = repo.insert(tx(249_900, "Amazon", type = TransactionType.CREDIT, flow = Flow.INCOME, at = t0 + 8 * day, ref = "R77",
            source = Transaction.Source.STATEMENT, hash = "stmt:2").copy(category = Category.OTHER, importBatchId = batch))
        // An SMS reported the refund too, so undoing the import keeps that row.
        SmsLogRepository(db.smsLogDao()).log(SmsLogEntity(sender = "HDFCBK", receivedAt = t0 + 8 * day, outcome = "SAVED", reason = "Amazon",
            amountPaise = 249_900, type = "CREDIT", transactionId = credit, smsHash = "sms-refund", runId = 1))
        refunds.run()
        assertEquals(Flow.REFUND, repo.getById(credit)!!.flow)

        ledger.undoImport(batch)
        assertEquals(listOf(credit), repo.getAll().map { it.id })
        assertEquals(Flow.INCOME, repo.getById(credit)!!.flow)
    }

    @Test fun requestsDuringARunFoldIntoOneMoreRun() = runBlocking {
        gate = CompletableDeferred()
        ledger.followUp()                 // starts, and waits on the gate
        ledger.followUp()
        ledger.followUp()
        val last = ledger.followUp()
        assertEquals(listOf(false), followUps)
        gate!!.complete(Unit)
        last.join()
        // One run for the first request, one for the three that came while it ran.
        assertEquals(listOf(false, false), followUps)
    }
}
