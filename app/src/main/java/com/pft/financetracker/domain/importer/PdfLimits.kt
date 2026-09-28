package com.pft.financetracker.domain.importer

/** Limits for reading PDFs on a phone. */
object PdfLimits {
    const val DPI = 200f
    /** About 16 megapixels (64 MB as ARGB): an A4 page at 200 dpi is 3.9 MP. */
    const val MAX_PIXELS = 16_000_000f
    /** Scanned pages OCR'd per file. */
    const val MAX_OCR_PAGES = 30
    /** Text pages read per file. */
    const val MAX_TEXT_PAGES = 200
    /** Memory PDFBox may use for one file's objects and streams. */
    const val MAX_PDF_MEMORY = 96L * 1024 * 1024

    /** Resolution to render a scanned page at: 200 dpi, lowered for huge pages so the bitmap stays under [MAX_PIXELS]. */
    fun renderDpi(widthPt: Float, heightPt: Float): Float {
        val px = (widthPt / 72f * DPI) * (heightPt / 72f * DPI)
        if (!(px > MAX_PIXELS)) return DPI
        return DPI * kotlin.math.sqrt(MAX_PIXELS / px) * 0.999f
    }
}
