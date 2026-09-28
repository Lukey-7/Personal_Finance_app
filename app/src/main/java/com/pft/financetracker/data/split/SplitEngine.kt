package com.pft.financetracker.data.split

import com.pft.financetracker.data.local.SplitDao
import com.pft.financetracker.data.local.SplitDecisionEntity
import com.pft.financetracker.data.local.SplitEntity
import com.pft.financetracker.data.local.SplitLinkEntity
import com.pft.financetracker.data.local.SplitPersonEntity
import com.pft.financetracker.data.local.SplitShareEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.split.PayerClassifier
import com.pft.financetracker.domain.split.SplitAiProvider
import com.pft.financetracker.domain.split.SplitAiRequest
import com.pft.financetracker.domain.split.SplitDecider
import com.pft.financetracker.domain.split.SplitDecision
import com.pft.financetracker.domain.split.SplitKind
import com.pft.financetracker.domain.split.SplitMode
import com.pft.financetracker.domain.split.SplitProposal
import com.pft.financetracker.domain.split.SplitSolver
import com.pft.financetracker.domain.split.SplitSource
import com.pft.financetracker.domain.split.SplitStatus
import com.pft.financetracker.domain.split.SplitTx
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Remembers AI answers by request, so an unchanged week is never paid for twice. */
interface AiAnswerCache {
    fun get(requestJson: String): String?
    fun put(requestJson: String, answer: String)
}

/**
 * Split intelligence, wired to the database. Each [run] starts from the raw facts (bank amounts and meanings
 * before any automatic split), finds shared payments with the local solver and, when enabled, the AI judge,
 * verifies every answer, and brings the stored automatic splits in line: new ones are applied or suggested, ones
 * that no longer hold are undone exactly. Manual splits, rows a person edited and splits the user accepted or
 * rejected are never touched.
 */
class SplitEngine(
    private val txDao: TransactionDao,
    private val splitDao: SplitDao,
    private val ai: SplitAiProvider?,
    private val aiEnabled: () -> Boolean,
    private val myName: () -> String,
    private val cache: AiAnswerCache? = null,
    private val windowDays: Int = SplitSolver.DEFAULT_WINDOW_DAYS,
) {
    data class RunResult(val applied: Int, val suggested: Int, val undone: Int, val askedAi: Boolean)

    private val lock = Mutex()

    private object Role { const val PAYMENT = "PAYMENT"; const val PAYBACK = "PAYBACK"; const val ADVANCE = "ADVANCE" }
    private object Decision { const val REJECTED = "REJECTED"; const val ACCEPTED = "ACCEPTED" }

    suspend fun run(useAi: Boolean = true): RunResult = lock.withLock { runLocked(useAi) }

    private suspend fun runLocked(useAi: Boolean): RunResult {
        val txnsAtStart = txDao.getAll().map { it.toDomain() }
        val byTx = txnsAtStart.associateBy { it.id }
        val splits = splitDao.allSplits()
        val links = splitDao.allLinks()
        val decisions = splitDao.decisions().associateBy { it.paymentTransactionId }
        val linksBySplit = links.groupBy { it.splitId }
        val autoSplits = splits.filter { it.source != SplitSource.MANUAL.name }

        var undone = 0
        val aiRun = useAi && ai != null && aiEnabled()
        // Automatic splits whose rows vanished (deleted) are undone; ones the user accepted or whose rows a person
        // edited since are frozen as they are.
        val frozen = mutableSetOf<Long>()
        for (s in autoSplits) {
            val ls = linksBySplit[s.id].orEmpty()
            val payment = s.linkedTransactionId
            if (ls.isEmpty() || ls.any { byTx[it.transactionId] == null }) { undo(s, ls, byTx, links); undone++; continue }
            if (payment != null && decisions[payment]?.decision == Decision.ACCEPTED) frozen += s.id
            else if (ls.any { byTx[it.transactionId]?.userEdited == true }) frozen += s.id
            // Only an AI run can judge what the AI found. A quick local run (after an SMS, an undo) keeps it as it is;
            // found on the emulator, where undoing one split silently undid an AI-found one too.
            else if (!aiRun && s.source == SplitSource.AUTO_AI.name) frozen += s.id
        }
        val txns = if (undone > 0) txDao.getAll().map { it.toDomain() } else txnsAtStart
        val liveAuto = splitDao.allSplits().filter { it.source != SplitSource.MANUAL.name && it.id !in frozen }
        val liveLinks = splitDao.allLinks()
        val appliedLinks = liveLinks.filter { l -> liveAuto.any { it.id == l.splitId && it.status == SplitStatus.APPLIED.name } }

        // Raw view: undo the effect of applied automatic splits on paper.
        val rawAmount = appliedLinks.filter { it.role == Role.PAYMENT }.associate { it.transactionId to (it.prevAmountPaise ?: 0L) }
        val rawFlow = appliedLinks.filter { it.role != Role.PAYMENT }.associate { it.transactionId to (Flow.fromName(it.prevFlow) ?: Flow.INCOME) }

        val manualPayments = splits.filter { it.source == SplitSource.MANUAL.name }.mapNotNull { it.linkedTransactionId }.toSet()
        val manualSplitIds = splits.filter { it.source == SplitSource.MANUAL.name }.map { it.id }.toSet()
        val manualCredits = liveLinks.filter { it.splitId in manualSplitIds }.map { it.transactionId }.toSet()
        val frozenTx = liveLinks.filter { it.splitId in frozen }.map { it.transactionId }.toSet()
        val rejected = decisions.filterValues { it.decision == Decision.REJECTED }.keys

        val view = txns.mapNotNull { t ->
            if (t.id in frozenTx || t.needsReview || t.source == Transaction.Source.SPLIT) return@mapNotNull null
            val amount = rawAmount[t.id] ?: t.amountPaise
            val flow = rawFlow[t.id] ?: t.flow
            when (t.type) {
                TransactionType.DEBIT -> {
                    if (flow != Flow.EXPENSE || t.id in manualPayments || t.id in rejected || (t.userEdited && t.id !in rawAmount)) return@mapNotNull null
                    SplitTx(t.id, amount, t.type, t.timestamp, t.merchant, t.category, isPerson(t))
                }
                TransactionType.CREDIT -> {
                    if (flow != Flow.INCOME || t.id in manualCredits || (t.userEdited && t.id !in rawFlow) || !isPerson(t)) return@mapNotNull null
                    SplitTx(t.id, amount, t.type, t.timestamp, t.merchant, t.category, true)
                }
            }
        }
        val viewById = view.associateBy { it.id }

        val local = SplitSolver.solve(view, windowDays)
        var aiProposals: MutableList<SplitProposal>? = null
        val asked = mutableSetOf<Long>()
        if (aiRun) {
            for (req in aiRequests(view, local)) {
                val answer = cache?.get(req.json) ?: ai.judge(req)?.also { cache?.put(req.json, it) }
                // Debug builds only: the anonymised request and the model's answer, to turn real cases into tests.
                if (com.pft.financetracker.BuildConfig.DEBUG) runCatching { android.util.Log.d("FinTrackSplitAI", "request=${req.json} answer=$answer") }
                if (answer == null) continue
                val parsed = req.parse(answer) ?: continue
                (aiProposals ?: mutableListOf<SplitProposal>().also { aiProposals = it }).addAll(parsed)
                asked += req.payments
            }
        }
        val decided = SplitDecider.decide(local, aiProposals, asked, viewById, windowDays).associateBy { it.proposal.paymentId }

        // Reconcile the stored automatic splits with the new decisions.
        var applied = 0; var suggested = 0
        val current = splitDao.allSplits().filter { it.source != SplitSource.MANUAL.name && it.id !in frozen }
        val currentLinks = splitDao.allLinks().groupBy { it.splitId }
        val keep = mutableSetOf<Long>()
        for (s in current) {
            val pid = s.linkedTransactionId ?: continue
            val d = decided[pid]
            val ls = currentLinks[s.id].orEmpty()
            val same = d != null && sameLinks(d.proposal, ls)
            val statusOk = d != null && (s.status == SplitStatus.APPLIED.name || !d.auto)
            if (same && statusOk) {
                keep += pid
                // Refresh the explanation (a late payback, AI agreement) without touching the numbers.
                splitDao.updateSplit(s.copy(confidence = d!!.proposal.confidence, reasons = d.proposal.reasons.joinToString("\n")))
            } else {
                undo(s, ls, txDao.getAll().map { it.toDomain() }.associateBy { it.id }, splitDao.allLinks()); undone++
            }
        }
        for ((pid, d) in decided) {
            if (pid in keep) continue
            create(d, viewById)
            if (d.auto) applied++ else suggested++
        }
        return RunResult(applied, suggested, undone, asked.isNotEmpty())
    }

    private fun isPerson(t: Transaction): Boolean =
        (t.counterpartyKind ?: PayerClassifier.classify("", t.merchant, t.type)) == CounterpartyKind.PERSON

    private fun sameLinks(p: SplitProposal, ls: List<SplitLinkEntity>): Boolean {
        val mine = ls.filter { it.role != Role.PAYMENT }.map { it.transactionId to it.allocatedPaise }.sortedBy { it.first }
        return mine == p.allocations.map { it.txId to it.paise }.sortedBy { it.first }
    }

    /** Payments worth asking the AI about, grouped into clusters of related days. */
    private fun aiRequests(view: List<SplitTx>, local: List<SplitProposal>): List<SplitAiRequest> {
        val window = windowDays * SplitSolver.DAY
        val payments = view.filter { it.type == TransactionType.DEBIT }.sortedBy { it.timestamp }
        val credits = view.filter { it.type == TransactionType.CREDIT }
        val localBy = local.associateBy { it.paymentId }
        val interesting = payments.filter { p ->
            val cands = credits.filter { c -> c.amountPaise < p.amountPaise && c.timestamp > p.timestamp && c.timestamp - p.timestamp <= window }
            localBy[p.id] != null || (cands.isNotEmpty() && SplitSolver.estimateShare(p, cands) != null) ||
                // Uneven shares no rule can see: money from people soon after a payment of some size.
                (p.amountPaise >= 200_00 && cands.any { c -> c.timestamp - p.timestamp <= 3 * SplitSolver.DAY })
        }
        // What the local rules explain exactly (high confidence: shares that add up) is settled; asking the AI about it
        // only invites a worse answer (found on the emulator: it missed a transfer covering two bills). The AI gets
        // the rest, without the transfers those settled payments already account for.
        val settled = local.filter { it.level == SplitProposal.Level.HIGH }
        val taken = settled.flatMap { it.allocations }.map { it.txId }.toSet()
        val askable = interesting.filter { p -> settled.none { it.paymentId == p.id } }
        if (askable.isEmpty()) return emptyList()
        val free = credits.filter { it.id !in taken }

        val clusters = mutableListOf<MutableList<SplitTx>>()
        for (p in askable) {
            val last = clusters.lastOrNull()
            if (last != null && p.timestamp - last.last().timestamp <= window && last.size < 25) last += p else clusters += mutableListOf(p)
        }
        return clusters.mapNotNull { ps ->
            // Only transfers that could be a payback for one of these payments: after it, within the window, smaller
            // than it. Found on the emulator: sending the fortnight *before* as well invited the model to call earlier
            // transfers paybacks (the verifier threw that out, but the real split was lost with it).
            val cs = free.filter { c -> ps.any { p -> c.timestamp > p.timestamp && c.timestamp - p.timestamp <= window && c.amountPaise < p.amountPaise } }
                .sortedBy { c -> ps.minOf { kotlin.math.abs(c.timestamp - it.timestamp) } }.take(80)
            if (cs.isEmpty()) null else SplitAiRequest.build(ps, cs)
        }
    }

    // ---------------------------------------------------------------------------------------------------------
    // Applying and undoing.
    // ---------------------------------------------------------------------------------------------------------

    private suspend fun create(d: SplitDecision, view: Map<Long, SplitTx>) {
        val p = d.proposal
        val pay = view[p.paymentId] ?: return
        val status = if (d.auto) SplitStatus.APPLIED else SplitStatus.SUGGESTED
        val mine = pay.amountPaise - p.allocatedPaise
        val senders = p.allocations.groupBy { a -> view[a.txId]?.merchant ?: "Friend" }
        val splitId = splitDao.insertSplit(
            SplitEntity(
                title = pay.merchant, totalPaise = pay.amountPaise, date = pay.timestamp, mode = SplitMode.CUSTOM.name, payerIndex = 0,
                linkedTransactionId = pay.id, note = null, source = p.source.name, status = status.name,
                confidence = p.confidence, reasons = p.reasons.joinToString("\n"), kind = p.kind.name,
            )
        )
        val names = listOf(myName()) + senders.keys
        splitDao.insertPeople(names.mapIndexed { i, n -> SplitPersonEntity(0, splitId, i, n, i == 0) })
        splitDao.insertShares(
            listOf(SplitShareEntity(0, splitId, 0, mine, 0)) +
                senders.values.mapIndexed { i, parts -> parts.sumOf { it.paise }.let { SplitShareEntity(0, splitId, i + 1, it, it) } }
        )
        val payTx = txDao.getById(pay.id)?.toDomain() ?: return
        splitDao.insertLinks(
            listOf(SplitLinkEntity(0, splitId, pay.id, Role.PAYMENT, mine, payTx.flow.name, pay.amountPaise)) +
                p.allocations.map { a ->
                    val c = txDao.getById(a.txId)?.toDomain()
                    SplitLinkEntity(0, splitId, a.txId, if (p.kind == SplitKind.ADVANCE) Role.ADVANCE else Role.PAYBACK, a.paise, (c?.flow ?: Flow.INCOME).let { f -> if (f == Flow.SETTLEMENT) Flow.INCOME else f }.name, null)
                }
        )
        if (status == SplitStatus.APPLIED) applyEffects(splitId)
    }

    /** Make the numbers reflect split [splitId]: the payment counts only my share, the transfers stop being income. */
    private suspend fun applyEffects(splitId: Long) {
        for (l in splitDao.linksFor(splitId)) {
            val t = txDao.getById(l.transactionId)?.toDomain() ?: continue
            val updated = if (l.role == Role.PAYMENT) t.copy(amountPaise = l.allocatedPaise, originalAmountPaise = l.prevAmountPaise ?: t.amountPaise)
            else t.copy(flow = Flow.SETTLEMENT)
            if (updated != t) txDao.update(updated.toEntity())
        }
    }

    /** Restore every row [s] changed and delete it. A transfer still used by another applied split stays a settlement. */
    private suspend fun undo(s: SplitEntity, ls: List<SplitLinkEntity>, byTx: Map<Long, Transaction>, allLinks: List<SplitLinkEntity>) {
        if (s.status == SplitStatus.APPLIED.name) {
            val otherApplied = splitDao.allSplits().filter { it.id != s.id && it.status == SplitStatus.APPLIED.name }.map { it.id }.toSet()
            for (l in ls) {
                val t = byTx[l.transactionId] ?: continue
                val restored = if (l.role == Role.PAYMENT) t.copy(amountPaise = l.prevAmountPaise ?: t.amountPaise, originalAmountPaise = null)
                else {
                    val stillUsed = allLinks.any { it.transactionId == l.transactionId && it.splitId != s.id && it.splitId in otherApplied }
                    if (stillUsed) t else t.copy(flow = Flow.fromName(l.prevFlow) ?: Flow.INCOME)
                }
                if (restored != t) txDao.update(restored.toEntity())
            }
        }
        splitDao.deleteSplit(s.id)
    }

    // ---------------------------------------------------------------------------------------------------------
    // The user's decisions.
    // ---------------------------------------------------------------------------------------------------------

    /** "Yes, apply it": a suggestion becomes applied, and the split is frozen as the user confirmed it. */
    suspend fun accept(splitId: Long) = lock.withLock {
        val s = splitDao.getSplit(splitId) ?: return@withLock
        if (s.status != SplitStatus.APPLIED.name) {
            splitDao.updateSplit(s.copy(status = SplitStatus.APPLIED.name))
            applyEffects(splitId)
        }
        s.linkedTransactionId?.let { splitDao.insertDecision(SplitDecisionEntity(paymentTransactionId = it, decision = Decision.ACCEPTED)) }
    }

    /** "Not a split" / undo: everything goes back exactly as it was, and this payment is never split automatically again. */
    suspend fun reject(splitId: Long) = lock.withLock {
        val s = splitDao.getSplit(splitId) ?: return@withLock
        val byTx = txDao.getAll().map { it.toDomain() }.associateBy { it.id }
        undo(s, splitDao.linksFor(splitId), byTx, splitDao.allLinks())
        s.linkedTransactionId?.let { splitDao.insertDecision(SplitDecisionEntity(paymentTransactionId = it, decision = Decision.REJECTED)) }
    }

    /**
     * A friend's transfer settles (part of) their share in a manual split: it stops counting as income and the
     * share is marked paid. Undone by [unlinkManual] when the split is deleted.
     */
    suspend fun linkSettlement(splitId: Long, shareId: Long, newSettledPaise: Long, credit: Transaction, amountPaise: Long) = lock.withLock {
        splitDao.insertLinks(listOf(SplitLinkEntity(0, splitId, credit.id, Role.PAYBACK, amountPaise, credit.flow.name, null)))
        txDao.update(credit.copy(flow = Flow.SETTLEMENT).toEntity())
        splitDao.settle(shareId, newSettledPaise)
    }

    /** Before a manual split is deleted: transfers linked to it count as what they were again. */
    suspend fun unlinkManual(splitId: Long) = lock.withLock {
        for (l in splitDao.linksFor(splitId)) {
            if (l.role == Role.PAYMENT) continue
            val t = txDao.getById(l.transactionId)?.toDomain() ?: continue
            txDao.update(t.copy(flow = Flow.fromName(l.prevFlow) ?: Flow.INCOME).toEntity())
        }
        splitDao.deleteLinks(splitId)
    }
}
