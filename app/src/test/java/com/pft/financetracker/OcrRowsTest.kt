package com.pft.financetracker

import com.pft.financetracker.domain.ocr.OcrLine
import com.pft.financetracker.domain.ocr.OcrRows
import org.junit.Assert.assertEquals
import org.junit.Test
import kotlin.math.roundToInt
import kotlin.math.tan

class OcrRowsTest {

    private val rows = listOf("Paneer Tikka" to "320.00", "Dal Makhani" to "280.00", "Butter Naan" to "240.00", "Jeera Rice" to "360.00")
    private val expected = rows.joinToString("\n") { (n, p) -> "$n  $p" }

    /**
     * A two-column receipt photographed at [degrees] (positive = clockwise, as ML Kit reports it). Names start
     * at x=100, prices at x=620; printed rows are 40px apart and 30px tall.
     */
    private fun photographed(degrees: Double): List<OcrLine> {
        val slope = tan(Math.toRadians(degrees))
        return rows.flatMapIndexed { i, (name, price) ->
            val y = 200 + i * 40
            listOf(
                OcrLine(name, cx = 200, cy = (y + 200 * slope).roundToInt(), left = 100, height = 30, angle = degrees.toFloat()),
                OcrLine(price, cx = 680, cy = (y + 680 * slope).roundToInt(), left = 620, height = 30, angle = degrees.toFloat()),
            )
        }.shuffled(java.util.Random(1))
    }

    @Test fun straightPhotoPairsEachNameWithItsPrice() = assertEquals(expected, OcrRows.merge(photographed(0.0)))

    // This is the case that failed on the emulator: at 4 degrees each price sat level with the row above.
    @Test fun anticlockwiseTiltStillPairsCorrectly() = assertEquals(expected, OcrRows.merge(photographed(-4.0)))

    @Test fun clockwiseTiltStillPairsCorrectly() = assertEquals(expected, OcrRows.merge(photographed(4.0)))

    @Test fun oneMisreadAngleDoesNotThrowOffTheRest() {
        val lines = photographed(-4.0).toMutableList()
        lines[0] = lines[0].copy(angle = 25f)
        assertEquals(expected, OcrRows.merge(lines))
    }
}
