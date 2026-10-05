package com.pft.financetracker.data.sms

import com.pft.financetracker.data.local.ParserTemplateEntity
import com.pft.financetracker.data.local.TemplateDao
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.LearnedTemplate
import com.pft.financetracker.domain.parser.TemplateLearner

/**
 * Message shapes learned from Review, kept in the encrypted database and mirrored in memory so the parser can read
 * them without touching the disk for every SMS. [load] at app start; [learn] and [delete] keep the mirror current.
 */
class TemplateStore(private val dao: TemplateDao) {
    @Volatile var current: List<LearnedTemplate> = emptyList()
        private set

    suspend fun load() {
        current = dao.getAll().mapNotNull { e -> TransactionType.entries.firstOrNull { it.name == e.type }?.let { LearnedTemplate(e.senderCore, e.skeleton, it) } }
    }

    /** Learns from a message a person confirmed. Returns false when the message cannot teach a reliable shape. */
    suspend fun learn(sender: String, body: String, confirmed: Transaction): Boolean {
        val t = TemplateLearner.learn(sender, body, confirmed.amountPaise, confirmed.type, confirmed.merchant) ?: return false
        dao.insert(ParserTemplateEntity(senderCore = t.senderCore, skeleton = t.skeleton, type = t.type.name))
        load()
        return true
    }

    suspend fun delete(id: Long) {
        dao.delete(id)
        load()
    }
}
