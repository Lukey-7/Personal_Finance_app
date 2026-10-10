package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.importer.StatementImporter
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.data.split.SplitEngine
import com.pft.financetracker.domain.importer.CsvReader
import com.pft.financetracker.domain.importer.ImportFormat
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

/** Statement import against a real Room database: duplicates with SMS, re-imports, undo, and split detection. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class StatementImporterTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var sms: SmsImporter
    private lateinit var stmt: StatementImporter
    private lateinit var engine: SplitEngine

    private val sat = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -9); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun at(day: Int, h: Int, m: Int = 0) = sat + day * 86_400_000L + h * 3_600_000L + m * 60_000L
    private fun ddmmyy(t: Long) = SimpleDateFormat("dd/MM/yy", Locale.ENGLISH).format(t)
    private fun smsDate(t: Long) = SimpleDateFormat("dd-MM-yy", Locale.ENGLISH).format(t)

    private val friends = listOf("NEHA JOSHI", "KARAN MEHTA", "SNEHA RAO")
    /** (time, name, vpa, debit rupees, credit rupees) */
    private val weekend = listOf(
        Triple(at(0, 20, 10), "SBOW" to "sbow.pay@ybl", -5_000),
        Triple(at(1, 11), "RAHUL SHARMA" to "rahul@okicici", 1_000),
    ) + friends.mapIndexed { i, n -> Triple(at(1, 12, i), n to (n.substringBefore(' ').lowercase() + "@okhdfcbank"), 1_000) }

    private fun csv(): ByteArray {
        var bal = 20_000_00L
        val body = weekend.joinToString("\n") { (t, who, amt) ->
            bal += amt * 100L
            val dr = if (amt < 0) "%.2f".format(-amt.toDouble()) else ""
            val cr = if (amt > 0) "%.2f".format(amt.toDouble()) else ""
            "${ddmmyy(t)},UPI-${who.first}-${who.second}-HDFC0001234-PAYMENT,$dr,$cr,%.2f".format(bal / 100.0)
        }
        return ("Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n$body").toByteArray()
    }

    private fun smsFor(t: Long, who: Pair<String, String>, amt: Int) = if (amt < 0)
        SmsMessage("VM-HDFCBK", "Rs.${-amt}.00 debited from a/c **1234 on ${smsDate(t)} to VPA ${who.second}.", t)
    else SmsMessage("VM-KOTAKB", "Received Rs.$amt.00 in your Kotak Bank AC X1234 from ${who.second} on ${smsDate(t)}.", t)

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        sms = SmsImporter(ctx, SmsParser(), repo, SmsLogRepository(db.smsLogDao()), SettingsRepository(ctx))
        stmt = StatementImporter(db.transactionDao(), repo, db.importDao())
        engine = SplitEngine(db.transactionDao(), db.splitDao(), null, { false }, { "Me" })
    }

    @After fun tearDown() { db.close() }

    private fun parsed() = StatementInterpreter.interpret(CsvReader.read(csv()), ImportFormat.CSV)

    @Test fun statementOfPaymentsAlreadySeenBySmsAddsNothing() = runBlocking {
        weekend.forEach { (t, who, amt) -> sms.process(smsFor(t, who, amt)) }
        assertEquals(5, repo.getAll().size)
        val p = stmt.preview(parsed(), "hdfc.csv")
        assertEquals(0, p.newRows.size)
        assertEquals(5, p.duplicates.size)
        stmt.commit(p)
        assertEquals(5, repo.getAll().size)
    }

    @Test fun importThenSplitThenReimportThenUndo() = runBlocking {
        val batch = stmt.commit(stmt.preview(parsed(), "hdfc.csv"))
        assertEquals(5, batch.added)
        assertEquals(5, repo.getAll().count { it.source == Transaction.Source.STATEMENT })
        engine.run()
        assertEquals("Rs 5,000 for 5, four paid back", 1_000_00L, repo.getAll().first { it.merchant.contains("Sbow", true) }.amountPaise)

        val again = stmt.preview(parsed(), "hdfc.csv")
        assertEquals(0, again.newRows.size)

        stmt.undo(batch.id)
        engine.run()
        assertEquals(0, repo.getAll().size)
        assertEquals(0, db.importDao().observeBatches().first().size)
    }

    @Test fun smsArrivingAfterAStatementImportIsNotCountedTwice() = runBlocking {
        stmt.commit(stmt.preview(parsed(), "hdfc.csv"))
        weekend.forEach { (t, who, amt) -> sms.process(smsFor(t, who, amt)) }
        assertEquals(5, repo.getAll().size)
        // The SMS knows the minute; the statement only knew the day.
        assertEquals(at(0, 20, 10), repo.getAll().first { it.merchant.contains("Sbow", true) }.timestamp)
    }

    @Test fun unreadableRowsGoToReview() = runBlocking {
        val bad = "Date,Narration,Withdrawal,Deposit,Balance\n${ddmmyy(at(0, 1))},UPI-SBOW-sbow@ybl,4000.00,,16000.00\n${ddmmyy(at(1, 1))},UPI-RAHUL-rahul@okicici,,1000.00,99999.00"
        val b = stmt.commit(stmt.preview(StatementInterpreter.interpret(CsvReader.read(bad.toByteArray()), ImportFormat.CSV), "bad.csv"))
        assertEquals(1, b.added)
        assertEquals(1, b.needsReview)
        assertEquals("statement_balance_mismatch", repo.reviewQueue.first().single().reason)
    }

    // ---- v1.5 review: references and deletions ----

    private val refHead = "Date,Narration,Chq./Ref.No.,Withdrawal Amt.,Deposit Amt.,Closing Balance"
    private fun ddmmyyyy(t: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(t)
    private fun statement(rows: String) = StatementInterpreter.interpret(CsvReader.read("$refHead\n$rows".toByteArray()), ImportFormat.CSV)
    private suspend fun smsRow(type: TransactionType, merchant: String, ref: String, at: Long) = repo.insert(
        Transaction(amountPaise = 50_000, type = type, merchant = merchant, category = Category.OTHER, timestamp = at, bankName = "HDFC Bank", accountRef = null,
            source = Transaction.Source.SMS, flow = if (type == TransactionType.DEBIT) Flow.EXPENSE else Flow.INCOME, smsHash = "sms-$merchant-$ref", refNumber = ref)
    )

    /** Money received and money paid can share a reference number; only the same direction is the same payment. */
    @Test fun aRefMatchNeedsTheSameDirection() = runBlocking {
        smsRow(TransactionType.CREDIT, "Kiran Rao", "412345678901", at(1, 11))
        val p = stmt.preview(statement("${ddmmyyyy(at(1, 0))},UPI-BUNDL TECHNOLOGIES-PAYMENT,412345678901,500.00,,9500.00"), "x.csv")
        assertEquals(1, p.newRows.size)
    }

    /** Statements pad references with zeros; the SMS has the bare RRN. */
    @Test fun aPaddedStatementRefMatchesTheSmsRef() = runBlocking {
        smsRow(TransactionType.DEBIT, "Swiggy", "427712345678", at(1, 13))
        val p = stmt.preview(statement("${ddmmyyyy(at(1, 0))},UPI-BUNDL TECHNOLOGIES-PAYMENT,000427712345678,500.00,,9500.00"), "x.csv")
        assertEquals(0, p.newRows.size)
    }

    /** A row the person deleted does not come back when the same statement is imported again. */
    @Test fun aDeletedStatementRowStaysDeletedOnReimport() = runBlocking {
        val withLog = StatementImporter(db.transactionDao(), repo, db.importDao(), SmsLogRepository(db.smsLogDao()))
        withLog.commit(withLog.preview(parsed(), "hdfc.csv"))
        assertEquals(5, repo.getAll().size)
        val sbow = repo.getAll().first { it.merchant.contains("Sbow", true) }
        repo.delete(sbow)
        sms.forgetDeleted(sbow)
        val again = withLog.preview(parsed(), "hdfc.csv")
        assertEquals(0, again.newRows.size)
        assertEquals(1, again.deletedBefore.size)
        assertEquals(0, withLog.commit(again).added)
        assertEquals(4, repo.getAll().size)
    }
}
