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
import com.pft.financetracker.domain.importer.PositionedTable
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.importer.Word
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import com.pft.financetracker.domain.split.SplitStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

/** v1.2.1 edge cases for storing imports: identical rows, placeholder refs, SMS meeting statements, undo. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class EdgeStatementImporterTest {
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
    private fun d(t: Long) = SimpleDateFormat("dd/MM/yyyy", Locale.ENGLISH).format(t)
    private fun smsDate(t: Long) = SimpleDateFormat("dd-MM-yy", Locale.ENGLISH).format(t)
    private fun parse(csv: String) = StatementInterpreter.interpret(CsvReader.read(csv.toByteArray()), ImportFormat.CSV)
    private suspend fun import(csv: String, name: String = "s.csv") = stmt.commit(stmt.preview(parse(csv), name))

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        sms = SmsImporter(ctx, SmsParser(), repo, SmsLogRepository(db.smsLogDao()), SettingsRepository(ctx))
        stmt = StatementImporter(db.transactionDao(), repo, db.importDao())
        engine = SplitEngine(db.transactionDao(), db.splitDao(), null, { false }, { "Me" })
    }

    @After fun tearDown() { db.close() }

    // ---- 1. Identical rows ----------------------------------------------------------------------------------

    @Test fun twoIdenticalRowsInACardStatementAreBothImportedAndReimportAddsNothing() = runBlocking {
        val csv = "Date,Description,Amount\n${d(at(1, 0))},CHAI POINT BANGALORE,20.00\n${d(at(1, 0))},CHAI POINT BANGALORE,20.00\n${d(at(2, 0))},PAYMENT RECEIVED THANK YOU,5000.00 Cr"
        assertEquals(3, import(csv).added)
        assertEquals(3, repo.getAll().size)
        val again = stmt.preview(parse(csv), "s.csv")
        assertEquals(0, again.newRows.size)
    }

    // ---- 2. Placeholder reference numbers --------------------------------------------------------------------

    @Test fun placeholderRefsDoNotMakeDifferentMonthsDuplicates() = runBlocking {
        val head = "Date,Narration,Chq./Ref.No.,Withdrawal Amt.,Deposit Amt.,Closing Balance"
        for (placeholder in listOf("0000000000000000", "-", "NA", "0")) {
            db.clearAllTables()
            import("$head\n${d(at(-30, 0))},UPI-SWIGGY-swiggy@icici,$placeholder,500.00,,9500.00")
            val p = stmt.preview(parse("$head\n${d(at(1, 0))},UPI-ZOMATO-zomato@hdfcbank,$placeholder,500.00,,9000.00"), "oct.csv")
            assertEquals(placeholder, 1, p.newRows.size)
        }
    }

    @Test fun aRealRefFarAwayInTimeIsNotADuplicate() = runBlocking {
        val head = "Date,Narration,Chq./Ref.No.,Withdrawal Amt.,Deposit Amt.,Closing Balance"
        import("$head\n${d(at(-60, 0))},UPI-SWIGGY-swiggy@icici,412345678901,500.00,,9500.00")
        assertEquals(1, stmt.preview(parse("$head\n${d(at(1, 0))},UPI-ZOMATO-zomato@hdfcbank,412345678901,500.00,,9000.00"), "x.csv").newRows.size)
        assertEquals(0, stmt.preview(parse("$head\n${d(at(-60, 0))},UPI-SWIGGY-swiggy@icici,412345678901,500.00,,9500.00"), "x.csv").newRows.size)
    }

    // ---- 17 / 25. SMS and statements together -------------------------------------------------------------------

    private val friends = listOf("rahul@okicici" to "RAHUL SHARMA", "priya@okaxis" to "PRIYA NAIR", "amit@oksbi" to "AMIT DESAI")
    private fun dinnerSms() = listOf(SmsMessage("VM-HDFCBK", "Rs.4000.00 debited from a/c **1234 on ${smsDate(at(0, 20, 10))} to VPA toit.pay@ybl.", at(0, 20, 10))) +
        friends.mapIndexed { i, (vpa, _) -> SmsMessage("VM-KOTAKB", "Received Rs.1000.00 in your Kotak Bank AC X1234 from $vpa on ${smsDate(at(1, 10 + i))}.", at(1, 10 + i)) }
    private fun dinnerCsv(): String {
        var bal = 20_000.0
        val rows = listOf(Triple(at(0, 0), "UPI-TOIT-toit.pay@ybl-412345678900-PAYMENT", -4000.0)) +
            friends.mapIndexed { i, (vpa, name) -> Triple(at(1, 0), "UPI-$name-$vpa-41234567891$i-PAYMENT", 1000.0) }
        return "Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n" + rows.joinToString("\n") { (t, n, a) ->
            bal += a; "${d(t)},$n,${if (a < 0) "%.2f".format(-a) else ""},${if (a > 0) "%.2f".format(a) else ""},${"%.2f".format(bal)}"
        }
    }
    private suspend fun toit() = repo.getAll().first { it.merchant.contains("toit", true) }

    @Test fun smsSplitThenStatementAddsNothingAndKeepsTheSplit() = runBlocking {
        dinnerSms().forEach { sms.process(it) }
        assertEquals(1, engine.run().applied)
        assertEquals(1_000_00L, toit().amountPaise)
        assertEquals(0, stmt.preview(parse(dinnerCsv()), "hdfc.csv").newRows.size)
        engine.run()
        assertEquals(1_000_00L, toit().amountPaise)
    }

    @Test fun statementSplitThenSmsArrivesIsRecognisedAndKeepsTheSplit() = runBlocking {
        import(dinnerCsv())
        assertEquals(1, engine.run().applied)
        dinnerSms().forEach { sms.process(it) }
        assertEquals(4, repo.getAll().size)
        engine.run()
        assertEquals(1_000_00L, toit().amountPaise)
        assertEquals(SplitStatus.APPLIED.name, db.splitDao().allSplits().single().status)
        assertEquals("the friends' transfers stay settlements", 3, repo.getAll().count { it.flow == Flow.SETTLEMENT })
        // A rescan changes nothing.
        val snap = repo.getAll().sortedBy { it.id }
        dinnerSms().forEach { sms.process(it) }
        assertEquals(snap, repo.getAll().sortedBy { it.id })
    }

    @Test fun aSplitAppliedBetweenPreviewAndCommitIsNotOverwritten() = runBlocking {
        dinnerSms().forEach { sms.process(it) }
        val preview = stmt.preview(parse(dinnerCsv()), "hdfc.csv")
        assertEquals(4, preview.duplicates.size)
        assertEquals(1, engine.run().applied) // an SMS refresh while the preview is on screen
        stmt.commit(preview)
        assertEquals(1_000_00L, toit().amountPaise)
        assertEquals(3, repo.getAll().count { it.flow == Flow.SETTLEMENT })
        assertEquals("the statement's refs are still filled in", 4, repo.getAll().count { it.refNumber != null })
    }

    @Test fun statementBooksThePaymentADayLateNearMidnight() = runBlocking {
        sms.process(SmsMessage("VM-HDFCBK", "Rs.750.00 debited from a/c **1234 on ${smsDate(at(2, 23, 58))} to VPA swiggy@icici.", at(2, 23, 58)))
        val p = stmt.preview(parse("Date,Narration,Withdrawal Amt.,Deposit Amt.,Closing Balance\n${d(at(3, 0))},UPI-SWIGGY-swiggy@icici-PAYMENT,750.00,,9250.00"), "x.csv")
        assertEquals(0, p.newRows.size)
    }

    @Test fun theSamePeriodAsCsvThenAsPdfAddsNothing() = runBlocking {
        import(dinnerCsv())
        fun w(t: String, x: Float, y: Float) = Word(t, x, y, if (t.length > 12) 160f else 60f, 10f)
        var y = 100f
        val words = mutableListOf(w("Date", 20f, y), w("Narration", 100f, y), w("Withdrawal", 300f, y), w("Deposit", 400f, y), w("Balance", 500f, y))
        var bal = 20_000.0
        val rows = listOf(Triple(at(0, 0), "UPI/TOIT/toit.pay@ybl", -4000.0)) + friends.map { (vpa, name) -> Triple(at(1, 0), "UPI/$name/$vpa", 1000.0) }
        for ((t, n, a) in rows) {
            y += 20f; bal += a
            words += listOf(w(d(t), 20f, y), w(n, 100f, y), w("%,.2f".format(kotlin.math.abs(a)), if (a < 0) 300f else 400f, y), w("%,.2f".format(bal), 500f, y))
        }
        val pdf = StatementInterpreter.interpret(PositionedTable.toTable(words), ImportFormat.PDF)
        assertEquals(pdf.problems.toString(), 4, pdf.rows.size)
        assertEquals(0, stmt.preview(pdf, "hdfc.pdf").newRows.size)
    }

    // ---- 28. Undo ------------------------------------------------------------------------------------------

    @Test fun undoTwiceIsHarmless() = runBlocking {
        val b = import(dinnerCsv())
        stmt.undo(b.id); stmt.undo(b.id)
        assertTrue(repo.getAll().isEmpty())
    }

    @Test fun undoRemovesAnEditedImportedRowToo() = runBlocking {
        val b = import(dinnerCsv())
        repo.update(toit().copy(category = com.pft.financetracker.domain.model.Category.ENTERTAINMENT, userEdited = true))
        stmt.undo(b.id)
        assertTrue(repo.getAll().isEmpty())
    }

    @Test fun undoingAnImportKeepsRowsALaterImportAlsoHad() = runBlocking {
        val first = import(dinnerCsv(), "sep.csv")
        val second = import(dinnerCsv(), "sep-again.pdf")
        assertEquals(0, second.added); assertEquals(4, second.duplicates)
        stmt.undo(first.id)
        assertEquals("the second import still vouches for these rows", 4, repo.getAll().size)
        stmt.undo(second.id)
        assertTrue(repo.getAll().isEmpty())
    }

    @Test fun undoingAnImportWhoseTransferSettledAManualSplitUnsettlesTheShare() = runBlocking {
        val b = import(dinnerCsv())
        val rahul = repo.getAll().first { it.merchant.contains("Rahul", true) }
        val dao = db.splitDao()
        val splitId = dao.insertFull(
            com.pft.financetracker.data.local.SplitEntity(title = "Movie", totalPaise = 2_000_00, date = at(0, 18), mode = "EQUAL", payerIndex = 0, linkedTransactionId = null, note = null),
            listOf(com.pft.financetracker.data.local.SplitPersonEntity(0, 0, 0, "Me", true), com.pft.financetracker.data.local.SplitPersonEntity(0, 0, 1, "Rahul", false)),
            listOf(com.pft.financetracker.data.local.SplitShareEntity(0, 0, 0, 1_000_00, 0), com.pft.financetracker.data.local.SplitShareEntity(0, 0, 1, 1_000_00, 0)),
            emptyList(),
        )
        val share = dao.sharesFor(splitId).first { it.personIndex == 1 }
        engine.linkSettlement(splitId, share.id, 1_000_00, rahul, 1_000_00)
        assertEquals(1_000_00L, dao.sharesFor(splitId).first { it.personIndex == 1 }.settledPaise)
        stmt.undo(b.id)
        engine.run(useAi = false)
        assertTrue("no link to a row that is gone", dao.allLinks().none { it.transactionId == rahul.id })
        assertEquals("Rahul owes again", 0L, dao.sharesFor(splitId).first { it.personIndex == 1 }.settledPaise)
    }

    // ---- 23. Two things at once ----------------------------------------------------------------------------

    @Test fun aRefreshDuringACommitCountsEverythingOnce() = runBlocking {
        dinnerSms().take(2).forEach { sms.process(it) }
        val preview = stmt.preview(parse(dinnerCsv()), "hdfc.csv")
        listOf(async(Dispatchers.Default) { stmt.commit(preview) }, async(Dispatchers.Default) { engine.run() }, async(Dispatchers.Default) { engine.run() }).awaitAll()
        engine.run()
        val all = repo.getAll()
        assertEquals(4, all.size)
        val r = engine.run()
        assertEquals(0, r.applied + r.suggested + r.undone)
        val settled = all.filter { it.flow == Flow.SETTLEMENT }.sumOf { it.amountPaise }
        val toit = all.first { it.merchant.contains("toit", true) }
        assertEquals((toit.originalAmountPaise ?: toit.amountPaise) - toit.amountPaise, settled)
        assertEquals(Transaction.Source.SMS, toit.source)
    }
}
