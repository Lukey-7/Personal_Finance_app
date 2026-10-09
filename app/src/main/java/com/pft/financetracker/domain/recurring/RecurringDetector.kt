package com.pft.financetracker.domain.recurring

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import java.time.Instant
import java.time.ZoneId
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * How often a charge repeats. [days] is one cycle (used to judge gaps); [months], when set, steps the next expected
 * date by calendar months so it does not drift; [tolerance] is how far a real charge may drift from the rhythm.
 */
enum class Period(val days: Int, val tolerance: Int, val label: String, val months: Int = 0) {
    WEEKLY(7, 2, "Weekly"),
    FORTNIGHTLY(14, 3, "Every 2 weeks"),
    MONTHLY(30, 4, "Monthly", 1),
    QUARTERLY(91, 10, "Every 3 months", 3),
    HALF_YEARLY(182, 15, "Every 6 months", 6),
    YEARLY(365, 20, "Yearly", 12),
    /** An AutoPay seen once: the rhythm is not known until it charges again. */
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
    /** An AutoPay seen once has no known rhythm, so it adds nothing to the totals until it charges again. */
    val monthlyPaise: Long get() = when (period) {
        Period.WEEKLY -> amountPaise * 52 / 12
        Period.FORTNIGHTLY -> amountPaise * 26 / 12
        Period.MONTHLY -> amountPaise
        Period.QUARTERLY -> amountPaise / 3
        Period.HALF_YEARLY -> amountPaise / 6
        Period.YEARLY -> amountPaise / 12
        Period.UNKNOWN -> 0
    }
    val yearlyPaise: Long get() = when (period) {
        Period.WEEKLY -> amountPaise * 52
        Period.FORTNIGHTLY -> amountPaise * 26
        Period.MONTHLY -> amountPaise * 12
        Period.QUARTERLY -> amountPaise * 4
        Period.HALF_YEARLY -> amountPaise * 2
        Period.YEARLY -> amountPaise
        Period.UNKNOWN -> 0
    }

    /** The rhythm is known, so the item has a cost; an AutoPay seen once is waiting for its second charge. */
    val rhythmKnown: Boolean get() = period != Period.UNKNOWN

    /** Still active, but the next charge was expected before [now] and has not been seen yet. */
    fun isLate(now: Long): Boolean = active && nextExpectedAt != null && now > nextExpectedAt
}

/**
 * Finds subscriptions and other repeating charges, on the phone, from spending alone: payments to the same company at
 * a steady amount and a steady rhythm (weekly to yearly, with an occasional skipped cycle allowed).
 *
 * Precision first. Payments to a person, unnamed "Payment (Bank)" rows, payment-app and gateway handles, loan EMIs,
 * insurance and rent are left out (those belong in Bills & EMIs). Within one merchant, the best repeating series is
 * picked out by price level, so an extra purchase from the same company does not hide the subscription, and a price
 * change that sticks keeps the series together. A charge marked AutoPay / mandate / NACH is listed from the first one,
 * but costs nothing in the totals until a second charge shows its rhythm.
 */
object RecurringDetector {
    private const val DAY = 86_400_000L
    /** Charges within 10% of each other are one price level (covers FX-billed services and small tax wobbles). */
    private const val LEVEL = 0.10
    /** Two charges are only a series when their amounts agree this closely, unless the brand or an AutoPay vouches. */
    private const val PAIR_TIGHT = 0.03
    /** A single AutoPay charge stays listed (waiting for its second charge) this long. */
    private const val AUTOPAY_WAIT_DAYS = 100L

    private val noise = setOf("upi", "ach", "nach", "si", "autopay", "auto", "mandate", "emandate", "e", "payment", "com", "www", "in",
        "pvt", "ltd", "private", "limited", "india", "the", "dr", "debit", "via", "to",
        // Company-name filler, so "Netflix Entertainment Services India Pvt Ltd" and "NETFLIX.COM" are one service.
        "services", "service", "entertainment", "technologies", "technology", "digital", "media", "online", "payments", "co")
    private val autopayWords = Regex("""\b(?:auto\s?pay|e-?mandate|mandate|nach|ach|standing instruction|si)\b""", RegexOption.IGNORE_CASE)
    /** Loans, insurance and housing costs repeat, but they are bills, not subscriptions. */
    private val billWords = Regex("""\b(?:emi|loans?|insurance|assurance|lic|policy|rent|maintenance|society|finance|finserv)\b""", RegexOption.IGNORE_CASE)
    /** Names the parser uses when the bank gave none: these pool unrelated payments together. */
    private val genericNames = setOf("payment", "payments", "credit", "debit", "upi", "upi payment", "transfer", "fund transfer", "sent", "paid",
        "money sent", "unknown", "merchant", "purchase", "pos", "card payment", "online payment", "na", "others", "other")
    /** Payment apps and gateways: one handle stands for many unrelated shops. */
    private val aggregators = listOf("gpay", "googlepay", "phonepe", "paytm", "bharatpe", "bharatpay", "razorpay", "rzp", "cashfree", "payu",
        "billdesk", "ccavenue", "juspay", "pinelabs", "mswipe", "instamojo", "amazonpay", "amznpay", "mobikwik", "freecharge", "bhim",
        "ezetap", "paynearby", "cred")
    private val notSpendCategories = setOf(Category.TRANSFER, Category.INVESTMENT, Category.ATM, Category.INCOME)
    /** Food, travel and shopping repeat by habit (milk, a commute): they need more evidence than two equal charges. */
    private val everydayCategories = setOf(Category.FOOD, Category.TRANSPORT, Category.SHOPPING)

    /** The same service under its different names on SMS, card and UPI. First match wins, so order matters. */
    private val aliases: List<Pair<Regex, String>> = listOf(
        Regex("""hotstar|^novi$""") to "hotstar",
        Regex("""netflix""") to "netflix",
        Regex("""^(?:amazon|amzn)(?:in|india)?(?:prime|video)|^primevideo""") to "amazonprime",
        Regex("""youtube""") to "youtube",
        Regex("""googleone|googlestorage""") to "googleone",
        Regex("""googleplay|playstore""") to "googleplay",
        Regex("""^spotify""") to "spotify",
        Regex("""^(?:apple|itunes|icloud)""") to "apple",
        Regex("""^(?:microsoft|msft)""") to "microsoft",
        Regex("""^(?:vi|viprepaid|vipostpaid|vilimited)$|^vodafone""") to "vodafoneidea",
        Regex("""^reliancejio|^jio(?:prepaid|postpaid|recharge)?$""") to "jio",
        Regex("""^bhartiairtel|^airtel(?:prepaid|postpaid)?$""") to "airtel",
        Regex("""^(?:sonyliv|culvermax)""") to "sonyliv",
        Regex("""^zee""") to "zee",
        Regex("""^tata(?:play|sky)""") to "tataplay",
        Regex("""^(?:openai|chatgpt)""") to "openai",
        Regex("""^(?:cultfit|curefit|cult$)""") to "cultfit",
        Regex("""^(?:jiosaavn|saavn)""") to "jiosaavn",
        Regex("""^adobe""") to "adobe",
        Regex("""^linkedin""") to "linkedin",
        Regex("""^audible""") to "audible",
    )

    /** The merchant reduced to letters with filler words dropped, before brand aliases are applied. */
    private fun rawKey(merchant: String): String =
        merchant.substringBefore('@').lowercase(Locale.ROOT).replace(Regex("""[^a-z]+"""), " ").split(' ')
            .filter { it.isNotBlank() && it !in noise }.joinToString("")

    private fun brand(raw: String): String? = aliases.firstOrNull { it.first.containsMatchIn(raw) }?.second

    /** The same service under its UPI handle ("netflix.upi@icici"), card name ("NETFLIX.COM") or company name gets one key. */
    fun merchantKey(merchant: String): String {
        val raw = rawKey(merchant)
        return brand(raw) ?: raw.take(16)
    }

    /** The next charge after [lastAt] for [period]: calendar months for monthly and longer plans, so it does not drift. */
    fun nextAfter(lastAt: Long, period: Period, zone: ZoneId = ZoneId.systemDefault()): Long {
        val z = Instant.ofEpochMilli(lastAt).atZone(zone)
        return (if (period.months > 0) z.plusMonths(period.months.toLong()) else z.plusDays(period.days.toLong())).toInstant().toEpochMilli()
    }

    private class Charge(val t: Transaction, val autopay: Boolean)
    private class Fit(val charges: List<Charge>, val period: Period, val onRhythm: Int, val extras: Int, val active: Boolean)

    fun detect(all: List<Transaction>, now: Long = System.currentTimeMillis()): List<RecurringItem> {
        val charges = all.mapNotNull { t -> val ap = isAutopay(t); if (eligible(t, ap)) Charge(t, ap) else null }
        return charges.groupBy { rawKey(it.t.merchant) }
            .filterKeys { raw -> aggregators.none { raw.startsWith(it) } }
            .entries.groupBy({ (raw, _) -> brand(raw) ?: raw.take(16) }, { it.value })
            .mapNotNull { (key, lists) ->
                val known = aliases.any { it.second == key }
                if (!known && key.length < 3) return@mapNotNull null
                judge(key, known, lists.flatten().sortedBy { it.t.timestamp }, now)
            }
            .sortedByDescending { it.monthlyPaise }
    }

    private fun isAutopay(t: Transaction) = autopayWords.containsMatchIn(t.merchant) || autopayWords.containsMatchIn(t.note ?: "")

    private fun eligible(t: Transaction, autopay: Boolean): Boolean {
        if (t.type != TransactionType.DEBIT || t.flow != Flow.EXPENSE || t.needsReview || t.amountPaise < 1_000) return false
        if (t.category in notSpendCategories) return false
        // A maid, a landlord or a friend paid the same amount twice is not a subscription; a mandate to a person can be.
        if (t.counterpartyKind == CounterpartyKind.PERSON && !autopay) return false
        if (isGeneric(t.merchant)) return false
        if (billWords.containsMatchIn(t.merchant) || billWords.containsMatchIn(t.note ?: "")) return false
        return true
    }

    /** "Payment (HDFC Bank)", "UPI", "Transfer": the bank named no one. */
    private fun isGeneric(merchant: String): Boolean {
        val m = merchant.replace(Regex("""\(.*?\)"""), " ").lowercase(Locale.ROOT).replace(Regex("""[^a-z]+"""), " ").trim()
        return m.isEmpty() || m in genericNames
    }

    private fun judge(key: String, known: Boolean, list: List<Charge>, now: Long): RecurringItem? {
        val autopay = list.any { it.autopay }
        val lenient = known || autopay
        val levels = levels(list)
        val candidates = levels.filter { it.size >= 2 } + priceSteps(levels)
        var best: Fit? = null
        for (cand in candidates) for (p in Period.entries) {
            if (p == Period.UNKNOWN) continue
            val f = fit(cand, p, lenient, now) ?: continue
            if (best == null || better(f, best)) best = f
        }
        best?.let { return item(key, it.charges, it.period, autopay, now) }
        if (!autopay) return null
        // An AutoPay whose rhythm is not clear yet (one charge, or charges that fit no plan): list it, cost nothing.
        val lastAutopay = list.last { it.autopay }
        val level = levels.first { lastAutopay in it }.filter { it.autopay }
        return item(key, level, Period.UNKNOWN, autopay = true, now = now)
    }

    /** Charges grouped into price levels: each level spans at most [LEVEL] above its cheapest charge. Each sorted by time. */
    private fun levels(list: List<Charge>): List<List<Charge>> {
        val out = mutableListOf<MutableList<Charge>>()
        for (c in list.sortedBy { it.t.amountPaise }) {
            val cur = out.lastOrNull()
            if (cur != null && c.t.amountPaise <= cur.first().t.amountPaise * (1 + LEVEL)) cur += c else out += mutableListOf(c)
        }
        return out.map { l -> l.sortedBy { it.t.timestamp } }
    }

    /**
     * A price change that stuck: a level of two or more charges, followed in time by a level at a plausible new price
     * (0.7× to 1.6×). Every level but the latest must have repeated, so a one-off purchase cannot pose as a new price.
     */
    private fun priceSteps(levels: List<List<Charge>>): List<List<Charge>> {
        val byStart = levels.sortedBy { it.first().t.timestamp }
        val out = mutableListOf<List<Charge>>()
        for (start in byStart) {
            if (start.size < 2) continue
            val chain = start.toMutableList()
            var steps = 0
            while (true) {
                val lastAt = chain.last().t.timestamp
                val lastAmount = chain.last().t.amountPaise.toDouble()
                val next = byStart
                    .filter { it.first().t.timestamp > lastAt && it.first().t.amountPaise / lastAmount in 0.7..1.6 }
                    .maxWithOrNull(compareBy<List<Charge>> { it.size }.thenByDescending { it.first().t.timestamp }) ?: break
                chain += next
                steps++
                if (next.size < 2) break
            }
            if (steps > 0) out += chain
        }
        return out
    }

    /** 1 when [gapDays] is one cycle of [p], 2 when one cycle was skipped, 0 when it fits neither. */
    private fun cycles(gapDays: Double, p: Period): Int =
        (1..2).firstOrNull { k -> abs(gapDays - k * p.days) <= p.tolerance * k } ?: 0

    /**
     * The longest run of charges in [cand] that keeps the rhythm of [p]. Most gaps must be exactly one cycle (a skipped
     * cycle now and then is fine, so 60-day gaps are never "monthly"), and stray same-price charges inside the run
     * may not outnumber half of it (a daily chai is not a weekly plan).
     */
    private fun fit(cand: List<Charge>, p: Period, lenient: Boolean, now: Long): Fit? {
        val n = cand.size
        if (n < 2) return null
        val len = IntArray(n) { 1 }
        val ones = IntArray(n)
        val prev = IntArray(n) { -1 }
        for (i in 1 until n) for (j in 0 until i) {
            val k = cycles((cand[i].t.timestamp - cand[j].t.timestamp).toDouble() / DAY, p)
            if (k == 0) continue
            val l = len[j] + 1
            val o = ones[j] + if (k == 1) 1 else 0
            if (l > len[i] || (l == len[i] && o > ones[i])) { len[i] = l; ones[i] = o; prev[i] = j }
        }
        var end = 0
        for (i in 1 until n) if (len[i] > len[end] || (len[i] == len[end] && ones[i] >= ones[end])) end = i
        if (len[end] < 2) return null
        val chain = generateSequence(end) { i -> prev[i].takeIf { it >= 0 } }.toList().reversed().map { cand[it] }
        if (ones[end] * 2 < chain.size - 1) return null
        val from = chain.first().t.timestamp
        val to = chain.last().t.timestamp
        val extras = cand.count { it.t.timestamp in from..to } - chain.size
        if (extras * 2 > chain.size) return null

        val category = chain.last().t.category
        if (!lenient && category in everydayCategories && (chain.size < 3 || p == Period.WEEKLY || p == Period.FORTNIGHTLY)) return null
        if (chain.size == 2 && !lenient) {
            val a = chain[0].t.amountPaise
            if (abs(chain[1].t.amountPaise - a).toDouble() / a > PAIR_TIGHT) return null
        }
        return Fit(chain, p, ones[end], extras, isActive(to, p, now))
    }

    /** Active first (a current plan beats an old one), then more charges, then the most recent, then the steadiest. */
    private fun better(a: Fit, b: Fit): Boolean = compareValuesBy(a, b,
        { it.active }, { it.charges.size }, { it.charges.last().t.timestamp }, { it.onRhythm }, { -it.extras }) > 0

    private fun isActive(lastAt: Long, p: Period, now: Long): Boolean =
        if (p == Period.UNKNOWN) now - lastAt <= AUTOPAY_WAIT_DAYS * DAY
        // One missed cycle (at most 45 days for long plans) past the expected date, then it has probably stopped.
        else now <= nextAfter(lastAt, p) + (min(p.days, 45) + p.tolerance) * DAY

    /**
     * The latest price step in the series, if the new price has held since and it happened recently enough to be news
     * (within three cycles, or 100 days).
     */
    private fun priceRise(chain: List<Charge>, p: Period, now: Long): PriceRise? {
        val a = chain.map { it.t.amountPaise }
        val i = (1 until a.size).lastOrNull { a[it] >= a[it - 1] * 1.05 } ?: return null
        if ((i until a.size).any { a[it] < a[i] * 0.97 }) return null
        if (chain[i].t.timestamp < now - max(3L * p.days, 100L) * DAY) return null
        return PriceRise(a[i - 1], a[i])
    }

    private fun item(key: String, chain: List<Charge>, period: Period, autopay: Boolean, now: Long): RecurringItem {
        val last = chain.last().t
        return RecurringItem(
            key = "rec:$key", merchant = last.merchant, category = last.category, period = period, amountPaise = last.amountPaise,
            lastChargeAt = last.timestamp, nextExpectedAt = if (period == Period.UNKNOWN) null else nextAfter(last.timestamp, period),
            transactionIds = chain.map { it.t.id }, active = isActive(last.timestamp, period, now), autopay = autopay,
            priceRise = priceRise(chain, period, now),
        )
    }
}
