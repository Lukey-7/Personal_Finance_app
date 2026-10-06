package com.pft.financetracker.ui.components

import com.pft.financetracker.data.sms.ImportStats
import com.pft.financetracker.domain.model.Paise
import com.pft.financetracker.domain.model.Rupees

/*
 * Words people see in place of internal codes and counts. Plain, sentence case, no jargon.
 */

private val reasons = mapOf(
    // Why a message went to Review
    "type_ambiguous" to "Couldn't tell if money came in or went out",
    "amount_not_found" to "No amount found",
    "user_flagged" to "You sent it to Review",
    "balance_mismatch" to "Statement balance doesn't add up",
    "statement_balance_mismatch" to "Statement balance doesn't add up",
    // Why a message was skipped
    "not_moved" to "No money moved",
    "otp" to "A one-time code",
    "promo" to "An offer or promotion",
    "promotional_sender" to "An offer or promotion",
    "future" to "A payment due later, not yet made",
    "future_soft" to "A payment due later, not yet made",
    "failed" to "A failed payment",
    "request" to "A payment request, not a payment",
    "balance_only" to "Only a balance update",
    "statement" to "A statement summary",
    "login" to "A login or security alert",
    "empty" to "An empty message",
    "no_amount_no_type" to "Not a transaction",
    "no_transaction_hint" to "Not a transaction",
    // Duplicates and your own choices
    "same_sms" to "Same message as one already saved",
    "same_ref" to "Same payment as one already saved",
    "dismissed_by_user" to "You dismissed it",
    "deleted_by_user" to "You deleted it",
)

/** Plain words for a parser or importer reason code; unknown codes still read as a sentence. */
fun reasonLabel(code: String): String {
    reasons[code]?.let { return it }
    if (code.startsWith("low_confidence")) return "Not sure enough to save it"
    return code.replace('_', ' ').trim().replaceFirstChar { it.uppercase() }.ifBlank { "Unknown" }
}

/** "12 added · 1 to review · 4 skipped", or why the scan could not run. */
fun scanResultLine(stats: ImportStats?, failed: Boolean): String {
    if (failed || stats == null) return "Couldn't read your SMS. Check the permission in Settings."
    val skipped = stats.ignored + stats.duplicates
    val parts = buildList {
        if (stats.inserted > 0) add("${stats.inserted} added")
        if (stats.queuedForReview > 0) add("${stats.queuedForReview} to review")
        if (isEmpty()) add("No new transactions")
        if (skipped > 0) add("$skipped skipped")
    }
    return parts.joinToString(" · ")
}

/** "1 payment", "3 payments", "2 people". */
fun countLabel(n: Int, one: String, many: String = one + "s"): String = "$n ${if (n == 1) one else many}"

private val amountLike = Regex("""^[₹\s\d,.]*\d[₹\s\d,.]*$""")

/**
 * True when [query] is an amount (digits, with or without ₹, commas or paise) that appears in this figure. A query with
 * letters in it ("1mg", "zomato 250") is a name, never an amount.
 */
fun matchesAmount(paise: Long, query: String): Boolean {
    if (!amountLike.matches(query.trim())) return false
    val q = query.filter { it.isDigit() || it == '.' }
    return listOf(Rupees.format(paise, Paise.WHEN_NONZERO), Rupees.format(paise, Paise.ALWAYS))
        .map { it.filter { c -> c.isDigit() || c == '.' } }
        .any { it.contains(q) }
}

/** Which way spending moved: up is drawn as spend, down as income, flat in plain grey. */
enum class Trend { UP, DOWN, FLAT }

/**
 * The line under Home's figure: how it compares, the daily rate and where the month is heading. Never blank, never
 * "0% more", and never "nothing spent" when refunds simply outweigh spending.
 */
fun heroLine(
    spendPaise: Long,
    change: Int?,
    comparedWith: String,
    running: Boolean,
    daysIn: Int,
    dailyPaise: Long,
    projectedPaise: Long?,
): Pair<String, Trend> {
    if (spendPaise < 0) return (if (running) "Refunds are more than you've spent so far" else "Refunds were more than you spent") to Trend.FLAT
    if (spendPaise == 0L) return (if (running) "Nothing spent yet" else "Nothing spent in this period") to Trend.FLAT
    // Averages over one or two days say more about the calendar than about spending.
    val settled = daysIn >= 3
    val parts = listOfNotNull(
        change?.let {
            when {
                it == 0 -> "About the same as $comparedWith"
                it > 0 -> "$it% more than $comparedWith"
                else -> "${-it}% less than $comparedWith"
            }
        },
        if (settled) "about ${approxMoney(dailyPaise)} a day" else null,
        if (running && settled) projectedPaise?.let { "on track for ${approxMoney(it)}" } else null,
    )
    val trend = when { change == null || change == 0 -> Trend.FLAT; change > 0 -> Trend.UP; else -> Trend.DOWN }
    return (parts.joinToString(" · ").ifEmpty { "Too early to compare" }) to trend
}
