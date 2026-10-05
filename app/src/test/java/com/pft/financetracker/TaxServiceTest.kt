package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.data.tax.TaxService
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.TaxSection
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.ZoneId

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TaxServiceTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val tax = TaxService(db.transactionDao(), db.taxDao(), zone)
    private val fy = FinancialYear(2026)
    @After fun tearDown() = db.close()

    private fun pay(m: String, paise: Long) = runBlocking {
        db.transactionDao().insert(Transaction(amountPaise = paise, type = TransactionType.DEBIT, merchant = m, category = Category.OTHER,
            timestamp = LocalDate.of(2026, 10, 1).atTime(12, 0).atZone(zone).toInstant().toEpochMilli(), bankName = null, accountRef = null,
            source = Transaction.Source.SMS, flow = Flow.EXPENSE).toEntity())
    }

    @Test fun tagsAPersonSetsAreKeptAndCanBeCleared() = runBlocking {
        val swiggy = pay("Swiggy", 500_00); val lic = pay("LIC premium", 10_000_00)
        tax.tag(swiggy, TaxSection.S80G); tax.tag(lic, null)
        assertEquals(listOf(TaxSection.S80G), tax.summary(fy).map { it.section })
        tax.clearTag(lic)
        assertEquals(listOf(TaxSection.S80C, TaxSection.S80G), tax.summary(fy).map { it.section })
    }

    @Test fun theCsvListsEachPaymentUnderItsSectionAndEscapesFormulas() = runBlocking {
        pay("LIC premium", 10_000_00); pay("=HYPERLINK(evil) donation", 1_000_00)
        val csv = tax.csv(fy)
        val lines = csv.trim().lines()
        assertEquals("Financial year,Section,What,Date,Payee,Amount (INR)", lines[0])
        assertTrue(lines.any { it.startsWith("FY 2026-27,80C,") && it.endsWith(",10000.00") })
        assertTrue(csv, lines.any { it.contains("'=HYPERLINK") })
    }

    @Test fun deletingTheTransactionDropsItsTag() = runBlocking {
        val swiggy = pay("Swiggy", 500_00)
        tax.tag(swiggy, TaxSection.S80G)
        db.transactionDao().delete(db.transactionDao().getById(swiggy)!!)
        assertTrue(db.taxDao().getAll().isEmpty())
    }
}
