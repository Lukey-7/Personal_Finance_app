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
    /** Counts toward the monthly and yearly totals. */
    val counted: Boolean get() = item.active && (status == null || status == RecurringStatus.CONFIRMED || chargedAfterCancel)
}

/** Detected charges merged with the person's decisions: what to show, and what they cost per month and per year. */
class RecurringBook private constructor(val shown: List<RecurringView>) {
    val monthlyPaise: Long = shown.filter { it.counted }.sumOf { it.item.monthlyPaise }
    val yearlyPaise: Long = shown.filter { it.counted }.sumOf { it.item.yearlyPaise }

    /** A heads-up two days before each counted charge with a known next date. */
    fun reminders(): List<Reminder> = shown.filter { it.counted && it.item.nextExpectedAt != null }.map { v ->
        val due = v.item.nextExpectedAt!!
        Reminder(
            key = v.item.key, title = "${v.item.merchant} renews soon",
            body = "About ₹${InsightsEngine.fmt(v.item.amountPaise)} on ${SimpleDateFormat("d MMM", Locale.ENGLISH).format(Date(due))}",
            dueAt = due, leadDays = listOf(2),
        )
    }

    companion object {
        val EMPTY = RecurringBook(emptyList())

        fun of(items: List<RecurringItem>, decisions: List<RecurringDecision>): RecurringBook {
            val byKey = decisions.associateBy { it.key }
            val views = items.mapNotNull { i ->
                val d = byKey[i.key]
                if (d?.status == RecurringStatus.DISMISSED) return@mapNotNull null
                RecurringView(i, d?.status, chargedAfterCancel = d?.status == RecurringStatus.CANCELLED && i.lastChargeAt > d.decidedAt)
            }
            // Charged-after-cancel first (needs action), then confirmed, then the rest by cost; lapsed and cancelled last.
            return RecurringBook(views.sortedWith(
                compareByDescending<RecurringView> { it.chargedAfterCancel }
                    .thenByDescending { it.counted }
                    .thenByDescending { it.status == RecurringStatus.CONFIRMED }
                    .thenByDescending { it.item.monthlyPaise }
            ))
        }
    }
}
