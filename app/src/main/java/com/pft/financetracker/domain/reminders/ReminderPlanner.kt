package com.pft.financetracker.domain.reminders

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Something with a due date the user may want a heads-up about: a subscription renewal, a bill, an EMI.
 * [key] is stable for the thing (e.g. "bill:12"); [leadDays] are how many days before [dueAt] to remind (0 = on the day).
 */
data class Reminder(val key: String, val title: String, val body: String, val dueAt: Long, val leadDays: List<Int>)

/** What to post now, and the updated record of reminders already sent (persist it and pass it back next time). */
data class ReminderPlan(val toNotify: List<Reminder>, val sent: Set<String>)

/**
 * Decides which reminders to post. Runs once or twice a day from a worker, so it must be idempotent: each
 * (thing, due date, lead) is sent at most once. When the worker missed a day, only the closest reminder goes
 * out and the earlier ones are retired, so a person never gets "in 3 days" and "tomorrow" together.
 */
object ReminderPlanner {
    private const val KEEP_DAYS = 45L

    fun plan(reminders: List<Reminder>, now: Long, alreadySent: Set<String>, zone: ZoneId = ZoneId.systemDefault()): ReminderPlan {
        val today = day(now, zone)
        val sent = alreadySent.filterTo(mutableSetOf()) { k -> k.substringAfter('@').substringBefore('#').toLongOrNull()?.let { it >= today.toEpochDay() - KEEP_DAYS } ?: false }
        val out = mutableListOf<Reminder>()
        for (r in reminders) {
            val due = day(r.dueAt, zone)
            if (today.isAfter(due)) continue
            val daysLeft = due.toEpochDay() - today.toEpochDay()
            val started = r.leadDays.filter { it >= daysLeft }
            val closest = started.minOrNull() ?: continue
            if (sentKey(r, due, closest) !in sent) out += r
            started.forEach { sent += sentKey(r, due, it) }
        }
        return ReminderPlan(out, sent)
    }

    private fun sentKey(r: Reminder, due: LocalDate, lead: Int) = "${r.key}@${due.toEpochDay()}#$lead"
    private fun day(millis: Long, zone: ZoneId): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
}
