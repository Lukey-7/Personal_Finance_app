package com.pft.financetracker

import com.pft.financetracker.domain.backup.AutoBackup
import com.pft.financetracker.ui.model.HomeLines
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

/** v1.6: weekly backups are named by day, only FinTrack's own old ones are removed, and a run is due after a week. */
class AutoBackupTest {
    private val zone = ZoneId.of("Asia/Kolkata")
    private fun at(day: String) = LocalDate.parse(day).atTime(10, 0).atZone(zone).toInstant().toEpochMilli()

    @Test fun filesAreNamedByDayAndNeverOverwriteOneThere() {
        assertEquals("FinTrack-auto-2026-11-05.ftbackup", AutoBackup.fileName(at("2026-11-05"), emptyList(), zone))
        assertEquals("FinTrack-auto-2026-11-05-2.ftbackup", AutoBackup.fileName(at("2026-11-05"), listOf("FinTrack-auto-2026-11-05.ftbackup"), zone))
        assertEquals("FinTrack-auto-2026-11-05-3.ftbackup",
            AutoBackup.fileName(at("2026-11-05"), listOf("FinTrack-auto-2026-11-05.ftbackup", "FinTrack-auto-2026-11-05-2.ftbackup"), zone))
    }

    @Test fun onlyTheirOwnOlderFilesAreRemoved() {
        val names = listOf(
            "FinTrack-auto-2026-10-01.ftbackup", "FinTrack-auto-2026-10-08.ftbackup", "FinTrack-auto-2026-10-15.ftbackup",
            "FinTrack-auto-2026-10-22.ftbackup", "FinTrack-auto-2026-10-29.ftbackup", "FinTrack-auto-2026-10-29-2.ftbackup",
            // Made by hand, or something else entirely: never touched.
            "FinTrack-2026-09-01.ftbackup", "holiday.jpg", "FinTrack-auto-notes.txt",
        )
        assertEquals(listOf("FinTrack-auto-2026-10-01.ftbackup", "FinTrack-auto-2026-10-08.ftbackup"), AutoBackup.toRemove(names))
        assertTrue(AutoBackup.toRemove(names.take(3)).isEmpty())
        assertFalse(AutoBackup.isOurs("FinTrack-2026-09-01.ftbackup"))
    }

    @Test fun aRunIsDueAfterAWeekWithALittleSlack() {
        val now = at("2026-11-05")
        assertTrue(AutoBackup.isDue(0L, now))
        assertTrue(AutoBackup.isDue(now - 7 * 86_400_000L, now))
        assertTrue("a few hours early still counts", AutoBackup.isDue(now - 7 * 86_400_000L + 3 * 3_600_000L, now))
        assertFalse(AutoBackup.isDue(now - 3 * 86_400_000L, now))
    }

    @Test fun savingsTileWordsOnlyWithoutIncome() {
        assertEquals("No income yet", HomeLines.savingsInstead(0L))
        assertNull(HomeLines.savingsInstead(1L))
    }
}
