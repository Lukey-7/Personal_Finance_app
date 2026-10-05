package com.pft.financetracker.domain.recurring

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import java.util.Locale
import kotlin.math.abs

/** How often a charge repeats. [days] is the step used for the next expected date; [tolerance] how far a real charge may drift. */
enum class Period(val days: Int, val tolerance: Int, val label: String) {
    WEEKLY(7, 2, "Weekly"),
    MONTHLY(30, 4, "Monthly"),
    QUARTERLY(91, 10, "Every 3 months"),
    YEARLY(365, 20, "Yearly"),
    UNKNOWN(0, 0, "AutoPay");
}

data class PriceRise(val fromPaise: Long, val toPaise: Long)

/**
 * A subscription or other repeating charge found in the transactions. [key] is stable across runs (it comes from the
 * merchant), so a person's "not a subscription" or "cancelled" decision sticks to it. Costs are for the latest amount.
 */
data class RecurringItem(
    val key: String,
    val merchant: String,
    val category: Category,
    val period: Period,
    val amountPaise: Long,
    val lastChargeAt: Long,
    val nextExpectedAt: Long?,
    val transactionIds: List<Long>,
    val active: Boolean,
    val autopay: Boolean,
    val priceRise: PriceRise?,
) {
    val monthlyPaise: Long get() = when (period) {
        Period.WEEKLY -> amountPaise * 52 / 12
        Period.MONTHLY, Period.UNKNOWN -> amountPaise
        Period.QUARTERLY -> amountPaise / 3
        Period.YEARLY -> amountPaise / 12
    }
    val yearlyPaise: Long get() = when (period) {
        Period.WEEKLY -> amountPaise * 52
        Period.MONTHLY, Period.UNKNOWN -> amountPaise * 12
        Period.QUARTERLY -> amountPaise * 4
        Period.YEARLY -> amountPaise
    }
}

/**
 * Finds subscriptions and other repeating charges, on the phone, from spending alone: the same merchant, a steady
 * amount and a steady rhythm (weekly, monthly, quarterly or yearly, with one skipped cycle allowed). Two identical
 * charges a cycle apart are enough; a charge marked AutoPay / mandate / NACH is listed from the first one.
 */
object RecurringDetector {
    private const val DAY = 86_400_000L
    private val noise = setOf("upi", "ach", "nach", "si", "autopay", "auto", "mandate", "emandate", "e", "payment", "com", "www", "in",
        "pvt", "ltd", "private", "limited", "india", "the", "dr", "debit", "via", "to",
        // Company-name filler, so "Netflix Entertainment Services India Pvt Ltd" and "NETFLIX.COM" are one service.
        "services", "service", "entertainment", "technologies", "technology", "digital", "media", "online", "payments", "co")
    private val autopayWords = Regex("""\b(?:auto\s?pay|e-?mandate|mandate|nach|ach|standing instruction|si)\b""", RegexOption.IGNORE_CASE)

    /** The same service under its UPI handle ("netflix.upi@icici"), card name ("NETFLIX.COM") or company name gets one key. */
    fun merchantKey(merchant: String): String =
        merchant.substringBefore('@').lowercase(Locale.ROOT).replace(Regex("""[^a-z]+"""), " ").split(' ')
            .filter { it.isNotBlank() && it !in noise }.joinToString("").take(16)

    fun detect(all: List<Transaction>, now: Long = System.currentTimeMillis()): List<RecurringItem> {
        val charges = all.filter { it.type == TransactionType.DEBIT && it.flow == Flow.EXPENSE && !it.needsReview && it.amountPaise >= 1_000 }
        return charges.groupBy { merchantKey(it.merchant) }
            .filterKeys { it.length >= 3 }
            .mapNotNull { (key, list) -> judge(key, list.sortedBy { it.timestamp }, now) }
            .sortedByDescending { it.monthlyPaise }
    }

    private fun judge(key: String, list: List<Transaction>, now: Long): RecurringItem? {
        val last = list.last()
        val autopay = list.any { autopayWords.containsMatchIn(it.merchant) || autopayWords.containsMatchIn(it.note ?: "") }
        if (list.size == 1) {
            return if (autopay) item(key, list, Period.UNKNOWN, autopay = true, now = now) else null
        }

        val amounts = list.map { it.amountPaise }
        val before = amounts.dropLast(1)
        val tightness = if (list.size == 2) 0.02 else 0.15
        val median = before.sorted()[before.size / 2].toDouble()
        if (before.any { abs(it - median) / median > tightness }) return null
        val prev = before.last()
        // The latest charge may have changed price, but not into a different kind of spend.
        if (list.size == 2 && abs(last.amountPaise - prev).toDouble() / prev > 0.02) return null
        if (last.amountPaise > prev * 1.6 || last.amountPaise < prev * 0.7) return null

        val gaps = list.zipWithNext { a, b -> (b.timestamp - a.timestamp).toDouble() / DAY }
        val period = Period.entries.filter { it != Period.UNKNOWN }.firstOrNull { p -> gaps.all { g -> fits(g, p) } } ?: return null
        return item(key, list, period, autopay, now)
    }

    private fun fits(gapDays: Double, p: Period): Boolean = (1..2).any { k -> abs(gapDays - k * p.days) <= p.tolerance * k }

    private fun item(key: String, list: List<Transaction>, period: Period, autopay: Boolean, now: Long): RecurringItem {
        val last = list.last()
        val prev = list.getOrNull(list.size - 2)?.amountPaise
        val rise = prev?.takeIf { last.amountPaise >= it * 1.05 }?.let { PriceRise(it, last.amountPaise) }
        val active = period == Period.UNKNOWN || now - last.timestamp <= (2L * period.days + period.tolerance) * DAY
        return RecurringItem(
            key = "rec:$key", merchant = last.merchant, category = last.category, period = period, amountPaise = last.amountPaise,
            lastChargeAt = last.timestamp, nextExpectedAt = if (period == Period.UNKNOWN) null else last.timestamp + period.days * DAY,
            transactionIds = list.map { it.id }, active = active, autopay = autopay, priceRise = rise,
        )
    }
}
