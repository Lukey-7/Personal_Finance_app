package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A scan that cannot read the inbox must fail loudly, so the app says why instead of "No new transactions". */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SmsScanPermissionTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val importer = SmsImporter(
        ApplicationProvider.getApplicationContext(), SmsParser(), TransactionRepository(db.transactionDao(), db.reviewDao()),
        SmsLogRepository(db.smsLogDao()), SettingsRepository(ApplicationProvider.getApplicationContext()),
    )
    @After fun tearDown() = db.close()

    @Test fun withoutSmsPermissionTheScanFails() = runBlocking {
        val r = runCatching { importer.scanInbox(0L) }
        assertTrue("expected a failure, got ${r.getOrNull()}", r.exceptionOrNull() is SecurityException)
    }
}
