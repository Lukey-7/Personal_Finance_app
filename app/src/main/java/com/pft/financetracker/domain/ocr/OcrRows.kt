package com.pft.financetracker.domain.ocr

import kotlin.math.abs
import kotlin.math.tan

/** One recognised line of text and where it sits on the image (pixels), with its rotation in degrees. */
data class OcrLine(val text: String, val cx: Int, val cy: Int, val left: Int, val height: Int, val angle: Float = 0f)

/**
 * Rebuilds printed rows from recognised lines. A receipt row ("Paneer Tikka ...... 320.00") usually comes
 * back as two lines, the name and the price, far apart horizontally; they belong together when they sit at
 * the same height.
 *
 * "The same height" has to be measured along the paper, not the photo. On a photo tilted by only 4 degrees
 * the price, 500px to the right of its name, sits about 35px higher: a whole receipt line. Measured in
 * photo pixels, every price then pairs with the item above it. So the skew is estimated from the lines'
 * own angles (the median, which ignores the odd misread line) and heights are compared after removing it.
 */
object OcrRows {
    /**
     * The same photo read by the Latin and the Devanagari recognisers. The Devanagari model also reads Latin
     * text, but the Latin model is the stronger of the two on English-only bills, which are most Indian bills
     * (Hinglish item names like "Paneer" or "Chaas" are printed in Latin letters). So the Devanagari reading
     * is used only when it actually found Hindi script: a few Devanagari letters, not stray marks.
     */
    fun pickScript(latin: String, devanagari: String): String {
        val hindiLetters = devanagari.count { it in 'ऀ'..'ॿ' && it !in '०'..'९' && it.isLetter() }
        return if (hindiLetters >= MIN_HINDI_LETTERS) devanagari else latin
    }

    private const val MIN_HINDI_LETTERS = 3

    fun merge(lines: List<OcrLine>): String {
        if (lines.isEmpty()) return ""
        val skew = lines.filter { it.text.length >= 4 }.map { it.angle }.sorted().let { a ->
            if (a.isEmpty()) 0f else a[a.size / 2]
        }
        val slope = tan(Math.toRadians(skew.toDouble()))
        // Height along the paper: undo the tilt, so text on one printed row lines up again.
        fun level(l: OcrLine) = l.cy - l.cx * slope

        val sorted = lines.sortedWith(compareBy({ level(it) }, { it.left }))
        val rows = mutableListOf<MutableList<OcrLine>>()
        for (l in sorted) {
            val row = rows.lastOrNull()
            val first = row?.first()
            if (first != null && abs(level(first) - level(l)) <= maxOf(8.0, first.height / 2.0)) row += l else rows += mutableListOf(l)
        }
        return rows.joinToString("\n") { row -> row.sortedBy { it.left }.joinToString("  ") { it.text } }
    }
}
