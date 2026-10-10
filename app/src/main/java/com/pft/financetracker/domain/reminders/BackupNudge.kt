package com.pft.financetracker.domain.reminders

/**
 * "Time for a backup", for people who already keep backups: once when the last one turns 30 days old, then at most
 * once a week until a new backup is made. Each week after day 30 is one reminder with a stable key and due date, so
 * [ReminderPlanner] sends it once, on whichever day of that week the worker runs.
 */
object BackupNudge {
    private const val DAY = 86_400_000L
    private const val AFTER_DAYS = 30L
    private const val EVERY_DAYS = 7L

    fun reminder(lastBackupAt: Long, now: Long): Reminder? {
        if (lastBackupAt <= 0L) return null
        val first = lastBackupAt + AFTER_DAYS * DAY
        if (now < first) return null
        val week = (now - first) / (EVERY_DAYS * DAY)
        val weekStart = first + week * EVERY_DAYS * DAY
        // Due on the week's last day, reminding from 6 days before: any run in that week sends it, and only once.
        val due = weekStart + (EVERY_DAYS - 1) * DAY
        return Reminder(
            key = "backup:$lastBackupAt", title = "Time for a backup", body = "Your last FinTrack backup is over a month old.",
            dueAt = due, leadDays = listOf((EVERY_DAYS - 1).toInt()),
        )
    }
}
