package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Phase 1.2: one payment reported by two senders is stored once; two real payments are both kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class) // plain Application: FinanceApp would load SQLCipher natives
class DuplicateDetectionTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private val now = System.currentTimeMillis()

    @Before fun setUp() {
        // Plain (unencrypted) in-memory Room for JVM tests; SQLCipher's native library is not loaded here.
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
    }

    @After fun tearDown() { db.close() }

    private fun tx(paise: Long, merchant: String, bank: String?, at: Long, ref: String? = null, hash: String = "h$merchant$bank$at$ref") =
        Transaction(amountPaise = paise, type = TransactionType.DEBIT, merchant = merchant, category = Category.FOOD, timestamp = at, bankName = bank, accountRef = null,
            source = Transaction.Source.SMS, flow = Flow.EXPENSE, smsHash = hash, refNumber = ref)

    @Test fun sameRefIsDuplicate() = runBlocking {
        repo.insert(tx(25_000, "Swiggy", "HDFC Bank", now, ref = "4223"))
        val dup = repo.findLikelyDuplicate(tx(25_000, "Payment (Paytm)", "Paytm", now + 3 * 60_000, ref = "4223"))
        assertNotNull(dup)
        assertEquals("Swiggy", dup!!.merchant)
    }

    @Test fun sameAmountDifferentReporterWithinWindowIsDuplicate() = runBlocking {
        repo.insert(tx(25_000, "Swiggy", "HDFC Bank", now))
        assertNotNull(repo.findLikelyDuplicate(tx(25_000, "Swiggy", "Paytm", now + 2 * 60_000)))
    }

    @Test fun sameAmountSameBankDifferentMerchantIsKept() = runBlocking {
        repo.insert(tx(5_000, "Chai Point", "HDFC Bank", now))
        assertNull(repo.findLikelyDuplicate(tx(5_000, "Auto Rickshaw", "HDFC Bank", now + 5 * 60_000)))
    }

    @Test fun differentRefsAreNeverDuplicates() = runBlocking {
        repo.insert(tx(5_000, "Swiggy", "HDFC Bank", now, ref = "111111"))
        assertNull(repo.findLikelyDuplicate(tx(5_000, "Swiggy", "Paytm", now + 60_000, ref = "222222")))
    }

    @Test fun differentMerchantOutsideWindowIsKept() = runBlocking {
        repo.insert(tx(25_000, "Swiggy", "HDFC Bank", now))
        assertNull(repo.findLikelyDuplicate(tx(25_000, "Blinkit", "Paytm", now + 30 * 60_000)))
    }

    /**
     * The upgrade path. v1.0.0 stored a body-dated transaction at midnight because its parser dropped the
     * time of day; v1.1 keeps the SMS time. A rescan re-parses the same message hours away from the stored
     * row (and the hash changed too), so without a same-day rule it would be imported a second time.
     */
    @Test fun legacyMidnightRowIsMatchedOnRescan() = runBlocking {
        val midnight = startOfDay(now)
        repo.insert(tx(89_900, "Netflix", "HDFC Bank", midnight, ref = null, hash = "legacy-hash"))
        val reparsed = tx(89_900, "Netflix", "HDFC Bank", midnight + 7 * 3600_000 + 45 * 60_000, ref = "433312345678")
        val dup = repo.findLikelyDuplicate(reparsed)
        assertNotNull("legacy row should be recognised on rescan", dup)
        assertEquals("legacy-hash", dup!!.smsHash)
    }

    /** Same-day matching must not swallow a genuine repeat purchase: distinct references keep both. */
    @Test fun sameMerchantSameDayWithDifferentRefsAreBothKept() = runBlocking {
        val midnight = startOfDay(now)
        repo.insert(tx(5_000, "Chai Point", "HDFC Bank", midnight + 9 * 3600_000, ref = "111111"))
        assertNull(repo.findLikelyDuplicate(tx(5_000, "Chai Point", "HDFC Bank", midnight + 18 * 3600_000, ref = "222222")))
    }

    @Test fun sameMerchantOnAnotherDayIsKept() = runBlocking {
        repo.insert(tx(5_000, "Chai Point", "HDFC Bank", startOfDay(now) + 9 * 3600_000))
        assertNull(repo.findLikelyDuplicate(tx(5_000, "Chai Point", "HDFC Bank", startOfDay(now) - 15 * 3600_000)))
    }

    private fun startOfDay(t: Long) = java.util.Calendar.getInstance().apply {
        timeInMillis = t
        set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis

    @Test fun richerRecordWins() {
        val generic = tx(25_000, "Payment (Paytm)", "Paytm", now)
        val detailed = tx(25_000, "Swiggy", "HDFC Bank", now).copy(accountRef = "1234")
        assertEquals("Swiggy", repo.richer(generic, detailed).merchant)
        assertEquals("Swiggy", repo.richer(detailed, generic).merchant)
    }

    /**
     * The retrospective sweep: rows already double counted by an older version are found and merged,
     * keeping the record with more detail and folding in anything only the other one had.
     */
    @Test fun findsAndMergesDuplicatesAlreadyInTheDatabase() = runBlocking {
        val at = startOfDay(now) + 13 * 3600_000
        repo.insert(tx(25_000, "Swiggy", "HDFC Bank", at, ref = "4223").copy(accountRef = "1234"))
        repo.insert(tx(25_000, "Payment (Paytm)", "Paytm", at + 3 * 60_000, ref = "4223"))
        // A genuine second payment the same day, different merchant: must survive untouched.
        repo.insert(tx(25_000, "Blinkit", "HDFC Bank", at + 5 * 3600_000, ref = "9999"))

        val pairs = repo.findExistingDuplicates()
        assertEquals(1, pairs.size)
        assertEquals("Swiggy", pairs.first().keep.merchant)
        assertEquals("Payment (Paytm)", pairs.first().drop.merchant)

        repo.mergeDuplicates(pairs)
        val left = repo.getAll().sortedBy { it.timestamp }
        assertEquals(2, left.size)
        assertEquals("Swiggy", left[0].merchant)
        assertEquals("1234", left[0].accountRef)
        assertEquals("Blinkit", left[1].merchant)
    }

    /** Two payments of the same amount with different references are never swept up. */
    @Test fun sweepKeepsGenuineRepeatsWithDifferentRefs() = runBlocking {
        val at = startOfDay(now) + 9 * 3600_000
        repo.insert(tx(5_000, "Chai Point", "HDFC Bank", at, ref = "111111"))
        repo.insert(tx(5_000, "Chai Point", "HDFC Bank", at + 6 * 3600_000, ref = "222222"))
        assertEquals(0, repo.findExistingDuplicates().size)
    }

    /**
     * A row carried over from v1.0.0 only ever had a flow guessed from its category, so a credit-card bill
     * payment sits there counted as spend. Re-importing the original SMS must correct the flow in place
     * rather than add a second transaction.
     */
    @Test fun rescanCorrectsTheFlowOfALegacyRow() = runBlocking {
        val importer = SmsImporter(
            ApplicationProvider.getApplicationContext(), SmsParser(), repo,
            SmsLogRepository(db.smsLogDao()), SettingsRepository(ApplicationProvider.getApplicationContext()),
        )
        // The body carries its own date, so the stored row and the SMS must sit on that same day for this
        // to be the real upgrade case: v1.0.0 filed it at midnight, the SMS itself arrived that evening.
        val day = java.util.Calendar.getInstance().apply {
            set(2026, java.util.Calendar.SEPTEMBER, 5, 0, 0, 0); set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis
        val body = "Payment of Rs.12,500.00 received towards your HDFC Bank Credit Card XX3344 on 05-09-26. Thank you."
        val at = day + 18 * 3600_000
        // What the migration would have produced: amount right, time lost, flow guessed as plain spend.
        repo.insert(
            Transaction(
                amountPaise = 1_250_000, type = TransactionType.DEBIT, merchant = "Payment (HDFC Bank)",
                category = Category.OTHER, timestamp = day, bankName = "HDFC Bank", accountRef = "3344",
                source = Transaction.Source.SMS, flow = Flow.EXPENSE, smsHash = "legacy", refNumber = null,
            )
        )
        val outcome = importer.process(SmsMessage("VM-HDFCBK", body, at))

        assertEquals(SmsImporter.Outcome.DUPLICATE, outcome)
        val all = repo.getAll()
        assertEquals("must not add a second row", 1, all.size)
        assertEquals("flow must be corrected in place", Flow.TRANSFER, all.first().flow)
    }
}
