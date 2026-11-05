package com.pft.financetracker.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import com.pft.financetracker.appContainer
import com.pft.financetracker.data.readAtMost
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext

/** Settings › Backup: one backup to a file, a restore, and the weekly automatic backup into a picked folder. */
class BackupViewModel(app: Application) : AndroidViewModel(app) {
    private val c = app.appContainer

    val lastBackupAt: StateFlow<Long> = c.settings.lastBackupAt
    private val _backupBusy = MutableStateFlow<String?>(null)
    /** "Locking your backup…" / "Opening the backup…" while it runs; null otherwise. */
    val backupBusy: StateFlow<String?> = _backupBusy
    /** Seals every table with [passphrase] and writes it to the file the person picked. Returns an error message, or null. */
    suspend fun writeBackup(uri: Uri, passphrase: CharArray): String? = withContext(Dispatchers.IO) {
        _backupBusy.value = "Locking your backup…"
        runCatching {
            val bytes = c.backup.export(passphrase)
            getApplication<Application>().contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Could not write the file.")
            c.settings.setLastBackupAt(System.currentTimeMillis())
        }.exceptionOrNull()?.let { it.message ?: "Backup failed." }.also { passphrase.fill(' '); _backupBusy.value = null }
    }
    /** Replaces all data with the backup's. Returns an error message, or null when done. */
    suspend fun restoreBackup(uri: Uri, passphrase: CharArray): String? = withContext(Dispatchers.IO) {
        _backupBusy.value = "Opening the backup…"
        runCatching {
            val stream = getApplication<Application>().contentResolver.openInputStream(uri) ?: error("Could not open the file.")
            // Far bigger than any real backup; a larger file is refused before it is loaded whole.
            val bytes = stream.use { it.readAtMost(256 * 1024 * 1024) } ?: error("This file is too large to be a FinTrack backup.")
            val madeAt = c.backup.restore(bytes, passphrase)
            // The next scan re-reads SMS from when the backup was made (all of them if unknown); duplicates are skipped by hash.
            c.settings.setLastImportAt(if (madeAt > 0L) minOf(madeAt, c.settings.lastImportAt.value) else 0L)
            // Reminders already sent belong to the replaced data.
            c.settings.setSentReminders(emptySet())
            c.templates.load()
            c.categories.load()
            c.ledger.catchUp()
        }.exceptionOrNull()?.let { it.message ?: "Restore failed." }.also { passphrase.fill(' '); _backupBusy.value = null }
    }

    /** Automatic backup: the folder it writes to (null while off), its last run, and why that run failed, if it did. */
    val autoBackupFolder: StateFlow<String?> = c.settings.autoBackupFolder
    val lastAutoBackupAt: StateFlow<Long> = c.settings.lastAutoBackupAt
    val autoBackupProblem: StateFlow<String?> = c.settings.autoBackupProblem

    /**
     * Turns automatic backup on for the folder the person picked, keeping access to it across restarts, and makes the
     * first backup at once. Returns an error message, or null when the first backup saved.
     */
    suspend fun startAutoBackup(folder: Uri, passphrase: CharArray): String? = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        try {
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { app.contentResolver.takePersistableUriPermission(folder, flags) }
                .onFailure { return@withContext "FinTrack couldn't keep access to that folder. Try another one." }
            if (!c.settings.setAutoBackup(folder.toString(), passphrase)) return@withContext "The passphrase couldn't be stored safely on this phone."
            // The first backup before the weekly job is booked, so the job finds it done.
            com.pft.financetracker.data.backup.AutoBackups.runNow(app, c)
                .also { com.pft.financetracker.data.backup.AutoBackups.schedule(app, true) }
        } finally {
            passphrase.fill(' ')
        }
    }

    /** One automatic backup now, into the chosen folder. Returns an error message, or null when it saved. */
    suspend fun autoBackupNow(): String? = com.pft.financetracker.data.backup.AutoBackups.runNow(getApplication(), c)

    /** Stops automatic backup and lets go of the folder; the backups already in it stay where they are. */
    fun stopAutoBackup() = com.pft.financetracker.data.backup.AutoBackups.stop(getApplication(), c.settings)
}
