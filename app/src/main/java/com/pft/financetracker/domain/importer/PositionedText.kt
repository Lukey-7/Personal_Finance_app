package com.pft.financetracker.domain.importer

import com.pft.financetracker.domain.model.TransactionType
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs

/** A word (or OCR line fragment) and where it sits on the page, in page units with y growing downwards. */
data class Word(val text: String, val x: Float, val y: Float, val w: Float, val h: Float, val page: Int = 0) {
    val right get() = x + w
    val cx get() = x + w / 2
}

/**
 * Rebuilds a statement table from positioned text, for PDFs (text with coordinates) and OCR'd statement photos.
 * Lines are words at the same height; cells are runs of words close together; columns come from the header row's
 * positions (each data cell goes under the header it sits beneath). The result is an ordinary [Table], so PDFs and
 * photos share every later step with CSV and Excel.
 */
object PositionedTable {
    private class Line(val page: Int, val y: Float, val h: Float, val words: MutableList<Word>)
    private data class Chunk(val text: String, val left: Float, val right: Float) { val cx get() = (left + right) / 2 }

    fun toTable(words: List<Word>): Table {
        if (words.isEmpty()) return emptyList()
        val lines = lines(words)
        val chunked = lines.map { chunks(it) }
        val headerIdx = chunked.indexOfFirst { cs -> cs.count { ColumnDetector.roleOf(it.text) != null } >= 3 && cs.any { c -> ColumnDetector.roleOf(c.text).let { it == ColumnDetector.Role.DATE || it == ColumnDetector.Role.VALUE_DATE } } }
        if (headerIdx < 0) return chunked.map { cs -> cs.map { it.text } }

        var header = chunked[headerIdx]
        // Two-line headers ("Withdrawal" over "Amount"): fold the second line into the first.
        chunked.getOrNull(headerIdx + 1)?.let { next ->
            val hasData = next.any { Dates.parse(it.text) != null || Amounts.parse(it.text) != null }
            if (!hasData && next.isNotEmpty() && next.all { n -> header.any { h -> n.cx in (h.left - 20)..(h.right + 20) } }) {
                header = header.map { h -> val add = next.filter { it.cx in (h.left - 20)..(h.right + 20) }.joinToString(" ") { it.text }; if (add.isEmpty()) h else h.copy(text = "${h.text} $add") }
            }
        }
        val centers = header.map { it.cx }
        val bounds = centers.zipWithNext { a, b -> (a + b) / 2 }
        fun col(c: Chunk): Int {
            // Numbers are right-aligned: judge them by their right edge against the header's right edge.
            val probe = if (Amounts.parse(c.text) != null) c.right - 1 else c.cx
            val byBounds = bounds.indexOfFirst { probe < it }.let { if (it < 0) bounds.size else it }
            if (Amounts.parse(c.text) != null) {
                val nearest = header.indices.minByOrNull { abs(header[it].right - c.right) } ?: byBounds
                if (abs(header[nearest].right - c.right) < abs(header[byBounds].cx - c.cx)) return nearest
            }
            return byBounds
        }

        data class Row(val page: Int, val y: Float, val cells: MutableList<String>)
        val body = mutableListOf<Row>()
        for ((i, cs) in chunked.withIndex()) {
            if (i <= headerIdx + (if (header !== chunked[headerIdx]) 1 else 0)) continue
            // A repeated header on a later page.
            if (cs.count { ColumnDetector.isHeaderCell(it.text) } >= 3) continue
            val cells = MutableList(header.size) { "" }
            for (c in cs) { val j = col(c); cells[j] = (cells[j] + " " + c.text).trim() }
            body += Row(lines[i].page, lines[i].y, cells)
        }

        // Wrapped narration lines sit below their date line (top-aligned rows) or on both sides of it (vertically centred
        // rows, where a long narration starts *above* the date). A run of text-only lines between two dated rows on one
        // page is split at its widest vertical gap: lines before it belong to the row above, lines after it to the row
        // below. A tie keeps them with the row above, which is how most statements are drawn.
        val roles = header.map { ColumnDetector.roleOf(it.text) }
        val moneyRoles = setOf(ColumnDetector.Role.DEBIT, ColumnDetector.Role.CREDIT, ColumnDetector.Role.AMOUNT, ColumnDetector.Role.BALANCE)
        val dated = BooleanArray(body.size) { k -> body[k].cells.indices.any { j -> (roles[j] == ColumnDetector.Role.DATE || roles[j] == ColumnDetector.Role.VALUE_DATE) && Dates.parse(body[k].cells[j]) != null } }
        val textOnly = BooleanArray(body.size) { k -> !dated[k] && body[k].cells.indices.none { j -> roles[j] in moneyRoles && body[k].cells[j].isNotBlank() } && body[k].cells.any { it.isNotBlank() } }
        val moveDown = BooleanArray(body.size)
        var k = 0
        while (k < body.size) {
            if (!textOnly[k]) { k++; continue }
            val start = k
            while (k < body.size && textOnly[k]) k++
            val above = start - 1; val below = k
            if (above < 0 || below >= body.size || !dated[above] || !dated[below]) continue
            if (body[above].page != body[below].page || (start until below).any { body[it].page != body[above].page }) continue
            // Gaps: above -> first line, between lines, last line -> below. Split after the widest (last widest on a tie).
            val ys = listOf(body[above].y) + (start until below).map { body[it].y } + body[below].y
            val gaps = ys.zipWithNext { a, b -> b - a }
            val widest = gaps.indices.maxWithOrNull(compareBy<Int> { gaps[it] }.thenBy { it })!!
            for (m in start + widest until below) moveDown[m] = true
        }
        val out = mutableListOf<List<String>>()
        out += header.map { it.text }
        val carry = mutableListOf<Row>()
        for ((m, r) in body.withIndex()) {
            if (moveDown[m]) { carry += r; continue }
            if (carry.isNotEmpty()) {
                for (j in r.cells.indices) r.cells[j] = (carry.joinToString(" ") { it.cells[j] } + " " + r.cells[j]).trim()
                carry.clear()
            }
            out += r.cells
        }
        return out
    }

    private fun lines(words: List<Word>): List<Line> {
        val out = mutableListOf<Line>()
        for (w in words.filter { it.text.isNotBlank() }.sortedWith(compareBy({ it.page }, { it.y }, { it.x }))) {
            val l = out.lastOrNull()
            if (l != null && l.page == w.page && abs(l.y - w.y) <= maxOf(2f, minOf(l.h, w.h) * 0.5f)) l.words += w
            else out += Line(w.page, w.y, w.h, mutableListOf(w))
        }
        return out
    }

    private fun chunks(l: Line): List<Chunk> {
        val ws = l.words.sortedBy { it.x }
        val out = mutableListOf<Chunk>()
        var cur: Chunk? = null
        for (w in ws) {
            val gap = cur?.let { w.x - it.right }
            cur = if (cur != null && gap != null && gap <= maxOf(w.h, l.h) * 0.8f) cur.copy(text = cur.text + " " + w.text, right = maxOf(cur.right, w.right))
            else { cur?.let { out += it }; Chunk(w.text, w.x, w.right) }
        }
        cur?.let { out += it }
        return out
    }
}

/**
 * Payment-app transaction history screenshots (Google Pay, PhonePe, Paytm, Amazon Pay, CRED, bank apps). No per-app
 * templates: a row is a name, an amount and a date or time, and the words around them say the direction ("Paid to",
 * "Received from", "+", "Debited", "Credited"). Failed and pending payments are skipped. Rows repeated across
 * overlapping screenshots are read once.
 */
object AppHistoryParser {
    private val amountRx = Regex("""([+\-−])?\s*(?:₹|rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)|([+\-−])\s*([\d,]{2,}(?:\.\d{1,2})?)$""", RegexOption.IGNORE_CASE)
    private val creditWords = Regex("""\b(received|credited|refund(?:ed)?|cashback|added to|from)\b""", RegexOption.IGNORE_CASE)
    private val debitWords = Regex("""\b(paid|sent|debited|payment to|to|spent|bill paid|recharge)\b""", RegexOption.IGNORE_CASE)
    private val skipWords = Regex("""\b(failed|declined|pending|processing|cancelled|expired|reversed|request(?:ed)?|scheduled)\b""", RegexOption.IGNORE_CASE)
    private val prefix = Regex("""^(paid to|payment to|money sent to|sent to|received from|money received from|refund from|cashback from|transfer to|transfer from|to|from|paid|received)\s*[:\-]?\s*""", RegexOption.IGNORE_CASE)
    private val noise = Regex("""^(debited from|credited to|paid|received|completed|successful|success|view details|upi|split|bank|₹.*|\d.*)$""", RegexOption.IGNORE_CASE)
    private val months = "jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december"
    private val dateLike = Regex("""\b(today|yesterday|\d{1,2}\s+(?:$months)\b[^\n]*|(?:$months)\s+\d{1,2}\b[^\n]*|\d{1,2}[/-]\d{1,2}[/-]\d{2,4}[^\n]*)""", RegexOption.IGNORE_CASE)
    /** "September", "September 2026": a month, not a day ("Sep 12" is a day header and dates the rows below it). */
    private val monthHeader = Regex("""^(?:$months)(?:\s+\d{4})?$""", RegexOption.IGNORE_CASE)
    private val statusStart = Regex("""^\W*(failed|declined|pending|processing|cancelled|expired|reversed|request(?:ed)?|scheduled)\b""", RegexOption.IGNORE_CASE)
    private val debitMarkers = Regex("""\b(paid to|sent to|debited)\b""", RegexOption.IGNORE_CASE)
    private val indianRx = Regex("""^\d{1,2}(,\d{2})*,\d{3}$""")

    /**
     * One OCR line; [w] is its box width (0 when unknown), used to tell a misread ₹ from a real digit. [page] is which
     * screenshot it came from (OCR also offsets [y] by page, so pages never share a row).
     */
    data class Line(val text: String, val x: Float, val y: Float, val h: Float, val w: Float = 0f, val page: Int = 0)

    fun parse(lines: List<Line>, now: Long = System.currentTimeMillis()): List<StatementRow> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.y }
        if (sorted.isEmpty()) return emptyList()
        val amounts = resolveAmounts(sorted)
        val rows = mutableListOf<StatementRow>()
        val pages = mutableListOf<Int>()
        var sectionDate: Long? = null
        val used = mutableSetOf<Int>()
        for ((i, l) in sorted.withIndex()) {
            // "September 2026" names a month, not a day: rows under it need their own date.
            if (monthHeader.matches(l.text.trim())) { sectionDate = null; continue }
            if (dateLike.matches(l.text.trim()) && amountRx.find(l.text) == null && sorted.none { o -> o !== l && abs(o.y - l.y) < l.h * 0.6 }) {
                parseWhen(l.text, now)?.let { sectionDate = it }
                continue
            }
            val am = amounts[i] ?: continue
            val paise = am.paise
            if (paise <= 0) continue
            val sign = am.sign
            // The row: lines at the same height, plus the one or two lines just below (date, status).
            val band = sorted.withIndex().filter { (j, o) -> j !in used && (abs(o.y - l.y) <= maxOf(l.h, o.h) * 0.7f || (o.y > l.y && o.y - l.y <= l.h * 2.6f)) }
            val text = band.joinToString(" \n ") { it.value.text }
            val named = band.filter { (j, o) -> o !== l && j !in amounts && amountRx.find(o.text) == null && dateLike.find(o.text) == null }.map { it.value }
                .map { it to prefix.replace(it.text.trim(), "").trim() }.firstOrNull { (_, n) -> n.isNotBlank() && !noise.matches(n) && n.count { c -> c.isLetter() } >= 2 }
                ?: (l to prefix.replace(l.text.replace(am.matched, "").trim(), "").trim()).takeIf { it.second.count { c -> c.isLetter() } >= 2 }
            // Failed, pending, requested or scheduled: a status word on any other line of the row ("Pending · Today",
            // "Failed - money will be refunded"), or at the start of the name line ("Request from Rahul"). A merchant
            // called "Pending Bills Store" under "Paid to" is still a payment.
            val status = band.any { (_, o) -> o !== named?.first && skipWords.containsMatchIn(o.text) } || (named != null && statusStart.containsMatchIn(named.first.text.replace(am.matched, "")))
            if (status) { band.forEach { used += it.index }; continue }
            val nameLine = named?.second ?: continue
            val type = when {
                sign == "+" -> TransactionType.CREDIT
                sign == "-" || sign == "−" -> TransactionType.DEBIT
                creditWords.containsMatchIn(text) && !debitMarkers.containsMatchIn(text) -> TransactionType.CREDIT
                else -> TransactionType.DEBIT
            }
            val whenText = band.map { it.value.text }.firstNotNullOfOrNull { dateLike.find(it)?.value }
            val at = whenText?.let { parseWhen(it, now) } ?: sectionDate ?: continue
            band.forEach { used += it.index }
            rows += StatementInterpreter.toRow(at, paise, type, text.replace("\n", " "), null, null, null, rows.size, knownName = nameLine)
            pages += l.page
        }
        // Overlapping screenshots show some rows twice. Within one screenshot two identical rows are two payments (two
        // Rs 20 teas on one day), so each row is kept as many times as the screenshot that shows it most often.
        val kept = rows.indices.groupBy { i -> rows[i].let { listOf(it.counterparty.lowercase(Locale.ROOT), it.amountPaise, it.type, it.time ?: it.date) } }.values
            .flatMap { same -> same.groupBy { pages[it] }.values.maxByOrNull { it.size }!!.map { rows[it] } }
        // Numbered like statement rows, so two identical teas stay two transactions when stored.
        return StatementInterpreter.numbered(kept.sortedWith(compareBy({ it.date }, { it.time ?: 0L })))
    }

    private data class Amt(val sign: String, val paise: Long, val matched: String)

    /** An amount alone on its line, right-aligned: "+ ₹300", "₹349", or what OCR makes of them ("+ 7300", "349", "T900"). */
    private val numericRx = Regex("""^([+\-−])?\s*(₹|rs\.?|inr|[^\d\s,.+\-−])?\s*(\d[\d,]*(?:\.\d{1,2})?)$""", RegexOption.IGNORE_CASE)

    /**
     * Amounts per line index. Payment apps print ₹ before every amount, and on-device OCR reads it badly: it drops
     * it ("349" for ₹349), reads it as a letter ("T900") or, worst, as a 7 ("+ 7300" for ₹300, "760" for ₹60).
     * Text alone cannot tell ₹60 from ₹760, but the box width can: amounts are right-aligned in one font, every
     * glyph about as wide as a digit, so a box three glyphs wide is "₹60". The glyph width is estimated from all the
     * amounts on the screen together, and each amount takes the reading that fits its own width.
     */
    private fun resolveAmounts(lines: List<Line>): Map<Int, Amt> {
        if (lines.isEmpty()) return emptyMap()
        val maxRight = lines.maxOf { it.x + it.w }
        data class Cand(val idx: Int, val line: Line, val sign: String, val junk: String?, val digits: String, val matched: String)
        val cands = lines.withIndex().mapNotNull { (i, l) ->
            val t = l.text.trim()
            val m = numericRx.matchEntire(t) ?: return@mapNotNull null
            if (maxRight > 0 && l.x < 0.45f * maxRight) return@mapNotNull null // amounts sit on the right; names and dates on the left
            Cand(i, l, m.groupValues[1], m.groupValues[2].ifBlank { null }, m.groupValues[3], t)
        }
        fun units(d: String) = d.sumOf { if (it.isDigit()) 1.0 else 0.35 }
        fun options(c: Cand): List<Pair<String, Double>> {
            val out = mutableListOf(c.digits to 1 + units(c.digits)) // "₹" + digits: ₹ dropped, or read as a letter
            if (c.junk == null && c.digits.length >= 2 && c.digits[0] in "72") c.digits.drop(1).trimStart(',').let { out += it to 1 + units(it) } // ₹ read as 7 or 2
            // Indian apps group as 1,23,45,678. "712,000" can't be that, so its 7 was the ₹: only the other reading stays.
            if (out.size > 1 && !indianGrouping(out[0].first) && indianGrouping(out[1].first)) out.removeAt(0)
            return out
        }
        val signUnits = 1.75 // "+" and the space after it
        fun err(c: Cand, n: Double, g: Double) = kotlin.math.abs(c.line.w / g - (if (c.sign.isNotEmpty()) signUnits else 0.0) - n)
        val measured = cands.filter { it.line.w > 0 }
        val g = when {
            measured.size >= 2 -> (8..400).map { it / 2.0 }.minByOrNull { g -> measured.sumOf { c -> options(c).minOf { err(c, it.second, g) } } }
            // One amount can't fit a glyph width on its own; a digit is about 0.55 of the line height in these apps.
            // That is only an estimate, so it decides only when one reading fits clearly better (by half a glyph).
            measured.size == 1 && measured[0].line.h > 0 -> (measured[0].line.h * 0.55).takeIf { g1 ->
                val errs = options(measured[0]).map { err(measured[0], it.second, g1) }.sorted()
                errs.size < 2 || errs[1] - errs[0] >= 0.5
            }
            else -> null
        }

        val out = mutableMapOf<Int, Amt>()
        for (c in cands) {
            val opts = options(c)
            val rupees = c.digits.substringBefore('.')
            val digits = when {
                opts.size == 1 -> opts[0].first
                g != null && c.line.w > 0 -> opts.minByOrNull { err(c, it.second, g) }!!.first
                // No widths: Indian apps write thousands with a comma, so a comma-less 4-digit "7300" is ₹300. Paise
                // don't count: "750.00" is three digits of rupees.
                rupees.length >= 4 && !rupees.contains(',') && c.digits[0] == '7' -> opts[1].first
                else -> opts[0].first
            }
            val paise = digits.replace(",", "").toDoubleOrNull()?.let { Math.round(it * 100) } ?: continue
            out[c.idx] = Amt(if (c.sign == "−") "-" else c.sign, paise, c.matched)
        }
        // Amounts inside longer text with an explicit currency ("Paid ₹250 to Swiggy").
        for ((i, l) in lines.withIndex()) {
            if (i in out) continue
            val am = amountRx.find(l.text) ?: continue
            val paise = (am.groupValues[2].ifBlank { am.groupValues[4] }).replace(",", "").toDoubleOrNull()?.let { Math.round(it * 100) } ?: continue
            out[i] = Amt(am.groupValues[1].ifBlank { am.groupValues[3] }.let { if (it == "−") "-" else it }, paise, am.value)
        }
        return out
    }

    /** "1,23,456" or "999" (whole rupees; paise ignored). A number without commas is fine too. */
    private fun indianGrouping(d: String): Boolean {
        val r = d.substringBefore('.')
        return !r.contains(',') || indianRx.matches(r)
    }

    /** "Today, 8:30 pm", "Yesterday", "12 Sep, 8:30 PM", "Sep 12, 2026", "12 September 2026", "12/09/2026 20:30". */
    fun parseWhen(raw: String, now: Long): Long? {
        val s = raw.trim().replace(Regex("""\s+"""), " ").replace("•", ",").replace("·", ",")
        val cal = Calendar.getInstance().apply { timeInMillis = now }
        val time = Regex("""(\d{1,2}):(\d{2})\s*(am|pm)?""", RegexOption.IGNORE_CASE).find(s)
        fun withTime(day: Calendar): Long {
            if (time != null) {
                var h = time.groupValues[1].toInt() % 24
                val ap = time.groupValues[3].lowercase()
                if (ap == "pm" && h < 12) h += 12
                if (ap == "am" && h == 12) h = 0
                day.set(Calendar.HOUR_OF_DAY, h); day.set(Calendar.MINUTE, time.groupValues[2].toInt())
            } else { day.set(Calendar.HOUR_OF_DAY, 0); day.set(Calendar.MINUTE, 0) }
            day.set(Calendar.SECOND, 0); day.set(Calendar.MILLISECOND, 0)
            return day.timeInMillis
        }
        when {
            s.startsWith("today", true) -> return withTime(cal)
            s.startsWith("yesterday", true) -> return withTime(cal.apply { add(Calendar.DAY_OF_YEAR, -1) })
        }
        // "12 Oct at 8:30 pm" -> "12 Oct" (trimming the letters a and t would turn "Oct" into "Oc").
        val datePart = s.substringBefore(time?.value ?: "\u0000").replace(Regex("""(?:[\s,]|\bat\b)+$""", RegexOption.IGNORE_CASE), "").trim()
        Dates.parse(datePart, now)?.let { d -> return withTime(Calendar.getInstance().apply { timeInMillis = d }) }
        // No year ("12 Sep"): this year, or last year if that would be in the future.
        val m = Regex("""(\d{1,2})\s+([A-Za-z]{3,9})|([A-Za-z]{3,9})\s+(\d{1,2})""").find(datePart) ?: return null
        val day = (m.groupValues[1].ifBlank { m.groupValues[4] }).toIntOrNull() ?: return null
        val mon = (m.groupValues[2].ifBlank { m.groupValues[3] }).take(3).lowercase(Locale.ROOT)
        val mi = listOf("jan", "feb", "mar", "apr", "may", "jun", "jul", "aug", "sep", "oct", "nov", "dec").indexOf(mon).takeIf { it >= 0 } ?: return null
        val c = Calendar.getInstance().apply { timeInMillis = now; set(Calendar.MONTH, mi); set(Calendar.DAY_OF_MONTH, day) }
        if (c.timeInMillis > now + 86_400_000L) c.add(Calendar.YEAR, -1)
        return withTime(c)
    }
}
