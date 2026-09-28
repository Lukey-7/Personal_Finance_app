package com.pft.financetracker

import com.pft.financetracker.domain.importer.AppHistoryParser
import com.pft.financetracker.domain.importer.CsvReader
import com.pft.financetracker.domain.importer.FormatSniffer
import com.pft.financetracker.domain.importer.HtmlTableReader
import com.pft.financetracker.domain.importer.ImportFormat
import com.pft.financetracker.domain.importer.ParsedStatement
import com.pft.financetracker.domain.importer.PositionedTable
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.importer.Word
import com.pft.financetracker.domain.importer.XlsxReader
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.SplitSolver
import com.pft.financetracker.domain.split.SplitTx
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * One weekend, written out the way different banks and apps export it. Every format must read back to the same
 * transactions, with the running balance checked, and split intelligence must find the same answer.
 */
class StatementImportTest {
    /** (day of Sep 2026, name, VPA, debit, credit) in time order. */
    private data class E(val day: Int, val name: String, val vpa: String, val dr: Int?, val cr: Int?, val kind: String = "UPI")
    private val friends = listOf("NEHA JOSHI", "KARAN MEHTA", "SNEHA RAO", "VIKRAM SINGH", "ANJALI DESAI", "ROHAN GUPTA", "POOJA IYER", "ARJUN NAIR", "MEERA PATEL")
    private val entries = listOf(
        E(19, "SBOW", "sbow.pay@ybl", 12_000, null),
        E(19, "UBER INDIA", "uber.india@axisbank", 600, null),
        E(20, "RAPIDO", "rapido.bike@ybl", 180, null),
        E(20, "RAHUL SHARMA", "rahul@okicici", null, 1_000),
        E(20, "PRIYA NAIR", "priya@okaxis", null, 1_200),
        E(20, "AMIT DESAI", "amit@oksbi", null, 200),
    ) + friends.map { E(21, it, it.substringBefore(' ').lowercase() + "@okhdfcbank", null, 1_000) } +
        E(22, "ACME CORP PVT LTD", "", null, 85_000, kind = "NEFT")
    private val opening = 50_000_00L
    private fun balances(): List<Long> { var b = opening; return entries.map { e -> b += ((e.cr ?: 0) - (e.dr ?: 0)) * 100L; b } }
    private fun money(p: Long) = "%,.2f".format(p / 100.0)
    private fun ref(i: Int) = "4262123456${10 + i}"

    private fun check(s: ParsedStatement) {
        assertEquals("problems: ${s.problems}", 0, s.problems.size)
        assertEquals(16, s.rows.size)
        assertTrue(s.rows.all { it.balanceOk != false })
        val sbow = s.rows.first { it.amountPaise == 12_000_00L }
        assertEquals(TransactionType.DEBIT, sbow.type)
        assertTrue(sbow.counterparty, sbow.counterparty.contains("Sbow", true))
        val rahul = s.rows.first { it.counterparty.contains("Rahul", true) }
        assertEquals(TransactionType.CREDIT, rahul.type); assertEquals(CounterpartyKind.PERSON, rahul.kind); assertEquals(Flow.INCOME, rahul.flow)
        val salary = s.rows.first { it.amountPaise == 85_000_00L }
        assertEquals(TransactionType.CREDIT, salary.type); assertEquals(CounterpartyKind.ORGANISATION, salary.kind)
        // Split intelligence on the imported rows: SBOW costs me Rs 1,000, the Uber Rs 200.
        val txs = s.rows.mapIndexed { i, r ->
            SplitTx(i.toLong(), r.amountPaise, r.type, r.date + 12 * 3_600_000L + r.order * 1_000L, r.counterparty, r.category, r.kind == CounterpartyKind.PERSON)
        }
        val ps = SplitSolver.solve(txs).associateBy { p -> txs.first { it.id == p.paymentId }.amountPaise }
        assertEquals(11_000_00L, ps[12_000_00L]!!.allocatedPaise)
        assertEquals(400_00L, ps[600_00L]!!.allocatedPaise)
    }

    @Test fun hdfcCsv() {
        val b = balances()
        val csv = buildString {
            appendLine("HDFC BANK Ltd.,,,,,,"); appendLine("Statement of account,,,,,,"); appendLine("Account No :XXXXXXXX1234,,,,,,"); appendLine()
            appendLine("Date,Narration,Chq./Ref.No.,Value Dt,Withdrawal Amt.,Deposit Amt.,Closing Balance")
            entries.forEachIndexed { i, e ->
                val narr = if (e.kind == "NEFT") "NEFT CR-CITI0000001-${e.name}-SALARY SEP 2026" else "UPI-${e.name}-${e.vpa}-HDFC0001234-${ref(i)}-PAYMENT"
                appendLine("%02d/09/26,\"%s\",0000%s,%02d/09/26,%s,%s,\"%s\"".format(e.day, narr, ref(i), e.day, e.dr?.let { "\"${money(it * 100L)}\"" } ?: "", e.cr?.let { "\"${money(it * 100L)}\"" } ?: "", money(b[i])))
            }
            appendLine("STATEMENT SUMMARY,,,,,,")
        }
        check(StatementInterpreter.interpret(CsvReader.read(csv.toByteArray()), ImportFormat.CSV))
    }

    @Test fun sbiHtmlXls() {
        val b = balances()
        val html = buildString {
            append("<html><body><table><tr><td>Account Name</td><td>Mr. Test</td></tr></table><table>")
            append("<tr><th>Txn Date</th><th>Value Date</th><th>Description</th><th>Ref No./Cheque No.</th><th>Debit</th><th>Credit</th><th>Balance</th></tr>")
            append("<tr><td></td><td></td><td>Opening Balance</td><td></td><td></td><td></td><td>${money(opening)}</td></tr>")
            entries.forEachIndexed { i, e ->
                val narr = when {
                    e.kind == "NEFT" -> "BY TRANSFER-NEFT*CITI0000001*N123456*${e.name}--"
                    e.dr != null -> "TO TRANSFER-UPI/DR/${ref(i)}/${e.name}/YESB/${e.vpa}/Payment--"
                    else -> "BY TRANSFER-UPI/CR/${ref(i)}/${e.name}/ICIC/${e.vpa}/UPI--"
                }
                append("<tr><td>${e.day} Sep 2026</td><td>${e.day} Sep 2026</td><td>$narr</td><td>TRANSFER FROM 1234</td><td>${e.dr?.let { money(it * 100L) } ?: "&nbsp;"}</td><td>${e.cr?.let { money(it * 100L) } ?: "&nbsp;"}</td><td>${money(b[i])}</td></tr>")
            }
            append("</table></body></html>")
        }
        assertEquals(FormatSniffer.Kind.XLS_HTML, FormatSniffer.sniff(html.toByteArray(), "stmt.xls"))
        check(StatementInterpreter.interpret(HtmlTableReader.read(html.toByteArray()), ImportFormat.XLS_HTML))
    }

    @Test fun iciciXlsx() {
        val b = balances()
        val rows = listOf(listOf("DETAILED STATEMENT"), listOf("S No.", "Value Date", "Transaction Date", "Cheque Number", "Transaction Remarks", "Withdrawal Amount (INR )", "Deposit Amount (INR )", "Balance (INR )")) +
            entries.mapIndexed { i, e ->
                val narr = if (e.kind == "NEFT") "NEFT-CITIN52026092200001-${e.name}-SALARY" else "UPI/${ref(i)}/${if (e.dr != null) e.name else "Payment from Ph"}/${e.vpa}/ICICI Bank"
                listOf("${i + 1}", "%02d/09/2026".format(e.day), "%02d/09/2026".format(e.day), "-", narr, e.dr?.let { money(it * 100L) } ?: "0.00", e.cr?.let { money(it * 100L) } ?: "0.00", money(b[i]))
            }
        val bytes = xlsx(rows)
        assertEquals(FormatSniffer.Kind.XLSX, FormatSniffer.sniff(bytes, "x.xlsx"))
        check(StatementInterpreter.interpret(XlsxReader.read(bytes), ImportFormat.XLSX))
    }

    @Test fun axisCsvNewestFirst() {
        val b = balances()
        val lines = entries.mapIndexed { i, e ->
            val narr = when {
                e.kind == "NEFT" -> "NEFT/CITIN52026/${e.name}"
                e.dr != null -> "UPI/P2M/${ref(i)}/${e.name}/YES BANK/UPI"
                else -> "UPI/P2A/${ref(i)}/${e.name}/ICICI/UPI"
            }
            "%02d-09-2026,,%s,%s,%s,%s,1234".format(e.day, narr, e.dr?.let { money(it * 100L).replace(",", "") } ?: "", e.cr?.let { money(it * 100L).replace(",", "") } ?: "", money(b[i]).replace(",", ""))
        }.reversed()
        val csv = "Tran Date,CHQNO,PARTICULARS,DR,CR,BAL,SOL\n" + lines.joinToString("\n")
        check(StatementInterpreter.interpret(CsvReader.read(csv.toByteArray()), ImportFormat.CSV))
    }

    @Test fun kotakAmountWithDrCr() {
        val b = balances()
        val csv = buildString {
            appendLine("Sl. No.,Transaction Date,Value Date,Description,Chq / Ref No.,Amount,Dr / Cr,Balance,Dr / Cr")
            entries.forEachIndexed { i, e ->
                val narr = if (e.kind == "NEFT") "NEFT ${e.name} SALARY" else "UPI/${e.name}/${ref(i)}/${if (e.dr != null) "Payment" else "Received"}"
                appendLine("${i + 1},%02d-09-2026,%02d-09-2026,\"%s\",UPI-%s,\"%s\",%s,\"%s\",CR".format(e.day, e.day, narr, ref(i), money(((e.dr ?: e.cr)!!) * 100L), if (e.dr != null) "DR" else "CR", money(b[i])))
            }
        }
        check(StatementInterpreter.interpret(CsvReader.read(csv.toByteArray()), ImportFormat.CSV))
    }

    @Test fun cardStatementWithoutBalanceUsesCrSuffix() {
        val csv = "Date,Transaction Details,Amount (in Rs.)\n19/09/2026,SWIGGY BANGALORE,450.00\n20/09/2026,PAYMENT RECEIVED - THANK YOU,\"5,000.00 Cr\"\n21/09/2026,UBER INDIA,320.00 Dr"
        val s = StatementInterpreter.interpret(CsvReader.read(csv.toByteArray()), ImportFormat.CSV)
        assertEquals(listOf(TransactionType.DEBIT, TransactionType.CREDIT, TransactionType.DEBIT), s.rows.map { it.type })
        assertTrue(s.rows.all { it.balanceOk == null })
    }

    @Test fun aRowWhoseBalanceDoesNotAddUpGoesToReview() {
        val csv = "Date,Narration,Withdrawal,Deposit,Balance\n19/09/2026,UPI-SBOW-sbow@ybl,12000.00,,38000.00\n20/09/2026,UPI-RAHUL-rahul@okicici,,1000.00,39000.00\n21/09/2026,UPI-PRIYA-priya@okaxis,,1200.00,41000.00"
        val s = StatementInterpreter.interpret(CsvReader.read(csv.toByteArray()), ImportFormat.CSV)
        assertEquals(2, s.rows.size)
        assertEquals("balance_mismatch", s.problems.single().reason)
    }

    /** A text PDF (or a photo of a statement) arrives as words with positions; the table is rebuilt from them. */
    @Test fun pdfLikePositionedWords() {
        val b = balances()
        val cols = listOf(40f, 110f, 330f, 430f, 510f) // Date, Narration, Withdrawal, Deposit, Balance (left edges)
        val words = mutableListOf<Word>()
        fun put(text: String, col: Int, y: Float, page: Int, rightAlign: Boolean = false) {
            val w = text.length * 5f
            words += Word(text, if (rightAlign) cols[col] + 70f - w else cols[col], y, w, 8f, page)
        }
        fun header(y: Float, page: Int) { listOf("Date", "Narration", "Withdrawal", "Deposit", "Balance").forEachIndexed { i, h -> put(h, i, y, page) } }
        put("HDFC BANK", 0, 20f, 0); header(60f, 0)
        var y = 80f; var page = 0
        entries.forEachIndexed { i, e ->
            if (i == 8) { page = 1; y = 80f; header(60f, 1) }
            put("%02d/09/26".format(e.day), 0, y, page)
            // Narration printed across two lines, as PDFs wrap long ones.
            put(if (e.kind == "NEFT") "NEFT CR-CITI0000001-${e.name}" else "UPI-${e.name}-${e.vpa}", 1, y, page)
            e.dr?.let { put(money(it * 100L), 2, y, page, true) }
            e.cr?.let { put(money(it * 100L), 3, y, page, true) }
            put(money(b[i]), 4, y, page, true)
            put("-HDFC0001234-${ref(i)}", 1, y + 10f, page)
            y += 24f
        }
        check(StatementInterpreter.interpret(PositionedTable.toTable(words), ImportFormat.PDF))
    }

    /** Payment-app history screenshots (Google Pay / PhonePe style), read by OCR as lines with positions. */
    @Test fun appHistoryScreenshots() {
        val now = java.util.Calendar.getInstance().apply { set(2026, 8, 28, 12, 0) }.timeInMillis
        fun l(text: String, x: Float, y: Float) = AppHistoryParser.Line(text, x, y, 30f)
        val gpay = listOf(
            l("September 2026", 60f, 150f),
            l("SBOW", 180f, 300f), l("₹12,000", 900f, 300f), l("19 Sep, 8:10 pm", 180f, 345f),
            l("Uber India", 180f, 450f), l("₹600", 900f, 450f), l("19 Sep, 11:40 pm", 180f, 495f),
            l("Rahul Sharma", 180f, 600f), l("+₹1,000", 900f, 600f), l("20 Sep, 11:00 am", 180f, 645f),
            l("Swiggy", 180f, 750f), l("₹250", 900f, 750f), l("Failed", 180f, 795f),
        )
        val rows = AppHistoryParser.parse(gpay, now)
        assertEquals(listOf(12_000_00L, 600_00L, 1_000_00L), rows.map { it.amountPaise })
        assertEquals(listOf(TransactionType.DEBIT, TransactionType.DEBIT, TransactionType.CREDIT), rows.map { it.type })
        assertEquals("Rahul Sharma", rows[2].counterparty)
        assertEquals(CounterpartyKind.PERSON, rows[2].kind)
        assertTrue("time kept", rows[0].time != null)

        val phonepe = listOf(
            l("Paid to", 180f, 300f), l("₹600", 900f, 300f), l("Uber India", 180f, 330f), l("19 Sep 2026, 11:40 PM", 180f, 370f), l("Debited from", 600f, 370f),
            l("Received from", 180f, 500f), l("₹1,200", 900f, 500f), l("Priya Nair", 180f, 530f), l("20 Sep 2026, 11:20 AM", 180f, 570f), l("Credited to", 600f, 570f),
        )
        val pp = AppHistoryParser.parse(phonepe, now)
        assertEquals(listOf(TransactionType.DEBIT, TransactionType.CREDIT), pp.map { it.type })
        assertEquals("Priya Nair", pp[1].counterparty)
        // The same rows in two overlapping screenshots are read once.
        assertEquals(3, AppHistoryParser.parse(gpay + gpay.map { it.copy(y = it.y + 10_000f) }, now).size)
    }

    // A minimal .xlsx: one sheet, inline strings.
    private fun xlsx(rows: List<List<String>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            fun put(name: String, text: String) { z.putNextEntry(ZipEntry(name)); z.write(text.toByteArray()); z.closeEntry() }
            put("[Content_Types].xml", "<Types/>")
            val sheet = buildString {
                append("<worksheet><sheetData>")
                rows.forEachIndexed { r, cells ->
                    append("<row r=\"${r + 1}\">")
                    cells.forEachIndexed { c, v -> append("<c r=\"${('A' + c)}${r + 1}\" t=\"inlineStr\"><is><t>${v.replace("&", "&amp;")}</t></is></c>") }
                    append("</row>")
                }
                append("</sheetData></worksheet>")
            }
            put("xl/worksheets/sheet1.xml", sheet)
        }
        return out.toByteArray()
    }
}
