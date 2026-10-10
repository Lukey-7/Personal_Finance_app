package com.pft.financetracker.data.local

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** What FinTrack may do when the secure key store cannot be read. It never deletes the person's data. */
object KeyRecovery {
    /** Rebuilding the key store is safe only when there is no database encrypted with the old key. */
    fun mayRebuild(databaseExists: Boolean): Boolean = !databaseExists

    /** Where an unreadable database is moved when the person chooses to start fresh: kept, never deleted. */
    fun keptName(now: Long): String = "fintrack-unreadable-" + SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date(now)) + ".db"
}
