package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.backup.BackupService
import com.pft.financetracker.data.bills.BillService
import com.pft.financetracker.data.goals.GoalService
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.domain.backup.BackupCodec
import com.pft.financetracker.domain.backup.BackupException
import com.pft.financetracker.domain.bills.Bill
import com.pft.financetracker.domain.goals.Goal
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class BackupTest {
    private val codec = BackupCodec(iterations = 1_000)   // the app uses 600,000; fewer keep the test quick
    private fun db() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val a = db()
    private val b = db()
    @After fun tearDown() { a.close(); b.close() }

    // ---- Codec ----

    @Test fun theCodecRoundTrips() {
        val bytes = codec.encrypt("""{"hello":"₹ world"}""", "correct horse".toCharArray())
        assertEquals("""{"hello":"₹ world"}""", codec.decrypt(bytes, "correct horse".toCharArray()))
    }

    @Test fun theFileHoldsNoPlainText() {
        val bytes = codec.encrypt("""{"merchant":"SWIGGY"}""", "correct horse".toCharArray())
        assertFalse(String(bytes, Charsets.ISO_8859_1).contains("SWIGGY"))
    }

    @Test fun twoBackupsOfTheSameDataDiffer() {
        val x = codec.encrypt("same", "pw-pw-pw".toCharArray()); val y = codec.encrypt("same", "pw-pw-pw".toCharArray())
        assertFalse(x.contentEquals(y))
    }

    @Test fun aWrongPassphraseIsRefused() {
        val bytes = codec.encrypt("data", "correct horse".toCharArray())
        assertThrows(BackupException.CannotOpen::class.java) { codec.decrypt(bytes, "wrong horse".toCharArray()) }
    }

    @Test fun aDamagedFileIsRefused() {
        val bytes = codec.encrypt("data", "correct horse".toCharArray())
        bytes[bytes.size - 3] = (bytes[bytes.size - 3].toInt() xor 1).toByte()
        assertThrows(BackupException.CannotOpen::class.java) { codec.decrypt(bytes, "correct horse".toCharArray()) }
    }

    @Test fun anotherFileIsNotABackup() {
        assertThrows(BackupException.NotABackup::class.java) { codec.decrypt("%PDF-1.7 whatever".toByteArray(), "x".toCharArray()) }
    }

    // ---- Whole database ----

    private fun seed(db: AppDatabase) = runBlocking {
        db.transactionDao().insert(Transaction(amountPaise = 450_00, type = TransactionType.DEBIT, merchant = "Swiggy", category = Category.FOOD, timestamp = 1L,
            bankName = "HDFC Bank", accountRef = "1234", source = Transaction.Source.SMS, flow = Flow.EXPENSE, note = "line1\nline2 \"quoted\"").toEntity())
        BillService(db.transactionDao(), db.billDao()).save(Bill(name = "Rent", amountPaise = 25_000_00, dueDay = 5, keyword = null))
        val g = GoalService(db.goalDao()); val id = g.save(Goal(name = "Trip", targetPaise = 1_00_000_00, targetDate = null)); g.contribute(id, 5_000_00)
    }

    @Test fun everyTableComesBackExactly() = runBlocking {
        seed(a)
        val file = BackupService(a, codec).export("correct horse".toCharArray())
        BackupService(b, codec).restore(file, "correct horse".toCharArray())
        for (table in BackupService(a, codec).tables()) {
            assertEquals(table, dump(a, table), dump(b, table))
        }
        assertEquals("line1\nline2 \"quoted\"", b.transactionDao().getAll().single().note)
        assertTrue(BackupService(a, codec).tables().containsAll(listOf("transactions", "bills", "goals", "goal_contributions", "refund_links", "networth_snapshots")))
    }

    @Test fun restoringReplacesWhatWasThere() = runBlocking {
        seed(a)
        val file = BackupService(a, codec).export("correct horse".toCharArray())
        b.transactionDao().insert(Transaction(amountPaise = 1, type = TransactionType.DEBIT, merchant = "Old", category = Category.OTHER, timestamp = 9L,
            bankName = null, accountRef = null, source = Transaction.Source.MANUAL, flow = Flow.EXPENSE).toEntity())
        BackupService(b, codec).restore(file, "correct horse".toCharArray())
        assertEquals(listOf("Swiggy"), b.transactionDao().getAll().map { it.merchant })
    }

    @Test fun aFailedRestoreLeavesTheDataAlone() = runBlocking {
        seed(b)
        val before = dump(b, "transactions")
        assertThrows(BackupException.CannotOpen::class.java) { runBlocking { BackupService(b, codec).restore(codec.encrypt("{}", "x".toCharArray()), "y".toCharArray()) } }
        assertArrayEquals(before.toTypedArray(), dump(b, "transactions").toTypedArray())
    }

    @Test fun aBackupFromANewerVersionIsRefused() {
        val newer = codec.encrypt("""{"app":"FinTrack","schema":99,"tables":{}}""", "pw".toCharArray())
        assertThrows(BackupException.TooNew::class.java) { BackupService(b, codec).restore(newer, "pw".toCharArray()) }
    }

    private fun dump(db: AppDatabase, table: String): List<String> {
        val out = mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT * FROM `$table` ORDER BY 1").use { c ->
            while (c.moveToNext()) out += (0 until c.columnCount).joinToString("|") { i -> if (c.isNull(i)) "∅" else c.getString(i) }
        }
        return out
    }
}
