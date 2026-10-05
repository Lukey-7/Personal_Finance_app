package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.bills.BillService
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.bills.BillState
import com.pft.financetracker.domain.bills.CardStatement
import com.pft.financetracker.domain.bills.Loan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BillServiceTest {
    private lateinit var db: AppDatabase
    private lateinit var bills: BillService
    private val zone = ZoneId.of("Asia/Kolkata")

    @Before fun setUp() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
        bills = BillService(db.transactionDao(), db.billDao(), zone)
    }
    @After fun tearDown() = db.close()

    @Test fun aBillAndALoanRoundTrip() = runBlocking {
        val loan = Bill(name = "Car loan", amountPaise = null, dueDay = 10, keyword = "hdfc loan", loan = Loan(5_00_000_00L, 900, 36, LocalDate.of(2026, 1, 10)))
        val id = bills.save(loan)
        assertEquals(loan.copy(id = id), bills.all().single())
    }

    @Test fun aCardStatementCreatesThenUpdatesOneBillPerCard() = runBlocking {
        bills.fromStatement(CardStatement("1234", 12_345_67L, 620_00L, LocalDate.of(2026, 10, 18)), "HDFC Bank")
        bills.fromStatement(CardStatement("1234", 8_000_00L, 400_00L, LocalDate.of(2026, 11, 18)), "HDFC Bank")
        val b = bills.all().single()
        assertEquals("HDFC Bank card ••1234", b.name)
        assertEquals(8_000_00L, b.amountPaise)
        assertEquals(LocalDate.of(2026, 11, 18), b.fixedDue)
    }

    @Test fun markingPaidByHandShowsPaidAndCanBeUndone() = runBlocking {
        val id = bills.save(Bill(name = "Rent", amountPaise = 25_000_00L, dueDay = 5, keyword = null))
        val today = LocalDate.of(2026, 10, 6)
        assertTrue(bills.states(today).single().second is BillState.Overdue)
        bills.markPaid(id, LocalDate.of(2026, 10, 5))
        assertEquals(BillState.Paid(LocalDate.of(2026, 10, 5), null), bills.states(today).single().second)
        bills.unmarkPaid(id, LocalDate.of(2026, 10, 5))
        assertTrue(bills.states(today).single().second is BillState.Overdue)
    }

    @Test fun upcomingBillsBecomeReminders() = runBlocking {
        bills.save(Bill(name = "Rent", amountPaise = 25_000_00L, dueDay = 5, keyword = null))
        val now = LocalDate.of(2026, 10, 2).atTime(9, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(listOf("Rent is due soon"), bills.reminders(now).map { it.title })
    }

    @Test fun deletingABillDropsItsMarks() = runBlocking {
        val id = bills.save(Bill(name = "Rent", amountPaise = 25_000_00L, dueDay = 5, keyword = null))
        bills.markPaid(id, LocalDate.of(2026, 10, 5))
        bills.delete(id)
        assertTrue(bills.all().isEmpty())
        assertTrue(db.billDao().marksFor(id).isEmpty())
    }

    @Test fun anOlderStatementReadLaterDoesNotMoveTheBillBack() = runBlocking {
        bills.fromStatement(CardStatement("1234", 8_000_00L, 400_00L, LocalDate.of(2026, 11, 18)), "HDFC Bank")
        bills.fromStatement(CardStatement("1234", 12_345_67L, 620_00L, LocalDate.of(2026, 10, 18)), "HDFC Bank")
        assertEquals(LocalDate.of(2026, 11, 18), bills.all().single().fixedDue)
        assertEquals(8_000_00L, bills.all().single().amountPaise)
    }
}
