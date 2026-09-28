package com.pft.financetracker.domain.importer

import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.domain.parser.MerchantExtractor
import com.pft.financetracker.domain.parser.RefExtractor
import com.pft.financetracker.domain.split.PayerClassifier
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import kotlin.math.abs

enum class ImportFormat(val label: String) {
    CSV("CSV"), XLSX("Excel"), XLS_HTML("Excel (.xls)"), PDF("PDF"), PDF_SCANNED("Scanned PDF"), IMAGE("Statement image"), APP_SCREENSHOT("Payment app screenshot")
}

/** One transaction read from a statement or screenshot, before it is stored. */
data class StatementRow(
    /** Local midnight of the transaction date. */
    val date: Long,
    /** Clock time when the source gave one (screenshots, some statements); otherwise null. */
    val time: Long?,
    val amountPaise: Long,
    val type: TransactionType,
    val narration: String,
    val counterparty: String,
    val ref: String?,
    val balancePaise: Long?,
    /** true/false when the running balance was checked, null when the file had no balance column. */
    val balanceOk: Boolean?,
    val category: Category,
    val flow: Flow,
    val kind: CounterpartyKind,
    /** Position among the same day's rows in chronological order, used to keep same-day rows in order. */
    val order: Int,
    /**
     * 0 for the first row with this date, amount, direction, narration and balance; 1, 2... for identical rows after it
     * (two Rs 20 teas on one day in a card statement). Keeps each one a separate transaction.
     */
    val occurrence: Int = 0,
)

/** A row that looked like a transaction but could not be read with confidence: it goes to the review list. */
data class RowProblem(val raw: String, val reason: String, val date: Long?, val amountPaise: Long?, val type: TransactionType?)

data class ParsedStatement(val format: ImportFormat, val rows: List<StatementRow>, val problems: List<RowProblem>, val note: String? = null) {
    val balanceMismatches: Int get() = problems.count { it.reason == "balance_mismatch" }
    val firstDate: Long? get() = rows.minOfOrNull { it.date }
    val lastDate: Long? get() = rows.maxOfOrNull { it.date }
}

/**
 * Finds the transaction table in any bank's statement by what the columns mean, not by bank. No templates: a header
 * row is recognised from words every Indian bank uses (Date / Narration / Withdrawal / Deposit / Balance and their
 * synonyms). Without a usable header, columns are inferred from their content (dates, amounts, a running balance).
 */
object ColumnDetector {
    enum class Role { DATE, VALUE_DATE, DESC, REF, DEBIT, CREDIT, AMOUNT, DRCR, BALANCE }

    data class Mapping(val headerRow: Int, val cols: Map<Role, Int>) {
        operator fun get(r: Role) = cols[r]
        val usable: Boolean get() = (cols.containsKey(Role.DATE) || cols.containsKey(Role.VALUE_DATE)) &&
            (cols.containsKey(Role.DEBIT) && cols.containsKey(Role.CREDIT) || cols.containsKey(Role.AMOUNT))
    }

    private val synonyms: List<Pair<Role, List<String>>> = listOf(
        Role.VALUE_DATE to listOf("value date", "value dt", "valuedate"),
        Role.DATE to listOf("txn date", "transaction date", "tran date", "trans date", "posting date", "post date", "date", "txn dt", "tran dt", "book date", "date of transaction"),
        Role.DESC to listOf("narration", "description", "particulars", "transaction remarks", "remarks", "transaction details", "details", "transaction description", "txn description", "beneficiary", "merchant"),
        Role.REF to listOf("chq ref no", "chq/ref no", "ref no", "reference no", "reference", "cheque no", "chq no", "cheque number", "chq number", "utr", "instrument no", "ref no cheque no", "transaction id", "txn id"),
        Role.DEBIT to listOf("withdrawal amt", "withdrawal amount", "withdrawals", "withdrawal", "debit amount", "debits", "debit", "dr amount", "dr", "paid out", "money out", "amount debited"),
        Role.CREDIT to listOf("deposit amt", "deposit amount", "deposits", "deposit", "credit amount", "credits", "credit", "cr amount", "cr", "paid in", "money in", "amount credited"),
        Role.DRCR to listOf("dr cr", "cr dr", "dr/cr", "cr/dr", "debit/credit", "debit credit", "txn type", "transaction type", "type"),
        Role.AMOUNT to listOf("transaction amount", "txn amount", "amount inr", "amount", "amt"),
        Role.BALANCE to listOf("closing balance", "running balance", "available balance", "balance amt", "balance", "bal"),
    )

    fun norm(s: String) = s.lowercase(Locale.ROOT).replace(Regex("""\(.*?\)|inr|rs\.?|₹"""), " ").replace(Regex("""[^a-z/ ]"""), " ")
        .replace(Regex("""\s*/\s*"""), "/").replace(Regex("""\s+"""), " ").trim()

    /** True when the cell is exactly a header word (used to spot a header repeated on a later page). */
    fun isHeaderCell(cell: String): Boolean { val n = norm(cell); return n.isNotEmpty() && synonyms.any { (_, ws) -> n in ws } }

    private val cardRx = Regex("""\bcard\b""")

    /** Role for one header cell, or null. Longest synonym wins ("withdrawal amt" over "amt"). */
    fun roleOf(cell: String): Role? {
        val n = norm(cell)
        if (n.isEmpty() || n.length > 40) return null
        // "Credit Card No" / "Card Number" hold a masked card number, not money.
        if (cardRx.containsMatchIn(n)) return null
        var best: Role? = null; var bestLen = 0
        for ((role, words) in synonyms) for (w in words) {
            val hit = n == w || n.startsWith("$w ") || n.endsWith(" $w") || (w.length >= 5 && n.contains(w))
            if (hit && w.length > bestLen) { best = role; bestLen = w.length }
        }
        return best
    }

    fun find(table: Table): Mapping? {
        var best: Mapping? = null
        // Some banks put a long account summary above the table: look well past it.
        for ((i, row) in table.withIndex().take(300)) {
            val cols = roles(row)
            if (cols.size >= 3) {
                val m = Mapping(i, fixDate(cols))
                if (m.usable && (best == null || m.cols.size > best.cols.size)) best = m
                if (m.usable) break
            }
        }
        return best?.let { twoLineHeader(table, it) } ?: inferFromContent(table)
    }

    private fun roles(row: List<String>): MutableMap<Role, Int> {
        val cols = mutableMapOf<Role, Int>()
        // "Dr / Cr" twice (Kotak: one after Amount, one after Balance): the first one is the transaction's.
        row.forEachIndexed { j, cell -> roleOf(cell)?.let { r -> if (r !in cols) cols[r] = j } }
        return cols
    }

    /** "Withdrawal" over "Amt.", "Closing" over "Balance": a header split over two rows reads as one. */
    private fun twoLineHeader(table: Table, m: Mapping): Mapping {
        val top = table[m.headerRow]
        val next = table.getOrNull(m.headerRow + 1) ?: return m
        if (next.none { it.isNotBlank() } || next.any { Dates.parse(it) != null || Amounts.parse(it) != null }) return m
        val merged = (0 until maxOf(top.size, next.size)).map { j -> "${top.getOrElse(j) { "" }} ${next.getOrElse(j) { "" }}".trim() }
        val two = Mapping(m.headerRow + 1, fixDate(roles(merged)))
        return if (two.usable && two.cols.size > m.cols.size) two else m
    }

    private fun fixDate(cols: MutableMap<Role, Int>): Map<Role, Int> {
        if (Role.DATE !in cols && Role.VALUE_DATE in cols) cols[Role.DATE] = cols[Role.VALUE_DATE]!!
        return cols
    }

    /** No header: the date column is the one full of dates, the balance the last amount column, and so on. */
    private fun inferFromContent(table: Table): Mapping? {
        val sample = table.filter { r -> r.any { Dates.parse(it) != null } }.take(40)
        if (sample.size < 3) return null
        val width = sample.maxOf { it.size }
        fun share(j: Int, pred: (String) -> Boolean) = sample.count { r -> r.getOrNull(j)?.let(pred) == true }.toDouble() / sample.size
        val dateCol = (0 until width).maxByOrNull { share(it) { c -> Dates.parse(c) != null } } ?: return null
        // A column of amounts, even a mostly empty one (one salary credit among thirty spends). Reference numbers
        // (long whole numbers) are not amounts.
        val refLike = Regex("""^\d{9,}$""")
        val amountCols = (0 until width).filter { j ->
            if (j == dateCol) return@filter false
            val cells = sample.mapNotNull { it.getOrNull(j)?.trim()?.takeIf { c -> c.isNotEmpty() } }
            cells.isNotEmpty() && cells.count { Amounts.parse(it) != null && !refLike.matches(it) } >= 0.9 * cells.size && share(j) { c -> Amounts.parse(c) != null } > 0.05
        }
        val descCol = (0 until width).filter { it != dateCol && it !in amountCols }.maxByOrNull { j -> sample.sumOf { it.getOrNull(j)?.length ?: 0 } } ?: return null
        val cols = mutableMapOf(Role.DATE to dateCol, Role.DESC to descCol)
        when (amountCols.size) {
            0 -> return null
            1 -> cols[Role.AMOUNT] = amountCols[0]
            2 -> { cols[Role.AMOUNT] = amountCols[0]; cols[Role.BALANCE] = amountCols[1] }
            else -> { cols[Role.DEBIT] = amountCols[amountCols.size - 3]; cols[Role.CREDIT] = amountCols[amountCols.size - 2]; cols[Role.BALANCE] = amountCols.last() }
        }
        return Mapping(-1, cols)
    }
}

/** Dates as banks print them, plus Excel serials. Indian order (day first) when ambiguous. */
object Dates {
    private val patterns = listOf(
        // XXX reads "+05:30" and "Z", XX "+0530", X "+05".
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX", "yyyy-MM-dd'T'HH:mm:ssXX", "yyyy-MM-dd'T'HH:mm:ss.SSSX", "yyyy-MM-dd'T'HH:mm:ssX",
        "dd/MM/yyyy HH:mm:ss", "dd/MM/yyyy HH:mm", "dd-MM-yyyy HH:mm:ss", "dd-MM-yyyy HH:mm", "yyyy-MM-dd HH:mm:ss", "yyyy-MM-dd'T'HH:mm:ss",
        "dd MMM yyyy, hh:mm a", "dd MMM yyyy hh:mm a", "dd MMM yyyy HH:mm", "dd-MMM-yyyy HH:mm:ss",
        "dd/MM/yyyy", "dd/MM/yy", "dd-MM-yyyy", "dd-MM-yy", "dd.MM.yyyy", "dd.MM.yy", "yyyy-MM-dd", "yyyy/MM/dd",
        "dd-MMM-yyyy", "dd-MMM-yy", "dd MMM yyyy", "d MMM yyyy", "dd MMM yy", "dd/MMM/yyyy", "dd/MMM/yy", "ddMMMyyyy",
        "MMM dd, yyyy", "MMM d, yyyy", "dd MMMM yyyy", "d MMMM yyyy", "dd MMMM, yyyy", "MMMM d, yyyy", "dd-MMMM-yyyy",
    )
    private val looksDate = Regex("""\d{1,4}[-/. ](?:\d{1,2}|[A-Za-z]{3,9})[-/. ,]+\d{2,4}|^[A-Za-z]{3,9} \d{1,2}, \d{4}|^\d{1,2}[A-Za-z]{3}\d{2,4}""")

    private val spaces = Regex("""\s+""")
    private val septRx = Regex("""(?i)\bsept\b""")

    /**
     * A cell of the date column: anything [parse] reads, plus Excel serials with a short fraction ("46266.5" is noon).
     * Elsewhere those look like money, so only the date column reads them as dates.
     */
    fun parseDateCell(raw: String): Long? = parse(raw) ?: raw.trim().takeIf { it.contains('.') }?.let { excelSerial("xlserial:$it") }

    /** Epoch millis (local time), or null. */
    fun parse(raw: String, now: Long = System.currentTimeMillis()): Long? {
        // "Sept" is how Indian banks and en-IN write September; the English formatter only knows "Sep".
        val s = raw.trim().replace(spaces, " ").replace(septRx, "Sep")
        if (s.isEmpty() || s.length > 32) return null
        excelSerial(s)?.let { return it }
        if (!looksDate.containsMatchIn(s)) return null
        for (p in patterns) {
            val f = SimpleDateFormat(p, Locale.ENGLISH).apply { isLenient = false }
            val pos = java.text.ParsePosition(0)
            val d = runCatching { f.parse(s, pos) }.getOrNull() ?: continue
            if (pos.index < s.length && s.substring(pos.index).any { it.isLetterOrDigit() }) continue
            val cal = Calendar.getInstance().apply { time = d }
            if (cal.get(Calendar.YEAR) < 100) cal.add(Calendar.YEAR, 2000)
            val t = cal.timeInMillis
            if (t in (now - 15L * 365 * 86_400_000L)..(now + 2 * 86_400_000L)) return t
        }
        return null
    }

    private fun excelSerial(s: String): Long? {
        val v = s.removePrefix("xlserial:").toDoubleOrNull() ?: return null
        if (v < 30_000 || v > 60_000) return null
        // Unmarked, only a whole day number or one with a time fraction is a serial: "45000.00" is money.
        if (!s.startsWith("xlserial:") && s.contains('.') && s.substringAfter('.').length <= 2) return null
        // Excel day 25569 = 1970-01-01. Serials carry no time zone: read them as local wall-clock time.
        val utc = Math.round((v - 25_569) * 86_400_000.0)
        return utc - TimeZone.getDefault().getOffset(utc)
    }

    fun startOfDay(t: Long): Long = Calendar.getInstance().apply {
        timeInMillis = t; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    fun hasTime(t: Long): Boolean = t != startOfDay(t)
}

/** Amounts as statements print them: "1,23,456.78", "₹ 500", "500.00 Dr", "(250.00)", "-", "". */
object Amounts {
    data class Signed(val paise: Long, val dr: Boolean?)

    // Groups: 1 "Dr"/"Cr" before, 2 "(", 3 and 4 a minus before or after the currency, 5 the number, 6 a trailing
    // minus, 7 "Dr"/"Cr" after (optionally bracketed).
    private val rx = Regex(
        """^(?:(dr|cr)\.?\s*)?(\()?\s*(-)?\s*(?:₹|rs\.?|inr)?\s*(-)?\s*(\d{1,3}(?:,\d{2,3})*(?:\.\d{1,2})?|\d+(?:\.\d{1,2})?)\s*(-)?\s*\)?\s*\(?\s*(dr|cr|db|d|c)?\.?\s*\)?$""",
        RegexOption.IGNORE_CASE,
    )

    fun parseSigned(raw: String): Signed? {
        val s = raw.trim().replace(' ', ' ')
        if (s.isEmpty() || s == "-" || s == "--") return null
        val m = rx.find(s) ?: return null
        val g = m.groupValues
        val paise = Math.round((g[5].replace(",", "").toDoubleOrNull() ?: return null) * 100)
        val mark = (g[7].ifEmpty { g[1] }).lowercase()
        val negative = g[2].isNotEmpty() || g[3].isNotEmpty() || g[4].isNotEmpty() || g[6].isNotEmpty()
        val dr = when {
            mark.startsWith("d") -> true
            mark.startsWith("c") -> false
            negative -> true
            else -> null
        }
        return Signed(paise, dr)
    }

    fun parse(raw: String): Long? = parseSigned(raw)?.paise
}

/**
 * Who is on the other side of a statement line. Narrations are machine-written ("UPI/CR/412345678901/RAHUL SH/
 * HDFC/rahul@okhdfc/UPI", "UPI-SBOW-sbow.pay@ybl-YESB0000001-412345678901-Payment", "NEFT CR-HDFC0000001-ACME CORP
 * LTD-SALARY", "POS 416021XXXXXX1234 SWIGGY BANGALORE"); the name is the one segment that is not a channel, a
 * reference number, a bank code or a UPI handle.
 */
object NarrationParser {
    private val channel = Regex("""^(upi|imps|neft|rtgs|nft|ift|ach|nach|ecs|pos|ecom|mmt|p2a|p2m|p2p|dr|cr|by transfer|to transfer|by|to|trf|transfer|inb|mob|mb|ib|bil|billpay|rev|chq|clg|cash|sweep)$""", RegexOption.IGNORE_CASE)
    private val ifsc = Regex("""^[A-Z]{4}0[A-Z0-9]{6}$""", RegexOption.IGNORE_CASE)
    private val bankCode = Regex("""^(hdfc|icic|icici|sbin|sbi|utib|axis|kkbk|kotak|yesb|punb|pnb|barb|bob|cnrb|ubin|idib|ioba|ucba|mahb|fdrl|indb|idfb|ratn|aubl|bkid|cbin|jake|kvbl|citi|hsbc|scbl|dbss|pytm|airp|jiop|ippb|ybl|ibl|axl|okaxis|okhdfcbank|okicici|oksbi)( bank)?$""", RegexOption.IGNORE_CASE)
    private val generic = Regex("""^(payment|payment from ph|payment from phone|upi payment|sent using paytm|paytm|phonepe|google pay|gpay|pay to|paid via|collect|request|na|null|none|upi--|imps transfer|fund transfer|transfer to|transfer from|salary|received|sent|money|misc|others?)$""", RegexOption.IGNORE_CASE)
    private val card = Regex("""\b\d{4,6}[x*]{4,}\d{2,4}\b|\b[x*]{4,}\d{3,4}\b""", RegexOption.IGNORE_CASE)

    data class Parsed(val name: String, val vpa: String?, val ref: String?)

    fun parse(narration: String, type: TransactionType): Parsed {
        val n = narration.replace(Regex("""\s+"""), " ").trim()
        val vpa = Regex("""[a-z0-9][a-z0-9._\-]{1,60}@[a-z][a-z0-9]{1,20}""", RegexOption.IGNORE_CASE).find(n)?.value
        val ref = Regex("""(?<!\d)(\d{12})(?!\d)""").find(n)?.groupValues?.get(1) ?: RefExtractor.extract(n)
        val cleaned = card.replace(n, " ")
        val segments = cleaned.split('/', '-', ':', '|', '*').map { it.trim() }.filter { it.isNotEmpty() }
        val name = segments.firstOrNull { seg ->
            val letters = seg.count { it.isLetter() }
            letters >= 3 && !seg.contains('@') && !seg.split(' ').all { w -> channel.matches(w) } && !ifsc.matches(seg) && !bankCode.matches(seg) && !generic.matches(seg) &&
                seg.count { it.isDigit() } <= 2 && !seg.lowercase().startsWith("upi ")
        }?.let { strip(it) }
        val best = name?.takeIf { it.length >= 3 }
            ?: vpa?.let { MerchantExtractor.clean(it) }?.takeIf { it.length >= 3 && !it.all { c -> c.isDigit() } }
            ?: MerchantExtractor.extract(n, type)
            ?: n.split(' ').filter { w -> w.count { it.isLetter() } >= 3 }.take(3).joinToString(" ").ifBlank { if (type == TransactionType.CREDIT) "Credit" else "Payment" }
        return Parsed(MerchantExtractor.clean(best), vpa, ref)
    }

    /** Drop trailing city names and filler from a POS line ("SWIGGY BANGALORE IN" -> "Swiggy Bangalore"). */
    private fun strip(s: String): String = s.replace(Regex("""\b(in|ind|india)$""", RegexOption.IGNORE_CASE), "").trim()
        .split(' ').take(4).joinToString(" ")
}

/**
 * Turns a table (from CSV, Excel, a PDF or OCR) into statement rows, checking every row it can against the running
 * balance. A row whose balance does not add up is not guessed: it goes to the review list.
 */
object StatementInterpreter {
    private val footer = Regex("""\b(opening balance|closing balance|total|grand total|statement summary|brought forward|carried forward|b/f|c/f|end of statement|page \d+)\b""", RegexOption.IGNORE_CASE)
    /** Footer phrases no narration uses: wherever they appear on an undated line, the line is not a transaction. */
    private val footerPhrase = Regex("""\b(opening balance|closing balance|statement summary|brought forward|carried forward|b/f|c/f|end of statement|page \d+)\b""", RegexOption.IGNORE_CASE)
    /** "Total" on its own (with numbers): "TOTAL ENERGIES" is a narration that wrapped, "Total | 1,000.00" is a footer. */
    private val totalOnly = Regex("""^\s*(grand )?total\b[^a-z]*(?:\b(?:cr|dr)\b[^a-z]*)?$""", RegexOption.IGNORE_CASE)
    private val summaryStart = Regex("""\b(statement summary|end of statement)\b""", RegexOption.IGNORE_CASE)
    private val openingRx = Regex("""opening balance|brought forward|b/f""", RegexOption.IGNORE_CASE)

    fun interpret(table: Table, format: ImportFormat): ParsedStatement {
        val map = ColumnDetector.find(table) ?: return ParsedStatement(format, emptyList(), emptyList(), "No transaction table found. Is this a bank statement?")
        data class Raw(val date: Long?, val narration: String, val ref: String?, val debit: Long?, val credit: Long?, val amount: Amounts.Signed?, val drcr: String?, val balance: Amounts.Signed?, val text: String)
        val raws = mutableListOf<Raw>()
        val problems = mutableListOf<RowProblem>()
        var opening: Long? = null
        var inSummary = false
        for ((i, row) in table.withIndex()) {
            if (i <= map.headerRow) continue
            fun cell(r: ColumnDetector.Role) = map[r]?.let { row.getOrNull(it) }?.trim().orEmpty()
            val text = row.filter { it.isNotBlank() }.joinToString(" | ")
            if (row.count { ColumnDetector.isHeaderCell(it) } >= 3) continue // header repeated on a new page
            val date = Dates.parseDateCell(cell(ColumnDetector.Role.DATE)) ?: Dates.parseDateCell(cell(ColumnDetector.Role.VALUE_DATE))
            val desc = cell(ColumnDetector.Role.DESC)
            val hasMoney = listOf(ColumnDetector.Role.DEBIT, ColumnDetector.Role.CREDIT, ColumnDetector.Role.AMOUNT).any { Amounts.parse(cell(it)) != null }
            if (date == null) {
                // An account summary ("Opening Balance  Dr Count  Cr Count  Debits ...") has undated number lines that are
                // not transactions. Dated rows after it (a consolidated statement) are still read.
                if (summaryStart.containsMatchIn(text)) inSummary = true
                if (footerPhrase.containsMatchIn(text) || totalOnly.matches(text) || (hasMoney && footer.containsMatchIn(text))) {
                    if (openingRx.containsMatchIn(text) && opening == null)
                        opening = Amounts.parseSigned(cell(ColumnDetector.Role.BALANCE))?.let { if (it.dr == true) -it.paise else it.paise }
                    continue
                }
                if (!hasMoney) {
                    // A narration that wrapped onto the next line.
                    val last = raws.lastOrNull()
                    if (last != null && desc.isNotBlank()) raws[raws.size - 1] = last.copy(narration = "${last.narration} $desc".trim(), text = "${last.text} $desc")
                    continue
                }
                // Bare numbers with no narration, or the numbers of an account summary: not a transaction.
                if (desc.isBlank() || inSummary) continue
                // Money and a narration on a line whose date can't be read: never guess, never drop it. It goes to review below.
            }
            if (footer.containsMatchIn(desc) && openingRx.containsMatchIn(desc)) {
                opening = Amounts.parseSigned(cell(ColumnDetector.Role.BALANCE))?.let { if (it.dr == true) -it.paise else it.paise }
                continue
            }
            raws += Raw(
                date, desc.ifBlank { text }, usableRef(cell(ColumnDetector.Role.REF)),
                Amounts.parse(cell(ColumnDetector.Role.DEBIT))?.takeIf { it > 0 }, Amounts.parse(cell(ColumnDetector.Role.CREDIT))?.takeIf { it > 0 },
                Amounts.parseSigned(cell(ColumnDetector.Role.AMOUNT)), cell(ColumnDetector.Role.DRCR).ifBlank { null },
                Amounts.parseSigned(cell(ColumnDetector.Role.BALANCE)), text,
            )
        }

        // Direction and amount for each row.
        data class Mid(val raw: Raw, val paise: Long, val dr: Boolean?, val bal: Long?)
        val mids = raws.mapNotNull { r ->
            val bal = r.balance?.let { if (it.dr == true) -it.paise else it.paise }
            when {
                r.debit != null && r.credit == null -> Mid(r, r.debit, true, bal)
                r.credit != null && r.debit == null -> Mid(r, r.credit, false, bal)
                r.amount != null -> {
                    val flag = r.drcr?.let { Amounts.parseSigned("0 $it")?.dr ?: when { it.startsWith("d", true) -> true; it.startsWith("c", true) -> false; else -> null } }
                    Mid(r, r.amount.paise, flag ?: r.amount.dr, bal)
                }
                else -> { problems += RowProblem(r.text, "no_amount", r.date, null, null); null }
            }
        }.filter { it.paise > 0 }

        // Statements run oldest-first or newest-first; the running balance says which.
        fun fits(prev: Long, m: Mid, dr: Boolean) = abs(prev + (if (dr) -m.paise else m.paise) - m.bal!!) <= 1
        fun score(list: List<Mid>): Int = list.zipWithNext().count { (a, b) -> a.bal != null && b.bal != null && b.dr != null && fits(a.bal, b, b.dr) }
        val dated = mids.filter { it.raw.date != null }
        val firstDate = dated.firstOrNull()?.raw?.date; val lastDate = dated.lastOrNull()?.raw?.date
        val chronological = if (dated.size >= 2 && firstDate!! > lastDate!!) mids.reversed()
            else if (dated.size >= 2 && firstDate == lastDate && score(mids.reversed()) > score(mids)) mids.reversed() else mids

        // No balance to check against: statements without one (credit cards) mark the exception, almost always the
        // credits ("5,000.00 Cr"). An unmarked amount is then a spend; if only debits are marked, it is a credit.
        val noBalance = chronological.none { it.bal != null }
        val marks = chronological.mapNotNull { it.dr }.toSet()
        val unmarked: Boolean? = when {
            !noBalance || marks.isEmpty() -> null
            false in marks -> true
            else -> false
        }

        val rows = mutableListOf<StatementRow>()
        var prevBal = opening
        for ((order, m) in chronological.withIndex()) {
            var dr = m.dr ?: unmarked
            var ok: Boolean? = null
            if (m.bal != null && prevBal != null) {
                if (dr == null) dr = when { fits(prevBal, m, true) -> true; fits(prevBal, m, false) -> false; else -> null }
                ok = dr != null && fits(prevBal, m, dr)
            }
            if (m.bal != null) prevBal = m.bal
            val type = dr?.let { if (it) TransactionType.DEBIT else TransactionType.CREDIT }
            val day = m.raw.date
            if (day == null) { problems += RowProblem(m.raw.text, "date_unknown", null, m.paise, type); continue }
            if (type == null) { problems += RowProblem(m.raw.text, "direction_unknown", day, m.paise, null); continue }
            if (ok == false) { problems += RowProblem(m.raw.text, "balance_mismatch", day, m.paise, type) }
            rows += toRow(day, m.paise, type, m.raw.narration, m.raw.ref, m.bal, ok, order)
        }
        return ParsedStatement(format, numbered(rows.filter { it.balanceOk != false }), problems)
    }

    /**
     * Same-day order counted within the day (the importer turns it into seconds after noon, so a whole-file count would
     * push big files' late rows into the next day), and identical rows numbered so each stays its own transaction.
     */
    fun numbered(rows: List<StatementRow>): List<StatementRow> {
        val perDay = mutableMapOf<Long, Int>()
        val seen = mutableMapOf<List<Any?>, Int>()
        return rows.map { r ->
            val key = listOf(r.date, r.amountPaise, r.type, r.narration.trim().lowercase(), r.balancePaise)
            val n = seen[key] ?: 0; seen[key] = n + 1
            val o = perDay[r.date] ?: 0; perDay[r.date] = o + 1
            r.copy(order = o, occurrence = n)
        }
    }

    /** A reference number worth matching on. Banks fill the column with "0", "-", "NA" or zeros when there is none. */
    fun usableRef(raw: String?): String? {
        val r = raw?.trim().orEmpty()
        return r.takeIf { it.count { c -> c.isLetterOrDigit() } >= 6 && it.any { c -> c in '1'..'9' } }
    }

    /** Shared by statements and screenshots: counterparty, category, flow and person/organisation for one row. */
    fun toRow(dateOrTime: Long, paise: Long, type: TransactionType, narration: String, ref: String?, balance: Long?, ok: Boolean?, order: Int, knownName: String? = null): StatementRow {
        val parsed = NarrationParser.parse(narration, type)
        val name = knownName?.let { MerchantExtractor.clean(it) }?.takeIf { it.length >= 2 } ?: parsed.name
        var category = Categorizer.categorize(name, type)
        if (category == Category.OTHER || category == Category.INCOME) Categorizer.categorize("$name $narration", type).takeIf { it != Category.OTHER }?.let { category = it }
        val flow = FlowClassifier.classify(type, narration, name, category)
        val kind = PayerClassifier.classify(narration + (parsed.vpa?.let { " $it" } ?: ""), name, type)
        val day = Dates.startOfDay(dateOrTime)
        return StatementRow(day, if (Dates.hasTime(dateOrTime)) dateOrTime else null, paise, type, narration, name, ref ?: parsed.ref, balance, ok, category, flow, kind, order)
    }
}
