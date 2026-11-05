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
 * v1.6: a v1.5 database (version 7) keeps every payment, budget, bill and goal, gains payee rules and the person's own
 * categories, and loses the net-worth tables. Room validates the result against schemas/8.json.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MigrationV8Test {
    private val dbName = "migration-v8-test.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    @Test
    fun migrate7To8KeepsEverythingButNetWorth() {
        helper.createDatabase(dbName, 7).apply {
            execSQL(
                "INSERT INTO transactions (id, amountPaise, type, merchant, category, timestamp, bankName, accountRef, source, flow, note, smsHash, refNumber, confidence, needsReview, createdAt, originalAmountPaise, userEdited, counterpartyKind, importBatchId) " +
                    "VALUES (1, 64900, 'DEBIT', 'Netflix', 'ENTERTAINMENT', 1700000000000, 'HDFC Bank', '1234', 'SMS', 'EXPENSE', NULL, 'h1', 'R1', 90, 0, 1700000000000, NULL, 1, 'ORGANISATION', NULL)"
            )
            execSQL("INSERT INTO budgets (category, monthlyLimitPaise) VALUES ('FOOD', 500000)")
            execSQL("INSERT INTO goals (id, name, targetPaise, targetDay, startDay) VALUES (1, 'Goa trip', 4000000, NULL, 0)")
            execSQL("INSERT INTO networth_snapshots (month, totalPaise, ownPaise, owePaise) VALUES ('2026-09', 800000, 800000, 0)")
            close()
        }

        val db = helper.runMigrationsAndValidate(dbName, 8, true, AppDatabase.MIGRATION_7_8)

        db.query("SELECT amountPaise, merchant, userEdited, customCategoryId FROM transactions WHERE id = 1").use { c ->
            c.moveToNext()
            assertEquals(64_900L, c.getLong(0)); assertEquals("Netflix", c.getString(1)); assertEquals(1, c.getInt(2)); assertTrue(c.isNull(3))
        }
        db.query("SELECT monthlyLimitPaise FROM budgets WHERE category = 'FOOD'").use { c -> c.moveToNext(); assertEquals(500_000L, c.getLong(0)) }
        db.query("SELECT name FROM goals").use { c -> c.moveToNext(); assertEquals("Goa trip", c.getString(0)) }
        db.query("SELECT name FROM sqlite_master WHERE type = 'table'").use { c ->
            val tables = buildList { while (c.moveToNext()) add(c.getString(0)) }
            assertTrue("payee_rules" in tables); assertTrue("custom_categories" in tables)
            listOf("assets", "account_balances", "holdings", "networth_snapshots").forEach { assertFalse(it, it in tables) }
        }
        db.close()
    }
}
