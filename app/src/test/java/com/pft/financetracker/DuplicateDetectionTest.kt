package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.repository.TransactionRepository
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

    @Test fun outsideWindowIsKept() = runBlocking {
        repo.insert(tx(25_000, "Swiggy", "HDFC Bank", now))
        assertNull(repo.findLikelyDuplicate(tx(25_000, "Swiggy", "Paytm", now + 30 * 60_000)))
    }

    @Test fun richerRecordWins() {
        val generic = tx(25_000, "Payment (Paytm)", "Paytm", now)
        val detailed = tx(25_000, "Swiggy", "HDFC Bank", now).copy(accountRef = "1234")
        assertEquals("Swiggy", repo.richer(generic, detailed).merchant)
        assertEquals("Swiggy", repo.richer(detailed, generic).merchant)
    }
}
