package com.pft.financetracker.domain.split

import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.TransactionType
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/**
 * The hard rules every split answer must satisfy before it can touch the user's numbers, whether it came from the
 * local solver or from an AI model. An AI answer that breaks any of them is thrown away: it can never be applied.
 */
object SplitVerifier {
    /** Problems with one proposal on its own; empty when it is possible. */
    fun problems(p: SplitProposal, byId: Map<Long, SplitTx>, windowDays: Int = SplitSolver.DEFAULT_WINDOW_DAYS): List<String> {
        val out = mutableListOf<String>()
        val pay = byId[p.paymentId]
        if (pay == null || pay.type != TransactionType.DEBIT) return listOf("payment ${p.paymentId} is not an outgoing payment")
        if (p.allocations.isEmpty()) out += "no transfers"
        if (p.allocations.map { it.txId }.toSet().size != p.allocations.size) out += "a transfer is used twice"
        val window = windowDays * SplitSolver.DAY
        for (a in p.allocations) {
            val c = byId[a.txId]
            when {
                c == null -> out += "unknown transfer ${a.txId}"
                c.type != TransactionType.CREDIT -> out += "${a.txId} is not incoming money"
                !c.fromPerson -> out += "${a.txId} is not from a person"
                a.paise <= 0 || a.paise > c.amountPaise -> out += "${a.txId} allocation ${a.paise} outside 1..${c.amountPaise}"
                p.kind == SplitKind.PAYBACK && (c.timestamp <= pay.timestamp || c.timestamp - pay.timestamp > window) -> out += "${a.txId} is not within ${windowDays} days after the payment"
                p.kind == SplitKind.ADVANCE && (c.timestamp >= pay.timestamp || pay.timestamp - c.timestamp > window) -> out += "${a.txId} is not within ${windowDays} days before the payment"
            }
        }
        if (p.allocatedPaise > pay.amountPaise) out += "friends' shares add up to more than the payment"
        return out
    }

    /**
     * The proposals that can all be applied together: each individually possible, no transfer used beyond its amount,
     * and every transfer that is used accounted for in full (a Rs 1,200 transfer cannot count Rs 1,000 as a
     * settlement and leave Rs 200 hanging). Higher confidence wins a conflict.
     */
    fun consistent(proposals: List<SplitProposal>, byId: Map<Long, SplitTx>, windowDays: Int = SplitSolver.DEFAULT_WINDOW_DAYS): List<SplitProposal> {
        val accepted = mutableListOf<SplitProposal>()
        val used = mutableMapOf<Long, Long>()
        val paymentsTaken = mutableSetOf<Long>()
        for (p in proposals.sortedByDescending { it.confidence }) {
            if (p.paymentId in paymentsTaken) continue
            if (problems(p, byId, windowDays).isNotEmpty()) continue
            if (p.allocations.any { a -> (used[a.txId] ?: 0) + a.paise > (byId[a.txId]?.amountPaise ?: 0) }) continue
            accepted += p
            paymentsTaken += p.paymentId
            p.allocations.forEach { a -> used[a.txId] = (used[a.txId] ?: 0) + a.paise }
        }
        // Drop proposals that leave a transfer half-used, until nothing changes.
        while (true) {
            val partial = used.filter { (id, sum) -> sum != byId[id]?.amountPaise }.keys
            if (partial.isEmpty()) break
            val drop = accepted.filter { p -> p.allocations.any { it.txId in partial } }
            if (drop.isEmpty()) break
            accepted.removeAll(drop)
            drop.flatMap { it.allocations }.forEach { a -> used[a.txId] = (used[a.txId] ?: 0) - a.paise }
            used.entries.removeIf { it.value == 0L }
        }
        return accepted
    }
}

/** What happens to a proposal: applied straight away, or shown to the user as a suggestion. */
data class SplitDecision(val proposal: SplitProposal, val auto: Boolean)

/**
 * Combines the local solver and the AI judge (see docs/PLAN-v1.2-split-intelligence.md, M5):
 *  - Agreement between the two applies automatically.
 *  - One confident answer the other has no view on applies automatically; a merely likely one is suggested.
 *  - Disagreement, or AI saying "not a split" where the solver found one, is suggested, never applied.
 *  - Advance collection (money received before paying) is always a suggestion.
 * Without AI, the solver's high-confidence answers apply and its medium ones are suggested.
 */
object SplitDecider {
    fun decide(
        local: List<SplitProposal>,
        ai: List<SplitProposal>?,
        askedAi: Set<Long>,
        byId: Map<Long, SplitTx>,
        windowDays: Int = SplitSolver.DEFAULT_WINDOW_DAYS,
    ): List<SplitDecision> {
        val localBy = local.associateBy { it.paymentId }
        val aiBy = ai.orEmpty().filter { SplitVerifier.problems(it, byId, windowDays).isEmpty() }.associateBy { it.paymentId }
        val chosen = mutableListOf<Pair<SplitProposal, Boolean>>()
        for (pid in (localBy.keys + aiBy.keys)) {
            val l = localBy[pid]
            val a = aiBy[pid]
            val viaAi = ai != null && pid in askedAi
            val pick: Pair<SplitProposal, Boolean>? = when {
                !viaAi -> l?.let { it to (it.level == SplitProposal.Level.HIGH) }?.takeIf { it.first.level != SplitProposal.Level.LOW }
                l != null && a != null && l.sameAnswer(a) -> {
                    val best = if (a.confidence >= l.confidence) a else l
                    val merged = best.copy(confidence = maxOf(l.confidence, a.confidence), reasons = (l.reasons + a.reasons.map { "AI: $it" }).distinct())
                    merged to (maxOf(l.confidence, a.confidence) >= SplitSolver.MEDIUM_AT)
                }
                l == null && a != null -> when (a.level) {
                    SplitProposal.Level.HIGH -> a to true
                    SplitProposal.Level.MEDIUM -> a to false
                    SplitProposal.Level.LOW -> null
                }
                l != null && a != null -> (if (a.confidence >= l.confidence) a else l) to false
                l != null -> (l to false).takeIf { l.level != SplitProposal.Level.LOW }
                else -> null
            }
            if (pick != null) chosen += pick.first to (pick.second && pick.first.kind == SplitKind.PAYBACK)
        }
        val ok = SplitVerifier.consistent(chosen.map { it.first }, byId, windowDays).toSet()
        return chosen.filter { it.first in ok }.map { SplitDecision(it.first, it.second) }
    }
}

/**
 * The anonymised request sent to an AI model, and the parser for its answer. Only amounts, relative days and times,
 * categories and labels travel: people become "Person A", "Person B"; payments are described by type
 * ("restaurant, food", "cab, travel"). Real names, account numbers and message text stay on the phone.
 */
class SplitAiRequest private constructor(
    val json: String,
    private val paymentIds: Map<String, Long>,
    private val incomingIds: Map<String, Long>,
    private val personLabels: Map<String, String>,
) {
    /** Payments this request asked about (their ids in the app). */
    val payments: Set<Long> get() = paymentIds.values.toSet()

    /** Parse the model's JSON answer into proposals. Anything malformed is skipped, never guessed. */
    fun parse(answer: String): List<SplitProposal>? {
        val root = runCatching { JSONObject(answer.trim().removePrefix("```json").removePrefix("```").removeSuffix("```")) }.getOrNull() ?: return null
        val groups = root.optJSONArray("groups") ?: return emptyList()
        val out = mutableListOf<SplitProposal>()
        for (i in 0 until groups.length()) {
            val g = groups.optJSONObject(i) ?: continue
            val pid = paymentIds[g.optString("payment")] ?: continue
            val kind = if (g.optString("kind").equals("advance", true)) SplitKind.ADVANCE else SplitKind.PAYBACK
            val arr = g.optJSONArray("allocations") ?: continue
            val alloc = (0 until arr.length()).mapNotNull { j ->
                val a = arr.optJSONObject(j) ?: return@mapNotNull null
                val tid = incomingIds[a.optString("incoming")] ?: return@mapNotNull null
                val rupees = a.optDouble("amount", Double.NaN)
                if (rupees.isNaN() || rupees <= 0) null else Allocation(tid, Math.round(rupees * 100))
            }
            if (alloc.isEmpty()) continue
            val people = g.optInt("people", 0).takeIf { it >= 2 }
            val conf = g.optInt("confidence", 0).coerceIn(0, 100)
            val reason = g.optString("reason").takeIf { it.isNotBlank() }?.let { r -> personLabels.entries.fold(r) { acc, (label, name) -> acc.replace(label, name) } }
            out += SplitProposal(pid, kind, alloc, null, people, conf, listOfNotNull(reason), SplitSource.AUTO_AI)
        }
        return out
    }

    companion object {
        /** Build the request for one cluster of related payments and incoming transfers. */
        fun build(payments: List<SplitTx>, incoming: List<SplitTx>): SplitAiRequest {
            val start = (payments + incoming).minOf { it.timestamp }
            val day0 = Calendar.getInstance().apply { timeInMillis = start; set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0) }.timeInMillis
            fun day(t: Long) = ((t - day0) / SplitSolver.DAY).toInt()
            fun clock(t: Long) = Calendar.getInstance().apply { timeInMillis = t }.let { "%02d:%02d".format(it.get(Calendar.HOUR_OF_DAY), it.get(Calendar.MINUTE)) }

            val pIds = mutableMapOf<String, Long>()
            val iIds = mutableMapOf<String, Long>()
            val labels = mutableMapOf<String, String>() // party key -> "Person A"
            val labelNames = mutableMapOf<String, String>() // "Person A" -> real name (for reasons shown locally)
            fun label(t: SplitTx): String {
                val k = PayerClassifier.partyTokens(t.merchant).sorted().joinToString(" ").ifBlank { "#${t.id}" }
                return labels.getOrPut(k) {
                    val l = "Person ${personName(labels.size)}"
                    labelNames[l] = t.merchant; l
                }
            }
            val root = JSONObject()
            root.put("currency", "INR")
            root.put("payments", JSONArray().apply {
                payments.sortedBy { it.timestamp }.forEachIndexed { i, p ->
                    val id = "P${i + 1}"; pIds[id] = p.id
                    put(JSONObject().put("id", id).put("amount", p.amountPaise / 100.0).put("day", day(p.timestamp)).put("time", clock(p.timestamp)).put("type", typeOf(p)))
                }
            })
            root.put("incoming", JSONArray().apply {
                incoming.sortedBy { it.timestamp }.forEachIndexed { i, c ->
                    val id = "C${i + 1}"; iIds[id] = c.id
                    put(JSONObject().put("id", id).put("amount", c.amountPaise / 100.0).put("day", day(c.timestamp)).put("time", clock(c.timestamp)).put("from", label(c)))
                }
            })
            return SplitAiRequest(root.toString(), pIds, iIds, labelNames)
        }

        private fun personName(i: Int): String = if (i < 26) ('A' + i).toString() else "${('A' + i / 26 - 1)}${('A' + i % 26)}"

        private fun typeOf(p: SplitTx): String = if (p.fromPerson) "paid to an individual" else when (p.category) {
            Category.FOOD -> "restaurant, food"
            Category.TRANSPORT -> "cab, travel"
            Category.ENTERTAINMENT -> "entertainment, tickets"
            Category.SHOPPING -> "shopping"
            Category.BILLS -> "bills, rent, utilities"
            Category.HEALTH -> "health"
            Category.EDUCATION -> "education"
            Category.INVESTMENT -> "investment"
            Category.ATM -> "cash withdrawal"
            Category.TRANSFER -> "transfer"
            else -> "other"
        }

        /** Instructions for the model. The answer format is strict JSON; the app verifies every number it returns. */
        const val SYSTEM_PROMPT = """You help a personal-finance app in India find GROUP PAYMENTS. The user sometimes pays a whole bill for friends (a dinner, a cab, tickets) and the friends then send their share back by UPI. Only the user's own share is their real expense.

You get the user's outgoing "payments" and the incoming transfers from individual people ("incoming") over a few weeks. People are anonymised as "Person A", "Person B". Days are counted from day 0; "time" is the clock time.

Find which incoming transfers are friends paying back a share of which payment. Rules:
- A payback comes AFTER the payment, within 14 days. Kind "payback".
- Friends may also send money BEFORE the user pays (collecting for a trip). Kind "advance". Only report advances when several people sent similar amounts shortly before a larger payment.
- One transfer may cover shares of two payments (e.g. 1200 = 1000 for dinner + 200 for the cab); split its amount across them. Every transfer you use must be used in FULL across your groups.
- Shares are usually the bill divided by the number of people, often rounded (1028.33 paid as 1030 or 1000). Uneven shares happen too.
- The shares you allocate to one payment must not exceed that payment.
- Salary, refunds and unrelated transfers are NOT paybacks. A single transfer that merely happens to be half of an unrelated shopping bill is weak evidence; give it low confidence.
- If nothing is a group payment, return an empty list. Do not invent.

Answer ONLY with JSON: {"groups":[{"payment":"P1","kind":"payback","allocations":[{"incoming":"C1","amount":1000}],"people":12,"confidence":0-100,"reason":"short reason using the Person labels"}]}"""
    }
}

/** Anything that can judge a split request: OpenAI today, other providers or an on-device model later. */
interface SplitAiProvider {
    /** The model's raw JSON answer for [request], or null when it could not be asked (no key, offline, error). */
    suspend fun judge(request: SplitAiRequest): String?
}
