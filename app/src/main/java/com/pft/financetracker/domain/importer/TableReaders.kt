package com.pft.financetracker.domain.importer

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Every reader turns a file into the same thing: rows of cell texts. Everything after that is shared. */
typealias Table = List<List<String>>

/** A file refused because reading it would take more memory or time than a phone should spend on it. */
class ImportTooLarge(message: String) : IOException(message)

/** A file refused because it is built to attack the reader (XML entities, a DOCTYPE), not to hold a statement. */
class ImportRejected(message: String) : IOException(message)

/**
 * CSV and other delimited text (tab, semicolon, pipe), as exported by bank net-banking. Handles quotes, doubled
 * quotes, line breaks inside quotes, UTF-8/UTF-16 with or without BOM, and the account-summary lines banks put above
 * the real header (the column detector finds the header later).
 */
object CsvReader {
    fun read(bytes: ByteArray): Table = parse(decode(bytes))

    fun decode(bytes: ByteArray): String {
        if (bytes.size >= 2 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xFE.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16LE)
        if (bytes.size >= 2 && bytes[0] == 0xFE.toByte() && bytes[1] == 0xFF.toByte()) return String(bytes, 2, bytes.size - 2, Charsets.UTF_16BE)
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        // UTF-16 without BOM: nearly every other byte is zero (the second of each pair for little-endian, the first for
        // big). "Nearly": a ₹ or an accented letter in the header has a non-zero high byte.
        if (bytes.size > 8) {
            val head = bytes.take(256)
            fun zeros(parity: Int) = head.filterIndexed { i, _ -> i % 2 == parity }.let { half -> half.count { it == 0.toByte() } >= 0.9 * half.size }
            if (zeros(1) && !zeros(0)) return String(bytes, Charsets.UTF_16LE)
            if (zeros(0) && !zeros(1)) return String(bytes, Charsets.UTF_16BE)
        }
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf8
    }

    fun parse(text: String): Table {
        val delim = sniff(text)
        // A quote that never closes would swallow the rest of the file into one cell ("RAHUL 5" TV): read that quote
        // as an ordinary character instead and try again.
        val literal = mutableSetOf<Int>()
        repeat(20) {
            val (rows, openAt) = parse(text, delim, literal)
            if (openAt < 0) return rows
            literal += openAt
        }
        return parse(text, delim, literal).first
    }

    /** The rows, and where an unclosed quote opened (-1 when every quote closed). */
    private fun parse(text: String, delim: Char, literal: Set<Int>): Pair<Table, Int> {
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var openedAt = -1
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' && i !in literal && (quoted || cell.isBlank()) -> { quoted = !quoted; if (quoted) { cell.setLength(0); openedAt = i } }
                !quoted && ch == delim -> { row += cell.toString().trim(); cell.setLength(0) }
                !quoted && (ch == '\n' || ch == '\r') -> {
                    if (ch == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                    row += cell.toString().trim(); cell.setLength(0)
                    if (row.any { it.isNotEmpty() }) rows += row
                    row = mutableListOf()
                }
                else -> cell.append(ch)
            }
            i++
        }
        row += cell.toString().trim()
        if (row.any { it.isNotEmpty() }) rows += row
        return rows to (if (quoted) openedAt else -1)
    }

    /** The delimiter that splits the most lines into the same, largest number of cells. */
    private fun sniff(text: String): Char {
        val lines = text.lineSequence().filter { it.isNotBlank() }.take(40).toList()
        return listOf(',', '\t', ';', '|').maxByOrNull { d ->
            val counts = lines.map { l -> l.count { it == d } }.filter { it > 0 }
            if (counts.isEmpty()) 0 else counts.groupingBy { it }.eachCount().maxOf { (n, times) -> n * times }
        } ?: ','
    }
}

/**
 * .xlsx without a spreadsheet library: an xlsx file is a zip of XML parts. Reads the shared strings and every
 * sheet, and returns the sheet with the most rows (banks put the statement on one sheet, sometimes after a cover
 * sheet). Numbers stay as text; Excel date serials are recognised later by the date parser.
 *
 * A hostile file is refused, never half-read: parts are inflated only up to a fixed size (zip bombs), a DOCTYPE is
 * refused (entity expansion, external files), and columns and rows are capped.
 */
object XlsxReader {
    const val MAX_PART_BYTES = 32L * 1024 * 1024
    const val MAX_TOTAL_BYTES = 64L * 1024 * 1024
    const val MAX_SHEETS = 64
    const val MAX_COLUMNS = 256
    const val MAX_ROWS = 200_000

    fun read(bytes: ByteArray): Table {
        val parts = mutableMapOf<String, ByteArray>()
        var total = 0L
        var sheets = 0
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                val sheet = e.name.startsWith("xl/worksheets/sheet") && e.name.endsWith(".xml")
                if (sheet && ++sheets > MAX_SHEETS) throw ImportTooLarge("The spreadsheet has too many sheets.")
                if (e.name == "xl/sharedStrings.xml" || sheet || e.name == "xl/styles.xml") {
                    val b = readCapped(z, MAX_PART_BYTES)
                    total += b.size
                    if (total > MAX_TOTAL_BYTES) throw ImportTooLarge("The spreadsheet is too large to read on this phone.")
                    parts[e.name] = b
                }
                e = z.nextEntry
            }
        }
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val dateStyles = parts["xl/styles.xml"]?.let { dateStyleIndexes(it) } ?: emptySet()
        return parts.filterKeys { it.startsWith("xl/worksheets/") }.values.map { sheet(it, shared, dateStyles) }.maxByOrNull { it.size } ?: emptyList()
    }

    private fun readCapped(z: ZipInputStream, cap: Long): ByteArray {
        val out = ByteArrayOutputStream()
        val buf = ByteArray(64 * 1024)
        var n = 0L
        while (true) {
            val r = z.read(buf)
            if (r < 0) break
            n += r
            if (n > cap) throw ImportTooLarge("The spreadsheet is too large to read on this phone.")
            out.write(buf, 0, r)
        }
        return out.toByteArray()
    }

    private fun sax(bytes: ByteArray, h: DefaultHandler) {
        // Spreadsheet parts never declare a DOCTYPE; one that does is an entity attack (billion laughs, XXE).
        if (hasDoctype(bytes)) throw ImportRejected("This file is not a valid spreadsheet.")
        val f = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        for ((feature, on) in listOf(
            "http://apache.org/xml/features/disallow-doctype-decl" to true,
            "http://xml.org/sax/features/external-general-entities" to false,
            "http://xml.org/sax/features/external-parameter-entities" to false,
        )) runCatching { f.setFeature(feature, on) }
        try {
            f.newSAXParser().parse(ByteArrayInputStream(bytes), h)
        } catch (e: org.xml.sax.SAXException) {
            throw ImportRejected("This file is not a valid spreadsheet.")
        }
    }

    private fun hasDoctype(bytes: ByteArray): Boolean {
        val pattern = "<!DOCTYPE".toByteArray()
        outer@ for (i in 0..bytes.size - pattern.size) {
            for (j in pattern.indices) {
                val b = bytes[i + j].toInt().toChar().uppercaseChar()
                if (b != pattern[j].toInt().toChar()) continue@outer
            }
            return true
        }
        return false
    }

    private fun sharedStrings(bytes: ByteArray): List<String> {
        val out = mutableListOf<String>()
        val sb = StringBuilder()
        var inT = false
        sax(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes?) {
                when (qName.substringAfter(':')) { "si" -> sb.setLength(0); "t" -> inT = true }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName.substringAfter(':')) { "si" -> out += sb.toString(); "t" -> inT = false }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inT) sb.appendRange(ch, start, start + length) }
        })
        return out
    }

    /** Cell style indexes whose number format is a date, so their serials are written out as dates. */
    private fun dateStyleIndexes(bytes: ByteArray): Set<Int> {
        val customDate = mutableSetOf<Int>()
        val xfs = mutableListOf<Int>()
        var inCellXfs = false
        sax(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (qName.substringAfter(':')) {
                    "numFmt" -> {
                        val code = a.getValue("formatCode")?.lowercase() ?: ""
                        if (code.contains("d") && code.contains("m") || code.contains("yy")) a.getValue("numFmtId")?.toIntOrNull()?.let { customDate += it }
                    }
                    "cellXfs" -> inCellXfs = true
                    "xf" -> if (inCellXfs) xfs += (a.getValue("numFmtId")?.toIntOrNull() ?: 0)
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) { if (qName.substringAfter(':') == "cellXfs") inCellXfs = false }
        })
        val builtIn = (14..22).toSet() + (45..47).toSet()
        return xfs.withIndex().filter { (_, id) -> id in builtIn || id in customDate }.map { it.index }.toSet()
    }

    private fun sheet(bytes: ByteArray, shared: List<String>, dateStyles: Set<Int>): Table {
        val rows = sortedMapOf<Int, MutableMap<Int, String>>()
        var rowIdx = 0
        var col = 0
        var type: String? = null
        var style = -1
        val v = StringBuilder()
        var inV = false
        sax(bytes, object : DefaultHandler() {
            override fun startElement(uri: String?, localName: String?, qName: String, a: Attributes) {
                when (qName.substringAfter(':')) {
                    "row" -> rowIdx = (a.getValue("r")?.toIntOrNull() ?: (rowIdx + 1))
                    "c" -> {
                        val ref = a.getValue("r")
                        col = if (ref == null) col + 1 else colIndex(ref.takeWhile { it.isLetter() })
                        type = a.getValue("t"); style = a.getValue("s")?.toIntOrNull() ?: -1
                        v.setLength(0)
                    }
                    "v", "t" -> inV = true
                }
            }
            override fun endElement(uri: String?, localName: String?, qName: String) {
                when (qName.substringAfter(':')) {
                    "v", "t" -> inV = false
                    "c" -> {
                        if (col !in 0 until MAX_COLUMNS) return
                        val raw = v.toString()
                        val text = when (type) {
                            "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            "str", "inlineStr", "e" -> raw
                            else -> if (style in dateStyles && raw.toDoubleOrNull() != null) "xlserial:$raw" else number(raw)
                        }
                        if (text.isNotEmpty()) {
                            if (rows.size >= MAX_ROWS && rowIdx !in rows) throw ImportTooLarge("The spreadsheet has too many rows.")
                            rows.getOrPut(rowIdx) { mutableMapOf() }[col] = text.trim()
                        }
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inV) v.appendRange(ch, start, start + length) }
        })
        return rows.values.map { cells -> val max = cells.keys.maxOrNull() ?: -1; (0..max).map { cells[it] ?: "" } }
    }

    /**
     * A stored number as the bank meant it. Excel keeps binary floating point, so a formula's Rs 1,234.56 is stored as
     * "1234.5599999999999", and some exports write "1.5E3". Those become "1234.56" and "1500.00". A value whose extra
     * digits are real (a date serial with a time, "46267.520833333336") is left as it is.
     */
    private fun number(raw: String): String {
        val d = raw.toBigDecimalOrNull() ?: return raw
        val cents = d.setScale(2, RoundingMode.HALF_UP)
        val scientific = raw.contains('e', ignoreCase = true)
        val noise = d.scale() > 2 && (d - cents).abs() < BigDecimal("0.000001")
        return if (scientific || noise) cents.toPlainString() else raw
    }

    /** "A" -> 0, "AB" -> 27. Anything past three letters (beyond XFD, Excel's last column) is out of range. */
    private fun colIndex(letters: String): Int {
        if (letters.isEmpty() || letters.length > 3) return -1
        return letters.uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
    }
}

/**
 * Many banks' ".xls" downloads are really an HTML table (or tab-separated text) with an xls name. This reads the
 * HTML case; the text case goes to [CsvReader]. A genuine binary .xls (BIFF) is reported so the user can save it
 * as .xlsx or .csv instead.
 *
 * One pass over the tags, so unclosed rows and cells (common in these exports) cost linear time, never quadratic.
 */
object HtmlTableReader {
    private val tagRx = Regex("""<(/?)(tr|td|th|table)(?=[\s>/])[^<>]*>""", RegexOption.IGNORE_CASE)
    private val anyTag = Regex("""<[^>]*>""")

    fun read(bytes: ByteArray): Table {
        val s = CsvReader.decode(bytes)
        val rows = mutableListOf<List<String>>()
        var row: MutableList<String>? = null
        var cellStart = -1
        fun closeCell(end: Int) {
            if (cellStart >= 0) row?.add(clean(s.substring(cellStart, end)))
            cellStart = -1
        }
        fun closeRow(end: Int) {
            closeCell(end)
            row?.let { r -> if (r.any { it.isNotEmpty() }) rows += r }
            row = null
        }
        for (m in tagRx.findAll(s)) {
            val closing = m.groupValues[1] == "/"
            when (m.groupValues[2].lowercase()) {
                "tr" -> { closeRow(m.range.first); if (!closing) row = mutableListOf() }
                "td", "th" -> { closeCell(m.range.first); if (!closing) { if (row == null) row = mutableListOf(); cellStart = m.range.last + 1 } }
                "table" -> closeRow(m.range.first)
            }
        }
        closeRow(s.length)
        return rows
    }

    private fun clean(html: String) = unescape(anyTag.replace(html, " ")).replace(Regex("""\s+"""), " ").trim()

    private fun unescape(s: String): String = s
        .replace(Regex("""&#[xX]([0-9a-fA-F]{1,6});""")) { m -> m.groupValues[1].toIntOrNull(16)?.let { cp -> String(Character.toChars(cp.coerceIn(0, 0x10FFFF))) } ?: m.value }
        .replace(Regex("""&#(\d{1,7});""")) { m -> m.groupValues[1].toIntOrNull()?.let { cp -> String(Character.toChars(cp.coerceIn(0, 0x10FFFF))) } ?: m.value }
        .replace("&nbsp;", " ").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'").replace("&amp;", "&")
}

/** What kind of file this is, from its first bytes (names and MIME types lie). */
object FormatSniffer {
    enum class Kind { CSV, XLSX, XLS_HTML, XLS_BINARY, PDF, IMAGE, UNKNOWN }

    fun sniff(bytes: ByteArray, name: String?): Kind {
        fun starts(vararg b: Int) = bytes.size >= b.size && b.indices.all { bytes[it] == b[it].toByte() }
        return when {
            starts(0x25, 0x50, 0x44, 0x46) -> Kind.PDF // %PDF
            starts(0x50, 0x4B, 0x03, 0x04) -> Kind.XLSX // zip
            starts(0xD0, 0xCF, 0x11, 0xE0) -> Kind.XLS_BINARY // OLE2
            starts(0x89, 0x50, 0x4E, 0x47) || starts(0xFF, 0xD8, 0xFF) || starts(0x52, 0x49, 0x46, 0x46) -> Kind.IMAGE
            else -> {
                val head = CsvReader.decode(bytes.copyOf(minOf(bytes.size, 2048))).trimStart().lowercase()
                when {
                    head.startsWith("<") && (head.contains("<table") || head.contains("<html") || head.contains("<tr")) -> Kind.XLS_HTML
                    head.isNotBlank() -> Kind.CSV
                    name?.lowercase()?.endsWith(".pdf") == true -> Kind.PDF
                    else -> Kind.UNKNOWN
                }
            }
        }
    }
}
