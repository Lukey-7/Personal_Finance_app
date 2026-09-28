package com.pft.financetracker

import com.pft.financetracker.domain.importer.Amounts
import com.pft.financetracker.domain.importer.CsvReader
import com.pft.financetracker.domain.importer.Dates
import com.pft.financetracker.domain.importer.ImportFormat
import com.pft.financetracker.domain.importer.ParsedStatement
import com.pft.financetracker.domain.importer.PositionedTable
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.importer.Word
import com.pft.financetracker.domain.importer.XlsxReader
import com.pft.financetracker.domain.model.TransactionType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.Calendar
import java.util.TimeZone
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** v1.2.1 edge cases for statement files: odd amounts, odd dates, odd layouts, odd encodings. */
class EdgeStatementParseTest {
    private fun csv(text: String): ParsedStatement = StatementInterpreter.interpret(CsvReader.read(text.toByteArray()), ImportFormat.CSV)
    private fun day(y: Int, m: Int, d: Int) = Calendar.getInstance().apply { clear(); set(y, m - 1, d) }.timeInMillis
    private fun dayOf(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { Triple(it.get(Calendar.YEAR), it.get(Calendar.MONTH) + 1, it.get(Calendar.DAY_OF_MONTH)) }

    // ---- 3. Rows with a date the parser cannot read must not vanish --------------------------------------------

    @Test fun aRowWithAnUnreadableDateGoesToReviewAndKeepsTheBalanceChain() {
        val s = csv(
            """
            Date,Narration,Withdrawal,Deposit,Balance
            01/09/2026,UPI-SWIGGY-swiggy@icici-PAYMENT,500.00,,9500.00
            Sept-2nd-2026,UPI-ZOMATO-zomato@hdfcbank,300.00,,9200.00
            03/09/2026,UPI-UBER-uber@axisbank,200.00,,9000.00
            """.trimIndent()
        )
        assertEquals(2, s.rows.size)
        val p = s.problems.single()
        assertEquals("date_unknown", p.reason)
        assertEquals(300_00L, p.amountPaise)
        // The Uber row still checks against the Zomato row's balance, so it is not a mismatch.
        assertEquals(true, s.rows.last().balanceOk)
    }

    @Test fun commonDateSpellingsParse() {
        val sep28 = Triple(2026, 9, 28)
        for (raw in listOf("28 Sept 2026", "28-Sep-26", "28.09.2026", "28 SEP 2026", "2026-09-28T10:15:00Z", "2026-09-28T10:15:00", "28/09/2026 10:15")) {
            val d = Dates.parse(raw, day(2026, 9, 28) + 20 * 3_600_000L)
            assertNotNull(raw, d); assertEquals(raw, sep28, dayOf(d!!))
        }
    }

    @Test fun moneyWithTwoDecimalsIsNotAnExcelDate() {
        assertNull(Dates.parse("45000.00"))
        assertNotNull(Dates.parse("46266"))
        assertNotNull(Dates.parse("46266.520833333336"))
    }

    // ---- 13. Amounts: Dr / Cr in every position ------------------------------------------------------------

    @Test fun amountsWithDrCrAnywhere() {
        val cases = mapOf(
            "Dr 500.00" to (50_000L to true), "DR. 500" to (50_000L to true), "500.00 Dr" to (50_000L to true), "500.00(Dr)" to (50_000L to true),
            "500.00-" to (50_000L to true), "-₹500" to (50_000L to true), "₹-500" to (50_000L to true), "Rs.500.00 CR" to (50_000L to false),
            "500 Cr." to (50_000L to false), "(1,250.00)" to (125_000L to true), "CR 1,200.50" to (120_050L to false), "INR 2,500.00 DR" to (250_000L to true),
            "1,00,000.00" to (10_000_000L to null), "₹ 349" to (34_900L to null),
        )
        for ((raw, want) in cases) {
            val got = Amounts.parseSigned(raw)
            assertNotNull(raw, got)
            assertEquals(raw, want.first, got!!.paise); assertEquals(raw, want.second, got.dr)
        }
        for (raw in listOf("", "-", "--", "Dr", "N/A")) assertNull(raw, Amounts.parseSigned(raw))
    }

    @Test fun drCrColumnWords() {
        val s = csv(
            """
            Date,Description,Amount,Dr/Cr,Balance
            01/09/2026,UPI-A,100.00,D,900.00
            01/09/2026,UPI-B,50.00,C,950.00
            01/09/2026,UPI-C,100.00,Debit,850.00
            01/09/2026,UPI-D,50.00,Credit,900.00
            01/09/2026,UPI-E,100.00,DR.,800.00
            01/09/2026,UPI-F,50.00,Cr.,850.00
            """.trimIndent()
        )
        assertEquals(s.problems.toString(), 0, s.problems.size)
        assertEquals(listOf(true, false, true, false, true, false), s.rows.map { it.type == TransactionType.DEBIT })
    }

    @Test fun bothDebitAndCreditFilledGoesToReview() {
        val s = csv("Date,Narration,Debit,Credit,Balance\n01/09/2026,ODD ROW,100.00,50.00,950.00\n02/09/2026,UPI-RAHUL,,50.00,1000.00")
        assertEquals(1, s.rows.size)
        assertEquals(1, s.problems.size)
    }

    // ---- 14. XLSX specifics ----------------------------------------------------------------------------------

    private fun xlsx(rows: List<List<Pair<String, Boolean>>>): ByteArray {
        // Pair(text, isNumber)
        val sheet = buildString {
            append("<?xml version=\"1.0\" encoding=\"UTF-8\"?><worksheet><sheetData>")
            rows.forEachIndexed { i, r ->
                append("<row r=\"${i + 1}\">")
                r.forEachIndexed { j, (v, num) ->
                    val ref = "${'A' + j}${i + 1}"
                    if (num) append("<c r=\"$ref\"><v>$v</v></c>") else append("<c r=\"$ref\" t=\"inlineStr\"><is><t>$v</t></is></c>")
                }
                append("</row>")
            }
            append("</sheetData></worksheet>")
        }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml")); z.write(sheet.toByteArray()); z.closeEntry() }
        return out.toByteArray()
    }

    @Test fun xlsxFormulaNoiseAndScientificNumbersAreAmounts() {
        val t = { s: String -> s to false }
        val n = { s: String -> s to true }
        val table = XlsxReader.read(
            xlsx(
                listOf(
                    listOf(t("Date"), t("Narration"), t("Debit"), t("Credit"), t("Balance")),
                    listOf(t("01/09/2026"), t("UPI-SWIGGY"), n("1234.5599999999999"), t(""), n("8765.4400000000005")),
                    listOf(t("02/09/2026"), t("UPI-RAHUL"), t(""), n("1.5E3"), n("10265.44")),
                    listOf(n("46267.520833333336"), t("UPI-UBER"), n("265.44"), t(""), n("10000")),
                )
            )
        )
        val s = StatementInterpreter.interpret(table, ImportFormat.XLSX)
        assertEquals(s.problems.toString(), 0, s.problems.size)
        assertEquals(listOf(1_234_56L, 1_500_00L, 265_44L), s.rows.map { it.amountPaise })
        assertEquals("Excel day 46267 is 2 September 2026", Triple(2026, 9, 2), dayOf(s.rows[2].date))
    }

    // ---- 15. Layout ------------------------------------------------------------------------------------------

    @Test fun footersMidTableAreSkippedButAWrappedTotalEnergiesLineIsKept() {
        val s = csv(
            """
            Date,Narration,Debit,Credit,Balance
            01/09/2026,POS 416021XXXXXX1234,1000.00,,9000.00
            ,TOTAL ENERGIES PETROL PUMP,,,
            Page 2 of 5,,,,
            Date,Narration,Debit,Credit,Balance
            02/09/2026,UPI-RAHUL SHARMA-rahul@okicici,,500.00,9500.00
            Total,,1000.00,500.00,
            Closing Balance,,,,9500.00
            """.trimIndent()
        )
        assertEquals(s.problems.toString(), 0, s.problems.size)
        assertEquals(2, s.rows.size)
        assertTrue(s.rows[0].narration, s.rows[0].narration.contains("TOTAL ENERGIES"))
    }

    @Test fun aNarrationLineAboveItsDateLineJoinsItsOwnRow() {
        // A PDF with vertically centred rows: the narration's first line sits above the date and amounts.
        fun w(t: String, x: Float, y: Float, width: Float = 60f) = Word(t, x, y, width, 10f)
        val words = listOf(
            w("Date", 20f, 100f, 30f), w("Narration", 100f, 100f), w("Withdrawal", 300f, 100f), w("Deposit", 400f, 100f), w("Balance", 500f, 100f),
            w("01/09/2026", 20f, 130f), w("UPI/SWIGGY", 100f, 130f), w("500.00", 300f, 130f), w("9,500.00", 500f, 130f),
            w("UPI/RAHUL", 100f, 160f), // first line of the next row's narration
            w("02/09/2026", 20f, 172f), w("SHARMA/OKICICI", 100f, 172f), w("1,000.00", 400f, 172f), w("10,500.00", 500f, 172f),
            w("PAYMENT", 100f, 184f),
        )
        val s = StatementInterpreter.interpret(PositionedTable.toTable(words), ImportFormat.PDF)
        assertEquals(s.problems.toString(), 2, s.rows.size)
        assertTrue(s.rows[0].narration, !s.rows[0].narration.contains("RAHUL"))
        assertTrue(s.rows[1].narration, s.rows[1].narration.contains("RAHUL"))
    }

    @Test fun headerAfterALongAccountSummary() {
        val summary = (1..100).joinToString("\n") { "Account detail line $it,value $it" }
        val s = csv("$summary\nDate,Narration,Debit,Credit,Balance\n01/09/2026,UPI-SWIGGY,500.00,,9500.00\n02/09/2026,UPI-RAHUL,,500.00,10000.00")
        assertEquals(2, s.rows.size)
        assertEquals(true, s.rows[1].balanceOk)
    }

    @Test fun twoLineHeaderInACsv() {
        val s = csv("Date,Narration,Withdrawal,Deposit,Closing\n,,Amt.,Amt.,Balance\n01/09/2026,UPI-SWIGGY,500.00,,9500.00\n02/09/2026,UPI-RAHUL,,500.00,10000.00")
        assertEquals(2, s.rows.size)
        assertEquals("the balance column is found and checked", true, s.rows[1].balanceOk)
    }

    @Test fun aCreditCardNumberColumnIsNotTheCreditColumn() {
        val s = csv("Date,Credit Card No,Description,Debit,Credit,Balance\n01/09/2026,XXXX1234,SWIGGY,500.00,,9500.00\n02/09/2026,XXXX1234,REFUND,,100.00,9600.00")
        assertEquals(s.problems.toString(), 2, s.rows.size)
        assertEquals(listOf(TransactionType.DEBIT, TransactionType.CREDIT), s.rows.map { it.type })
    }

    @Test fun headerlessCsvWithAmountsInTheExcelSerialRange() {
        val s = csv(
            """
            01/09/2026,UPI SWIGGY BANGALORE,45000.00,,55000.00
            02/09/2026,UPI RAHUL SHARMA,,35000.00,90000.00
            03/09/2026,UPI RENT PAYMENT,40000.00,,50000.00
            04/09/2026,UPI AMAZON,31000.00,,19000.00
            """.trimIndent()
        )
        assertEquals(s.problems.toString(), 4, s.rows.size)
        assertEquals(Triple(2026, 9, 1), dayOf(s.rows[0].date))
    }

    // ---- 16. Encodings and sizes -----------------------------------------------------------------------------

    private val simple = "Date,Narration,Withdrawal (₹),Deposit (₹),Balance (₹)\n01/09/2026,UPI-SWIGGY,500.00,,9500.00\n02/09/2026,UPI-RAHUL,,500.00,10000.00"

    @Test fun utf16WithAndWithoutBom() {
        val le = byteArrayOf(0xFF.toByte(), 0xFE.toByte()) + simple.toByteArray(Charsets.UTF_16LE)
        val be = simple.toByteArray(Charsets.UTF_16BE)
        for ((name, bytes) in listOf("LE+BOM" to le, "BE no BOM" to be)) {
            val s = StatementInterpreter.interpret(CsvReader.read(bytes), ImportFormat.CSV)
            assertEquals(name, 2, s.rows.size)
        }
    }

    @Test fun windows1252() {
        val bytes = "Date,Narration,Withdrawal,Deposit,Balance\n01/09/2026,CAFÉ £ LONDON,500.00,,9500.00".toByteArray(charset("windows-1252"))
        val s = StatementInterpreter.interpret(CsvReader.read(bytes), ImportFormat.CSV)
        assertTrue(s.rows.single().narration, s.rows.single().narration.contains("CAFÉ £"))
    }

    @Test fun emptyHeaderOnlyAndOneRowFiles() {
        assertTrue(StatementInterpreter.interpret(CsvReader.read(ByteArray(0)), ImportFormat.CSV).let { it.rows.isEmpty() && it.note != null })
        assertTrue(csv("Date,Narration,Debit,Credit,Balance").rows.isEmpty())
        assertEquals(1, csv("Date,Narration,Debit,Credit,Balance\n01/09/2026,UPI-SWIGGY,500.00,,9500.00").rows.size)
    }

    @Test(timeout = 20_000) fun aStrayQuoteDoesNotSwallowTheRestOfTheFile() {
        val s = csv("Date,Narration,Debit,Credit,Balance\n01/09/2026,\"RAHUL 5,500.00,,9500.00\n02/09/2026,UPI-PRIYA,,500.00,10000.00\n03/09/2026,UPI-UBER,200.00,,9800.00")
        assertEquals(s.problems.toString(), 3, s.rows.size + s.problems.size)
        assertTrue(s.rows.any { it.amountPaise == 200_00L })
    }

    @Test(timeout = 20_000) fun sameDayOrderIsCountedWithinTheDay() {
        // 500 rows a day for 20 days: the order used to keep same-day rows in sequence must count within the day, or
        // a big file's "noon + order seconds" pushes late rows into the next day (after 43,200 rows).
        val sb = StringBuilder("Date,Narration,Debit,Credit,Balance\n")
        var bal = 10_000_000_00L
        val c = Calendar.getInstance().apply { clear(); set(2026, 5, 1) }
        repeat(20) {
            val d = "%02d/%02d/%04d".format(c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.MONTH) + 1, c.get(Calendar.YEAR))
            repeat(500) { i -> bal -= 1_00; sb.append("$d,UPI-SHOP$i,1.00,,${"%.2f".format(bal / 100.0)}\n") }
            c.add(Calendar.DAY_OF_YEAR, 1)
        }
        val s = csv(sb.toString())
        assertEquals(10_000, s.rows.size)
        assertTrue(s.rows.maxOf { it.order } < 500)
    }

    // ---- 17. Balances ----------------------------------------------------------------------------------------

    @Test fun newestFirstWithOpeningBalanceAtTheBottom() {
        val s = csv(
            """
            Date,Narration,Debit,Credit,Balance
            03/09/2026,UPI-UBER,200.00,,9800.00
            02/09/2026,UPI-RAHUL,,500.00,10000.00
            01/09/2026,UPI-SWIGGY,500.00,,9500.00
            ,Opening Balance,,,10000.00
            """.trimIndent()
        )
        assertEquals(s.problems.toString(), 0, s.problems.size)
        assertEquals(listOf(500_00L, 500_00L, 200_00L), s.rows.map { it.amountPaise })
        assertTrue(s.rows.all { it.balanceOk == true })
    }

    @Test fun oneMissingRowIsOneMismatchNotACascade() {
        val s = csv(
            """
            Date,Narration,Debit,Credit,Balance
            01/09/2026,UPI-A,100.00,,9900.00
            02/09/2026,UPI-C,100.00,,9700.00
            03/09/2026,UPI-D,100.00,,9600.00
            04/09/2026,UPI-E,100.00,,9500.00
            """.trimIndent()
        )
        assertEquals(1, s.balanceMismatches)
        assertEquals(3, s.rows.size)
    }

    // ---- 24. Time zones --------------------------------------------------------------------------------------

    @Test fun datesLandOnTheSameCalendarDayInAnyTimeZone() {
        val saved = TimeZone.getDefault()
        try {
            for (tz in listOf("Asia/Kolkata", "UTC", "America/Los_Angeles")) {
                TimeZone.setDefault(TimeZone.getTimeZone(tz))
                assertEquals(tz, Triple(2026, 9, 1), dayOf(Dates.parse("01/09/2026")!!))
                assertEquals(tz, Triple(2026, 9, 1), dayOf(Dates.parse("xlserial:46266")!!))
            }
        } finally { TimeZone.setDefault(saved) }
    }
}
