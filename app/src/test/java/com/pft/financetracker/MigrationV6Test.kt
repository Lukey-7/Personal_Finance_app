package com.pft.financetracker

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.pft.financetracker.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.3: a v1.2 database keeps every row and gains the v1.3 tables. Room validates the result against schemas/6.json. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MigrationV6Test {
    private val dbName = "migration-v6-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun migrate5To6KeepsTransactionsAndBudgets() {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO transactions (id, amountPaise, type, merchant, category, timestamp, bankName, accountRef, source, flow, note, smsHash, refNumber, confidence, needsReview, createdAt, originalAmountPaise, userEdited, counterpartyKind, importBatchId) " +
                    "VALUES (1, 64900, 'DEBIT', 'Netflix', 'ENTERTAINMENT', 1700000000000, 'HDFC Bank', '1234', 'SMS', 'EXPENSE', NULL, 'h1', 'R1', 90, 0, 1700000000000, NULL, 1, 'ORGANISATION', NULL)"
            )
            execSQL("INSERT INTO budgets (category, monthlyLimitPaise) VALUES ('FOOD', 500000)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 6, true, AppDatabase.MIGRATION_5_6)

        db.query("SELECT amountPaise, merchant, flow, userEdited FROM transactions WHERE id = 1").use { c ->
            c.moveToNext()
            assertEquals(64_900L, c.getLong(0)); assertEquals("Netflix", c.getString(1)); assertEquals("EXPENSE", c.getString(2)); assertEquals(1, c.getInt(3))
        }
        db.query("SELECT monthlyLimitPaise FROM budgets WHERE category = 'FOOD'").use { c -> c.moveToNext(); assertEquals(500_000L, c.getLong(0)) }
        db.close()
    }
}
