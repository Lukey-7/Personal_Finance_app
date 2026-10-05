package com.pft.financetracker.domain.tax

import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Indian income-tax deductions (old regime) people most often pay for through their bank. [limitPaise] null: no fixed cap here. */
enum class TaxSection(val code: String, val label: String, val limitPaise: Long?) {
    S80C("80C", "Investments, insurance, tuition", 1_50_000_00),
    S80CCD1B("80CCD(1B)", "NPS (extra)", 50_000_00),
    S80D("80D", "Health insurance", 25_000_00),
    S80E("80E", "Education loan interest", null),
    S24B("24(b)", "Home loan interest", 2_00_000_00),
    S80G("80G", "Donations", null),
    HRA("HRA", "Rent paid", null);

    companion object {
        fun fromName(n: String?): TaxSection? = entries.firstOrNull { it.name == n }
    }
}

/** The Indian financial year that starts on 1 April of [startYear]. */
data class FinancialYear(val startYear: Int) {
    val start: LocalDate get() = LocalDate.of(startYear, 4, 1)
    val endInclusive: LocalDate get() = LocalDate.of(startYear + 1, 3, 31)
    val label: String get() = "FY $startYear-${(startYear + 1) % 100}"

    companion object {
        fun of(d: LocalDate) = FinancialYear(if (d.monthValue >= 4) d.year else d.year - 1)
    }
}

data class SectionTotal(val section: TaxSection, val totalPaise: Long, val claimablePaise: Long, val transactionIds: List<Long>)

/**
 * Suggests which payments may count toward a tax deduction, from the payee's name. For the person's records only:
 * the rules are a starting point, any payment can be tagged or untagged by hand, and nothing here is tax advice.
 */
object TaxTagger {
    private fun rx(p: String) = Regex(p, RegexOption.IGNORE_CASE)

    /** Order matters: the more specific rule first ("education loan interest" before anything with "loan"). */
    private val rules: List<Pair<TaxSection, Regex>> = listOf(
        TaxSection.S80CCD1B to rx("""\bnps\b|national pension"""),
        TaxSection.S80D to rx("""health insurance|mediclaim|star health|care health|niva bupa|max bupa|hdfc ergo|icici lombard|aditya birla health|preventive health"""),
        TaxSection.S80E to rx("""education loan"""),
        TaxSection.S24B to rx("""home loan interest|housing loan interest"""),
        TaxSection.S80C to rx("""\blic\b|life insurance|\bppf\b|public provident|\belss\b|tax ?saver|\bepf\b|\bvpf\b|sukanya|\bssy\b|\bnsc\b|tuition|school fee|\bulip\b|home loan principal"""),
        TaxSection.S80G to rx("""donation|charity|pm cares|\bngo\b|giveindia|\bcry\b"""),
        TaxSection.HRA to rx("""\brent\b|house rent|nobroker"""),
    )

    fun suggest(t: Transaction): TaxSection? {
        if (t.type != TransactionType.DEBIT || t.needsReview) return null
        if (t.flow != Flow.EXPENSE && t.flow != Flow.INVESTMENT && t.flow != Flow.TRANSFER) return null
        val text = t.merchant + " " + (t.note ?: "")
        return rules.firstOrNull { it.second.containsMatchIn(text) }?.first
    }

    /** Totals per section for [fy]. [manual] holds the person's own tags by transaction id; a null value means "not a deduction". */
    fun summary(txns: List<Transaction>, fy: FinancialYear, manual: Map<Long, TaxSection?>, zone: ZoneId = ZoneId.systemDefault()): List<SectionTotal> {
        val inYear = txns.filter { Instant.ofEpochMilli(it.timestamp).atZone(zone).toLocalDate().let { d -> !d.isBefore(fy.start) && !d.isAfter(fy.endInclusive) } }
        val tagged = inYear.mapNotNull { t -> (if (manual.containsKey(t.id)) manual[t.id] else suggest(t))?.let { it to t } }
        return TaxSection.entries.mapNotNull { s ->
            val list = tagged.filter { it.first == s }.map { it.second }.sortedBy { it.timestamp }
            if (list.isEmpty()) null else {
                val total = list.sumOf { it.amountPaise }
                SectionTotal(s, total, s.limitPaise?.let { minOf(total, it) } ?: total, list.map { it.id })
            }
        }
    }
}
