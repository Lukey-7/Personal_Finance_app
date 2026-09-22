package com.pft.financetracker.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.local.SmsLogEntity
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.parser.FlowClassifier
import com.pft.financetracker.domain.parser.Hashing
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportStats(val runId: Long, val scanned: Int, val inserted: Int, val queuedForReview: Int, val ignored: Int, val duplicates: Int)

/** Every SMS the importer sees ends up in the log with one of these. */
object Outcomes {
    const val SAVED = "SAVED"
    const val REVIEW = "REVIEW"
    const val IGNORED = "IGNORED"
    const val DUPLICATE = "DUPLICATE"
}

/**
 * The SMS -> parser -> Room pipeline. Used both for the one-off inbox scan and for live incoming messages.
 * Only the parsed fields are stored. Raw bodies are kept only for messages queued for manual review.
 * Every message, including ignored ones, gets a row in the SMS log (without its body).
 */
class SmsImporter(
    private val context: Context,
    private val parser: SmsParser,
    private val repo: TransactionRepository,
    private val log: SmsLogRepository,
    private val settings: SettingsRepository,
) {
    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Scans the inbox for messages newer than [sinceMillis] (0 = everything, capped by [maxAgeDays]). */
    suspend fun scanInbox(sinceMillis: Long = 0L, maxAgeDays: Int = 365): ImportStats = withContext(Dispatchers.IO) {
        val runId = System.currentTimeMillis()
        if (!hasSmsPermission()) return@withContext ImportStats(runId, 0, 0, 0, 0, 0)
        val floor = maxOf(sinceMillis, runId - maxAgeDays.toLong() * 24 * 3600 * 1000)
        val messages = SmsReader.read(context, floor)
        var inserted = 0; var review = 0; var ignored = 0; var dup = 0
        for (m in messages) {
            when (process(m, runId)) {
                Outcome.INSERTED -> inserted++
                Outcome.REVIEW -> review++
                Outcome.IGNORED -> ignored++
                Outcome.DUPLICATE -> dup++
            }
        }
        log.prune()
        settings.setLastImportAt(runId)
        ImportStats(runId, messages.size, inserted, review, ignored, dup)
    }

    enum class Outcome { INSERTED, REVIEW, IGNORED, DUPLICATE }

    suspend fun process(sms: SmsMessage, runId: Long = System.currentTimeMillis()): Outcome {
        val hash = Hashing.smsHash(sms.sender, sms.body, sms.receivedAt)
        // Exact same SMS seen before (live receiver + later inbox scan): nothing to do, nothing new to log.
        if (repo.hashSeen(hash)) return Outcome.DUPLICATE

        fun entry(outcome: String, reason: String, amountPaise: Long? = null, type: String? = null, txId: Long? = null) =
            SmsLogEntity(sender = sms.sender, receivedAt = sms.receivedAt, outcome = outcome, reason = reason, amountPaise = amountPaise, type = type, transactionId = txId, smsHash = hash, runId = runId)

        return when (val r = parser.parse(sms)) {
            is ParseResult.Success -> {
                val p = r.transaction
                val category = Categorizer.categorize(p.merchant, p.type, p.bankName)
                val candidate = Transaction(
                    amountPaise = p.amountPaise,
                    type = p.type,
                    merchant = p.merchant,
                    category = category,
                    timestamp = p.timestamp,
                    bankName = p.bankName,
                    accountRef = p.accountRef,
                    source = Transaction.Source.SMS,
                    flow = FlowClassifier.classify(p.type, sms.body, p.merchant, category),
                    smsHash = hash,
                    refNumber = p.refNumber,
                    confidence = p.confidence,
                    needsReview = false,
                )
                val existing = repo.findLikelyDuplicate(candidate)
                if (existing != null) {
                    // Same payment reported by a second sender, or a row imported by an older version whose
                    // hash no longer matches. Keep one record: the richer of the two for the descriptive
                    // fields, but always this parse's flow and category. We are holding the full SMS body,
                    // whereas a row carried over from v1.0.0 only ever had a flow guessed from its category,
                    // so a rescan is the moment a mis-filed card-bill payment or transfer gets corrected.
                    val best = repo.richer(existing, candidate)
                    val merged = best.copy(id = existing.id, smsHash = existing.smsHash, flow = candidate.flow, category = candidate.category)
                    if (merged != existing) repo.update(merged)
                    val why = if (candidate.refNumber != null && candidate.refNumber == existing.refNumber) "same_ref_${existing.id}" else "same_amount_within_10min_${existing.id}"
                    log.log(entry(Outcomes.DUPLICATE, why, p.amountPaise, p.type.name, existing.id))
                    return Outcome.DUPLICATE
                }
                val id = repo.insert(candidate)
                if (id == -1L) { log.log(entry(Outcomes.DUPLICATE, "same_sms", p.amountPaise, p.type.name)); return Outcome.DUPLICATE }
                log.log(entry(Outcomes.SAVED, p.merchant, p.amountPaise, p.type.name, id))
                Outcome.INSERTED
            }
            is ParseResult.NeedsReview -> {
                val ok = repo.enqueueReview(
                    ReviewItemEntity(
                        sender = sms.sender,
                        body = sms.body,
                        receivedAt = sms.receivedAt,
                        smsHash = hash,
                        guessedAmountPaise = r.guessedAmountPaise,
                        guessedType = r.guessedType?.name,
                        reason = r.reason,
                    )
                )
                log.log(entry(if (ok) Outcomes.REVIEW else Outcomes.DUPLICATE, r.reason, r.guessedAmountPaise, r.guessedType?.name))
                if (ok) Outcome.REVIEW else Outcome.DUPLICATE
            }
            is ParseResult.Ignored -> {
                log.log(entry(Outcomes.IGNORED, r.reason))
                Outcome.IGNORED
            }
        }
    }

    /** Re-read one message's body from the phone's inbox (for the log detail screen). Nothing is stored. */
    fun readBody(sender: String, receivedAt: Long): String? = SmsReader.readOne(context, sender, receivedAt)

    /** The user says an ignored/duplicate message was actually a transaction: put it in the review queue. */
    suspend fun sendToReview(logId: Long): Boolean {
        val e = log.getById(logId) ?: return false
        val body = readBody(e.sender, e.receivedAt) ?: return false
        val ok = repo.enqueueReview(ReviewItemEntity(sender = e.sender, body = body, receivedAt = e.receivedAt, smsHash = e.smsHash, guessedAmountPaise = e.amountPaise, guessedType = e.type, reason = "user_flagged"))
        if (ok) log.updateOutcome(e.smsHash, Outcomes.REVIEW, "user_flagged", null)
        return ok
    }
}

object SmsReader {
    /**
     * Reads inbox messages from alphanumeric senders since [sinceMillis]. Uses DATE_SENT (when the operator
     * has it) so timestamps agree with the live receiver, falling back to DATE (received).
     */
    fun read(context: Context, sinceMillis: Long): List<SmsMessage> {
        val out = mutableListOf<SmsMessage>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI,
            projection,
            "${Telephony.Sms.DATE} >= ?",
            arrayOf(sinceMillis.toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return out
        cursor.use { c ->
            val ia = c.getColumnIndex(Telephony.Sms.ADDRESS)
            val ib = c.getColumnIndex(Telephony.Sms.BODY)
            val id = c.getColumnIndex(Telephony.Sms.DATE)
            val ids = c.getColumnIndex(Telephony.Sms.DATE_SENT)
            while (c.moveToNext()) {
                val addr = c.getString(ia) ?: continue
                val body = c.getString(ib) ?: continue
                val date = c.getLong(id)
                val sent = if (ids >= 0) c.getLong(ids) else 0L
                // Personal messages from phone numbers are skipped: bank/UPI alerts come from alphanumeric sender IDs.
                if (!looksLikeServiceSender(addr)) continue
                out += SmsMessage(addr, body, if (sent > 0) sent else date)
            }
        }
        return out
    }

    /** Fetch one body by sender + timestamp (±2 min, to cover sent/received skew). */
    fun readOne(context: Context, sender: String, at: Long): String? {
        val projection = arrayOf(Telephony.Sms.BODY, Telephony.Sms.DATE, Telephony.Sms.DATE_SENT)
        val cursor = context.contentResolver.query(
            Telephony.Sms.Inbox.CONTENT_URI, projection,
            "${Telephony.Sms.ADDRESS} = ? AND ((${Telephony.Sms.DATE_SENT} BETWEEN ? AND ?) OR (${Telephony.Sms.DATE} BETWEEN ? AND ?))",
            arrayOf(sender, (at - 120_000).toString(), (at + 120_000).toString(), (at - 120_000).toString(), (at + 120_000).toString()),
            "${Telephony.Sms.DATE} ASC"
        ) ?: return null
        cursor.use { c -> return if (c.moveToFirst()) c.getString(c.getColumnIndex(Telephony.Sms.BODY)) else null }
    }

    /** Sender IDs like "VM-HDFCBK", "AX-ICICIB-S", "JD-PAYTMB" contain letters; personal numbers do not. */
    fun looksLikeServiceSender(address: String): Boolean = address.any { it.isLetter() }
}
