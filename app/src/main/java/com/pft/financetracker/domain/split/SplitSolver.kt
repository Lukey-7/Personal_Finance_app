package com.pft.financetracker.domain.split

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType
import kotlin.math.abs
import kotlin.math.roundToLong

/**
 * A transaction as split intelligence sees it: the bank amount and meaning *before* any automatic split changed
 * it, so the solver always starts from the raw facts and can be re-run at any time.
 */
data class SplitTx(
    val id: Long,
    val amountPaise: Long,
    val type: TransactionType,
    val timestamp: Long,
    /** Counterparty as shown to the user ("SBOW", "Rahul Sharma"). */
    val merchant: String,
    val category: Category,
    /** Credits: true when the sender is a person (the only possible payback). Debits: true when paid to a person. */
    val fromPerson: Boolean,
)

data class Allocation(val txId: Long, val paise: Long)

/**
 * "This payment was shared": the payment, which incoming transfers belong to it and how much of each, how many
 * people shared it, and why the app believes it.
 */
data class SplitProposal(
    val paymentId: Long,
    val kind: SplitKind,
    val allocations: List<Allocation>,
    /** Estimated share per head, when the amounts reveal it. */
    val sharePaise: Long?,
    /** Estimated number of people including me, when the amounts reveal it. */
    val people: Int?,
    /** 0-100. */
    val confidence: Int,
    val reasons: List<String>,
    val source: SplitSource,
) {
    val allocatedPaise: Long get() = allocations.sumOf { it.paise }
    val level: Level get() = Level.of(confidence)

    enum class Level {
        HIGH, MEDIUM, LOW;
        companion object {
            fun of(c: Int) = when { c >= SplitSolver.HIGH_AT -> HIGH; c >= SplitSolver.MEDIUM_AT -> MEDIUM; else -> LOW }
        }
    }

    /** Same payment, same transfers, same amounts: two answers that agree. */
    fun sameAnswer(o: SplitProposal): Boolean =
        paymentId == o.paymentId && kind == o.kind &&
            allocations.sortedBy { it.txId }.map { it.txId to it.paise } == o.allocations.sortedBy { it.txId }.map { it.txId to it.paise }
}

/**
 * The local (no AI) split solver. It recognises the fingerprint a group payment leaves: one outgoing payment,
 * then incoming transfers from people, of about the bill divided by the number of people, within a window.
 *
 * It handles several shared payments in the same days (a dinner and the cab home), transfers that pay for two of
 * them at once ("Rs 1,200 = Rs 1,000 dinner + Rs 200 cab"), a share paid in two parts, rounded shares ("Rs 1,030"
 * for Rs 1,028.33) and friends who have not paid yet. Every transfer is used at most once and always in full.
 */
object SplitSolver {
    const val HIGH_AT = 80
    const val MEDIUM_AT = 55
    const val DAY = 86_400_000L
    const val DEFAULT_WINDOW_DAYS = 14

    private const val MAX_PEOPLE = 60

    fun solve(txns: List<SplitTx>, windowDays: Int = DEFAULT_WINDOW_DAYS): List<SplitProposal> {
        val window = windowDays * DAY
        val payments = txns.filter { it.type == TransactionType.DEBIT && it.amountPaise > 0 }.sortedBy { it.timestamp }
        val credits = txns.filter { it.type == TransactionType.CREDIT && it.fromPerson && it.amountPaise > 0 }.sortedBy { it.timestamp }
        if (payments.isEmpty() || credits.isEmpty()) return emptyList()

        val paybacks = assign(payments, credits, window, SplitKind.PAYBACK)
        val used = paybacks.values.flatten().map { it.txId }.toSet()
        val advances = assign(payments.filter { it.id !in paybacks.keys }, credits.filter { it.id !in used }, window, SplitKind.ADVANCE)

        val overlapping = overlaps(payments, credits, window)
        return (paybacks.map { (pid, alloc) -> build(payments.first { it.id == pid }, alloc, credits, SplitKind.PAYBACK, overlapping) } +
            advances.map { (pid, alloc) -> build(payments.first { it.id == pid }, alloc, credits, SplitKind.ADVANCE, overlapping) })
            .filter { it.allocations.isNotEmpty() }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Share estimation: which "bill / k" do the transfers point at?
    // ---------------------------------------------------------------------------------------------------------

    internal data class Share(val k: Int, val paise: Long, val support: Int)

    /** Transfers a person could have sent for [p]: after it (payback) or before it (advance), inside the window, smaller than it. */
    internal fun candidates(p: SplitTx, credits: List<SplitTx>, window: Long, kind: SplitKind) = credits.filter { c ->
        c.amountPaise < p.amountPaise && when (kind) {
            SplitKind.PAYBACK -> c.timestamp > p.timestamp && c.timestamp - p.timestamp <= window
            SplitKind.ADVANCE -> c.timestamp < p.timestamp && p.timestamp - c.timestamp <= window
        }
    }

    /** Does [amount] look like one person's share of [bill] split [k] ways, allowing rounding friends do? */
    internal fun fits(amount: Long, bill: Long, k: Int): Boolean {
        val exact = bill.toDouble() / k
        val tol = maxOf(100.0, exact * 0.015) // Rs 1, or 1.5% for odd splits
        if (abs(amount - exact) <= tol) return true
        // Friends round shares: Rs 1,028.33 is paid as Rs 1,030 or Rs 1,050 or Rs 1,100 (up to the next 10/50/100).
        // Only for shares big enough that the rounding is a small part of them (Rs 100 steps for shares of Rs 1,000+).
        for (unit in longArrayOf(1_000, 5_000, 10_000)) {
            if (exact < unit * 10) continue
            val up = kotlin.math.ceil(exact / unit).toLong() * unit
            val near = (exact / unit).roundToLong() * unit
            if (amount == up || amount == near) return true
        }
        return false
    }

    /** The best "bill / k" for this payment given the candidate transfers, or null when none fits. */
    internal fun estimateShare(p: SplitTx, cands: List<SplitTx>): Share? {
        if (cands.isEmpty()) return null
        // A sender supports "k people" when one transfer, or all their transfers together (a share paid in two
        // parts), is one share.
        val bySender = cands.groupBy { key(it) }.values
        var best: Share? = null
        for (k in 2..MAX_PEOPLE) {
            val share = (p.amountPaise.toDouble() / k).roundToLong()
            if (share < 1_000) break // under Rs 10 a head is noise
            val support = bySender.count { ts -> ts.any { fits(it.amountPaise, p.amountPaise, k) } || (ts.size > 1 && fits(ts.sumOf { it.amountPaise }, p.amountPaise, k)) }
            if (support == 0 || support > k - 1) continue
            val b = best
            // More supporting senders wins; on a tie prefer the smaller group (Rs 600 / 2 = 300 beats / 4 = 150 only
            // if more people actually sent 150).
            if (b == null || support > b.support) best = Share(k, share, support)
        }
        return best
    }

    private fun key(t: SplitTx) = PayerClassifier.partyTokens(t.merchant).sorted().joinToString(" ").ifBlank { "#${t.id}" }

    // ---------------------------------------------------------------------------------------------------------
    // Assignment: each transfer to at most one payment (or split in full between several), in full.
    // ---------------------------------------------------------------------------------------------------------

    private class Open(val p: SplitTx, val share: Share, val cands: Set<Long>, val inWindow: Set<Long>) {
        val alloc = mutableListOf<Allocation>()
        val paidBy = mutableSetOf<String>()
        val slots get() = share.k - 1 - paidBy.size
        /** How strongly the transfers point at this payment: supporting senders, then how full the group is. */
        val strength get() = share.support * 1000 + (share.support * 1000) / maxOf(1, share.k - 1)
    }

    private fun assign(payments: List<SplitTx>, credits: List<SplitTx>, window: Long, kind: SplitKind): Map<Long, List<Allocation>> {
        val open = payments.mapNotNull { p ->
            val cands = candidates(p, credits, window, kind)
            val timely = credits.filter { c -> when (kind) {
                SplitKind.PAYBACK -> c.timestamp > p.timestamp && c.timestamp - p.timestamp <= window
                SplitKind.ADVANCE -> c.timestamp < p.timestamp && p.timestamp - c.timestamp <= window
            } }.map { it.id }.toSet()
            estimateShare(p, cands)?.let { Open(p, it, cands.map { c -> c.id }.toSet(), timely) }
        }
        if (open.isEmpty()) return emptyMap()
        val left = credits.filter { c -> open.any { c.id in it.inWindow } }.toMutableList()

        fun give(o: Open, c: SplitTx, paise: Long) { o.alloc += Allocation(c.id, paise); o.paidBy += key(c) }

        // 1. Transfers that are exactly one share. When several payments could take it, the one the transfers point
        //    at most strongly wins (the dinner 11 friends paid back beats a grocery run nobody else paid for), then the
        //    closest in time. Never twice from the same person for the same payment.
        for (c in left.toList()) {
            val target = open.filter { c.id in it.cands && it.slots > 0 && key(c) !in it.paidBy && fits(c.amountPaise, it.p.amountPaise, it.share.k) }
                .sortedWith(compareByDescending<Open> { it.strength }.thenBy { abs(c.timestamp - it.p.timestamp) })
                .firstOrNull() ?: continue
            give(target, c, c.amountPaise); left -= c
        }

        // 2. One transfer covering shares of two or three payments ("Rs 1,200 = dinner Rs 1,000 + cab Rs 200"). Prefer
        //    payments other friends already paid back, so a coincidental sum with an unrelated payment loses.
        for (c in left.toList()) {
            val fitsIn = open.filter { c.id in it.inWindow && it.slots > 0 && key(c) !in it.paidBy }
            val combo = combos(fitsIn).filter { set -> near(c.amountPaise, set.sumOf { it.share.paise }, set.size) }
                .maxWithOrNull(compareBy<List<Open>> { set -> set.count { it.alloc.isNotEmpty() } }.thenBy { set -> set.sumOf { it.strength } }) ?: continue
            var rest = c.amountPaise
            combo.sortedBy { it.p.timestamp }.forEachIndexed { i, o ->
                val part = if (i == combo.size - 1) rest else o.share.paise
                give(o, c, part); rest -= part
            }
            left -= c
        }

        // 3. One share paid in two parts by the same person (Rs 500 now, Rs 500 later).
        for (o in open) {
            if (o.slots <= 0) continue
            val byPerson = left.filter { it.id in o.cands && key(it) !in o.paidBy }.groupBy { key(it) }
            for ((_, parts) in byPerson) {
                if (o.slots <= 0) break
                if (parts.size < 2) continue
                val pair = parts.sortedBy { it.timestamp }.take(3).let { ps ->
                    (2..ps.size).asSequence().map { ps.take(it) }.firstOrNull { near(it.sumOf { x -> x.amountPaise }, o.share.paise, 1) }
                } ?: continue
                pair.forEach { give(o, it, it.amountPaise); left -= it }
            }
        }

        // The per-payment total can never exceed the payment.
        return open.filter { it.alloc.isNotEmpty() && it.alloc.sumOf { a -> a.paise } <= it.p.amountPaise }
            .associate { it.p.id to it.alloc.toList() }
    }

    private fun combos(os: List<Open>): List<List<Open>> {
        val out = mutableListOf<List<Open>>()
        for (i in os.indices) for (j in i + 1 until os.size) {
            out += listOf(os[i], os[j])
            for (k in j + 1 until os.size) out += listOf(os[i], os[j], os[k])
        }
        return out
    }

    private fun near(amount: Long, target: Long, parts: Int) = abs(amount - target) <= 100L * parts

    /** Transfers that fit the share of more than one payment: the assignment there is a judgement call. */
    private fun overlaps(payments: List<SplitTx>, credits: List<SplitTx>, window: Long): Set<Long> {
        val hits = mutableMapOf<Long, Int>()
        for (p in payments) {
            val cands = candidates(p, credits, window, SplitKind.PAYBACK)
            val share = estimateShare(p, cands) ?: continue
            cands.filter { fits(it.amountPaise, p.amountPaise, share.k) }.forEach { hits[it.id] = (hits[it.id] ?: 0) + 1 }
        }
        return hits.filterValues { it > 1 }.keys
    }

    // ---------------------------------------------------------------------------------------------------------
    // Confidence and the reasons shown to the user.
    // ---------------------------------------------------------------------------------------------------------

    private fun build(p: SplitTx, alloc: List<Allocation>, credits: List<SplitTx>, kind: SplitKind, overlapping: Set<Long>): SplitProposal {
        val byId = credits.associateBy { it.id }
        val senders = alloc.mapNotNull { byId[it.txId] }.distinctBy { key(it) }
        val n = senders.size
        val cands = candidates(p, credits, Long.MAX_VALUE / 4, kind)
        val share = estimateShare(p, cands.filter { c -> alloc.any { it.txId == c.id } }) ?: estimateShare(p, cands)
        val k = share?.k
        val combined = alloc.count { a -> byId[a.txId]?.let { it.amountPaise != a.paise } == true }
        val spanDays = alloc.mapNotNull { byId[it.txId]?.timestamp }.maxOfOrNull { abs(it - p.timestamp) }?.let { it / DAY } ?: 0

        var c = 40
        c += when { n >= 3 -> 35; n == 2 -> 25; else -> 10 }
        if (spanDays <= 3) c += 10
        c += when (p.category) {
            Category.FOOD, Category.TRANSPORT, Category.ENTERTAINMENT -> 10
            Category.BILLS, Category.HEALTH, Category.EDUCATION, Category.INVESTMENT, Category.ATM, Category.TRANSFER -> -10
            else -> 0
        }
        if (p.fromPerson) c -= 5
        if (alloc.any { it.txId in overlapping }) c -= 15
        c -= minOf(10, 5 * minOf(2, combined))
        if (k != null && n + 1 == k) c += 5
        c = c.coerceIn(0, 100)

        val mine = p.amountPaise - alloc.sumOf { it.paise }
        val reasons = buildList {
            if (share != null) add("${rupees(p.amountPaise)} ÷ ${share.k} people ≈ ${rupees(share.paise)} a head")
            val who = senders.map { it.merchant }
            when (kind) {
                SplitKind.PAYBACK -> add("${plural(n, "person", "people")} paid you back within ${if (spanDays == 0L) "the same day" else plural(spanDays.toInt(), "day", "days")}: ${who.take(4).joinToString(", ")}${if (who.size > 4) " and ${who.size - 4} more" else ""}")
                SplitKind.ADVANCE -> add("${plural(n, "person", "people")} sent you money before you paid: ${who.take(4).joinToString(", ")}${if (who.size > 4) " and ${who.size - 4} more" else ""}")
            }
            alloc.groupBy { it.txId }.filter { (id, parts) -> parts.size == 1 && byId[id]?.amountPaise != parts[0].paise }.forEach { (id, parts) ->
                byId[id]?.let { t -> add("${t.merchant}'s ${rupees(t.amountPaise)} also covers another shared payment; ${rupees(parts[0].paise)} of it is for this one") }
            }
            if (share != null && n + 1 < share.k) add("${plural(share.k - 1 - n, "person hasn't", "people haven't")} paid yet (about ${rupees((share.k - 1 - n) * share.paise)})")
            add("Your share so far: ${rupees(mine)}")
        }
        return SplitProposal(p.id, kind, alloc, share?.paise, k, c, reasons, SplitSource.AUTO_LOCAL)
    }

    fun rupees(paise: Long): String {
        val r = paise / 100
        val p = paise % 100
        val s = java.text.NumberFormat.getIntegerInstance(java.util.Locale("en", "IN")).format(r)
        return "₹$s" + if (p != 0L) ".%02d".format(p) else ""
    }

    private fun plural(n: Int, one: String, many: String) = "$n ${if (n == 1) one else many}"
}
