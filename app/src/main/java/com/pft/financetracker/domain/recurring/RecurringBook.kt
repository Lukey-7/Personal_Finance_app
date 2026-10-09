package com.pft.financetracker.domain.recurring

import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.reminders.Reminder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What the person said about a detected charge. Stored by [RecurringItem.key], so it survives re-detection. */
enum class RecurringStatus { CONFIRMED, DISMISSED, CANCELLED }

data class RecurringDecision(val key: String, val status: RecurringStatus, val decidedAt: Long)

/** One row of the Recurring screen. [chargedAfterCancel]: marked cancelled, yet charged again since. */
data class RecurringView(val item: RecurringItem, val status: RecurringStatus?, val chargedAfterCancel: Boolean) {
    /** Counts toward the monthly and yearly totals. An AutoPay seen once has no rhythm yet, so it waits for a second charge. */
    val counted: Boolean get() = item.active && item.rhythmKnown && (status == null || status == RecurringStatus.CONFIRMED || chargedAfterCancel)

    /** Belongs in the main list: still going, or charging after a cancel. Everything else goes under "Stopped". */
    val current: Boolean get() = chargedAfterCancel || (item.active && status != RecurringStatus.CANCELLED)
}

/**
 * Detected charges merged with the person's decisions: what to show, and what they cost per month and per year.
 * [computed] is false only for [EMPTY], the placeholder before the first detection has run, so a screen can show its
 * loading shape instead of "none found".
 */
class RecurringBook private constructor(val shown: List<RecurringView>, val computed: Boolean = true) {
    val monthlyPaise: Long = shown.filter { it.counted }.sumOf { it.item.monthlyPaise }
    val yearlyPaise: Long = shown.filter { it.counted }.sumOf { it.item.yearlyPaise }

    /** Still going: the main list. */
    val current: List<RecurringView> get() = shown.filter { it.current }
    /** Lapsed or cancelled: kept out of the way in a collapsed section. */
    val stopped: List<RecurringView> get() = shown.filterNot { it.current }

    /** A heads-up two days before each counted charge with a known next date. */
    fun reminders(): List<Reminder> = shown.filter { it.counted && it.item.nextExpectedAt != null }.map { v ->
        val due = v.item.nextExpectedAt!!
        Reminder(
            key = v.item.key, title = "${v.item.merchant} renews soon",
            body = "About ${InsightsEngine.rupees(v.item.amountPaise)} on ${SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(due))}",
            dueAt = due, leadDays = listOf(2),
        )
    }

    companion object {
        val EMPTY = RecurringBook(emptyList(), computed = false)

        fun of(items: List<RecurringItem>, decisions: List<RecurringDecision>): RecurringBook {
            // Old decisions are read under today's keys; when two map to one service, the latest choice wins.
            val byKey = decisions.sortedBy { it.decidedAt }.associateBy { RecurringDetector.canonicalKey(it.key) }
            val views = items.mapNotNull { i ->
                val d = byKey[i.key]
                if (d?.status == RecurringStatus.DISMISSED) return@mapNotNull null
                RecurringView(i, d?.status, chargedAfterCancel = d?.status == RecurringStatus.CANCELLED && i.lastChargeAt > d.decidedAt)
            }
            // Charged-after-cancel first (needs action), then confirmed, then the rest by cost; lapsed and cancelled last.
            return RecurringBook(views.sortedWith(
                compareByDescending<RecurringView> { it.chargedAfterCancel }
                    .thenByDescending { it.counted }
                    .thenByDescending { it.current }
                    .thenByDescending { it.status == RecurringStatus.CONFIRMED }
                    .thenByDescending { it.item.monthlyPaise }
            ))
        }
    }
}
