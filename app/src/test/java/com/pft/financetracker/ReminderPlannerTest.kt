package com.pft.financetracker

import com.pft.financetracker.domain.reminders.Reminder
import com.pft.financetracker.domain.reminders.ReminderPlanner
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import com.pft.financetracker.domain.reminders.BackupNudge
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class ReminderPlannerTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(date: String, hour: Int = 9): Long = LocalDate.parse(date).atTime(hour, 0).atZone(zone).toInstant().toEpochMilli()
    private val netflix = Reminder(key = "sub:7", title = "Netflix renews", body = "₹649 on 10 Oct", dueAt = at("2026-10-10", 0), leadDays = listOf(3, 1))

    @Test
    fun firesTheThreeDayReminderThreeDaysBefore() {
        val plan = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-07"), alreadySent = emptySet(), zone = zone)
        assertEquals(listOf("Netflix renews"), plan.toNotify.map { it.title })
    }

    @Test
    fun staysQuietFourDaysBefore() {
        val plan = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-06"), alreadySent = emptySet(), zone = zone)
        assertTrue(plan.toNotify.isEmpty())
    }

    @Test
    fun neverSendsTheSameReminderTwice() {
        val first = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-07"), alreadySent = emptySet(), zone = zone)
        val second = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-07", 18), alreadySent = first.sent, zone = zone)
        assertTrue(second.toNotify.isEmpty())
    }

    @Test
    fun aMissedDaySendsOnlyTheClosestReminder() {
        // The worker did not run on the 7th; on the 9th only the 1-day reminder goes out, and the 3-day one is retired.
        val plan = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-09"), alreadySent = emptySet(), zone = zone)
        assertEquals(1, plan.toNotify.size)
        val again = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-09", 20), alreadySent = plan.sent, zone = zone)
        assertTrue(again.toNotify.isEmpty())
    }

    @Test
    fun theDayItselfStillReminds() {
        val r = netflix.copy(leadDays = listOf(0))
        val plan = ReminderPlanner.plan(listOf(r), now = at("2026-10-10", 8), alreadySent = emptySet(), zone = zone)
        assertEquals(1, plan.toNotify.size)
    }

    @Test
    fun aPastDueDateIsNotReminded() {
        val plan = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-11"), alreadySent = emptySet(), zone = zone)
        assertTrue(plan.toNotify.isEmpty())
    }

    @Test
    fun theNextCycleRemindsAgain() {
        val oct = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-09"), alreadySent = emptySet(), zone = zone)
        val nov = netflix.copy(dueAt = at("2026-11-10", 0))
        val plan = ReminderPlanner.plan(listOf(nov), now = at("2026-11-09"), alreadySent = oct.sent, zone = zone)
        assertEquals(1, plan.toNotify.size)
    }

    @Test
    fun oldSentKeysArePruned() {
        val oct = ReminderPlanner.plan(listOf(netflix), now = at("2026-10-09"), alreadySent = emptySet(), zone = zone)
        val later = ReminderPlanner.plan(emptyList(), now = at("2027-01-20"), alreadySent = oct.sent, zone = zone)
        assertTrue(later.sent.isEmpty())
    }

    // ---- Backup nudge ----

    private val lastBackup = at("2026-09-01", 20)

    @Test fun theBackupNudgeWaitsAMonthThenFiresOnceAWeek() {
        assertNull(BackupNudge.reminder(lastBackup, at("2026-09-30")))
        assertNull(BackupNudge.reminder(0L, at("2026-12-01")))
        var sent = emptySet<String>()
        val fired = mutableListOf<String>()
        var day = LocalDate.parse("2026-10-01")
        while (!day.isAfter(LocalDate.parse("2026-10-21"))) {
            for (hour in listOf(9, 21)) {
                val now = at(day.toString(), hour)
                val plan = ReminderPlanner.plan(listOfNotNull(BackupNudge.reminder(lastBackup, now)), now, sent, zone)
                if (plan.toNotify.isNotEmpty()) fired += "$day $hour"
                sent = plan.sent
            }
            day = day.plusDays(1)
        }
        // Day 30 is 1 Oct at 20:00: the 21:00 run sends it, then nothing until a week later, then a week after that.
        assertEquals(listOf("2026-10-01 21", "2026-10-08 21", "2026-10-15 21"), fired)
    }

    @Test fun aNewBackupStopsTheNudge() {
        val after = at("2026-10-05")
        assertNull(BackupNudge.reminder(after, at("2026-10-20")))
    }
}
