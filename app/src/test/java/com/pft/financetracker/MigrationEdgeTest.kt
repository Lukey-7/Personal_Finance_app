package com.pft.financetracker

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.platform.app.InstrumentationRegistry
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.repository.SplitRepository
import com.pft.financetracker.data.split.SplitEngine
import com.pft.financetracker.domain.split.SplitStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** v1.2.1: upgrades from every old version with real-looking data, then the split engine on the result. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class MigrationEdgeTest {
    private val dbName = "migration-edge.db"

    @get:Rule
    val helper = MigrationTestHelper(InstrumentationRegistry.getInstrumentation(), AppDatabase::class.java, emptyList(), FrameworkSQLiteOpenHelperFactory())

    private val day = 86_400_000L
    private val t0 = System.currentTimeMillis() - 10 * day

    private fun room(): AppDatabase = Room.databaseBuilder(ApplicationProvider.getApplicationContext<android.app.Application>(), AppDatabase::class.java, dbName)
        .addMigrations(*AppDatabase.ALL_MIGRATIONS).allowMainThreadQueries().build()

    private fun SupportSQLiteDatabase.v4tx(id: Int, paise: Long, type: String, merchant: String, cat: String, t: Long, flow: String, original: Long? = null, source: String = "SMS") =
        execSQL("INSERT INTO transactions (id, amountPaise, type, merchant, category, timestamp, bankName, accountRef, source, flow, note, smsHash, refNumber, confidence, needsReview, createdAt, originalAmountPaise, userEdited) " +
            "VALUES ($id, $paise, '$type', '$merchant', '$cat', $t, NULL, NULL, '$source', '$flow', NULL, 'h$id', NULL, 90, 0, $t, ${original ?: "NULL"}, 0)")

    @Test fun v5To6KeepsImportsAndAddsMatches() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO import_batches (id, fileName, format, importedAt, rowsFound, added, duplicates, needsReview, balanceMismatches, firstDate, lastDate) VALUES (1, 'sep.csv', 'CSV', $t0, 4, 4, 0, 0, 0, $t0, $t0)")
            execSQL("INSERT INTO transactions (id, amountPaise, type, merchant, category, timestamp, bankName, accountRef, source, flow, note, smsHash, refNumber, confidence, needsReview, createdAt, originalAmountPaise, userEdited, counterpartyKind, importBatchId) VALUES (1, 50000, 'DEBIT', 'Swiggy', 'FOOD', $t0, NULL, NULL, 'STATEMENT', 'EXPENSE', NULL, 'stmt:1', NULL, 95, 0, $t0, NULL, 0, 'ORGANISATION', 1)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 6, true, AppDatabase.MIGRATION_5_6)
        db.query("SELECT importBatchId FROM transactions WHERE id = 1").use { c -> c.moveToNext(); assertEquals(1L, c.getLong(0)) }
        db.query("SELECT COUNT(*) FROM import_matches").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
        db.query("SELECT COUNT(shareId) FROM split_links").use { c -> c.moveToNext(); assertEquals(0, c.getInt(0)) }
        db.close()
    }

    @Test fun v5To6RemembersWhichShareAnOldSettlementPaid() {
        helper.createDatabase(dbName, 5).apply {
            execSQL("INSERT INTO transactions (id, amountPaise, type, merchant, category, timestamp, bankName, accountRef, source, flow, note, smsHash, refNumber, confidence, needsReview, createdAt, originalAmountPaise, userEdited, counterpartyKind, importBatchId) VALUES (7, 50000, 'CREDIT', 'Rahul Sharma', 'INCOME', $t0, NULL, NULL, 'SMS', 'SETTLEMENT', NULL, 'h7', NULL, 90, 0, $t0, NULL, 0, 'PERSON', NULL)")
            execSQL("INSERT INTO splits (id, title, totalPaise, date, mode, payerIndex, linkedTransactionId, note, createdAt, source, status, confidence, reasons, kind) VALUES (1, 'Movie', 150000, $t0, 'EQUAL', 0, NULL, NULL, $t0, 'MANUAL', 'APPLIED', NULL, NULL, 'PAYBACK')")
            listOf("Me", "Priya", "Rahul").forEachIndexed { i, n -> execSQL("INSERT INTO split_people (splitId, personIndex, name, isMe) VALUES (1, $i, '$n', ${if (i == 0) 1 else 0})") }
            (0..2).forEach { i -> execSQL("INSERT INTO split_shares (id, splitId, personIndex, amountPaise, settledPaise) VALUES (${10 + i}, 1, $i, 50000, ${if (i == 2) 50000 else 0})") }
            execSQL("INSERT INTO split_links (splitId, transactionId, role, allocatedPaise, prevFlow, prevAmountPaise) VALUES (1, 7, 'PAYBACK', 50000, 'INCOME', NULL)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 6, true, AppDatabase.MIGRATION_5_6)
        db.query("SELECT shareId FROM split_links").use { c -> c.moveToNext(); assertEquals("Rahul's share", 12L, c.getLong(0)) }
        db.close()
    }

    @Test fun anEmptyV1DatabaseUpgradesAllTheWay() {
        helper.createDatabase(dbName, 1).close()
        helper.runMigrationsAndValidate(dbName, AppDatabase.ALL_MIGRATIONS.last().endVersion, true, *AppDatabase.ALL_MIGRATIONS).close()
    }

    @Test fun aV1DatabaseWithDataUpgradesInOneChain() {
        helper.createDatabase(dbName, 1).apply {
            execSQL("INSERT INTO transactions (id, amount, type, merchant, category, timestamp, bankName, accountRef, source, note, smsHash, confidence, needsReview, createdAt) VALUES (1, 1234.56, 'DEBIT', 'Swiggy', 'FOOD', $t0, 'HDFC', '1234', 'SMS', NULL, 'h1', 90, 0, $t0)")
            execSQL("INSERT INTO transactions (id, amount, type, merchant, category, timestamp, bankName, accountRef, source, note, smsHash, confidence, needsReview, createdAt) VALUES (2, 85000.0, 'CREDIT', 'Acme', 'INCOME', $t0, 'HDFC', '1234', 'SMS', NULL, 'h2', 90, 0, $t0)")
            execSQL("INSERT INTO budgets (category, monthlyLimit) VALUES ('FOOD', 5000.5)")
            execSQL("INSERT INTO review_queue (id, sender, body, receivedAt, smsHash, guessedAmount, guessedType, reason) VALUES (1, 'VM-HDFC', 'x', $t0, 'r1', 99.99, 'DEBIT', 'unsure')")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, AppDatabase.ALL_MIGRATIONS.last().endVersion, true, *AppDatabase.ALL_MIGRATIONS)
        db.query("SELECT amountPaise, flow FROM transactions ORDER BY id").use { c ->
            c.moveToNext(); assertEquals(123_456L, c.getLong(0)); assertEquals("EXPENSE", c.getString(1))
            c.moveToNext(); assertEquals(8_500_000L, c.getLong(0)); assertEquals("INCOME", c.getString(1))
        }
        db.query("SELECT monthlyLimitPaise FROM budgets").use { c -> c.moveToNext(); assertEquals(500_050L, c.getLong(0)) }
        db.query("SELECT guessedAmountPaise FROM review_queue").use { c -> c.moveToNext(); assertEquals(9_999L, c.getLong(0)) }
        db.close()
    }

    @Test fun v3To4OddRowsAreLeftSafe() {
        helper.createDatabase(dbName, 3).apply {
            v4tx(1, 90_000, "DEBIT", "Dinner", "FOOD", t0, "EXPENSE")
            v4tx(2, 60_000, "DEBIT", "Cab", "TRANSPORT", t0, "EXPENSE", source = "MANUAL")
            v4tx(3, 12_345, "DEBIT", "Edited", "FOOD", t0, "EXPENSE")
            // Two splits on transaction 1 (a v1.1 double tap), one split with no "me", one on a manual row.
            for ((sid, tx) in listOf(1 to 1, 2 to 1, 3 to 2, 4 to 3)) execSQL("INSERT INTO splits (id, title, totalPaise, date, mode, payerIndex, linkedTransactionId, note, createdAt) VALUES ($sid, 'S', 90000, $t0, 'EQUAL', 0, $tx, NULL, $t0)")
            for (sid in listOf(1, 2, 4)) execSQL("INSERT INTO split_people (splitId, personIndex, name, isMe) VALUES ($sid, 0, 'Me', 1)")
            for (sid in 1..4) execSQL("INSERT INTO split_shares (splitId, personIndex, amountPaise, settledPaise) VALUES ($sid, 0, 30000, 0)")
            close()
        }
        val db = helper.runMigrationsAndValidate(dbName, 4, true, AppDatabase.MIGRATION_3_4)
        db.query("SELECT id, amountPaise, originalAmountPaise FROM transactions ORDER BY id").use { c ->
            c.moveToNext(); assertEquals(30_000L, c.getLong(1)); assertEquals(90_000L, c.getLong(2))
            c.moveToNext(); assertEquals("manual row untouched", 60_000L, c.getLong(1)); assertTrue(c.isNull(2))
            c.moveToNext(); assertEquals("neither total nor share: a person's edit", 12_345L, c.getLong(1)); assertTrue(c.isNull(2))
        }
        db.close()
    }

    @Test fun v4SplitsWithMissingPeopleOrMissingTransactionsDoNotBreakTheEngine() = runBlocking {
        helper.createDatabase(dbName, 4).apply {
            v4tx(1, 30_000, "DEBIT", "Dinner", "FOOD", t0, "EXPENSE", original = 90_000)
            execSQL("INSERT INTO splits (id, title, totalPaise, date, mode, payerIndex, linkedTransactionId, note, createdAt) VALUES (1, 'No people', 90000, $t0, 'EQUAL', 0, 1, NULL, $t0)")
            execSQL("INSERT INTO split_shares (splitId, personIndex, amountPaise, settledPaise) VALUES (1, 0, 30000, 0)")
            execSQL("INSERT INTO splits (id, title, totalPaise, date, mode, payerIndex, linkedTransactionId, note, createdAt) VALUES (2, 'Gone', 50000, $t0, 'EQUAL', 0, 999, NULL, $t0)")
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5).close()
        val db = room()
        try {
            SplitEngine(db.transactionDao(), db.splitDao(), null, { false }, { "Me" }).run()
            assertEquals(2, db.splitDao().allSplits().size)
            assertTrue(db.splitDao().allSplits().all { it.source == "MANUAL" })
            SplitRepository(db.splitDao()).all.first() // the Split screen can read them
            assertEquals(30_000L, db.transactionDao().getById(1)!!.amountPaise)
        } finally { db.close() }
    }

    @Test fun aV11ManualSplitsUnsettledPaybacksAreNotClaimedByAnotherPayment() = runBlocking {
        helper.createDatabase(dbName, 4).apply {
            v4tx(1, 100_000, "DEBIT", "Toit", "FOOD", t0, "EXPENSE", original = 300_000)
            v4tx(2, 100_000, "CREDIT", "Rahul Sharma", "INCOME", t0 + day, "INCOME")
            v4tx(3, 100_000, "CREDIT", "Priya Nair", "INCOME", t0 + day + 60_000, "INCOME")
            v4tx(4, 300_000, "DEBIT", "Myntra", "SHOPPING", t0 + 3_600_000, "EXPENSE")
            execSQL("INSERT INTO splits (id, title, totalPaise, date, mode, payerIndex, linkedTransactionId, note, createdAt) VALUES (1, 'Toit', 300000, $t0, 'EQUAL', 0, 1, NULL, $t0)")
            listOf("Me", "Rahul", "Priya").forEachIndexed { i, n -> execSQL("INSERT INTO split_people (splitId, personIndex, name, isMe) VALUES (1, $i, '$n', ${if (i == 0) 1 else 0})") }
            (0..2).forEach { i -> execSQL("INSERT INTO split_shares (splitId, personIndex, amountPaise, settledPaise) VALUES (1, $i, 100000, 0)") }
            close()
        }
        helper.runMigrationsAndValidate(dbName, 5, true, AppDatabase.MIGRATION_4_5).close()
        val db = room()
        try {
            SplitEngine(db.transactionDao(), db.splitDao(), null, { false }, { "Me" }).run()
            val applied = db.splitDao().allSplits().filter { it.source != "MANUAL" && it.status == SplitStatus.APPLIED.name }.map { it.id }.toSet()
            val claimed = db.splitDao().allLinks().filter { it.splitId in applied }.map { it.transactionId }
            assertTrue("Rahul's and Priya's paybacks for the Toit split were given to Myntra: $claimed", claimed.none { it == 2L || it == 3L })
            assertEquals(300_000L, db.transactionDao().getById(4)!!.amountPaise)
        } finally { db.close() }
    }
}
