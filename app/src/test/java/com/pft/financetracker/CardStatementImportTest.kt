package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.bills.BillService
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** A card statement SMS adds no transaction, but sets up (or moves on) the card's bill. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CardStatementImportTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val bills = BillService(db.transactionDao(), db.billDao())
    private val importer = SmsImporter(
        ApplicationProvider.getApplicationContext(), SmsParser(), TransactionRepository(db.transactionDao(), db.reviewDao()),
        SmsLogRepository(db.smsLogDao()), SettingsRepository(ApplicationProvider.getApplicationContext()),
        onCardStatement = { s, bank -> bills.fromStatement(s, bank) },
    )

    @After fun tearDown() = db.close()

    @Test fun aStatementSmsCreatesTheCardBill() = runBlocking {
        importer.process(SmsMessage("VM-HDFCBK-S", "Your HDFC Bank Credit Card XX1234 statement is generated. Total Amount Due: Rs.12,345.67, Minimum Amount Due: Rs.620.00. Payment Due Date: 18-10-2026.", 1_760_000_000_000L))
        val b = bills.all().single()
        assertEquals("HDFC Bank card ••1234", b.name)
        assertEquals(LocalDate.of(2026, 10, 18), b.fixedDue)
        assertTrue(db.transactionDao().getAll().isEmpty())
    }
}
