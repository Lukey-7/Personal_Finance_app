package com.pft.financetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.pft.financetracker.appContainerOrNull
import com.pft.financetracker.domain.parser.SmsMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Receives new SMS while the app is installed and feeds them through the same parser pipeline.
 * Protected by the system BROADCAST_SMS permission so only the OS can trigger it.
 */
class SmsReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return
        val container = context.appContainerOrNull ?: return
        if (!container.settings.autoImport.value) return
        val parts = Telephony.Sms.Intents.getMessagesFromIntent(intent) ?: return
        if (parts.isEmpty()) return
        val sender = parts.first().displayOriginatingAddress ?: return
        if (!SmsReader.looksLikeServiceSender(sender)) return
        val body = parts.joinToString("") { it.messageBody ?: "" }
        // timestampMillis is the operator's *sent* time; SmsReader uses DATE_SENT too, so hashes agree.
        val ts = parts.first().timestampMillis
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                container.importer.process(SmsMessage(sender, body, ts))
                // Refunds, splits and the widget follow in a background job: a receiver has about ten seconds, and on a
                // slow phone with a long history they could run past it and be reported as not responding.
                AfterSmsWorker.enqueue(context)
            } catch (_: Exception) {
                // Never crash the receiver; a missed message can be picked up by the next inbox scan.
            } finally {
                pending.finish()
            }
        }
    }
}

/** The work after a new SMS (refund pairing, split detection with local rules, the widget), run off the receiver. */
class AfterSmsWorker(context: Context, params: androidx.work.WorkerParameters) : androidx.work.CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.appContainerOrNull ?: return Result.success()
        // A friend's payback may complete a shared payment. Local rules only; the AI judge runs on the next scan.
        c.ledger.catchUp(useAi = false)
        return Result.success()
    }

    companion object {
        private const val WORK = "fintrack-after-sms"

        /** One run after a burst of SMS: each new message appends, so none is skipped and none runs twice at once. */
        fun enqueue(context: Context) {
            val req = androidx.work.OneTimeWorkRequestBuilder<AfterSmsWorker>().build()
            androidx.work.WorkManager.getInstance(context).enqueueUniqueWork(WORK, androidx.work.ExistingWorkPolicy.APPEND_OR_REPLACE, req)
        }
    }
}
