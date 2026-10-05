package com.pft.financetracker.data.cards

import com.pft.financetracker.data.local.CardDao
import com.pft.financetracker.data.local.CardEntity
import com.pft.financetracker.data.local.TransactionDao
import com.pft.financetracker.data.local.toDomain
import com.pft.financetracker.domain.cards.Card
import com.pft.financetracker.domain.cards.CardCycles
import com.pft.financetracker.domain.cards.CardSummary
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import java.time.LocalDate
import java.time.ZoneId

/** Credit cards the person set up, and each one's current billing cycle worked out from the transactions. */
class CardService(private val txDao: TransactionDao, private val dao: CardDao, private val zone: ZoneId = ZoneId.systemDefault()) {

    suspend fun all(): List<Card> = dao.getAll().map { it.toDomain() }

    /** Saving a card whose last four digits already exist replaces it, so there is one card per number. */
    suspend fun save(c: Card): Long = dao.upsert(c.toEntity())

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun summaries(today: LocalDate = LocalDate.now(zone)): List<CardSummary> =
        summariesOf(all(), txDao.getAll().map { it.toDomain() }, today)

    fun summariesOf(cards: List<Card>, txns: List<Transaction>, today: LocalDate): List<CardSummary> =
        cards.map { CardCycles.summary(it, today, txns, zone) }

    /**
     * Last-four digits worth offering when adding a card: [extra] first (cards known from statement SMS), then accounts
     * that paid for things in the last 90 days, most used first. Numbers already set up as cards are left out.
     */
    suspend fun suggestions(extra: List<String>): List<String> {
        val since = System.currentTimeMillis() - 90L * 86_400_000L
        val taken = all().map { it.last4 }.toSet()
        val seen = txDao.getAll().asSequence()
            .filter { it.type == TransactionType.DEBIT.name && it.timestamp >= since && it.accountRef != null }
            .groupingBy { it.accountRef!! }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        return (extra + seen).distinct().filter { it !in taken }
    }
}

fun CardEntity.toDomain() = Card(id, last4, name, statementDay, dueDay, rewardBp)
fun Card.toEntity() = CardEntity(id, last4, name, statementDay, dueDay, rewardBp)
