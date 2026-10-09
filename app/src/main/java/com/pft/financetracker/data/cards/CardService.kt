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

    /**
     * Saves [c] (a new card, or changes to one by its id). The table allows one card per last four digits and an insert
     * would replace the other card silently, so a different card with the same digits is refused instead.
     * Only the last four digits are ever stored: anything else in [Card.last4] is refused too.
     */
    suspend fun save(c: Card): CardSave {
        if (c.last4.length != 4 || !c.last4.all { it in '0'..'9' }) return CardSave.NotLast4
        CardCycles.clash(c, all())?.let { return CardSave.Duplicate(it) }
        return CardSave.Saved(dao.upsert(c.toEntity()))
    }

    suspend fun delete(id: Long) = dao.delete(id)

    suspend fun summaries(today: LocalDate = LocalDate.now(zone)): List<CardSummary> =
        summariesOf(all(), txDao.getAll().map { it.toDomain() }, today)

    fun summariesOf(cards: List<Card>, txns: List<Transaction>, today: LocalDate): List<CardSummary> =
        cards.map { CardCycles.summary(it, today, txns, zone) }

    /**
     * Last-four digits worth offering when adding a card: [extra] first (cards known from statement SMS), then accounts
     * that paid for things in the last 90 days, most used first. Numbers already set up as cards, and anything that is not
     * exactly four digits, are left out.
     */
    suspend fun suggestions(extra: List<String>): List<String> {
        val since = System.currentTimeMillis() - 90L * 86_400_000L
        val taken = all().map { it.last4 }.toSet()
        val seen = txDao.getAll().asSequence()
            .filter { it.type == TransactionType.DEBIT.name && it.timestamp >= since && it.accountRef != null }
            .groupingBy { it.accountRef!! }.eachCount().entries.sortedByDescending { it.value }.map { it.key }
        return (extra + seen).distinct().filter { it !in taken && it.length == 4 && it.all { ch -> ch in '0'..'9' } }
    }
}

/** The outcome of [CardService.save]. */
sealed interface CardSave {
    data class Saved(val id: Long) : CardSave
    /** Another card already has these last four digits; edit that one instead. */
    data class Duplicate(val existing: Card) : CardSave
    /** The digits were not exactly four. */
    data object NotLast4 : CardSave
}

fun CardEntity.toDomain() = Card(id, last4, name, statementDay, dueDay, rewardBp)
fun Card.toEntity() = CardEntity(id, last4, name, statementDay, dueDay, rewardBp)
