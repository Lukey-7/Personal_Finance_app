package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.bills.BillService
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.networth.NetWorthService
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A bank alert's "Avl Bal" keeps that account's balance current for net worth. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BalanceImportTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val nw = NetWorthService(db.netWorthDao(), BillService(db.transactionDao(), db.billDao()))
    private val importer = SmsImporter(
        ApplicationProvider.getApplicationContext(), SmsParser(), TransactionRepository(db.transactionDao(), db.reviewDao()),
        SmsLogRepository(db.smsLogDao()), SettingsRepository(ApplicationProvider.getApplicationContext()),
        onBalance = { ref, bank, paise, at -> nw.recordBalance(ref, bank, paise, at) },
    )
    @After fun tearDown() = db.close()

    @Test fun theBalanceInADebitAlertIsRecordedForItsAccount() = runBlocking {
        importer.process(SmsMessage("VM-HDFCBK-S", "Rs.450.00 debited from a/c **1234 on 03-10-26 to VPA swiggy@ybl (UPI Ref No 426212345678). Avl Bal INR 5,000.00", 1_760_000_000_000L))
        val b = db.netWorthDao().getBalances().single()
        assertEquals("1234", b.accountRef); assertEquals(5_000_00L, b.balancePaise); assertEquals("HDFC Bank", b.bankName)
    }
}
