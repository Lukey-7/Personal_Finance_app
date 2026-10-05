package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.sms.TemplateStore
import com.pft.financetracker.domain.model.Category
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.model.Transaction
import com.pft.financetracker.domain.model.TransactionType
import com.pft.financetracker.domain.parser.ParseResult
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class TemplateStoreTest {
    private lateinit var db: AppDatabase
    private val body = "Zeta: INR 1,250.00 gone, ZETA MART, a/c XX1234, 03-Oct-26"
    private val confirmed = Transaction(amountPaise = 1_25_000, type = TransactionType.DEBIT, merchant = "ZETA MART", category = Category.SHOPPING,
        timestamp = 1L, bankName = null, accountRef = null, source = Transaction.Source.SMS, flow = Flow.EXPENSE)

    @Before fun setUp() { db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build() }
    @After fun tearDown() = db.close()

    @Test fun aConfirmedReviewTeachesTheParser() = runBlocking {
        val store = TemplateStore(db.templateDao())
        assertTrue(store.learn("VM-ZETABK-S", body, confirmed))
        val parser = SmsParser(templates = { store.current })
        val r = parser.parse(SmsMessage("VM-ZETABK-S", "Zeta: INR 89.50 gone, CORNER SHOP, a/c XX1234, 05-Nov-26", 1L))
        assertEquals(8_950L, (r as ParseResult.Success).transaction.amountPaise)
    }

    @Test fun templatesSurviveARestartAndAreNotDuplicated() = runBlocking {
        TemplateStore(db.templateDao()).apply { learn("VM-ZETABK-S", body, confirmed); learn("AX-ZETABK", body, confirmed) }
        val again = TemplateStore(db.templateDao()).apply { load() }
        assertEquals(1, again.current.size)
    }

    @Test fun aDeletedTemplateIsForgotten() = runBlocking {
        val store = TemplateStore(db.templateDao())
        store.learn("VM-ZETABK-S", body, confirmed)
        store.delete(db.templateDao().getAll().single().id)
        assertTrue(store.current.isEmpty())
    }

    @Test fun nothingIsStoredWhenTheMessageCannotTeach() = runBlocking {
        val store = TemplateStore(db.templateDao())
        assertFalse(store.learn("VM-ZETABK-S", "Payment done", confirmed))
        assertTrue(db.templateDao().getAll().isEmpty())
    }
}
