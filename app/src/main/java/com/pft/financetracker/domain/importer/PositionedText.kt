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

        val out = mutableListOf<List<String>>()
        out += header.map { it.text }
        for ((i, cs) in chunked.withIndex()) {
            if (i <= headerIdx + (if (header !== chunked[headerIdx]) 1 else 0)) continue
            // A repeated header on a later page.
            if (cs.count { ColumnDetector.isHeaderCell(it.text) } >= 3) continue
            val cells = MutableList(header.size) { "" }
            for (c in cs) { val j = col(c); cells[j] = (cells[j] + " " + c.text).trim() }
            out += cells
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
    private val monthHeader = Regex("""^(?:$months)\s*\d{0,4}$""", RegexOption.IGNORE_CASE)

    data class Line(val text: String, val x: Float, val y: Float, val h: Float)

    fun parse(lines: List<Line>, now: Long = System.currentTimeMillis()): List<StatementRow> {
        val sorted = lines.filter { it.text.isNotBlank() }.sortedBy { it.y }
        if (sorted.isEmpty()) return emptyList()
        val rows = mutableListOf<StatementRow>()
        var sectionDate: Long? = null
        val used = mutableSetOf<Int>()
        for ((i, l) in sorted.withIndex()) {
            if (monthHeader.matches(l.text.trim()) || (dateLike.matches(l.text.trim()) && amountRx.find(l.text) == null && sorted.none { o -> o !== l && abs(o.y - l.y) < l.h * 0.6 })) {
                parseWhen(l.text, now)?.let { sectionDate = it }
                continue
            }
            val am = amountRx.find(l.text) ?: continue
            val paise = Math.round(((am.groupValues[2].ifBlank { am.groupValues[4] }).replace(",", "").toDoubleOrNull() ?: continue) * 100)
            if (paise <= 0) continue
            val sign = am.groupValues[1].ifBlank { am.groupValues[3] }
            // The row: lines at the same height, plus the one or two lines just below (date, status).
            val band = sorted.withIndex().filter { (j, o) -> j !in used && (abs(o.y - l.y) <= maxOf(l.h, o.h) * 0.7f || (o.y > l.y && o.y - l.y <= l.h * 2.6f)) }
            val text = band.joinToString(" \n ") { it.value.text }
            if (skipWords.containsMatchIn(text)) { band.forEach { used += it.index }; continue }
            val nameLine = band.map { it.value }.filter { it !== l && amountRx.find(it.text) == null && dateLike.find(it.text) == null }
                .map { prefix.replace(it.text.trim(), "").trim() }.firstOrNull { it.isNotBlank() && !noise.matches(it) && it.count { c -> c.isLetter() } >= 2 }
                ?: prefix.replace(l.text.replace(am.value, "").trim(), "").trim().takeIf { it.count { c -> c.isLetter() } >= 2 }
                ?: continue
            val type = when {
                sign == "+" -> TransactionType.CREDIT
                sign == "-" || sign == "−" -> TransactionType.DEBIT
                creditWords.containsMatchIn(text) && !Regex("""\b(paid to|sent to|debited)\b""", RegexOption.IGNORE_CASE).containsMatchIn(text) -> TransactionType.CREDIT
                else -> TransactionType.DEBIT
            }
            val whenText = band.map { it.value.text }.firstNotNullOfOrNull { dateLike.find(it)?.value }
            val at = whenText?.let { parseWhen(it, now) } ?: sectionDate ?: continue
            band.forEach { used += it.index }
            rows += StatementInterpreter.toRow(at, paise, type, text.replace("\n", " "), null, null, null, rows.size, knownName = nameLine)
        }
        // Overlapping screenshots show some rows twice.
        return rows.distinctBy { listOf(it.counterparty.lowercase(Locale.ROOT), it.amountPaise, it.type, it.time ?: it.date) }
            .sortedWith(compareBy({ it.date }, { it.time ?: 0L })).mapIndexed { i, r -> r.copy(order = i) }
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
        val datePart = s.substringBefore(time?.value ?: "\u0000").trim().trimEnd(',', ' ', 'a', 't').trim()
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
