package com.pft.financetracker.domain.parser

import com.pft.financetracker.domain.model.TransactionType

/**
 * The cashback a payment alert says was credited with it: "Rs 200 debited ... Cashback of Rs 20 credited". Only money
 * already credited counts: "will be credited", "eligible for", "up to" and offers are not money in.
 */
object Cashback {
    private const val AMOUNT = """(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)"""
    private val before = Regex("""cash\s*-?\s*back\s+(?:of\s+)?$AMOUNT\s+(?:has\s+been\s+|is\s+|was\s+)?credited""")
    private val after = Regex("""$AMOUNT\s+cash\s*-?\s*back\s+(?:has\s+been\s+|is\s+|was\s+)?credited""")
    private val notYet = Regex("""\b(will\s+be|to\s+be|shall\s+be|eligible|up\s*to|upto|win|get\s+up)\b""")

    /** The cashback in paise, or null when the message credits none (or only promises one). */
    fun credited(body: String, type: TransactionType, paymentPaise: Long): Long? {
        if (type != TransactionType.DEBIT) return null
        val text = SmsText.normalize(body).lowercase()
        val m = before.find(text) ?: after.find(text) ?: return null
        // "Cashback of Rs 20 will be credited" never matches above; this catches wording around the match itself.
        val around = text.substring(maxOf(0, m.range.first - 25), minOf(text.length, m.range.last + 1))
        if (notYet.containsMatchIn(around)) return null
        val paise = toPaise(m.groupValues[1]) ?: return null
        // A cashback is part of a payment, never all of it or more.
        return paise.takeIf { it in 1 until paymentPaise }
    }

    private fun toPaise(s: String): Long? {
        val clean = s.replace(",", "")
        val parts = clean.split('.')
        val rupees = parts[0].toLongOrNull() ?: return null
        val paise = parts.getOrNull(1)?.padEnd(2, '0')?.take(2)?.toLongOrNull() ?: 0L
        return rupees * 100 + paise
    }
}
