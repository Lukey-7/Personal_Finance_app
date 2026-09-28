package com.pft.financetracker

import com.pft.financetracker.domain.importer.HtmlTableReader
import com.pft.financetracker.domain.importer.PdfLimits
import com.pft.financetracker.domain.importer.XlsxReader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/** Malicious or broken files must fail fast with a clean error: no hang, no memory blow-up, no file reads. */
class ImportSecurityTest {
    private fun zip(vararg parts: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z -> parts.forEach { (n, b) -> z.putNextEntry(ZipEntry(n)); z.write(b); z.closeEntry() } }
        return out.toByteArray()
    }

    private fun timed(limitMs: Long, block: () -> Unit) {
        val start = System.nanoTime()
        block()
        val ms = (System.nanoTime() - start) / 1_000_000
        assertTrue("took $ms ms", ms < limitMs)
    }

    @Test(timeout = 20_000) fun anXlsxZipBombIsRefused() {
        // 80 MB of whitespace inside one part compresses to a few hundred KB.
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            z.putNextEntry(ZipEntry("xl/worksheets/sheet1.xml"))
            z.write("<worksheet><sheetData>".toByteArray())
            val mb = ByteArray(1 shl 20) { ' '.code.toByte() }
            repeat(80) { z.write(mb) }
            z.write("</sheetData></worksheet>".toByteArray())
            z.closeEntry()
        }
        assertTrue(out.size() < 2_000_000)
        timed(5_000) { assertThrows(IOException::class.java) { XlsxReader.read(out.toByteArray()) } }
    }

    @Test(timeout = 20_000) fun thousandsOfSheetsAreRefused() {
        val parts = (1..5_000).map { "xl/worksheets/sheet$it.xml" to "<worksheet><sheetData/></worksheet>".toByteArray() }.toTypedArray()
        timed(5_000) { assertThrows(IOException::class.java) { XlsxReader.read(zip(*parts)) } }
    }

    @Test(timeout = 20_000) fun farAwayColumnsDoNotBlowUpRows() {
        val sheet = buildString {
            append("<worksheet><sheetData>")
            for (r in 1..2_000) append("<row r=\"$r\"><c r=\"A$r\" t=\"inlineStr\"><is><t>x</t></is></c><c r=\"XFD$r\"><v>1</v></c><c r=\"ZZZZZZZ$r\"><v>2</v></c></row>")
            append("</sheetData></worksheet>")
        }
        timed(3_000) {
            val t = XlsxReader.read(zip("xl/worksheets/sheet1.xml" to sheet.toByteArray()))
            assertEquals(2_000, t.size)
            assertTrue(t.maxOf { it.size } <= 256)
        }
    }

    @Test(timeout = 20_000) fun aDoctypeWithAnExternalEntityIsRefusedAndReadsNothing() {
        val secret = File.createTempFile("secret", ".txt").apply { writeText("TOPSECRET-VALUE"); deleteOnExit() }
        val sheet = """<?xml version="1.0"?><!DOCTYPE worksheet [<!ENTITY e SYSTEM "${secret.toURI()}">]><worksheet><sheetData><row r="1"><c r="A1" t="inlineStr"><is><t>&e;</t></is></c></row></sheetData></worksheet>"""
        val result = runCatching { XlsxReader.read(zip("xl/worksheets/sheet1.xml" to sheet.toByteArray())) }
        assertFalse(result.toString(), result.getOrNull().toString().contains("TOPSECRET"))
        assertTrue("refused", result.exceptionOrNull() is IOException)
    }

    @Test(timeout = 20_000) fun billionLaughsIsRefusedFast() {
        val lol = buildString {
            append("<?xml version=\"1.0\"?><!DOCTYPE lolz [<!ENTITY lol \"lol\">")
            for (i in 1..9) append("<!ENTITY lol$i \"${"&lol${if (i == 1) "" else i - 1};".repeat(10)}\">")
            append("]><sst><si><t>&lol9;</t></si></sst>")
        }
        timed(3_000) {
            assertThrows(IOException::class.java) { XlsxReader.read(zip("xl/sharedStrings.xml" to lol.toByteArray(), "xl/worksheets/sheet1.xml" to "<worksheet/>".toByteArray())) }
        }
    }

    @Test(timeout = 20_000) fun deeplyNestedXmlDoesNotOverflowTheStack() {
        val deep = "<worksheet><sheetData>" + "<x>".repeat(100_000) + "</x>".repeat(100_000) + "</sheetData></worksheet>"
        timed(5_000) { runCatching { XlsxReader.read(zip("xl/worksheets/sheet1.xml" to deep.toByteArray())) }.exceptionOrNull()?.let { assertFalse(it.toString(), it is StackOverflowError) } }
    }

    @Test(timeout = 20_000) fun htmlWithUnclosedRowsIsLinear() {
        val html = "<html><table>" + "<tr><td>01/09/2026<td>UPI-X<td>100.00".repeat(100_000) + "</table></html>"
        timed(3_000) { assertEquals(100_000, HtmlTableReader.read(html.toByteArray()).size) }
    }

    @Test fun htmlTrackTagIsNotARow() {
        val html = "<table><track src=\"x\"><tr><td>Date</td><td>Narration</td></tr><tr><td>01/09/2026</td><td>UPI&#8377;X &#x20B9;Y</td></tr></table>"
        val t = HtmlTableReader.read(html.toByteArray())
        assertEquals(2, t.size)
        assertEquals("UPI₹X ₹Y", t[1][1])
    }

    @Test fun hugePdfPagesAreRenderedAtALowerResolution() {
        // A 200-inch-square page at 200 dpi would be a 40,000 px square bitmap. Cap it at about 16 megapixels.
        val dpi = PdfLimits.renderDpi(widthPt = 14_400f, heightPt = 14_400f)
        val px = (14_400f / 72f * dpi).let { it * it }
        assertTrue("dpi $dpi -> $px px", px <= 16_000_000f)
        assertEquals(200f, PdfLimits.renderDpi(595f, 842f)) // A4 keeps the normal resolution
    }
}
