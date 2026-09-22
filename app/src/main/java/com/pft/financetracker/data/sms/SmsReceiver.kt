package com.pft.financetracker.data.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.provider.Telephony
import com.pft.financetracker.appContainer
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
        val container = context.appContainer
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
            } catch (_: Exception) {
                // Never crash the receiver; a missed message can be picked up by the next inbox scan.
            } finally {
                pending.finish()
            }
        }
    }
}
