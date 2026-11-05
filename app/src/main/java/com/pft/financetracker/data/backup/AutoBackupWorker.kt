package com.pft.financetracker.data.backup

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pft.financetracker.AppContainer
import com.pft.financetracker.appContainerOrNull
import com.pft.financetracker.domain.backup.AutoBackup
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * Checks once a day whether a weekly automatic backup is due and, if so, writes one into the folder the person picked.
 * Entirely on the phone: the file goes only where they chose, sealed like any other backup.
 */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainerOrNull ?: return Result.success()
        if (c.settings.autoBackupFolder.value == null) return Result.success()
        AutoBackups.runNow(applicationContext, c, onlyIfDue = true)
        return Result.success()
    }
}

object AutoBackups {
    private const val WORK = "fintrack-auto-backup"

    /** One backup at a time: turning it on writes the first one while the newly booked job may be starting too. */
    private val running = kotlinx.coroutines.sync.Mutex()

    /** Books the daily check while automatic backup is on, and cancels it when off. Idempotent; called at app start too. */
    fun schedule(context: Context, on: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!on) { wm.cancelUniqueWork(WORK); return }
        val req = PeriodicWorkRequestBuilder<AutoBackupWorker>(1, TimeUnit.DAYS)
            .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
            .build()
        wm.enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    /**
     * Writes one backup now and removes FinTrack's own older automatic ones beyond [AutoBackup.KEEP]. Returns null when
     * it saved, or the words Settings shows (also recorded, so the page says why after a background run). With [onlyIfDue]
     * (the weekly job) it does nothing unless a week has passed, checked once it holds the lock.
     */
    suspend fun runNow(context: Context, c: AppContainer, onlyIfDue: Boolean = false): String? = running.withLock { write(context, c, onlyIfDue) }

    private suspend fun write(context: Context, c: AppContainer, onlyIfDue: Boolean): String? = withContext(Dispatchers.IO) {
        if (onlyIfDue && !AutoBackup.isDue(c.settings.lastAutoBackupAt.value, System.currentTimeMillis())) return@withContext null
        val folder = c.settings.autoBackupFolder.value ?: return@withContext "Automatic backup is off."
        val passphrase = c.settings.autoBackupPassphrase()
            ?: return@withContext fail(c, "Automatic backup stopped: its passphrase is gone. Turn it on again in Settings › Backup.")
        try {
            val tree = Uri.parse(folder)
            val resolver = context.contentResolver
            val dir = DocumentsContract.buildDocumentUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
            val existing = children(context, tree)
            val name = AutoBackup.fileName(System.currentTimeMillis(), existing.keys)
            val bytes = c.backup.export(passphrase)
            val file = DocumentsContract.createDocument(resolver, dir, "application/octet-stream", name)
                ?: return@withContext fail(c, "Automatic backup stopped: the backup folder can't be written to. Pick it again in Settings › Backup.")
            try {
                (resolver.openOutputStream(file, "w") ?: error("no stream")).use { it.write(bytes) }
            } catch (e: Exception) {
                runCatching { DocumentsContract.deleteDocument(resolver, file) }
                throw e
            }
            // Only FinTrack's own automatic files, and only once the new one is safely written.
            AutoBackup.toRemove(existing.keys + name).forEach { old -> existing[old]?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } } }
            c.settings.recordAutoBackup(System.currentTimeMillis())
            null
        } catch (e: SecurityException) {
            fail(c, "Automatic backup stopped: FinTrack can no longer open the backup folder. Pick it again in Settings › Backup.")
        } catch (e: Exception) {
            fail(c, "The last automatic backup didn't save. It will try again tomorrow.")
        } finally {
            passphrase.fill(' ')
        }
    }

    /** Turns automatic backup off: forgets the folder and the passphrase, and cancels the daily check. */
    fun stop(context: Context, settings: com.pft.financetracker.data.prefs.SettingsRepository) {
        settings.autoBackupFolder.value?.let { f ->
            val flags = android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION or android.content.Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            runCatching { context.contentResolver.releasePersistableUriPermission(Uri.parse(f), flags) }
        }
        settings.turnOffAutoBackup()
        schedule(context, false)
    }

    private fun fail(c: AppContainer, words: String): String = words.also { c.settings.recordAutoBackupProblem(it) }

    /** The folder's files by display name. */
    private fun children(context: Context, tree: Uri): Map<String, Uri> {
        val list = DocumentsContract.buildChildDocumentsUriUsingTree(tree, DocumentsContract.getTreeDocumentId(tree))
        val out = HashMap<String, Uri>()
        context.contentResolver.query(list, arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { cur ->
            while (cur.moveToNext()) {
                val id = cur.getString(0) ?: continue
                val name = cur.getString(1) ?: continue
                out[name] = DocumentsContract.buildDocumentUriUsingTree(tree, id)
            }
        }
        return out
    }
}
