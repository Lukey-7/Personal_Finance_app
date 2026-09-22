package com.pft.financetracker.domain.parser

import java.security.MessageDigest

object Hashing {
    /**
     * Stable fingerprint of an SMS for de-duplication. One-way; the body cannot be recovered from it.
     * Keyed on sender + normalised body + the calendar *day* of the sent time, so the live receiver and the
     * inbox scan (whose timestamps differ by seconds) always agree. Identical bodies on the same day are the
     * same alert redelivered; genuinely repeated payments carry different reference numbers in the body.
     */
    fun smsHash(sender: String, body: String, sentAt: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val normalizedBody = body.replace(Regex("""\s+"""), " ").trim()
        val day = java.util.Calendar.getInstance().apply { timeInMillis = sentAt }
        val dayKey = day.get(java.util.Calendar.YEAR) * 1000 + day.get(java.util.Calendar.DAY_OF_YEAR)
        val bytes = md.digest("${sender.uppercase()}|$normalizedBody|$dayKey".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
