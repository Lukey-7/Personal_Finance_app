package com.pft.financetracker.data.reminders

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.pft.financetracker.MainActivity
import com.pft.financetracker.R
import com.pft.financetracker.appContainer
import com.pft.financetracker.domain.reminders.Reminder
import com.pft.financetracker.domain.reminders.ReminderPlanner
import java.util.concurrent.TimeUnit

/** Supplies upcoming due dates (subscriptions, bills, EMIs, backups). Registered in the AppContainer. */
fun interface ReminderSource {
    suspend fun reminders(now: Long): List<Reminder>
}

/**
 * Runs twice a day, entirely on the phone: asks every [ReminderSource] what is coming up, lets [ReminderPlanner]
 * decide what is new, and posts local notifications. Nothing leaves the device; no exact alarms are needed.
 */
class ReminderWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainer
        if (!c.settings.remindersEnabled.value || !Reminders.canPost(applicationContext)) return Result.success()
        val now = System.currentTimeMillis()
        val all = c.reminderSources.flatMap { runCatching { it.reminders(now) }.getOrDefault(emptyList()) }
        val plan = ReminderPlanner.plan(all, now, c.settings.sentReminders())
        plan.toNotify.forEach { Reminders.post(applicationContext, it) }
        c.settings.setSentReminders(plan.sent)
        return Result.success()
    }
}

object Reminders {
    const val CHANNEL = "reminders"
    private const val WORK = "fintrack-reminders"

    /** Idempotent: keeps an existing schedule. Called at app start. */
    fun schedule(context: Context) {
        val req = PeriodicWorkRequestBuilder<ReminderWorker>(12, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(WORK, ExistingPeriodicWorkPolicy.KEEP, req)
    }

    fun ensureChannel(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        if (nm.getNotificationChannel(CHANNEL) != null) return
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, "Bills and renewals", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Heads-up before a bill, EMI or subscription is due"
            }
        )
    }

    fun canPost(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
            NotificationManagerCompat.from(context).areNotificationsEnabled()

    fun post(context: Context, r: Reminder) {
        if (!canPost(context)) return
        ensureChannel(context)
        val open = PendingIntent.getActivity(
            context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(r.title)
            .setContentText(r.body)
            .setContentIntent(open)
            .setAutoCancel(true)
            // Amounts stay off the lock screen.
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .build()
        runCatching { NotificationManagerCompat.from(context).notify(r.key.hashCode(), n) }
    }
}
