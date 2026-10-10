package com.pft.financetracker.data.importer

import com.pft.financetracker.data.local.ImportBatchEntity
import com.pft.financetracker.data.local.ImportDao
import com.pft.financetracker.data.local.ImportMatchEntity
import com.pft.financetracker.data.local.ReviewItemEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.domain.importer.ParsedStatement
import com.pft.financetracker.domain.importer.StatementRow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.ledger.SamePayment
import java.security.MessageDigest

/**
 * Stores what a statement or screenshot import found. Rows the app already has (from SMS, or from importing the
 * same file before) are recognised and skipped, and the missing details they carry (a reference number, who the
 * other side is) fill the stored row in. Rows that could not be read with confidence go to the review list.
 */
class StatementImporter(
    private val txDao: TransactionDao,
    private val repo: TransactionRepository,
    private val importDao: ImportDao,
    private val smsLog: com.pft.financetracker.data.repository.SmsLogRepository? = null,
) {
    data class Preview(
        val statement: ParsedStatement,
        val fileName: String,
        val newRows: List<StatementRow>,
        /** A row and the stored transaction it matched. */
        val duplicates: List<Pair<StatementRow, Transaction>>,
        /** Rows from an earlier import of this statement that the person deleted since: not added again. */
        val deletedBefore: List<StatementRow> = emptyList(),
    )

    suspend fun preview(statement: ParsedStatement, fileName: String): Preview {
        val newRows = mutableListOf<StatementRow>()
        val dupes = mutableListOf<Pair<StatementRow, Transaction>>()
        val deleted = mutableListOf<StatementRow>()
        val consumed = mutableSetOf<Long>()
        for (r in statement.rows) {
            if (wasDeleted(r)) { deleted += r; continue }
            val match = findExisting(r, consumed)
            if (match != null) { dupes += r to match; consumed += match.id } else newRows += r
        }
        return Preview(statement, fileName, newRows, dupes, deleted)
    }

    /** The person deleted the row an earlier import of this same statement added (see SmsImporter.forgetDeleted). */
    private suspend fun wasDeleted(r: StatementRow): Boolean =
        smsLog?.getByHash(com.pft.financetracker.data.sms.SmsImporter.statementTombstone(hash(r))) != null

    private suspend fun findExisting(r: StatementRow, consumed: Set<Long>): Transaction? {
        // The same file (or an overlapping one) imported before.
        txDao.getByHash(hash(r))?.let { return it.toDomain() }
        return SamePayment.findForStatement(r, repo.stored, consumed)
    }

    suspend fun commit(p: Preview): ImportBatchEntity {
        val batchId = importDao.insert(
            ImportBatchEntity(
                fileName = p.fileName, format = p.statement.format.name, rowsFound = p.statement.rows.size + p.statement.problems.size,
                added = 0, duplicates = p.duplicates.size, needsReview = 0, balanceMismatches = p.statement.balanceMismatches,
                firstDate = p.statement.firstDate, lastDate = p.statement.lastDate,
            )
        )
        var added = 0
        // Look again: an SMS for the same payment may have arrived while the preview was on screen.
        val consumed = p.duplicates.map { it.second.id }.toMutableSet()
        val lateDuplicates = mutableListOf<Pair<StatementRow, Transaction>>()
        for (r in p.newRows) {
            if (wasDeleted(r)) continue
            val late = findExisting(r, consumed)
            if (late != null) { lateDuplicates += r to late; consumed += late.id; continue }
            val t = Transaction(
                amountPaise = r.amountPaise, type = r.type, merchant = r.counterparty, category = r.category,
                // Statements give the day, not the time: noon keeps it on that day, and the row order keeps same-day
                // payments in sequence (a dinner before its paybacks).
                timestamp = r.time ?: (r.date + 12 * 3_600_000L + r.order * 1_000L),
                bankName = null, accountRef = null, source = Transaction.Source.STATEMENT, flow = r.flow,
                smsHash = hash(r), refNumber = r.ref, confidence = if (r.balanceOk == true) 95 else 85,
                counterpartyKind = r.kind, importBatchId = batchId,
            )
            // A row this import just added is not a duplicate of the next identical one (two Rs 20 teas).
            val id = repo.insert(t)
            if (id > 0) { added++; consumed += id }
        }
        // Fill in what the statement knows and the stored row lacks. Read the row again: the preview may have been on
        // screen while an SMS or a split changed it, and writing the preview's copy back would undo that.
        val matched = mutableListOf<ImportMatchEntity>()
        for ((r, seen) in p.duplicates + lateDuplicates) {
            val existing = txDao.getById(seen.id)?.toDomain() ?: continue
            val filled = existing.copy(refNumber = existing.refNumber ?: r.ref, counterpartyKind = existing.counterpartyKind ?: r.kind)
            if (filled != existing) txDao.update(filled.toEntity())
            if (existing.importBatchId != null && existing.importBatchId != batchId) matched += ImportMatchEntity(batchId = batchId, transactionId = existing.id)
        }
        if (matched.isNotEmpty()) importDao.insertMatches(matched)
        var review = 0
        for (prob in p.statement.problems) {
            val ok = repo.enqueueReview(
                ReviewItemEntity(
                    sender = "Statement: ${p.fileName}", body = prob.raw, receivedAt = prob.date ?: System.currentTimeMillis(),
                    smsHash = "stmtrev:" + sha(prob.raw + "|" + prob.date), guessedAmountPaise = prob.amountPaise,
                    guessedType = prob.type?.name, reason = "statement_${prob.reason}",
                )
            )
            if (ok) review++
        }
        val batch = importDao.get(batchId)!!.copy(added = added, duplicates = p.duplicates.size + lateDuplicates.size, needsReview = review)
        importDao.update(batch)
        return batch
    }

    /**
     * Remove every transaction an import added. Rows it only matched (already in the app) are left alone, and a row a
     * later import also contained stays and moves to that import. [beforeDelete] runs for each row about to go.
     */
    suspend fun undo(batchId: Long, beforeDelete: suspend (Long) -> Unit = {}) {
        for (listed in txDao.getByBatch(batchId)) {
            // Read again: giving back an earlier purchase's refund may have changed this row since the list was read.
            val t = txDao.getById(listed.id) ?: continue
            val other = importDao.matchesFor(t.id).firstOrNull { it.batchId != batchId && importDao.get(it.batchId) != null }
            when {
                other != null -> {
                    txDao.update(t.copy(importBatchId = other.batchId))
                    importDao.deleteMatch(other.id)
                    importDao.get(other.batchId)?.let { b -> importDao.update(b.copy(added = b.added + 1, duplicates = maxOf(0, b.duplicates - 1))) }
                }
                // An SMS reported the same payment since: it is the SMS's row now, and the SMS won't be read again.
                smsLog?.pointsAt(t.id) == true -> txDao.update(t.copy(importBatchId = null))
                else -> { beforeDelete(t.id); txDao.delete(t) }
            }
        }
        importDao.deleteMatchesForBatch(batchId)
        importDao.delete(batchId)
    }

    /** Identical rows in one file (two Rs 20 teas) are told apart by their occurrence; the first keeps the old hash. */
    private fun hash(r: StatementRow) = "stmt:" + sha("${r.date}|${r.amountPaise}|${r.type}|${r.narration.trim().lowercase()}|${r.balancePaise}" +
        if (r.occurrence > 0) "|#${r.occurrence}" else "")

    private fun sha(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }.take(32)
}
