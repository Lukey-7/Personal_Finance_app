package com.pft.financetracker

import com.pft.financetracker.domain.ocr.OcrRows
import org.junit.Assert.assertEquals
import org.junit.Test

class OcrScriptPickTest {
    private val latin = "TRUFFLES CAFE\nPaneer Tikka  1  320.00"

    @Test fun englishBillKeepsTheLatinReading() =
        assertEquals(latin, OcrRows.pickScript(latin, "TRUFFLES CAFE\nPaneer Tikka  1  320.OO"))

    @Test fun hindiBillUsesTheDevanagariReading() {
        val deva = "शर्मा भोजनालय\nपनीर टिक्का  १  ३२०.००"
        assertEquals(deva, OcrRows.pickScript("????? ??????\n???? ?????  ?  ???.??", deva))
    }

    @Test fun mixedBillWithAFewHindiNamesUsesTheDevanagariReading() {
        val deva = "GUPTA SWEETS\nसमोसा  4  60.00"
        assertEquals(deva, OcrRows.pickScript("GUPTA SWEETS\n#R#  4  60.00", deva))
    }

    @Test fun devanagariDigitsAloneAreNotHindiText() =
        assertEquals(latin, OcrRows.pickScript(latin, "TRUFFLES CAFE\n१२"))
}
