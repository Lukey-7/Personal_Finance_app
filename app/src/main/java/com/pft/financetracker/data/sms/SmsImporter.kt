package com.pft.financetracker.data.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Telephony
import androidx.core.content.ContextCompat
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.domain.categorize.Categorizer
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.parser.Hashing
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class ImportStats(val scanned: Int, val inserted: Int, val queuedForReview: Int, val ignored: Int, val duplicates: Int)

/**
 * The SMS -> parser -> Room pipeline. Used both for the one-off inbox scan and for live incoming messages.
 * Only the parsed fields are stored. Raw bodies are kept only for messages queued for manual review.
 */
class SmsImporter(
    private val context: Context,
    private val parser: SmsParser,
    private val repo: TransactionRepository,
    private val settings: SettingsRepository,
) {
    fun hasSmsPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED

    /** Scans the inbox for messages newer than [sinceMillis] (0 = everything, capped by [maxAgeDays]). */
    suspend fun scanInbox(sinceMillis: Long = 0L, maxAgeDays: Int = 365): ImportStats = withContext(Dispatchers.IO) {
        if (!hasSmsPermission()) return@withContext ImportStats(0, 0, 0, 0, 0)
        val floor = maxOf(sinceMillis, System.currentTimeMillis() - maxAgeDays.toLong() * 24 * 3600 * 1000)
        val messages = SmsReader.read(context, floor)
        var inserted = 0; var review = 0; var ignored = 0; var dup = 0
        for (m in messages) {
            when (process(m)) {
                Outcome.INSERTED -> inserted++
                Outcome.REVIEW -> review++
                Outcome.IGNORED -> ignored++
                Outcome.DUPLICATE -> dup++
            }
        }
        settings.setLastImportAt(System.currentTimeMillis())
        ImportStats(messages.size, inserted, review, ignored, dup)
    }

    enum class Outcome { INSERTED, REVIEW, IGNORED, DUPLICATE }

    suspend fun process(sms: SmsMessage): Outcome {
        val hash = Hashing.smsHash(sms.sender, sms.body, sms.receivedAt)
        if (repo.hashSeen(hash)) return Outcome.DUPLICATE
        return when (val r = parser.parse(sms)) {
            is ParseResult.Success -> {
                val p = r.transaction
                val t = Transaction(
                    amount = p.amount,
                    type = p.type,
                    merchant = p.merchant,
                    category = Categorizer.categorize(p.merchant, p.type, p.bankName),
                    timestamp = p.timestamp,
                    bankName = p.bankName,
                    accountRef = p.accountRef,
                    source = Transaction.Source.SMS,
                    smsHash = hash,
                    confidence = p.confidence,
                    needsReview = false,
                )
                if (repo.insert(t)) Outcome.INSERTED else Outcome.DUPLICATE
            }
            is ParseResult.NeedsReview -> {
                val ok = repo.enqueueReview(
                    ReviewItemEntity(
                        sender = sms.sender,
                        body = sms.body,
                        receivedAt = sms.receivedAt,
                        smsHash = hash,
                        guessedAmount = r.guessedAmount,
                        guessedType = r.guessedType?.name,
                        reason = r.reason,
                    )
                )
                if (ok) Outcome.REVIEW else Outcome.DUPLICATE
            }
            is ParseResult.Ignored -> Outcome.IGNORED
        }
    }
}

object SmsReader {
    fun read(context: Context, sinceMillis: Long): List<SmsMessage> {
        val out = mutableListOf<SmsMessage>()
        val projection = arrayOf(Telephony.Sms.ADDRESS, Telephony.Sms.BODY, Telephony.Sms.DATE)
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
            while (c.moveToNext()) {
                val addr = c.getString(ia) ?: continue
                val body = c.getString(ib) ?: continue
                val date = c.getLong(id)
                // Personal messages from phone numbers are skipped: bank/UPI alerts come from alphanumeric sender IDs.
                if (!looksLikeServiceSender(addr)) continue
                out += SmsMessage(addr, body, date)
            }
        }
        return out
    }

    /** Sender IDs like "VM-HDFCBK", "AX-ICICIB-S", "JD-PAYTMB" contain letters; personal numbers do not. */
    fun looksLikeServiceSender(address: String): Boolean = address.any { it.isLetter() }
}
