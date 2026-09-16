package com.pft.financetracker.domain.parser

import java.security.MessageDigest

object Hashing {
    /** Stable fingerprint of an SMS for de-duplication. One-way; the body cannot be recovered from it. */
    fun smsHash(sender: String, body: String, receivedAt: Long): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest("$sender|$body|${receivedAt / 60000}".toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}
