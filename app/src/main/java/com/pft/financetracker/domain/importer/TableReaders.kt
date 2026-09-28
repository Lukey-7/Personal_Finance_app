package com.pft.financetracker.domain.importer

import org.xml.sax.Attributes
import org.xml.sax.helpers.DefaultHandler
import java.io.ByteArrayInputStream
import java.nio.charset.Charset
import java.util.zip.ZipInputStream
import javax.xml.parsers.SAXParserFactory

/** Every reader turns a file into the same thing: rows of cell texts. Everything after that is shared. */
typealias Table = List<List<String>>

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
        // UTF-16 without BOM: every other byte is zero.
        if (bytes.size > 8 && bytes.take(64).filterIndexed { i, _ -> i % 2 == 1 }.all { it == 0.toByte() }) return String(bytes, Charsets.UTF_16LE)
        val utf8 = String(bytes, Charsets.UTF_8)
        return if (utf8.contains('�')) String(bytes, Charset.forName("windows-1252")) else utf8
    }

    fun parse(text: String): Table {
        val delim = sniff(text)
        val rows = mutableListOf<List<String>>()
        var row = mutableListOf<String>()
        val cell = StringBuilder()
        var quoted = false
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            when {
                quoted && ch == '"' && i + 1 < text.length && text[i + 1] == '"' -> { cell.append('"'); i++ }
                ch == '"' && (quoted || cell.isBlank()) -> { quoted = !quoted; if (quoted) cell.setLength(0) }
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
        return rows
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
 */
object XlsxReader {
    fun read(bytes: ByteArray): Table {
        val parts = mutableMapOf<String, ByteArray>()
        ZipInputStream(ByteArrayInputStream(bytes)).use { z ->
            var e = z.nextEntry
            while (e != null) {
                if (e.name == "xl/sharedStrings.xml" || (e.name.startsWith("xl/worksheets/sheet") && e.name.endsWith(".xml")) || e.name == "xl/styles.xml") parts[e.name] = z.readBytes()
                e = z.nextEntry
            }
        }
        val shared = parts["xl/sharedStrings.xml"]?.let { sharedStrings(it) } ?: emptyList()
        val dateStyles = parts["xl/styles.xml"]?.let { dateStyleIndexes(it) } ?: emptySet()
        return parts.filterKeys { it.startsWith("xl/worksheets/") }.values.map { sheet(it, shared, dateStyles) }.maxByOrNull { it.size } ?: emptyList()
    }

    private fun sax(bytes: ByteArray, h: DefaultHandler) {
        val f = SAXParserFactory.newInstance().apply { isNamespaceAware = false }
        f.newSAXParser().parse(ByteArrayInputStream(bytes), h)
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
                        val ref = a.getValue("r") ?: ""
                        col = colIndex(ref.takeWhile { it.isLetter() })
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
                        val raw = v.toString()
                        val text = when (type) {
                            "s" -> raw.toIntOrNull()?.let { shared.getOrNull(it) } ?: ""
                            "b" -> if (raw == "1") "TRUE" else "FALSE"
                            else -> if (style in dateStyles && raw.toDoubleOrNull() != null) "xlserial:$raw" else raw
                        }
                        if (text.isNotEmpty()) rows.getOrPut(rowIdx) { mutableMapOf() }[col] = text.trim()
                    }
                }
            }
            override fun characters(ch: CharArray, start: Int, length: Int) { if (inV) v.appendRange(ch, start, start + length) }
        })
        return rows.values.map { cells -> val max = cells.keys.maxOrNull() ?: -1; (0..max).map { cells[it] ?: "" } }
    }

    private fun colIndex(letters: String): Int = letters.uppercase().fold(0) { acc, c -> acc * 26 + (c - 'A' + 1) } - 1
}

/**
 * Many banks' ".xls" downloads are really an HTML table (or tab-separated text) with an xls name. This reads the
 * HTML case; the text case goes to [CsvReader]. A genuine binary .xls (BIFF) is reported so the user can save it
 * as .xlsx or .csv instead.
 */
object HtmlTableReader {
    private val tr = Regex("""<tr[^>]*>(.*?)</tr>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val td = Regex("""<t[dh][^>]*>(.*?)</t[dh]>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val tag = Regex("""<[^>]+>""")

    fun read(bytes: ByteArray): Table = tr.findAll(CsvReader.decode(bytes)).map { r ->
        td.findAll(r.groupValues[1]).map { unescape(tag.replace(it.groupValues[1], " ")).replace(Regex("""\s+"""), " ").trim() }.toList()
    }.filter { row -> row.any { it.isNotEmpty() } }.toList()

    private fun unescape(s: String) = s.replace("&nbsp;", " ").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&#39;", "'")
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
