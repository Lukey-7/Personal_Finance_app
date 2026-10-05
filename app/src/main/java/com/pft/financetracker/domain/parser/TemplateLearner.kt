package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.model.TransactionType

/**
 * The shape of one sender's message, learned from a message a person confirmed in Review. [skeleton] is the text with
 * the amount as {AMT}, the merchant as {MER}, every other number (account, date, balance) as {N} and month names as
 * {MON}. It holds no amounts, names or account digits, only the bank's fixed wording.
 */
data class LearnedTemplate(val senderCore: String, val skeleton: String, val type: TransactionType)

data class TemplateHit(val amountPaise: Long, val type: TransactionType, val merchant: String?)

/**
 * Learns a sender's message shape from one confirmed example and reads later messages of exactly that shape. Matching
 * is strict (same sender, same wording, only the numbers and the merchant may differ), so a "will be debited" or a
 * credit in otherwise similar words never matches a learned debit.
 */
object TemplateLearner {
    private const val AMT = "{AMT}"
    private const val MER = "{MER}"
    private const val N = "{N}"
    private const val MON = "{MON}"

    /** A number that is not part of a masked account like XX1234 or **1234. */
    private val amountToken = Regex("""(?<![A-Za-z0-9*.,])(\d{1,3}(?:,\d{2,3})+|\d+)(?:\.(\d{1,2}))?(?![\d])""")
    private val currencyBefore = Regex("""(?:rs\.?|inr|₹)\s*$""", RegexOption.IGNORE_CASE)
    private val number = Regex("""[Xx*]*\d[\d,]*(?:\.\d+)?""")
    private val month = Regex("""\b(?:jan|feb|mar|apr|may|jun|jul|aug|sep|sept|oct|nov|dec|january|february|march|april|june|july|august|september|october|november|december)\b""", RegexOption.IGNORE_CASE)
    private val placeholder = Regex("""\{(AMT|MER|N|MON)\}""")

    fun learn(sender: String, body: String, amountPaise: Long, type: TransactionType, merchant: String?): LearnedTemplate? {
        val text = normalize(body)
        val amount = amountToken.findAll(text)
            .filter { Money.parsePaise(it.value) == amountPaise }
            .sortedByDescending { currencyBefore.containsMatchIn(text.substring(0, it.range.first)) }
            .firstOrNull() ?: return null
        val mer = merchant?.trim()?.takeIf { it.length >= 3 }?.let { m ->
            Regex(Regex.escape(m), RegexOption.IGNORE_CASE).findAll(text).firstOrNull { !it.range.overlaps(amount.range) }
        }

        val spans = listOfNotNull(amount.range to AMT, mer?.let { it.range to MER }).sortedBy { it.first.first }
        val sb = StringBuilder()
        var at = 0
        for ((range, tag) in spans) {
            sb.append(generalize(text.substring(at, range.first))).append(tag)
            at = range.last + 1
        }
        sb.append(generalize(text.substring(at)))
        return LearnedTemplate(SenderId.core(sender), sb.toString(), type)
    }

    fun apply(t: LearnedTemplate, sender: String, body: String): TemplateHit? {
        if (SenderId.core(sender) != t.senderCore) return null
        val m = toRegex(t.skeleton).matchEntire(normalize(body)) ?: return null
        val names = placeholder.findAll(t.skeleton).map { it.groupValues[1] }.filter { it == "AMT" || it == "MER" }.toList()
        val amt = names.indexOf("AMT").takeIf { it >= 0 }?.let { m.groupValues[it + 1] } ?: return null
        val paise = Money.parsePaise(amt)?.takeIf { it > 0 } ?: return null
        val merchant = names.indexOf("MER").takeIf { it >= 0 }?.let { m.groupValues[it + 1].trim() }?.takeIf { it.isNotEmpty() }
        return TemplateHit(paise, t.type, merchant)
    }

    private fun normalize(s: String) = s.replace('\n', ' ').replace(Regex("""\s+"""), " ").trim()

    private fun generalize(literal: String): String = month.replace(number.replace(literal, N), MON)

    private fun toRegex(skeleton: String): Regex {
        val sb = StringBuilder()
        var at = 0
        for (p in placeholder.findAll(skeleton)) {
            sb.append(literal(skeleton.substring(at, p.range.first)))
            sb.append(
                when (p.groupValues[1]) {
                    "AMT" -> """([\d,]+(?:\.\d{1,2})?)"""
                    "MER" -> """(.+?)"""
                    "N" -> """[Xx*]*\d[\d,]*(?:\.\d+)?"""
                    else -> """[A-Za-z]{3,9}"""
                }
            )
            at = p.range.last + 1
        }
        sb.append(literal(skeleton.substring(at)))
        return Regex(sb.toString(), RegexOption.IGNORE_CASE)
    }

    /** Fixed wording, with any run of spaces allowed to vary. */
    private fun literal(s: String): String = s.split(' ').joinToString("""\s+""") { if (it.isEmpty()) "" else Regex.escape(it) }

    private fun IntRange.overlaps(o: IntRange) = first <= o.last && o.first <= last

}
