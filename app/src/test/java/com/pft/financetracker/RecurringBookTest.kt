package com.pft.financetracker

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.recurring.Period
import com.pft.financetracker.domain.recurring.RecurringBook
import com.pft.financetracker.domain.recurring.RecurringDecision
import com.pft.financetracker.domain.recurring.RecurringItem
import com.pft.financetracker.domain.recurring.RecurringStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecurringBookTest {
    private val day = 86_400_000L
    private val now = 1_760_000_000_000L
    private fun item(key: String, paise: Long, lastDaysAgo: Int, period: Period = Period.MONTHLY) = RecurringItem(
        key = key, merchant = key.removePrefix("rec:"), category = Category.ENTERTAINMENT, period = period, amountPaise = paise,
        lastChargeAt = now - lastDaysAgo * day, nextExpectedAt = now - lastDaysAgo * day + period.days * day, transactionIds = listOf(1),
        active = true, autopay = false, priceRise = null,
    )

    private val netflix = item("rec:netflix", 64_900, 10)
    private val spotify = item("rec:spotify", 11_900, 5)
    private val prime = item("rec:prime", 1_49_900, 30, Period.YEARLY)

    @Test fun dismissedChargesAreHidden() {
        val b = RecurringBook.of(listOf(netflix, spotify), listOf(RecurringDecision("rec:spotify", RecurringStatus.DISMISSED, now - day)))
        assertEquals(listOf("rec:netflix"), b.shown.map { it.item.key })
    }

    @Test fun totalsAddUpActiveChargesPerMonthAndYear() {
        val b = RecurringBook.of(listOf(netflix, spotify, prime), emptyList())
        assertEquals(64_900L + 11_900L + 1_49_900L / 12, b.monthlyPaise)
        assertEquals(64_900L * 12 + 11_900L * 12 + 1_49_900L, b.yearlyPaise)
    }

    @Test fun aCancelledChargeLeavesTheTotals() {
        val b = RecurringBook.of(listOf(netflix, spotify), listOf(RecurringDecision("rec:spotify", RecurringStatus.CANCELLED, now - day)))
        assertEquals(64_900L, b.monthlyPaise)
        assertFalse(b.shown.single { it.item.key == "rec:spotify" }.chargedAfterCancel)
    }

    @Test fun aChargeAfterCancellingIsFlagged() {
        // Cancelled 20 days ago, but Netflix charged 10 days ago.
        val b = RecurringBook.of(listOf(netflix), listOf(RecurringDecision("rec:netflix", RecurringStatus.CANCELLED, now - 20 * day)))
        val v = b.shown.single()
        assertTrue(v.chargedAfterCancel)
        assertEquals(64_900L, b.monthlyPaise)
    }

    @Test fun confirmedChargesSortFirst() {
        val b = RecurringBook.of(listOf(netflix, spotify), listOf(RecurringDecision("rec:spotify", RecurringStatus.CONFIRMED, now)))
        assertEquals("rec:spotify", b.shown.first().item.key)
    }

    @Test fun inactiveChargesAreShownButNotCounted() {
        val lapsed = netflix.copy(active = false)
        val b = RecurringBook.of(listOf(lapsed, spotify), emptyList())
        assertEquals(11_900L, b.monthlyPaise)
        assertEquals(2, b.shown.size)
    }

    @Test fun countedChargesWithADateBecomeRemindersTwoDaysAhead() {
        val autopay = netflix.copy(key = "rec:googleone", period = Period.UNKNOWN, nextExpectedAt = null)
        val r = RecurringBook.of(listOf(netflix, spotify.copy(active = false), autopay), emptyList()).reminders()
        assertEquals(listOf("rec:netflix"), r.map { it.key })
        assertEquals(netflix.nextExpectedAt, r.single().dueAt)
        assertEquals(listOf(2), r.single().leadDays)
        assertTrue(r.single().title.contains("netflix"))
    }

    @Test fun cancelledChargesAreNotReminded() {
        val r = RecurringBook.of(listOf(spotify), listOf(RecurringDecision("rec:spotify", RecurringStatus.CANCELLED, now))).reminders()
        assertTrue(r.isEmpty())
    }

    @Test fun thePlaceholderIsNotYetComputed() {
        assertFalse(RecurringBook.EMPTY.computed)
        assertTrue(RecurringBook.of(emptyList(), emptyList()).computed)
    }

    @Test fun lapsedAndCancelledGoUnderStopped() {
        val lapsed = netflix.copy(active = false)
        val b = RecurringBook.of(listOf(lapsed, spotify, prime), listOf(RecurringDecision("rec:prime", RecurringStatus.CANCELLED, now)))
        assertEquals(listOf("rec:spotify"), b.current.map { it.item.key })
        assertEquals(setOf("rec:netflix", "rec:prime"), b.stopped.map { it.item.key }.toSet())
    }

    @Test fun aChargeAfterCancellingStaysInTheMainList() {
        val b = RecurringBook.of(listOf(netflix), listOf(RecurringDecision("rec:netflix", RecurringStatus.CANCELLED, now - 20 * day)))
        assertEquals(listOf("rec:netflix"), b.current.map { it.item.key })
        assertTrue(b.stopped.isEmpty())
    }

    @Test fun choicesSavedUnderAnOldNameStillApply() {
        // Saved before brand names were merged: "Disney Hotstar" was its own key; it is "hotstar" now.
        val b = RecurringBook.of(listOf(item("rec:hotstar", 299_00, 3), item("rec:spotify", 119_00, 3)), listOf(
            RecurringDecision("rec:disneyhotstar", RecurringStatus.DISMISSED, 1L),
            RecurringDecision("rec:spotifyab", RecurringStatus.CONFIRMED, 1L),
        ))
        assertEquals(listOf("rec:spotify"), b.shown.map { it.item.key })
        assertEquals(RecurringStatus.CONFIRMED, b.shown.single().status)
    }
}
