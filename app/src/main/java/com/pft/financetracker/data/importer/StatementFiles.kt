package com.pft.financetracker.data.importer

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import com.pft.financetracker.domain.importer.AppHistoryParser
import com.pft.financetracker.domain.importer.CsvReader
import com.pft.financetracker.domain.importer.FormatSniffer
import com.pft.financetracker.domain.importer.HtmlTableReader
import com.pft.financetracker.domain.importer.ImportFormat
import com.pft.financetracker.domain.importer.ImportRejected
import com.pft.financetracker.domain.importer.ImportTooLarge
import com.pft.financetracker.domain.importer.PdfLimits
import com.pft.financetracker.domain.importer.ParsedStatement
import com.pft.financetracker.domain.importer.PositionedTable
import com.pft.financetracker.domain.importer.StatementInterpreter
import com.pft.financetracker.domain.importer.Word
import com.pft.financetracker.domain.importer.XlsxReader
import com.pft.financetracker.ui.ocr.OcrEngine
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.encryption.InvalidPasswordException
import com.tom_roush.pdfbox.rendering.PDFRenderer
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Opens a statement or screenshot the user picked and turns it into a [ParsedStatement]. Everything runs on the
 * phone: files are read through the system picker (no storage permission), nothing is copied or uploaded, and a PDF
 * password is used in memory only.
 */
class StatementFiles(private val context: Context) {
    sealed class Read {
        data class Ok(val statement: ParsedStatement, val fileName: String) : Read()
        /** The PDF is locked. [wrong] is true after a password that did not open it. */
        data class NeedsPassword(val fileName: String, val wrong: Boolean) : Read()
        data class Error(val message: String) : Read()
    }

    fun displayName(uri: Uri): String = runCatching {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
    }.getOrNull() ?: uri.lastPathSegment ?: "statement"

    suspend fun read(uri: Uri, password: String? = null): Read = withContext(Dispatchers.IO) {
        val name = displayName(uri)
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext Read.Error("Could not open the file.")
        if (bytes.size > 40 * 1024 * 1024) return@withContext Read.Error("The file is too large (over 40 MB).")
        try {
            when (FormatSniffer.sniff(bytes, name)) {
                FormatSniffer.Kind.CSV -> Read.Ok(StatementInterpreter.interpret(CsvReader.read(bytes), ImportFormat.CSV), name)
                FormatSniffer.Kind.XLSX -> Read.Ok(StatementInterpreter.interpret(XlsxReader.read(bytes), ImportFormat.XLSX), name)
                FormatSniffer.Kind.XLS_HTML -> Read.Ok(StatementInterpreter.interpret(HtmlTableReader.read(bytes), ImportFormat.XLS_HTML), name)
                FormatSniffer.Kind.XLS_BINARY -> Read.Error("This is an old-style Excel (.xls) file. Open it and save it as .xlsx or .csv, then import that.")
                FormatSniffer.Kind.PDF -> readPdf(bytes, name, password)
                FormatSniffer.Kind.IMAGE -> readImages(listOf(uri), name)
                FormatSniffer.Kind.UNKNOWN -> Read.Error("This file type is not supported. Use a statement in PDF, Excel, CSV, or a screenshot.")
            }
        } catch (e: OutOfMemoryError) {
            Read.Error("The file is too large to read on this phone.")
        } catch (e: ImportTooLarge) {
            Read.Error(e.message ?: "The file is too large to read on this phone.")
        } catch (e: ImportRejected) {
            Read.Error(e.message ?: "This file could not be read.")
        } catch (e: java.io.IOException) {
            // PDFBox reports its memory cap as a plain IOException ("Maximum allowed scratch file memory exceeded").
            if (e.message?.contains("memory", ignoreCase = true) == true) Read.Error("The file is too large to read on this phone.")
            else Read.Error("Could not read this file (${e.javaClass.simpleName}).")
        } catch (e: Exception) {
            Read.Error("Could not read this file (${e.javaClass.simpleName}).")
        }
    }

    sealed class TextRead {
        data class Ok(val text: String) : TextRead()
        data class NeedsPassword(val wrong: Boolean) : TextRead()
        data class Error(val message: String) : TextRead()
    }

    /**
     * The plain text of a PDF, for documents read line by line (a mutual-fund CAS). The password, when asked for, is
     * used in memory to open the file and never stored. Nothing is kept.
     */
    suspend fun readPdfText(uri: Uri, password: String? = null): TextRead = withContext(Dispatchers.IO) {
        val bytes = runCatching { context.contentResolver.openInputStream(uri)?.use { it.readBytes() } }.getOrNull()
            ?: return@withContext TextRead.Error("Could not open the file.")
        if (bytes.size > 40 * 1024 * 1024) return@withContext TextRead.Error("The file is too large (over 40 MB).")
        PDFBoxResourceLoader.init(context.applicationContext)
        try {
            val doc = try {
                if (password != null) PDDocument.load(bytes, password) else PDDocument.load(bytes)
            } catch (e: InvalidPasswordException) {
                return@withContext TextRead.NeedsPassword(wrong = password != null)
            }
            doc.use { d ->
                if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)
                TextRead.Ok(PDFTextStripper().apply { sortByPosition = true }.getText(d))
            }
        } catch (e: Exception) {
            TextRead.Error("Could not read this PDF (${e.javaClass.simpleName}).")
        }
    }

    private suspend fun readPdf(bytes: ByteArray, name: String, password: String?): Read {
        PDFBoxResourceLoader.init(context.applicationContext)
        // An empty box is no password (tapping "Open" with nothing typed must not say "wrong password").
        val pw = password?.takeIf { it.isNotEmpty() }
        val doc = try {
            PDDocument.load(bytes, pw ?: "", null, null, MemoryUsageSetting.setupMainMemoryOnly(PdfLimits.MAX_PDF_MEMORY))
        } catch (e: InvalidPasswordException) {
            return Read.NeedsPassword(name, wrong = pw != null)
        }
        doc.use { d ->
            if (d.isEncrypted) d.setAllSecurityToBeRemoved(true)
            val words = pdfWords(d)
            // A text PDF has words on every page; a scan has (almost) none and needs OCR.
            if (words.size >= 20 * minOf(d.numberOfPages, 3)) {
                return Read.Ok(StatementInterpreter.interpret(PositionedTable.toTable(words), ImportFormat.PDF), name)
            }
            val renderer = PDFRenderer(d)
            val ocrWords = mutableListOf<Word>()
            for (page in 0 until minOf(d.numberOfPages, PdfLimits.MAX_OCR_PAGES)) {
                // A giant page (a 200-inch MediaBox) would need a bitmap far bigger than the phone's memory.
                val box = d.getPage(page).mediaBox
                // One broken page (an impossible box) is skipped, not the whole statement.
                val bmp: Bitmap = runCatching { renderer.renderImageWithDPI(page, PdfLimits.renderDpi(box.width, box.height)) }.getOrNull() ?: continue
                try { ocrWords += OcrEngine.recognizeWords(bmp, page).first } finally { bmp.recycle() }
            }
            return Read.Ok(StatementInterpreter.interpret(PositionedTable.toTable(ocrWords), ImportFormat.PDF_SCANNED), name)
        }
    }

    /** Words and their positions from a text PDF, page by page. */
    private fun pdfWords(d: PDDocument): List<Word> {
        val out = mutableListOf<Word>()
        val stripper = object : PDFTextStripper() {
            override fun writeString(text: String?, positions: MutableList<TextPosition>?) {
                val ps = positions ?: return
                var cur = StringBuilder(); var x0 = 0f; var x1 = 0f; var y = 0f; var h = 0f
                fun flush() {
                    if (cur.isNotBlank()) out += Word(cur.toString().trim(), x0, y, x1 - x0, h, currentPageNo - 1)
                    cur = StringBuilder()
                }
                for (p in ps) {
                    val ch = p.unicode ?: continue
                    val gap = if (cur.isEmpty()) 0f else p.xDirAdj - x1
                    if (ch.isBlank() || gap > p.widthOfSpace * 1.5f) { flush(); if (ch.isBlank()) continue }
                    if (cur.isEmpty()) { x0 = p.xDirAdj; y = p.yDirAdj - p.heightDir / 2; h = maxOf(p.heightDir, 4f) }
                    cur.append(ch); x1 = p.xDirAdj + p.widthDirAdj
                }
                flush()
            }
        }
        stripper.sortByPosition = true
        stripper.startPage = 1
        stripper.endPage = minOf(d.numberOfPages, PdfLimits.MAX_TEXT_PAGES)
        stripper.getText(d)
        return out
    }

    /**
     * Statement photos and payment-app screenshots. A photo of a statement page has a table (header, dates, a
     * balance); an app history has name / amount / date rows. Both readings are tried, the one that finds more wins.
     */
    suspend fun readImages(uris: List<Uri>, name: String? = null): Read {
        val words = mutableListOf<Word>()
        val lines = mutableListOf<AppHistoryParser.Line>()
        for ((i, u) in uris.withIndex()) {
            val bmp = runCatching { OcrEngine.decodeForWords(context, u) }.getOrNull() ?: continue
            try {
                val (w, l) = OcrEngine.recognizeWords(bmp, i)
                words += w; lines += l
            } finally { bmp.recycle() }
        }
        if (words.isEmpty()) return Read.Error("No text found in the image. Try a sharper screenshot.")
        // Debug builds only: what OCR saw, so screenshot layouts can be turned into unit tests. Release builds never log it.
        if (com.pft.financetracker.BuildConfig.DEBUG) lines.forEach { android.util.Log.d("FinTrackImport", "line x=${it.x.toInt()} y=${it.y.toInt()} w=${it.w.toInt()} h=${it.h.toInt()} '${it.text}'") }
        val asTable = StatementInterpreter.interpret(PositionedTable.toTable(words), ImportFormat.IMAGE)
        val asApp = ParsedStatement(ImportFormat.APP_SCREENSHOT, AppHistoryParser.parse(lines), emptyList())
        val best = if (asTable.rows.size >= asApp.rows.size && asTable.rows.size >= 2) asTable else asApp
        if (best.rows.isEmpty()) return Read.Error("No transactions found in the image.")
        return Read.Ok(best, name ?: if (uris.size == 1) displayName(uris[0]) else "${uris.size} screenshots")
    }
}
