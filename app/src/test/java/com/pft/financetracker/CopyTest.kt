package com.pft.financetracker

import com.pft.financetracker.data.sms.ImportStats
import com.pft.financetracker.ui.components.countLabel
import com.pft.financetracker.ui.components.matchesAmount
import com.pft.financetracker.ui.components.reasonLabel
import com.pft.financetracker.ui.components.scanResultLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import com.pft.financetracker.ui.components.Trend
import com.pft.financetracker.ui.components.heroLine
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

    @Test fun amountSearchOnlyForQueriesThatLookLikeAnAmount() {
        assertFalse(matchesAmount(1_00_00, "1mg"))
        assertFalse(matchesAmount(250_00, "zomato 250"))
        assertTrue(matchesAmount(1_299_00, "299"))       // anywhere in the figure, as the search did before
        assertTrue(matchesAmount(1_299_00, "₹1,299"))
    }

    @Test fun heroLineWhenRefundsAreMoreThanSpending() {
        assertEquals("Refunds are more than you've spent so far" to Trend.FLAT, heroLine(-500_00, null, "1–6 Sep", running = true, daysIn = 6, dailyPaise = -83_33, projectedPaise = null))
        assertEquals("Refunds were more than you spent" to Trend.FLAT, heroLine(-500_00, null, "Aug 2026", running = false, daysIn = 30, dailyPaise = 0, projectedPaise = null))
    }

    @Test fun heroLineWithNothingSpent() {
        assertEquals("Nothing spent yet" to Trend.FLAT, heroLine(0, null, "1–6 Sep", running = true, daysIn = 6, dailyPaise = 0, projectedPaise = null))
        assertEquals("Nothing spent in this period" to Trend.FLAT, heroLine(0, null, "Aug 2026", running = false, daysIn = 30, dailyPaise = 0, projectedPaise = null))
    }

    @Test fun heroLineSaysTheSameRatherThanZeroPercentMore() {
        assertEquals("About the same as 1–6 Sep · about ₹133 a day · on track for ₹4,133" to Trend.FLAT,
            heroLine(800_00, 0, "1–6 Sep", running = true, daysIn = 6, dailyPaise = 133_33, projectedPaise = 4_133_23))
    }

    @Test fun heroLineWithAChange() {
        assertEquals("20% more than 1–6 Sep · about ₹133 a day · on track for ₹4,133" to Trend.UP,
            heroLine(800_00, 20, "1–6 Sep", running = true, daysIn = 6, dailyPaise = 133_33, projectedPaise = 4_133_23))
        assertEquals("15% less than Aug 2026 · about ₹50 a day" to Trend.DOWN,
            heroLine(1_500_00, -15, "Aug 2026", running = false, daysIn = 30, dailyPaise = 50_00, projectedPaise = null))
    }

    @Test fun heroLineIsNeverBlank() {
        assertEquals("Too early to compare" to Trend.FLAT, heroLine(800_00, null, "1–2 Sep", running = true, daysIn = 2, dailyPaise = 400_00, projectedPaise = 24_800_00))
    }

    @Test fun aFinishedShortRangeIsNotTooEarly() {
        assertEquals("Nothing earlier to compare with" to Trend.FLAT, heroLine(800_00, null, "29–30 Aug", running = false, daysIn = 2, dailyPaise = 400_00, projectedPaise = null))
    }
}
