package com.pft.financetracker

import com.pft.financetracker.data.sms.ImportStats
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.matchesAmount
import com.pft.financetracker.ui.components.reasonLabel
import com.pft.financetracker.ui.components.scanResultLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Words people see instead of internal codes and counts. */
class CopyTest {
    @Test fun knownReasonsReadAsWords() {
        assertEquals("Couldn't tell if money came in or went out", reasonLabel("type_ambiguous"))
        assertEquals("No amount found", reasonLabel("amount_not_found"))
        assertEquals("A one-time code", reasonLabel("otp"))
        assertEquals("An offer or promotion", reasonLabel("promo"))
        assertEquals("An offer or promotion", reasonLabel("promotional_sender"))
        assertEquals("A payment due later, not yet made", reasonLabel("future"))
        assertEquals("Statement balance doesn't add up", reasonLabel("balance_mismatch"))
        assertEquals("You dismissed it", reasonLabel("dismissed_by_user"))
        assertEquals("Not sure enough to save it", reasonLabel("low_confidence_40"))
    }

    @Test fun unknownReasonsStillReadAsASentence() {
        assertEquals("Some new rule", reasonLabel("some_new_rule"))
        assertEquals("Unknown", reasonLabel(""))
    }

    @Test fun scanLines() {
        assertEquals("12 added · 1 to review · 4 skipped", scanResultLine(ImportStats(1, 17, 12, 1, 3, 1), failed = false))
        assertEquals("No new transactions", scanResultLine(ImportStats(1, 0, 0, 0, 0, 0), failed = false))
        assertEquals("No new transactions · 3 skipped", scanResultLine(ImportStats(1, 3, 0, 0, 2, 1), failed = false))
        assertEquals("Couldn't read your SMS. Check the permission in Settings.", scanResultLine(null, failed = true))
    }

    @Test fun counts() {
        assertEquals("1 payment", countLabel(1, "payment"))
        assertEquals("3 payments", countLabel(3, "payment"))
        assertEquals("2 people", countLabel(2, "person", "people"))
    }

    @Test fun amountSearchIgnoresFormatting() {
        assertTrue(matchesAmount(1_299_00, "1299"))
        assertTrue(matchesAmount(1_299_00, "1,299"))
        assertTrue(matchesAmount(1_05_000_00, "105000"))
        assertTrue(matchesAmount(2_400_50, "2400.5"))
        assertFalse(matchesAmount(1_299_00, "1300"))
        assertFalse(matchesAmount(1_299_00, "food"))
    }
}
