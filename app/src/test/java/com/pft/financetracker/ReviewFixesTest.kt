package com.pft.financetracker

import com.pft.financetracker.domain.importer.AppHistoryParser
import com.pft.financetracker.domain.importer.CsvReader
import com.pft.financetracker.domain.importer.Dates
import com.pft.financetracker.domain.importer.ImportFormat
import com.pft.financetracker.domain.importer.PdfLimits
import com.pft.financetracker.domain.importer.PositionedTable
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.importer.Word
import com.pft.financetracker.domain.importer.XlsxReader
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.SplitSolver
import com.pft.financetracker.domain.split.SplitTx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Calendar
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Defects found by the v1.2.1 code review of the edge-case fixes, each pinned before it was fixed. */
class ReviewFixesTest {
    private fun csv(text: String) = StatementInterpreter.interpret(CsvReader.read(text.toByteArray()), ImportFormat.CSV)
    private fun at(y: Int, m: Int, d: Int, h: Int = 12, min: Int = 0) = Calendar.getInstance().apply { clear(); set(y, m - 1, d, h, min) }.timeInMillis
    private fun dayOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }

    // ---- Statement footers --------------------------------------------------------------------------------------

    @Test fun footersWithWordsAfterThemAreStillFooters() {
        val s = csv(
            """
            Date,Narration,Debit,Credit,Balance
            ,Opening Balance as of 01-Sep-2026,,,10000.00
            01/09/2026,UPI-SWIGGY,500.00,,9500.00
            ,Balance carried forward,,,9500.00
            Page 2 of 5 | Generated on 01/10/2026,,,,
            02/09/2026,UPI-RAHUL SHARMA,,500.00,10000.00
            ,Closing Balance as on 30-Sep-2026,,,10000.00
            """.trimIndent()
        )
        assertEquals(s.problems.toString(), 0, s.problems.size)
        assertEquals(listOf("UPI-SWIGGY", "UPI-RAHUL SHARMA"), s.rows.map { it.narration })
        assertEquals("the opening balance checks the first row", true, s.rows[0].balanceOk)
    }

    @Test fun rowsAfterAStatementSummaryLineAreStillRead() {
        val s = csv(
            """
            Date,Narration,Debit,Credit,Balance
            01/09/2026,UPI-SWIGGY,500.00,,9500.00
            ,Statement Summary for account 1234,,,
            ,12,5000.00,3000.00,
            02/09/2026,UPI-RAHUL SHARMA,,500.00,10000.00
            """.trimIndent()
        )
        assertEquals(2, s.rows.size)
        assertEquals(s.problems.toString(), 0, s.problems.size)
    }

    // ---- PDF narration lines -----------------------------------------------------------------------------------

    private fun w(t: String, x: Float, y: Float, width: Float = 60f, page: Int = 0) = Word(t, x, y, width, 10f, page)
    private val header = listOf(w("Date", 20f, 100f, 30f), w("Narration", 100f, 100f), w("Withdrawal", 300f, 100f), w("Deposit", 400f, 100f), w("Balance", 500f, 100f))

    @Test fun aThreeLineNarrationUnderItsDateStaysWithItsRow() {
        // Top-aligned rows: date line, then two wrapped lines, then the next row a little further down.
        val words = header + listOf(
            w("01/09/2026", 20f, 130f), w("UPI/SWIGGY", 100f, 130f), w("500.00", 300f, 130f), w("9,500.00", 500f, 130f),
            w("BANGALORE/ORDER", 100f, 140f), w("DINNER", 100f, 150f),
            w("02/09/2026", 20f, 162f), w("UPI/RAHUL", 100f, 162f), w("1,000.00", 400f, 162f), w("10,500.00", 500f, 162f),
        )
        val s = StatementInterpreter.interpret(PositionedTable.toTable(words), ImportFormat.PDF)
        assertEquals(2, s.rows.size)
        assertTrue(s.rows[0].narration, s.rows[0].narration.contains("DINNER"))
        assertTrue(s.rows[1].narration, !s.rows[1].narration.contains("DINNER"))
    }

    @Test fun pageTopTextIsNotPulledIntoTheFirstRowOfThePage() {
        val words = header + listOf(
            w("01/09/2026", 20f, 130f), w("UPI/SWIGGY", 100f, 130f), w("500.00", 300f, 130f), w("9,500.00", 500f, 130f),
            w("HDFC BANK LTD", 20f, 40f, page = 1),
            w("02/09/2026", 20f, 60f, page = 1), w("UPI/RAHUL", 100f, 60f, page = 1), w("1,000.00", 400f, 60f, page = 1), w("10,500.00", 500f, 60f, page = 1),
        )
        val s = StatementInterpreter.interpret(PositionedTable.toTable(words), ImportFormat.PDF)
        assertEquals(s.problems.toString(), 2, s.rows.size)
    }

    // ---- Screenshots ------------------------------------------------------------------------------------------

    private fun l(text: String, x: Float, y: Float, w: Float = 0f) = AppHistoryParser.Line(text, x, y, 30f, w)

    @Test fun statusLinesThatSayMoreStillSkipTheirRow() {
        val now = at(2026, 9, 28)
        for (status in listOf("Pending · Today, 8:30 pm", "Failed - money will be refunded in 3 days", "Payment pending with bank", "Scheduled for 15 Oct")) {
            val rows = AppHistoryParser.parse(
                listOf(l("Paid to Zomato", 180f, 500f), l("₹300", 900f, 500f), l("12 Sep, 9:30 PM", 180f, 545f), l(status, 500f, 575f)), now,
            )
            assertTrue(status, rows.isEmpty())
        }
        assertTrue(AppHistoryParser.parse(listOf(l("Request from Rahul Sharma", 180f, 500f), l("₹300", 900f, 500f), l("12 Sep, 9:30 PM", 180f, 545f)), now).isEmpty())
    }

    @Test fun aDayHeaderLikeSep12DatesTheRowsBelowIt() {
        val rows = AppHistoryParser.parse(listOf(l("Sep 12", 60f, 150f), l("Paid to Swiggy", 180f, 300f), l("₹250", 900f, 300f)), at(2026, 9, 28))
        assertEquals(Triple(2026, 9, 12), dayOf(rows.single().date))
    }

    @Test fun twoIdenticalScreenshotRowsGetDifferentOccurrences() {
        val shot = listOf(
            l("Chai Point", 180f, 300f), l("₹20", 900f, 300f), l("12 Sep", 180f, 345f),
            l("Chai Point", 180f, 500f), l("₹20", 900f, 500f), l("12 Sep", 180f, 545f),
        )
        assertEquals(listOf(0, 1), AppHistoryParser.parse(shot, at(2026, 9, 28)).map { it.occurrence })
    }

    @Test fun aSingleAmountWhoseWidthFitsBothReadingsUsesTheTextRule() {
        // 4.5 glyphs wide: halfway between "₹300" misread as "7300" (4) and "₹7300" with the ₹ dropped (5).
        val rows = AppHistoryParser.parse(listOf(l("Paid to Rahul Sharma", 180f, 300f), l("7300", 850f, 300f, 4.5f * 16.5f), l("12 Sep, 8:30 PM", 180f, 345f)), at(2026, 9, 28))
        assertEquals(300_00L, rows.single().amountPaise)
    }

    // ---- Dates ------------------------------------------------------------------------------------------------

    @Test fun isoDatesWithAnIndianOffset() {
        for (raw in listOf("2026-09-12T20:30:00+05:30", "2026-09-12T20:30:00+0530", "2026-09-12T20:30:00Z")) assertNotNull(raw, Dates.parse(raw, at(2026, 9, 28)))
    }

    @Test fun aDateColumnAcceptsSerialsWithShortFractions() {
        val xl = xlsx("<row r=\"1\"><c r=\"A1\" t=\"inlineStr\"><is><t>Date</t></is></c><c r=\"B1\" t=\"inlineStr\"><is><t>Narration</t></is></c><c r=\"C1\" t=\"inlineStr\"><is><t>Debit</t></is></c><c r=\"D1\" t=\"inlineStr\"><is><t>Credit</t></is></c></row>" +
            "<row r=\"2\"><c r=\"A2\"><v>46266.5</v></c><c r=\"B2\" t=\"inlineStr\"><is><t>UPI-SWIGGY</t></is></c><c r=\"C2\"><v>500</v></c></row>" +
            "<row r=\"3\"><c r=\"A3\"><v>46267.520000000004</v></c><c r=\"B3\" t=\"inlineStr\"><is><t>UPI-UBER</t></is></c><c r=\"C3\"><v>200</v></c></row>")
        val s = StatementInterpreter.interpret(XlsxReader.read(xl), ImportFormat.XLSX)
        assertEquals(s.problems.toString(), listOf(Triple(2026, 9, 1), Triple(2026, 9, 2)), s.rows.map { dayOf(it.date) })
    }

    // ---- XLSX ------------------------------------------------------------------------------------------------

    private fun xlsx(sheetRows: String, encoding: java.nio.charset.Charset = Charsets.UTF_8, bom: ByteArray = ByteArray(0), prolog: String = ""): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            z.write(bom + "$prolog<worksheet><sheetData>$sheetRows</sheetData></worksheet>".toByteArray(encoding)); z.closeEntry()
        }
        return out.toByteArray()
    }

    @Test fun cellsWithoutReferencesStartAtColumnAInEveryRow() {
        val t = XlsxReader.read(xlsx("<row><c t=\"inlineStr\"><is><t>a</t></is></c><c t=\"inlineStr\"><is><t>b</t></is></c></row><row><c t=\"inlineStr\"><is><t>c</t></is></c><c t=\"inlineStr\"><is><t>d</t></is></c></row>"))
        assertEquals(listOf(listOf("a", "b"), listOf("c", "d")), t)
    }

    @Test fun aReferenceNumberInScientificNotationStaysAWholeNumber() {
        val t = XlsxReader.read(xlsx("<row r=\"1\"><c r=\"A1\"><v>1.2345678901234E+15</v></c><c r=\"B1\"><v>1.5E3</v></c></row>"))
        assertEquals(listOf("1234567890123400", "1500"), t.single())
    }

    @Test fun aUtf16SheetWithADoctypeIsRefused() {
        val prolog = "<?xml version=\"1.0\" encoding=\"UTF-16\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\">]>"
        val part = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + "$prolog<worksheet/>".toByteArray(Charsets.UTF_16LE)
        // The JVM's parser refuses DOCTYPEs by feature flag; Android's does not support that flag, so the byte check
        // must catch every encoding by itself.
        assertTrue(XlsxReader.hasDoctype(part))
        assertTrue(XlsxReader.hasDoctype("$prolog<worksheet/>".toByteArray(Charsets.UTF_16BE)))
        assertTrue(!XlsxReader.hasDoctype("<worksheet><sheetData/></worksheet>".toByteArray(Charsets.UTF_16LE)))
    }

    // ---- PDF render size -------------------------------------------------------------------------------------

    @Test fun absurdPageSizesStillRenderAtAUsableSize() {
        for ((w, h) in listOf(1e30f to 1e30f, 1e7f to 0.01f, 0f to 842f, Float.NaN to 842f)) {
            val dpi = PdfLimits.renderDpi(w, h)
            assertTrue("$w x $h -> $dpi", dpi.isFinite() && dpi > 0f)
            val side = maxOf(if (w.isFinite()) w else 0f, if (h.isFinite()) h else 0f) / 72f * dpi
            assertTrue("$w x $h -> side $side", side <= PdfLimits.MAX_SIDE_PX)
        }
    }

    // ---- Placeholder sender names --------------------------------------------------------------------------------

    @Test fun twoUnnamedCreditsAreTwoPeople() {
        val t0 = at(2026, 9, 5, 20)
        for (placeholder in listOf("Credit", "Friend", "UPI Credit")) {
            val txs = listOf(
                SplitTx(1, 3_000_00, TransactionType.DEBIT, t0, "Toit", Category.FOOD, false),
                SplitTx(10, 1_000_00, TransactionType.CREDIT, t0 + 3_600_000, placeholder, Category.INCOME, true),
                SplitTx(11, 1_000_00, TransactionType.CREDIT, t0 + 7_200_000, placeholder, Category.INCOME, true),
            )
            assertEquals(placeholder, 2, SplitSolver.solve(txs).single().allocations.size)
        }
    }
}
