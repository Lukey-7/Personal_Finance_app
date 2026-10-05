package com.pft.financetracker.data.tax

import com.pft.financetracker.data.local.TaxDao
import com.pft.financetracker.data.local.TaxTagEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.export.CsvExporter
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.tax.FinancialYear
import com.pft.financetracker.domain.tax.SectionTotal
import com.pft.financetracker.domain.tax.TaxSection
import com.pft.financetracker.domain.tax.TaxTagger
import java.time.ZoneId

/** Tax-deduction totals per financial year, from the rules plus the person's own tags. For their records, not tax advice. */
class TaxService(private val txDao: TransactionDao, private val dao: TaxDao, private val zone: ZoneId = ZoneId.systemDefault()) {

    /** [section] null marks the payment as "not a deduction", overriding the rules. */
    suspend fun tag(txId: Long, section: TaxSection?) = dao.upsert(TaxTagEntity(txId, section?.name))
    suspend fun clearTag(txId: Long) = dao.delete(txId)

    suspend fun summary(fy: FinancialYear): List<SectionTotal> = summaryOf(txDao.getAll().map { it.toDomain() }, dao.getAll(), fy)

    fun summaryOf(txns: List<Transaction>, tags: List<TaxTagEntity>, fy: FinancialYear): List<SectionTotal> =
        TaxTagger.summary(txns, fy, tags.associate { it.transactionId to TaxSection.fromName(it.section) }, zone)

    suspend fun csv(fy: FinancialYear): String {
        val txns = txDao.getAll().map { it.toDomain() }
        return CsvExporter.taxToCsv(summaryOf(txns, dao.getAll(), fy), txns.associateBy { it.id }, fy.label)
    }
}
