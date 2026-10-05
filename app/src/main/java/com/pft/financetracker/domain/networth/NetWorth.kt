package com.pft.financetracker.domain.networth

import com.pft.financetracker.domain.model.Money
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToLong

enum class AssetKind(val label: String) {
    BANK("Bank accounts"), CASH("Cash"), FD("Fixed deposits"), MUTUAL_FUND("Mutual funds"), STOCKS("Shares"), GOLD("Gold"),
    PROPERTY("Property"), EPF("EPF"), PPF("PPF"), NPS("NPS"), OTHER("Other");

    companion object {
        fun fromName(n: String?) = entries.firstOrNull { it.name == n } ?: OTHER
    }
}

/** Something typed in by hand: an asset, or with [liability] a debt. A BANK asset with [accountRef] replaces that account's SMS balance. */
data class Asset(val id: Long = 0, val name: String, val kind: AssetKind, val valuePaise: Long, val liability: Boolean = false, val accountRef: String? = null)

/** The latest balance a bank SMS reported for an account. */
data class AccountBalance(val accountRef: String, val bankName: String?, val balancePaise: Long, val at: Long)

/** A mutual-fund holding read from a CAS statement, valued as the statement printed it. */
data class Holding(val folio: String, val scheme: String, val valuePaise: Long, val asOf: LocalDate)

data class NetWorthSummary(val ownPaise: Long, val owePaise: Long, val byKind: Map<AssetKind, Long>) {
    val totalPaise: Long get() = ownPaise - owePaise
}

/** Reads the balance a bank alert reports after a transaction ("Avl Bal INR 5,000.00"). A card's limit is not a balance. */
object BalanceExtractor {
    private val rx = Regex(
        """(?:avl\.?\s*bal(?:ance)?|available\s+bal(?:ance)?|\bbal(?:ance)?)\s*(?:is|of|:)?\s*(?:inr|rs\.?|₹)\s*([\d,]+(?:\.\d{1,2})?)""",
        RegexOption.IGNORE_CASE,
    )

    private val card = Regex("""\bcard\b""", RegexOption.IGNORE_CASE)
    private val debitCard = Regex("""debit\s*card""", RegexOption.IGNORE_CASE)

    /** Null for card alerts other than debit cards: what a credit card calls its "available balance" is its unused limit. */
    fun extract(body: String): Long? {
        if (card.containsMatchIn(body) && !debitCard.containsMatchIn(body)) return null
        return rx.find(body)?.groupValues?.get(1)?.let { Money.parsePaise(it) }
    }
}

/**
 * Reads a CAMS or KFintech Consolidated Account Statement (the text of the PDF): each scheme with units left, and its
 * market value on the statement date (units x NAV when no value is printed). Nothing is fetched from the internet.
 */
object CasParser {
    private val folio = Regex("""Folio\s*No\s*:?\s*(.+?)(?:\s+PAN\b.*)?$""", RegexOption.IGNORE_CASE)
    private val closing = Regex(
        """Closing\s+Unit\s+Balance\s*:?\s*([\d,]+(?:\.\d+)?)\s+NAV\s+on\s+(\d{1,2}-[A-Za-z]{3}-\d{4})\s*:?\s*INR\s*([\d,]+(?:\.\d+)?)(?:.*?Market\s+Value\s+on\s+[^:]+:\s*INR\s*([\d,]+(?:\.\d+)?))?""",
        RegexOption.IGNORE_CASE,
    )
    private val date = DateTimeFormatter.ofPattern("d-MMM-yyyy", Locale.ENGLISH)
    private val txnLine = Regex("""^\d{1,2}-[A-Za-z]{3}-\d{4}\b""")
    private val skip = Regex("""^(?:opening|closing|folio|consolidated|pan\b)""", RegexOption.IGNORE_CASE)

    fun parse(text: String): List<Holding> {
        val out = mutableListOf<Holding>()
        var currentFolio = ""
        var scheme: String? = null
        for (raw in text.lines()) {
            val line = raw.trim().replace(Regex("""\s+"""), " ")
            if (line.isEmpty()) continue
            val folioMatch = if (line.startsWith("Folio", ignoreCase = true)) folio.find(line) else null
            val m = closing.find(line)
            if (folioMatch != null) {
                currentFolio = folioMatch.groupValues[1].trim()
            } else if (m != null) {
                val units = m.groupValues[1].replace(",", "").toDoubleOrNull() ?: 0.0
                val nav = m.groupValues[3].replace(",", "").toDoubleOrNull() ?: 0.0
                val market = m.groupValues[4].takeIf { it.isNotEmpty() }?.let { Money.parsePaise(it) }
                val asOf = runCatching { LocalDate.parse(titleMonth(m.groupValues[2]), date) }.getOrNull()
                val name = scheme
                if (units > 0 && name != null && asOf != null) out += Holding(currentFolio, name, market ?: (units * nav * 100).roundToLong(), asOf)
                scheme = null
            } else if (!txnLine.containsMatchIn(line) && !skip.containsMatchIn(line) && (line.contains("fund", true) || line.contains(" plan", true))) {
                scheme = line.replace(Regex("""\s*\((?:Advisor|Non-Demat|formerly)[^)]*\)""", RegexOption.IGNORE_CASE), "")
                    .replace(Regex("""\s*Registrar\s*:.*$""", RegexOption.IGNORE_CASE), "").trim()
            }
        }
        return out
    }

    /** "30-SEP-2026" -> "30-Sep-2026", as DateTimeFormatter expects. */
    private fun titleMonth(s: String): String {
        val p = s.split('-')
        return if (p.size == 3) "${p[0]}-${p[1].lowercase(Locale.ENGLISH).replaceFirstChar { it.uppercase() }}-${p[2]}" else s
    }
}

object NetWorth {
    fun compute(assets: List<Asset>, balances: List<AccountBalance>, holdings: List<Holding>, loansOutstandingPaise: Long): NetWorthSummary {
        val typedAccounts = assets.filter { it.kind == AssetKind.BANK && it.accountRef != null }.mapNotNull { it.accountRef }.toSet()
        val smsBanks = balances.filter { it.accountRef !in typedAccounts }.sumOf { it.balancePaise }
        val owned = assets.filter { !it.liability }
        val byKind = owned.groupBy { it.kind }.mapValues { (_, l) -> l.sumOf { it.valuePaise } }.toMutableMap()
        if (smsBanks != 0L) byKind[AssetKind.BANK] = (byKind[AssetKind.BANK] ?: 0L) + smsBanks
        val funds = holdings.sumOf { it.valuePaise }
        if (funds != 0L) byKind[AssetKind.MUTUAL_FUND] = (byKind[AssetKind.MUTUAL_FUND] ?: 0L) + funds
        val own = owned.sumOf { it.valuePaise } + smsBanks + funds
        val owe = assets.filter { it.liability }.sumOf { it.valuePaise } + loansOutstandingPaise
        return NetWorthSummary(own, owe, byKind)
    }
}
