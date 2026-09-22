package com.pft.financetracker

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.platform.app.InstrumentationRegistry
import com.pft.financetracker.data.local.AppDatabase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Phase 2: a v1.0.0 database with real rows must survive the upgrade with every amount, budget and review
 * item intact, and end up matching the v2 schema Room expects (MigrationTestHelper validates that).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class) // plain Application: FinanceApp would load SQLCipher natives
class MigrationTest {
    private val dbName = "migration-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun migrate1To2KeepsDataAndConvertsToPaise() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO transactions (id, amount, type, merchant, category, timestamp, bankName, accountRef, source, note, smsHash, confidence, needsReview, createdAt) VALUES (1, 250.5, 'DEBIT', 'Swiggy', 'FOOD', 1700000000000, 'HDFC Bank', '1234', 'SMS', NULL, 'hash1', 90, 0, 1700000000000)")
            execSQL("INSERT INTO transactions (id, amount, type, merchant, category, timestamp, bankName, accountRef, source, note, smsHash, confidence, needsReview, createdAt) VALUES (2, 45000.0, 'CREDIT', 'Salary', 'INCOME', 1700000000001, 'SBI', '5678', 'SMS', 'note', 'hash2', 95, 0, 1700000000001)")
            execSQL("INSERT INTO transactions (id, amount, type, merchant, category, timestamp, bankName, accountRef, source, note, smsHash, confidence, needsReview, createdAt) VALUES (3, 2000.0, 'DEBIT', 'ATM', 'ATM', 1700000000002, 'Kotak', '5555', 'SMS', NULL, 'hash3', 80, 0, 1700000000002)")
            execSQL("INSERT INTO transactions (id, amount, type, merchant, category, timestamp, bankName, accountRef, source, note, smsHash, confidence, needsReview, createdAt) VALUES (4, 0.1, 'DEBIT', 'Tiny', 'OTHER', 1700000000003, NULL, NULL, 'MANUAL', NULL, NULL, 100, 0, 1700000000003)")
            execSQL("INSERT INTO budgets (category, monthlyLimit) VALUES ('FOOD', 5000.0)")
            execSQL("INSERT INTO review_queue (id, sender, body, receivedAt, smsHash, guessedAmount, guessedType, reason) VALUES (1, 'XX-UNK', 'Txn of Rs.350 on card', 1700000000004, 'hash4', 350.0, 'DEBIT', 'type_ambiguous')")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 2, true, AppDatabase.MIGRATION_1_2)

        db.query("SELECT id, amountPaise, flow, refNumber FROM transactions ORDER BY id").use { c ->
            assertEquals(4, c.count)
            c.moveToNext(); assertEquals(25_050L, c.getLong(1)); assertEquals("EXPENSE", c.getString(2)); assertTrue(c.isNull(3))
            c.moveToNext(); assertEquals(4_500_000L, c.getLong(1)); assertEquals("INCOME", c.getString(2))
            c.moveToNext(); assertEquals(200_000L, c.getLong(1)); assertEquals("CASH", c.getString(2))
            c.moveToNext(); assertEquals(10L, c.getLong(1))
        }
        db.query("SELECT monthlyLimitPaise FROM budgets WHERE category = 'FOOD'").use { c -> c.moveToNext(); assertEquals(500_000L, c.getLong(0)) }
        db.query("SELECT guessedAmountPaise, body FROM review_queue").use { c -> c.moveToNext(); assertEquals(35_000L, c.getLong(0)); assertEquals("Txn of Rs.350 on card", c.getString(1)) }
        db.query("SELECT COUNT(*) FROM sms_log").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(*) FROM splits").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
        // Unique index on smsHash must still exist after the rebuild.
        db.query("SELECT COUNT(*) FROM sqlite_master WHERE type='index' AND name='index_transactions_smsHash'").use { c -> c.moveToNext(); assertEquals(1, c.getInt(0)) }
        assertFalse(db.isReadOnly)
        db.close()
    }
}
