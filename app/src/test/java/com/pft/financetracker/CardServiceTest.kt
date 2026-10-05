package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
        val id = cards.save(Card(last4 = "1234", name = "Millennia", statementDay = 15, dueDay = 5, rewardBp = 150))
        assertEquals(Card(id, "1234", "Millennia", 15, 5, 150), cards.all().single())
        cards.save(Card(id = id, last4 = "1234", name = "Millennia", statementDay = 16, dueDay = 6))
        assertEquals(16, cards.all().single().statementDay)
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
