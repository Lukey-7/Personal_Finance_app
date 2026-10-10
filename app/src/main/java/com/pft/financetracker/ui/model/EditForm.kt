package com.pft.financetracker.ui.model

import com.pft.financetracker.domain.model.Rupees
import com.pft.financetracker.domain.parser.BankExtractor
import java.time.Instant
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.util.Locale

/*
 * The editor's rules, kept apart from the screen so they can be tested (which "counts as" choices fit money in or out
 * is a ledger rule: domain/ledger/Corrections.kt): the date picker's UTC days, what the amount field accepts, how quick-add shows a half-typed figure, and which bank a
 * message from Review came from.
 */

/** The Material date picker works in UTC midnights; a payment's time is local. */
object PickerDate {
    /** The UTC midnight of the local calendar day [millis] falls on, for the picker's initial selection. */
    fun toPicker(millis: Long, zone: ZoneId = ZoneId.systemDefault()): Long =
        Instant.ofEpochMilli(millis).atZone(zone).toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

    /** The picked day at [original]'s local time of day: changing the date never moves the time. */
    fun fromPicker(utcMidnight: Long, original: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val day = Instant.ofEpochMilli(utcMidnight).atZone(ZoneOffset.UTC).toLocalDate()
        val time = Instant.ofEpochMilli(original).atZone(zone).toLocalTime()
        return ZonedDateTime.of(day, time, zone).toInstant().toEpochMilli()
    }
}

/** What the editor's amount field accepts, and what it says when the amount can't be used. */
object AmountInput {
    /** Digits and the point; commas are kept as typed but not counted, so a pasted "12,34,56,789.12" stays whole. */
    const val MAX_CHARS = 13

    sealed interface Parsed {
        data class Ok(val paise: Long) : Parsed
        data object Empty : Parsed
        data object Zero : Parsed
        data object TooManyDecimals : Parsed
        data object Invalid : Parsed
    }

    /** Keeps digits, points and commas, up to [MAX_CHARS] digits and points. */
    fun clean(raw: String): String {
        val sb = StringBuilder()
        var counted = 0
        for (c in raw) {
            when {
                c == ',' -> sb.append(c)
                c.isDigit() || c == '.' -> { if (counted >= MAX_CHARS) break; sb.append(c); counted++ }
            }
        }
        return sb.toString()
    }

    private val shape = Regex("""\d*(\.\d*)?""")

    fun parse(text: String): Parsed {
        val s = text.replace(",", "").replace("₹", "").trim()
        if (s.isEmpty()) return Parsed.Empty
        if (!shape.matches(s) || s == ".") return Parsed.Invalid
        val dot = s.indexOf('.')
        val whole = if (dot >= 0) s.substring(0, dot) else s
        val frac = if (dot >= 0) s.substring(dot + 1) else ""
        if (frac.length > 2) return Parsed.TooManyDecimals
        // Exact, from the digits: no rounding through a Double.
        val rupees = if (whole.isEmpty()) 0L else whole.toLongOrNull() ?: return Parsed.Invalid
        if (rupees > Long.MAX_VALUE / 100 - 99) return Parsed.Invalid
        val paise = rupees * 100 + frac.padEnd(2, '0').toLong()
        return if (paise <= 0) Parsed.Zero else Parsed.Ok(paise)
    }

    fun paiseOf(text: String): Long? = (parse(text) as? Parsed.Ok)?.paise

    /** Why Save is off because of the amount; null when the amount is fine or not typed yet. */
    fun problem(text: String): String? = when (parse(text)) {
        Parsed.Invalid -> "Enter a valid amount."
        Parsed.TooManyDecimals -> "Use at most two digits after the point."
        Parsed.Zero -> "Enter an amount above ₹0."
        Parsed.Empty, is Parsed.Ok -> null
    }
}

/** The quick-add figure as it is typed: Indian grouping, with a trailing point or zeros kept ("₹1,200.", "₹12.0"). */
object TypedAmount {
    fun show(typed: String): String {
        if (typed.isEmpty()) return "₹0"
        val dot = typed.indexOf('.')
        val whole = if (dot >= 0) typed.substring(0, dot) else typed
        val grouped = whole.toLongOrNull()?.let { Rupees.group(it) } ?: "0"
        return "₹" + grouped + if (dot >= 0) "." + typed.substring(dot + 1) else ""
    }
}

/** The bank a Review message came from, by the parser's own sender and wording rules; null when it can't tell. */
object ReviewBank {
    fun of(sender: String, body: String): String? {
        val found = BankExtractor.extract(sender, body) ?: return null
        // The parser falls back to the bare sender code ("XYZABC"): that is not a bank name.
        val core = com.pft.financetracker.domain.parser.SenderId.core(sender).uppercase(Locale.ROOT)
        return found.takeIf { it != core && it != sender.trim() }
    }
}
