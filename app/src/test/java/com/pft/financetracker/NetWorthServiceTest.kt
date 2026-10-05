package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.bills.BillService
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.networth.NetWorthService
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.Loan
import com.pft.financetracker.domain.networth.Asset
import com.pft.financetracker.domain.networth.AssetKind
import com.pft.financetracker.domain.networth.Holding
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class NetWorthServiceTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val bills = BillService(db.transactionDao(), db.billDao())
    private val nw = NetWorthService(db.netWorthDao(), bills)
    private val today = LocalDate.of(2026, 10, 15)
    @After fun tearDown() = db.close()

    @Test fun theNewestSmsBalanceWinsPerAccount() = runBlocking {
        nw.recordBalance("1234", "HDFC Bank", 50_000_00, at = 2_000L)
        nw.recordBalance("1234", "HDFC Bank", 40_000_00, at = 1_000L)    // older message read later
        nw.recordBalance("1234", "HDFC Bank", 45_000_00, at = 3_000L)
        assertEquals(45_000_00L, nw.summary(today).ownPaise)
    }

    @Test fun aNewCasReplacesTheOldHoldings() = runBlocking {
        nw.replaceHoldings(listOf(Holding("1", "A", 10_000_00, today), Holding("2", "B", 20_000_00, today)))
        nw.replaceHoldings(listOf(Holding("1", "A", 12_000_00, today)))
        assertEquals(12_000_00L, nw.summary(today).ownPaise)
    }

    @Test fun loansFromBillsCountAsDebt() = runBlocking {
        bills.save(Bill(name = "Car loan", amountPaise = null, dueDay = 10, keyword = null, loan = Loan(5_00_000_00L, 900, 36, LocalDate.of(2026, 1, 10))))
        nw.saveAsset(Asset(name = "FD", kind = AssetKind.FD, valuePaise = 6_00_000_00))
        val s = nw.summary(today)
        assertEquals(6_00_000_00L, s.ownPaise)
        assertEquals(com.pft.financetracker.domain.bills.BillTracker.loanProgress(bills.all().single(), today)!!.outstandingPaise, s.owePaise)
    }

    @Test fun oneSnapshotPerMonthKeepsTheLatestFigure() = runBlocking {
        nw.saveAsset(Asset(name = "FD", kind = AssetKind.FD, valuePaise = 1_00_000_00))
        nw.snapshot(today)
        val id = db.netWorthDao().getAssets().single().id
        nw.saveAsset(Asset(id = id, name = "FD", kind = AssetKind.FD, valuePaise = 1_10_000_00))
        nw.snapshot(today.plusDays(3))
        nw.snapshot(today.plusMonths(1))
        assertEquals(listOf("2026-10" to 1_10_000_00L, "2026-11" to 1_10_000_00L), nw.history().map { it.month to it.totalPaise })
    }
}
