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
import com.pft.financetracker.domain.split.SettleMatch
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
    /**
     * Rows in split_decisions. REJECTED / ACCEPTED are keyed by the payment. REJECTED_TRANSFER is keyed by a friend's
     * transfer from a split the user rejected: it may still be suggested for another payment, never applied on its own.
     */
    private object Decision { const val REJECTED = "REJECTED"; const val ACCEPTED = "ACCEPTED"; const val REJECTED_TRANSFER = "REJECTED_TRANSFER" }

    suspend fun run(useAi: Boolean = true): RunResult = lock.withLock { runLocked(useAi) }

    private suspend fun runLocked(useAi: Boolean): RunResult {
        val txnsAtStart = txDao.getAll().map { it.toDomain() }
        val byTx = txnsAtStart.associateBy { it.id }
        val splits = splitDao.allSplits()
        val links = splitDao.allLinks()
        val decisions = splitDao.decisions().associateBy { it.paymentTransactionId }
        val linksBySplit = links.groupBy { it.splitId }
        val autoSplits = splits.filter { it.source != SplitSource.MANUAL.name }

        // A friend's transfer that settled a manual split is gone (deleted, or its import undone): they owe that share
        // again, and the link to the missing row goes.
        val manualIds = splits.filter { it.source == SplitSource.MANUAL.name }.map { it.id }.toSet()
        // A link made before v1.2.1 whose share could not be worked out on upgrade is kept: it is the only record.
        for (l in links.filter { it.splitId in manualIds && it.role != Role.PAYMENT && it.shareId != null && byTx[it.transactionId] == null }) {
            splitDao.share(l.shareId!!)?.let { sh -> splitDao.settle(sh.id, maxOf(0L, sh.settledPaise - l.allocatedPaise)) }
            splitDao.deleteLink(l.id)
        }

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
        val rejectedTransfers = decisions.filterValues { it.decision == Decision.REJECTED_TRANSFER }.keys
        // Friends' transfers that pay back a manual split of mine (a v1.1 split, or one never linked) are not free
        // for another payment of the same size to claim.
        // Only manual links count as "already linked" here: a transfer an automatic split took first is still the friend
        // paying back the manual split, and the automatic guess gives it up.
        val manualPaybacks = manualPaybacks(splits.filter { it.source == SplitSource.MANUAL.name }, txns, liveLinks.filter { it.splitId in manualSplitIds })

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
                    if (flow != Flow.INCOME || t.id in manualCredits || t.id in manualPaybacks || (t.userEdited && t.id !in rawFlow) || !isPerson(t)) return@mapNotNull null
                    SplitTx(t.id, amount, t.type, t.timestamp, t.merchant, t.category, true)
                }
            }
        }
        val viewById = view.associateBy { it.id }

        val local = SplitSolver.solve(view, windowDays)
        var aiProposals: MutableList<SplitProposal>? = null
        val asked = mutableSetOf<Long>()
        if (aiRun) {
            var unanswered = false
            for (req in aiRequests(view, local)) {
                // A cached answer is reused only while it still reads as an answer. Only answers that parse are cached,
                // so a cut-off reply (timeout mid-stream) is asked again next time instead of blocking that week for good.
                var answer = cache?.get(req.json)
                var parsed = answer?.let { req.parse(it) }
                if (parsed == null) {
                    answer = ai.judge(req)
                    parsed = answer?.let { req.parse(it) }
                    if (parsed != null) cache?.put(req.json, answer!!)
                }
                // Debug builds only: the anonymised request and the model's answer, to turn real cases into tests.
                if (com.pft.financetracker.BuildConfig.DEBUG) runCatching { android.util.Log.d("FinTrackSplitAI", "request=${req.json} answer=$answer") }
                if (parsed == null) { unanswered = true; continue }
                (aiProposals ?: mutableListOf<SplitProposal>().also { aiProposals = it }).addAll(parsed)
                asked += req.payments
            }
            // Offline, a timeout, a cut-off reply: without the AI's view an AI-found split can't be judged, and judging it
            // locally would undo it now and redo it next time. Run as a local run instead, which keeps AI splits as they
            // are. Answers that did come back are cached for the next AI run.
            if (unanswered) return runLocked(useAi = false)
        }
        val current = splitDao.allSplits().filter { it.source != SplitSource.MANUAL.name && it.id !in frozen }
        val currentLinks = splitDao.allLinks().groupBy { it.splitId }
        val currentByPayment = current.filter { it.linkedTransactionId != null }.associateBy { it.linkedTransactionId!! }
        // An applied split the new answer still matches stays applied even when its confidence dipped. Then splits that
        // share a transfer stand together: one of them only suggested makes them all suggestions, so a transfer is never
        // marked as a settlement while only part of it is accounted for (a Rs 1,200 transfer covering the dinner and
        // the cab, with only the dinner applied).
        val decided = SplitDecider.groupShared(
            SplitDecider.decide(local, aiProposals, asked, viewById, windowDays)
                .map { d -> if (d.auto && d.proposal.allocations.any { it.txId in rejectedTransfers }) d.copy(auto = false) else d }
                .map { d ->
                    val existing = currentByPayment[d.proposal.paymentId]
                    val staysApplied = existing != null && existing.status == SplitStatus.APPLIED.name && sameLinks(d.proposal, currentLinks[existing.id].orEmpty())
                    if (!d.auto && staysApplied) d.copy(auto = true) else d
                }
        ).associateBy { it.proposal.paymentId }

        // Reconcile the stored automatic splits with the new decisions.
        var applied = 0; var suggested = 0
        val keep = mutableSetOf<Long>()
        for (s in current) {
            val pid = s.linkedTransactionId ?: continue
            val d = decided[pid]
            val ls = currentLinks[s.id].orEmpty()
            val same = d != null && sameLinks(d.proposal, ls)
            val statusOk = d != null && (s.status == SplitStatus.APPLIED.name) == d.auto
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
            if (!create(d, viewById)) continue
            if (d.auto) applied++ else suggested++
        }
        return RunResult(applied, suggested, undone, asked.isNotEmpty())
    }

    private suspend fun manualPaybacks(manual: List<SplitEntity>, txns: List<Transaction>, links: List<SplitLinkEntity>): Set<Long> {
        val window = windowDays * SplitSolver.DAY
        val linked = links.map { it.transactionId }.toSet()
        val out = mutableSetOf<Long>()
        for (s in manual) {
            val people = splitDao.peopleFor(s.id)
            val me = people.firstOrNull { it.isMe }?.personIndex ?: continue
            if (s.payerIndex != me) continue // someone else paid: nobody pays me back for it
            for (share in splitDao.sharesFor(s.id)) {
                if (share.personIndex == me) continue
                // What is still owed, plus what was marked paid by a typed amount rather than a linked transfer: the
                // friend's transfer for that part is still in the app as income, and is theirs, not another payment's.
                val linkedPaise = links.filter { it.shareId == share.id }.sumOf { it.allocatedPaise }
                val typedPaise = (share.settledPaise - linkedPaise).coerceAtLeast(0)
                val cover = (share.amountPaise - share.settledPaise).coerceAtLeast(0) + typedPaise
                if (cover <= 0) continue
                val name = people.firstOrNull { it.personIndex == share.personIndex }?.name ?: continue
                txns.filter { t ->
                    t.type == TransactionType.CREDIT && t.id !in linked && t.timestamp >= s.date - SplitSolver.DAY && t.timestamp - s.date <= window &&
                        t.amountPaise <= cover + SettleMatch.roundUpPaise(cover) && PayerClassifier.sameParty(t.merchant, name)
                }.forEach { out += it.id }
            }
        }
        return out
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

    /**
     * Store split [d] and, when it is applied, change the numbers. Nothing is stored when a row it uses changed since
     * the view was read (the person edited the payment while the AI was being asked): the next run looks again.
     */
    private suspend fun create(d: SplitDecision, view: Map<Long, SplitTx>): Boolean {
        val p = d.proposal
        val pay = view[p.paymentId] ?: return false
        val unchanged = (listOf(pay.id) + p.allocations.map { it.txId }).all { id ->
            val now = txDao.getById(id)?.toDomain()
            now != null && !now.userEdited && now.amountPaise == view[id]?.amountPaise
        }
        if (!unchanged) return false
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
        val payTx = txDao.getById(pay.id)?.toDomain() ?: run { splitDao.deleteSplit(splitId); return false }
        splitDao.insertLinks(
            listOf(SplitLinkEntity(0, splitId, pay.id, Role.PAYMENT, mine, payTx.flow.name, pay.amountPaise)) +
                p.allocations.map { a ->
                    val c = txDao.getById(a.txId)?.toDomain()
                    SplitLinkEntity(0, splitId, a.txId, if (p.kind == SplitKind.ADVANCE) Role.ADVANCE else Role.PAYBACK, a.paise, (c?.flow ?: Flow.INCOME).let { f -> if (f == Flow.SETTLEMENT) Flow.INCOME else f }.name, null)
                }
        )
        if (status == SplitStatus.APPLIED && !applyEffects(splitId)) { splitDao.deleteSplit(splitId); return false }
        return true
    }

    /**
     * Make the numbers reflect split [splitId]: the payment counts only my share, the transfers stop being income. A row
     * a person changed since the split was found (a suggestion waits for days) keeps what they set.
     */
    private suspend fun applyEffects(splitId: Long): Boolean {
        val ls = splitDao.linksFor(splitId)
        // The payment changed since the split was worked out (edited while the AI was asked): change nothing at all,
        // not even the transfers. The next run works it out again from the row as it is now.
        ls.firstOrNull { it.role == Role.PAYMENT }?.let { pay ->
            val t = txDao.getById(pay.transactionId)?.toDomain() ?: return false
            if (t.amountPaise != (pay.prevAmountPaise ?: t.amountPaise)) return false
        }
        val splitsById = splitDao.allSplits().associateBy { it.id }
        val allLinks = splitDao.allLinks()
        for (l in ls) {
            val t = txDao.getById(l.transactionId)?.toDomain() ?: continue
            val updated = if (l.role == Role.PAYMENT) {
                t.copy(amountPaise = l.allocatedPaise, originalAmountPaise = l.prevAmountPaise ?: t.amountPaise)
            } else {
                // A transfer becomes a settlement only once applied splits account for all of it: a Rs 1,200 transfer
                // of which only Rs 1,000 is applied stays income until the rest is applied too.
                val usedPaise = allLinks.filter { o ->
                    o.transactionId == t.id && o.role != Role.PAYMENT &&
                        splitsById[o.splitId]?.let { it.source == SplitSource.MANUAL.name || it.status == SplitStatus.APPLIED.name } == true
                }.sumOf { it.allocatedPaise }
                if (usedPaise >= t.amountPaise && (t.flow == Flow.SETTLEMENT || t.flow == (Flow.fromName(l.prevFlow) ?: Flow.INCOME))) t.copy(flow = Flow.SETTLEMENT) else t
            }
            if (updated != t) txDao.update(updated.toEntity())
        }
        return true
    }

    /** Restore every row [s] changed and delete it. A transfer still used by another applied split stays a settlement. */
    private suspend fun undo(s: SplitEntity, ls: List<SplitLinkEntity>, byTx: Map<Long, Transaction>, allLinks: List<SplitLinkEntity>) {
        if (s.status == SplitStatus.APPLIED.name) {
            val otherApplied = splitDao.allSplits().filter { it.id != s.id && it.status == SplitStatus.APPLIED.name }.map { it.id }.toSet()
            for (l in ls) {
                val t = byTx[l.transactionId] ?: continue
                // Only what the split set is put back: an amount or flow a person changed since stays theirs.
                val restored = if (l.role == Role.PAYMENT) {
                    if (t.amountPaise == l.allocatedPaise) t.copy(amountPaise = l.prevAmountPaise ?: t.amountPaise, originalAmountPaise = null) else t.copy(originalAmountPaise = null)
                } else {
                    val stillUsed = allLinks.any { it.transactionId == l.transactionId && it.splitId != s.id && it.splitId in otherApplied }
                    if (stillUsed || t.flow != Flow.SETTLEMENT) t else t.copy(flow = Flow.fromName(l.prevFlow) ?: Flow.INCOME)
                }
                if (restored != t) txDao.update(restored.toEntity())
            }
        }
        splitDao.deleteSplit(s.id)
    }

    // ---------------------------------------------------------------------------------------------------------
    // The user's decisions.
    // ---------------------------------------------------------------------------------------------------------

    private class AcceptPlan(val split: SplitEntity, val payment: SplitLinkEntity?, val correctedTotal: Long?, val mine: Long?)

    /**
     * "Yes, apply it": a suggestion becomes applied, and the split is frozen as the user confirmed it. Automatic splits
     * that share a friend's transfer with it (Rs 1,200 = Rs 1,000 for the dinner + Rs 200 for the cab) are accepted
     * with it, so the transfer is accounted for in full; when any of them can't be applied, none is.
     */
    suspend fun accept(splitId: Long): Boolean = lock.withLock {
        val first = splitDao.getSplit(splitId) ?: return@withLock false
        val group = sharedGroup(first)
        val groupIds = group.map { it.id }.toSet()
        val splitsById = splitDao.allSplits().associateBy { it.id }
        val allLinks = splitDao.allLinks()
        // A suggestion can wait for days. A friend's transfer that since settled a manual split (or another applied
        // split), or that the person re-filed, can't pay for this one too: leave the suggestion as it is.
        val elsewhere = allLinks.filter { l ->
            l.splitId !in groupIds && l.role != Role.PAYMENT &&
                splitsById[l.splitId]?.let { it.source == SplitSource.MANUAL.name || it.status == SplitStatus.APPLIED.name } == true
        }
        val groupFriends = allLinks.filter { it.splitId in groupIds && it.role != Role.PAYMENT }
        val plans = mutableListOf<AcceptPlan>()
        for (s in group) {
            if (s.status == SplitStatus.APPLIED.name) continue
            val links = allLinks.filter { it.splitId == s.id }
            val friends = links.filter { it.role != Role.PAYMENT }
            for (l in friends) {
                val t = txDao.getById(l.transactionId)?.toDomain() ?: return@withLock false
                val claimed = elsewhere.filter { it.transactionId == t.id }.sumOf { it.allocatedPaise } + groupFriends.filter { it.transactionId == t.id }.sumOf { it.allocatedPaise }
                if (claimed > t.amountPaise) return@withLock false
                if (t.flow != Flow.SETTLEMENT && t.flow != (Flow.fromName(l.prevFlow) ?: Flow.INCOME)) return@withLock false
            }
            // The person corrected the bill since it was suggested: split the corrected amount (my share is what the
            // friends didn't pay of it). A corrected bill smaller than the friends' shares can't be this split.
            val pay = links.firstOrNull { it.role == Role.PAYMENT }
            val payTx = pay?.let { txDao.getById(it.transactionId)?.toDomain() }
            if (pay != null && payTx == null) return@withLock false
            if (pay != null && payTx != null && payTx.amountPaise != (pay.prevAmountPaise ?: payTx.amountPaise)) {
                val friendsPaise = friends.sumOf { it.allocatedPaise }
                if (payTx.amountPaise <= friendsPaise) return@withLock false
                plans += AcceptPlan(s, pay, payTx.amountPaise, payTx.amountPaise - friendsPaise)
            } else {
                plans += AcceptPlan(s, pay, null, null)
            }
        }
        for (p in plans) {
            var s = p.split
            if (p.payment != null && p.correctedTotal != null && p.mine != null) {
                splitDao.updateLink(p.payment.copy(allocatedPaise = p.mine, prevAmountPaise = p.correctedTotal))
                splitDao.sharesFor(s.id).firstOrNull { it.personIndex == 0 }?.let { splitDao.updateShare(it.copy(amountPaise = p.mine)) }
                s = s.copy(totalPaise = p.correctedTotal)
            }
            splitDao.updateSplit(s.copy(status = SplitStatus.APPLIED.name))
        }
        // Statuses first, effects second: a shared transfer becomes a settlement once every split using it is applied.
        var ok = true
        for (p in plans) {
            if (!applyEffects(p.split.id)) {
                splitDao.getSplit(p.split.id)?.let { splitDao.updateSplit(it.copy(status = SplitStatus.SUGGESTED.name)) }
                ok = false
            }
        }
        if (!ok) return@withLock false
        group.forEach { s -> s.linkedTransactionId?.let { splitDao.insertDecision(SplitDecisionEntity(paymentTransactionId = it, decision = Decision.ACCEPTED)) } }
        true
    }

    /** [start] and every automatic split joined to it through a friend's transfer they share, directly or in a chain. */
    private suspend fun sharedGroup(start: SplitEntity): List<SplitEntity> {
        val autos = splitDao.allSplits().filter { it.source != SplitSource.MANUAL.name }.associateBy { it.id }
        val friendLinks = splitDao.allLinks().filter { autos.containsKey(it.splitId) && it.role != Role.PAYMENT }
        val found = linkedSetOf(start.id)
        val queue = ArrayDeque(listOf(start.id))
        while (queue.isNotEmpty()) {
            val id = queue.removeFirst()
            val txs = friendLinks.filter { it.splitId == id }.map { it.transactionId }.toSet()
            for (l in friendLinks) if (l.transactionId in txs && found.add(l.splitId)) queue.addLast(l.splitId)
        }
        return found.mapNotNull { autos[it] ?: start.takeIf { s -> s.id == it } }
    }

    /** "Not a split" / undo: everything goes back exactly as it was, and this payment is never split automatically again. */
    suspend fun reject(splitId: Long) = lock.withLock {
        val s = splitDao.getSplit(splitId) ?: return@withLock
        val byTx = txDao.getAll().map { it.toDomain() }.associateBy { it.id }
        val ls = splitDao.linksFor(splitId)
        val splitsById = splitDao.allSplits().associateBy { it.id }
        // Transfers another applied (or manual) split stands on are not this "no" to give: marking them rejected would
        // turn that split back into a suggestion on the next run.
        val claimedElsewhere = splitDao.allLinks().filter { l ->
            l.splitId != splitId && splitsById[l.splitId]?.let { it.source == SplitSource.MANUAL.name || it.status == SplitStatus.APPLIED.name } == true
        }.map { it.transactionId }.toSet()
        undo(s, ls, byTx, splitDao.allLinks())
        s.linkedTransactionId?.let { splitDao.insertDecision(SplitDecisionEntity(paymentTransactionId = it, decision = Decision.REJECTED)) }
        // "Not a split" also says these transfers weren't paybacks for it: don't hand them to another payment unasked.
        ls.filter { it.role != Role.PAYMENT && it.transactionId !in claimedElsewhere }
            .forEach { splitDao.insertDecision(SplitDecisionEntity(paymentTransactionId = it.transactionId, decision = Decision.REJECTED_TRANSFER)) }
    }

    /**
     * Before a manual split takes payment [paymentId]: an automatic split or suggestion on it is undone first (the
     * payment back to its bank amount), so the two never both change it.
     */
    suspend fun releaseForManual(paymentId: Long) = lock.withLock {
        val autos = splitDao.allSplits().filter { it.source != SplitSource.MANUAL.name && it.linkedTransactionId == paymentId }
        for (s in autos) {
            val byTx = txDao.getAll().map { it.toDomain() }.associateBy { it.id }
            undo(s, splitDao.linksFor(s.id), byTx, splitDao.allLinks())
        }
    }

    /**
     * A transfer settles (part of) a share in a manual split: a friend's transfer to me, or my own payment to whoever
     * paid. It stops counting as income or spend and the share is marked paid. Undone by [deleteManual].
     */
    suspend fun linkSettlement(splitId: Long, shareId: Long, newSettledPaise: Long, credit: Transaction, amountPaise: Long) = lock.withLock {
        // Read the row again: the settle sheet may have been open while an SMS, an import or an edit changed it.
        val current = txDao.getById(credit.id)?.toDomain() ?: return@withLock
        splitDao.insertLinks(listOf(SplitLinkEntity(0, splitId, current.id, Role.PAYBACK, amountPaise, current.flow.name, null, shareId = shareId)))
        txDao.update(current.copy(flow = Flow.SETTLEMENT).toEntity())
        val settled = splitDao.share(shareId)?.let { minOf(it.amountPaise, it.settledPaise + amountPaise) } ?: newSettledPaise
        splitDao.settle(shareId, settled)
    }

    /**
     * Delete manual split [splitId] and put back what it changed: the payment it shrank to my share has its full
     * amount again (without the "Split:" note), a "my share" row it added goes, and transfers linked to it count as
     * what they were again.
     */
    suspend fun deleteManual(splitId: Long) = lock.withLock {
        val s = splitDao.getSplit(splitId) ?: return@withLock
        for (l in splitDao.linksFor(splitId)) {
            if (l.role == Role.PAYMENT) continue
            val t = txDao.getById(l.transactionId)?.toDomain() ?: continue
            // Only what the link set is put back: a row the person re-filed since stays as they filed it.
            if (t.flow != Flow.SETTLEMENT) continue
            val before = Flow.fromName(l.prevFlow)?.takeIf { it != Flow.SETTLEMENT } ?: if (t.type == TransactionType.DEBIT) Flow.EXPENSE else Flow.INCOME
            txDao.update(t.copy(flow = before).toEntity())
        }
        val payment = s.linkedTransactionId?.let { txDao.getById(it)?.toDomain() }
        if (payment != null && splitDao.allSplits().none { it.id != splitId && it.linkedTransactionId == payment.id }) {
            val original = payment.originalAmountPaise
            when {
                payment.source == Transaction.Source.SPLIT -> txDao.delete(payment.toEntity())
                original != null -> txDao.update(payment.copy(amountPaise = original, originalAmountPaise = null, note = withoutSplitNote(payment.note, s.title)).toEntity())
            }
        }
        splitDao.deleteLinks(splitId)
        splitDao.deleteSplit(splitId)
    }

    companion object {
        /** The note a manual split adds to the payment it shrinks. [withoutSplitNote] takes it off again. */
        fun splitNote(title: String, owedText: String): String = "Split: $title. $owedText owed to you."

        /** [note] without the line [splitNote] added for the split called [title]; null when nothing else is left. */
        fun withoutSplitNote(note: String?, title: String): String? {
            if (note == null) return null
            val rx = Regex("""\s*Split: ${Regex.escape(title)}\. .*? owed to you\.""")
            return note.replace(rx, "").trim().ifBlank { null }
        }
    }
}
