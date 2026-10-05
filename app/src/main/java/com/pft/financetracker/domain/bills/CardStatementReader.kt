package com.pft.financetracker.domain.bills

import com.pft.financetracker.domain.model.Money
import com.pft.financetracker.domain.parser.AccountExtractor
import java.time.LocalDate
import java.time.Month
import java.util.Locale

/** A credit-card statement alert: what is owed on which card, and by when. */
data class CardStatement(val cardLast4: String?, val totalDuePaise: Long, val minDuePaise: Long?, val dueDate: LocalDate)

/**
 * Reads "statement generated" SMS (which the transaction parser rightly ignores, since no money moved) for the
 * total due, minimum due and due date, so the card bill can be tracked and reminded like any other bill.
 */
object CardStatementReader {
    private const val AMT = """(?:rs\.?|inr|₹)\s*([\d,]+(?:\.\d{1,2})?)"""
    private val total = Regex("""total\s*(?:amount|amt\.?)?\s*due\s*(?:is|of|:)?\s*$AMT""", RegexOption.IGNORE_CASE)
    private val min = Regex("""min(?:imum)?\.?\s*(?:amount|amt\.?)?\s*due\s*(?:is|of|:)?\s*$AMT""", RegexOption.IGNORE_CASE)
    private const val MON = """(jan|feb|mar|apr|may|jun|jul|aug|sep|oct|nov|dec)[a-z]*"""
    private val dueLead = Regex("""(?:due\s*date|due\s*by|due\s*on|pay\s*by|payable\s*by|\bby)\s*:?\s*""", RegexOption.IGNORE_CASE)
    private val numeric = Regex("""^(\d{1,2})[-/.](\d{1,2})[-/.](\d{2,4})""")
    private val named = Regex("""^(\d{1,2})[-\s]$MON[-\s,]*(\d{2,4})""", RegexOption.IGNORE_CASE)

    fun read(body: String): CardStatement? {
        val t = total.find(body)?.groupValues?.get(1)?.let { Money.parsePaise(it) } ?: return null
        val due = dueLead.findAll(body).firstNotNullOfOrNull { date(body.substring(it.range.last + 1)) } ?: return null
        val m = min.find(body)?.groupValues?.get(1)?.let { Money.parsePaise(it) }
        return CardStatement(AccountExtractor.extract(body), t, m, due)
    }

    private fun date(s: String): LocalDate? {
        numeric.find(s)?.let { g -> return of(g.groupValues[3], g.groupValues[2].toInt(), g.groupValues[1]) }
        named.find(s)?.let { g ->
            val month = Month.entries.first { it.name.lowercase(Locale.ROOT).startsWith(g.groupValues[2].lowercase(Locale.ROOT)) }
            return of(g.groupValues[3], month.value, g.groupValues[1])
        }
        return null
    }

    private fun of(year: String, month: Int, day: String): LocalDate? {
        val y = year.toInt().let { if (it < 100) 2000 + it else it }
        return runCatching { LocalDate.of(y, month, day.toInt()) }.getOrNull()
    }
}
