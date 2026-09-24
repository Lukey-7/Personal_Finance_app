package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.data.sms.SmsImporter.Outcome
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.Hashing
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The importer remembers what it has seen: repeats, rescans and the user's own decisions. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class) // plain Application: FinanceApp would load SQLCipher natives
class ImportMemoryTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var log: SmsLogRepository
    private lateinit var importer: SmsImporter
    // Midday, so "five hours later" stays on the same calendar day.
    private val noon = java.util.Calendar.getInstance().apply { set(java.util.Calendar.HOUR_OF_DAY, 12); set(java.util.Calendar.MINUTE, 0) }.timeInMillis

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        log = SmsLogRepository(db.smsLogDao())
        importer = SmsImporter(ApplicationProvider.getApplicationContext(), SmsParser(), repo, log, SettingsRepository(ApplicationProvider.getApplicationContext()))
    }

    @After fun tearDown() { db.close() }

    private val card = "Spent Rs.180 On HDFC Bank Card 1234 At Starbucks. Not You? Call 18002586161"
    private fun sms(body: String, at: Long, sender: String = "VM-HDFCBK") = SmsMessage(sender, body, at)

    @Test fun sameMessageSeenTwiceSecondsApartIsOneRow() = runBlocking {
        assertEquals(Outcome.INSERTED, importer.process(sms(card, noon)))
        assertEquals(Outcome.DUPLICATE, importer.process(sms(card, noon + 20_000)))
        assertEquals(1, repo.getAll().size)
    }

    @Test fun identicalAlertsHoursApartAreTwoPaymentsAndStayTwoOnRescan() = runBlocking {
        assertEquals(Outcome.INSERTED, importer.process(sms(card, noon - 3 * 3600_000)))
        assertEquals(Outcome.INSERTED, importer.process(sms(card, noon + 2 * 3600_000)))
        // "Rescan the last 12 months" feeds both again, oldest first.
        assertEquals(Outcome.DUPLICATE, importer.process(sms(card, noon - 3 * 3600_000)))
        assertEquals(Outcome.DUPLICATE, importer.process(sms(card, noon + 2 * 3600_000)))
        assertEquals(2, repo.getAll().size)
    }

    @Test fun deletedTransactionStaysDeletedOnRescan() = runBlocking {
        val body = "Rs.999.00 debited from a/c **1234 to VPA myntra@ybl (UPI Ref No 422399999999)."
        importer.process(sms(body, noon))
        val row = repo.getAll().single()
        repo.delete(row); importer.forgetDeleted(row)
        assertEquals(Outcome.DUPLICATE, importer.process(sms(body, noon)))
        assertEquals(0, repo.getAll().size)
    }

    @Test fun dismissedReviewItemStaysDismissedOnRescan() = runBlocking {
        val body = "Transaction alert: Rs 300 on your account XX1234."
        assertEquals(Outcome.REVIEW, importer.process(sms(body, noon, "VM-XYZBNK")))
        val item = repo.reviewQueue.first().single()
        repo.resolveReview(item.id); importer.recordDismissed(item.smsHash)
        assertEquals(Outcome.DUPLICATE, importer.process(sms(body, noon, "VM-XYZBNK")))
        assertEquals(0, repo.reviewQueue.first().size)
    }

    /** An older parser ignored this real debit (OTP footer). A rescan with the fixed parser must recover it. */
    @Test fun messageIgnoredByAnOlderParserIsRecoveredOnRescan() = runBlocking {
        val body = "Rs.500.00 debited from a/c **1234 to VPA zomato@hdfcbank (UPI Ref No 422312345678). Never share your OTP with anyone."
        log.log(SmsLogEntity(sender = "VM-HDFCBK", receivedAt = noon, outcome = "IGNORED", reason = "otp", amountPaise = null, type = null, transactionId = null, smsHash = Hashing.smsHash("VM-HDFCBK", body, noon), runId = 0))
        assertEquals(Outcome.INSERTED, importer.process(sms(body, noon)))
        assertEquals(50_000L, repo.getAll().single().amountPaise)
    }

    private val icici = "ICICI Bank Acct XX123 debited for Rs 240.00 on 28-Mar-24; DAKSHIN CAFE credited. UPI:408812345678. Call 18002662 for dispute."

    /** What the pre-fix parser stored for [icici]: a Rs 240 "income" from "dispute". */
    private suspend fun storeStaleIcici(userEdited: Boolean): Long {
        val hash = Hashing.smsHash("AX-ICICIB", icici, noon)
        val id = repo.insert(Transaction(amountPaise = 24_000, type = TransactionType.CREDIT, merchant = "dispute", category = Category.INCOME, timestamp = noon,
            bankName = "ICICI Bank", accountRef = "123", source = Transaction.Source.SMS, flow = Flow.INCOME, smsHash = hash, userEdited = userEdited))
        log.log(SmsLogEntity(sender = "AX-ICICIB", receivedAt = noon, outcome = "SAVED", reason = "dispute", amountPaise = 24_000, type = "CREDIT", transactionId = id, smsHash = hash, runId = 0))
        return id
    }

    @Test fun rescanRepairsARowTheOldParserGotBackwards() = runBlocking {
        val id = storeStaleIcici(userEdited = false)
        assertEquals(Outcome.DUPLICATE, importer.process(sms(icici, noon, "AX-ICICIB")))
        val t = repo.getById(id)!!
        assertEquals(TransactionType.DEBIT, t.type)
        assertEquals(Flow.EXPENSE, t.flow)
        assertEquals(24_000L, t.amountPaise)
        assertEquals(1, repo.getAll().size)
    }

    @Test fun rescanNeverRepairsARowAPersonEdited() = runBlocking {
        val id = storeStaleIcici(userEdited = true)
        importer.process(sms(icici, noon, "AX-ICICIB"))
        assertEquals(TransactionType.CREDIT, repo.getById(id)!!.type)
    }

    /** A split shrank this row to my share and remembered the bank amount. A rescan must not put the full amount back. */
    @Test fun rescanLeavesASplitShrunkRowAlone() = runBlocking {
        val id = storeStaleIcici(userEdited = false)
        repo.update(repo.getById(id)!!.copy(type = TransactionType.DEBIT, amountPaise = 8_000, originalAmountPaise = 24_000))
        importer.process(sms(icici, noon, "AX-ICICIB"))
        assertEquals(8_000L, repo.getById(id)!!.amountPaise)
    }

    /** Rows from before v1.1.1 carry no edit flag. An amount that differs from the parse was changed on purpose. */
    @Test fun rescanNeverRewritesAnAmountThatDiffersFromTheParse() = runBlocking {
        val id = storeStaleIcici(userEdited = false)
        repo.update(repo.getById(id)!!.copy(amountPaise = 12_000))
        importer.process(sms(icici, noon, "AX-ICICIB"))
        val t = repo.getById(id)!!
        assertEquals(12_000L, t.amountPaise)
        assertEquals(TransactionType.CREDIT, t.type)
    }

    private val myntra = "Rs.999.00 debited from a/c **1234 to VPA myntra@ybl (UPI Ref No 422399999999)."
    private val midnight get() = TransactionRepository.startOfDay(noon)

    /** What v1.0.0 stored for [myntra]: midnight, an older hash, and no SMS log row. */
    private suspend fun storeLegacyMyntra(): Transaction {
        val id = repo.insert(Transaction(amountPaise = 99_900, type = TransactionType.DEBIT, merchant = "Myntra", category = Category.SHOPPING, timestamp = midnight,
            bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = Flow.EXPENSE, smsHash = "v1-legacy-hash"))
        return repo.getById(id)!!
    }

    @Test fun deletedLegacyRowStaysDeletedOnRescan() = runBlocking {
        val row = storeLegacyMyntra()
        repo.delete(row); importer.forgetDeleted(row)
        assertEquals(Outcome.IGNORED, importer.process(sms(myntra, noon)))
        assertEquals(0, repo.getAll().size)
        // A second rescan settles it by its own hash.
        assertEquals(Outcome.DUPLICATE, importer.process(sms(myntra, noon)))
        assertEquals(0, repo.getAll().size)
    }

    @Test fun deletedLegacyRowSwallowsOnlyOneMessage() = runBlocking {
        val row = storeLegacyMyntra()
        repo.delete(row); importer.forgetDeleted(row)
        importer.process(sms(myntra, noon))
        val second = myntra.replace("422399999999", "422388888888")
        assertEquals(Outcome.INSERTED, importer.process(sms(second, noon + 3 * 3600_000)))
        assertEquals(1, repo.getAll().size)
    }

    @Test fun legacyRowMatchedByItsDebitIsNotFlippedByASameDayRefund() = runBlocking {
        val row = storeLegacyMyntra()
        assertEquals(Outcome.DUPLICATE, importer.process(sms(myntra, noon)))
        val refund = "Rs.999.00 credited to a/c **1234 from VPA myntra@ybl as refund (UPI Ref No 422377777777)."
        importer.process(sms(refund, noon + 4 * 3600_000))
        assertEquals(TransactionType.DEBIT, repo.getById(row.id)!!.type)
    }
}
