package com.pft.financetracker.domain.importer

/** Limits for reading PDFs on a phone. */
object PdfLimits {
    const val DPI = 200f
    /** About 16 megapixels (64 MB as ARGB): an A4 page at 200 dpi is 3.9 MP. */
    const val MAX_PIXELS = 16_000_000f
    /** Longest side of a rendered page, in pixels. */
    const val MAX_SIDE_PX = 10_000f
    /** Scanned pages OCR'd per file. */
    const val MAX_OCR_PAGES = 30
    /** Text pages read per file. */
    const val MAX_TEXT_PAGES = 200
    /** Memory PDFBox may use for one file's objects and streams. */
    const val MAX_PDF_MEMORY = 96L * 1024 * 1024

    /** Resolution to render a scanned page at: 200 dpi, lowered for huge pages so the bitmap stays under [MAX_PIXELS]. */
    fun renderDpi(widthPt: Float, heightPt: Float): Float {
        // A broken box (zero, negative, NaN, infinite) is left to the renderer, which then fails for that page alone.
        if (!(widthPt > 0f) || !(heightPt > 0f) || !widthPt.isFinite() || !heightPt.isFinite()) return DPI
        val inW = widthPt / 72.0; val inH = heightPt / 72.0
        val dpi = minOf(DPI.toDouble(), kotlin.math.sqrt(MAX_PIXELS / (inW * inH)), MAX_SIDE_PX / maxOf(inW, inH))
        return if (dpi >= DPI) DPI else (dpi * 0.999).toFloat().coerceAtLeast(Float.MIN_VALUE)
    }
}
