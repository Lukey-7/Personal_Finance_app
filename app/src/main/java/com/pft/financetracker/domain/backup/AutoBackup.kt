package com.pft.financetracker.domain.backup

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * The rules of automatic backups, apart from Android: what each file is called, which of FinTrack's own files to remove
 * once there are more than [KEEP], and when a run is due. A file someone else put in the folder is never touched.
 */
object AutoBackup {
    /** How many automatic backups stay in the folder; older ones FinTrack wrote are removed. */
    const val KEEP = 4

    /** How often the job runs, in days. */
    const val EVERY_DAYS = 7L

    private const val PREFIX = "FinTrack-auto-"
    private const val SUFFIX = ".ftbackup"
    // " (1)" is how some file providers rename a clash; such a file is still one of FinTrack's own.
    private val Name = Regex("""^FinTrack-auto-(\d{4}-\d{2}-\d{2})(?:-(\d+))?(?: \(\d+\))?\.ftbackup$""")

    /** "FinTrack-auto-2026-11-05.ftbackup", or "-2" and so on when that name is already in the folder. */
    fun fileName(now: Long, existing: Collection<String>, zone: ZoneId = ZoneId.systemDefault()): String {
        val day = LocalDate.ofInstant(Instant.ofEpochMilli(now), zone)
        val base = "$PREFIX$day"
        if ("$base$SUFFIX" !in existing) return "$base$SUFFIX"
        var n = 2
        while ("$base-$n$SUFFIX" in existing) n++
        return "$base-$n$SUFFIX"
    }

    /** Whether [name] is one of the files automatic backup wrote. */
    fun isOurs(name: String) = Name.matches(name)

    /** FinTrack's own files beyond the newest [keep], oldest first: the ones to remove after a successful run. */
    fun toRemove(names: Collection<String>, keep: Int = KEEP): List<String> {
        val ours = names.mapNotNull { n -> Name.matchEntire(n)?.let { m -> Triple(n, m.groupValues[1], m.groupValues[2].toIntOrNull() ?: 1) } }
            .sortedWith(compareByDescending<Triple<String, String, Int>> { it.second }.thenByDescending { it.third })
        return ours.drop(keep).map { it.first }.reversed()
    }

    /** "Back up automatically" is on and the last automatic run is at least [EVERY_DAYS] old (or there never was one). */
    fun isDue(lastAutoAt: Long, now: Long): Boolean = now - lastAutoAt >= EVERY_DAYS * 86_400_000L - SLACK_MS

    /** WorkManager's periodic runs drift by a few hours; a run a little early still counts. */
    private const val SLACK_MS = 6 * 3_600_000L
}
