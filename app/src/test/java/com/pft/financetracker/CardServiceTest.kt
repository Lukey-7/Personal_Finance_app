package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.cards.CardSave
import com.pft.financetracker.data.cards.CardService
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.local.toEntity
import com.pft.financetracker.domain.cards.Card
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class CardServiceTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val cards = CardService(db.transactionDao(), db.cardDao())
    @After fun tearDown() = db.close()

    private fun spend(ref: String, daysAgo: Long) = runBlocking {
        db.transactionDao().insert(Transaction(amountPaise = 100_00, type = TransactionType.DEBIT, merchant = "X", category = Category.SHOPPING,
            timestamp = System.currentTimeMillis() - daysAgo * 86_400_000L, bankName = "HDFC Bank", accountRef = ref, source = Transaction.Source.SMS,
            flow = Flow.EXPENSE).toEntity())
    }

    @Test fun aCardRoundTripsAndOneCardPerLast4() = runBlocking {
        val id = (cards.save(Card(last4 = "1234", name = "Millennia", statementDay = 15, dueDay = 5, rewardBp = 150)) as CardSave.Saved).id
        assertEquals(Card(id, "1234", "Millennia", 15, 5, 150), cards.all().single())
        assertTrue(cards.save(Card(id = id, last4 = "1234", name = "Millennia", statementDay = 16, dueDay = 6)) is CardSave.Saved)
        assertEquals(16, cards.all().single().statementDay)
    }

    @Test fun aSecondCardWithTheSameLast4IsRefusedNotSwappedIn() = runBlocking {
        val first = Card(last4 = "1234", name = "Millennia", statementDay = 15, dueDay = 5)
        val id = (cards.save(first) as CardSave.Saved).id
        assertEquals(CardSave.Duplicate(first.copy(id = id)), cards.save(Card(last4 = "1234", name = "Regalia", statementDay = 1, dueDay = 20)))
        assertEquals("Millennia", cards.all().single().name)
    }

    @Test fun changingACardsDigitsToAnotherCardsIsRefused() = runBlocking {
        cards.save(Card(last4 = "1234", name = "Millennia", statementDay = 15, dueDay = 5))
        val other = (cards.save(Card(last4 = "5678", name = "Amazon Pay", statementDay = 1, dueDay = 20)) as CardSave.Saved).id
        assertTrue(cards.save(Card(id = other, last4 = "1234", name = "Amazon Pay", statementDay = 1, dueDay = 20)) is CardSave.Duplicate)
        assertEquals(setOf("1234", "5678"), cards.all().map { it.last4 }.toSet())
    }

    @Test fun onlyFourDigitsAreEverStored() = runBlocking {
        assertEquals(CardSave.NotLast4, cards.save(Card(last4 = "4111111111111234", name = "Full number", statementDay = 1, dueDay = 20)))
        assertEquals(CardSave.NotLast4, cards.save(Card(last4 = "12a4", name = "Typo", statementDay = 1, dueDay = 20)))
        assertTrue(cards.all().isEmpty())
    }

    @Test fun accountsSeenRecentlyAreSuggestedMostUsedFirstExceptExistingCards() = runBlocking {
        spend("1234", 3); spend("5678", 2); spend("5678", 1); spend("9999", 200)
        cards.save(Card(last4 = "1234", name = "Millennia", statementDay = 15, dueDay = 5))
        assertEquals(listOf("5678"), cards.suggestions(extra = emptyList()))
        assertEquals(listOf("4321", "5678"), cards.suggestions(extra = listOf("4321")))
    }

    @Test fun summariesCoverEveryCard() = runBlocking {
        cards.save(Card(last4 = "1234", name = "Millennia", statementDay = 15, dueDay = 5))
        assertTrue(cards.summaries(LocalDate.now()).single().card.last4 == "1234")
    }
}
