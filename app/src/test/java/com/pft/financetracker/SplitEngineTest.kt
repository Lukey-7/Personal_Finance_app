package com.pft.financetracker

import com.pft.financetracker.domain.books.Books
import com.pft.financetracker.domain.books.CountingRules
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.prefs.SettingsRepository
import com.pft.financetracker.data.repository.SmsLogRepository
import com.pft.financetracker.data.repository.SplitRepository
import com.pft.financetracker.data.repository.TransactionRepository
import com.pft.financetracker.data.sms.SmsImporter
import com.pft.financetracker.data.split.SplitEngine
import com.pft.financetracker.domain.insights.InsightsEngine
import com.pft.financetracker.domain.insights.Periods
import com.pft.financetracker.domain.model.CounterpartyKind
import com.pft.financetracker.domain.model.Flow
import com.pft.financetracker.domain.parser.SmsMessage
import com.pft.financetracker.domain.parser.SmsParser
import com.pft.financetracker.domain.split.SplitAiProvider
import com.pft.financetracker.domain.split.SplitAiRequest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Split intelligence end to end: real SMS through the parser and importer into Room, the engine applying and
 * undoing automatic splits, and the dashboard numbers that come out.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SplitEngineTest {
    private lateinit var db: AppDatabase
    private lateinit var repo: TransactionRepository
    private lateinit var importer: SmsImporter
    private lateinit var splits: SplitRepository
    private var aiCalls = 0
    private var aiAnswer: ((SplitAiRequest) -> String?)? = null
    private lateinit var engine: SplitEngine

    // Ronak's weekend, nine days ago (so every SMS date is in the past).
    private val sat = java.util.Calendar.getInstance().apply {
        add(java.util.Calendar.DAY_OF_YEAR, -9); set(java.util.Calendar.HOUR_OF_DAY, 0); set(java.util.Calendar.MINUTE, 0)
        set(java.util.Calendar.SECOND, 0); set(java.util.Calendar.MILLISECOND, 0)
    }.timeInMillis
    private fun at(day: Int, h: Int, m: Int = 0) = sat + day * 86_400_000L + h * 3_600_000L + m * 60_000L
    private fun d(t: Long) = SimpleDateFormat("dd-MM-yy", Locale.ENGLISH).format(t)
    private var ref = 426212345600L

    private fun debit(rupees: String, vpa: String, t: Long) = SmsMessage("VM-HDFCBK", "Rs.$rupees debited from a/c **1234 on ${d(t)} to VPA $vpa (UPI Ref No ${++ref}).", t)
    private fun credit(rupees: String, vpa: String, t: Long) = SmsMessage("VM-KOTAKB", "Received Rs.$rupees in your Kotak Bank AC X1234 from $vpa on ${d(t)}.UPI Ref:${++ref}.", t)

    private val friends = listOf("neha", "karan", "sneha", "vikram", "anjali", "rohan", "pooja", "arjun", "meera")

    private fun weekend() = listOf(
        debit("12000.00", "sbow.pay@ybl", at(0, 20, 10)),
        debit("600.00", "uber.india@axisbank", at(0, 23, 40)),
        debit("180.00", "rapido.bike@ybl", at(1, 10, 5)),
        credit("1000.00", "rahul@okicici", at(1, 11)),
        credit("1200.00", "priya@okaxis", at(1, 11, 20)),
        credit("200.00", "amit@oksbi", at(1, 12)),
    ) + friends.mapIndexed { i, n -> credit("1000.00", "$n@okhdfcbank", at(2, 9, i * 10)) } +
        SmsMessage("AD-SBIINB", "Dear Customer, your a/c no. XXXXX5678 is credited by Rs.85,000.00 on ${d(at(3, 10))} by ACME CORP SALARY (IMPS Ref no 987654321). -SBI", at(3, 10))

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        repo = TransactionRepository(db.transactionDao(), db.reviewDao())
        importer = SmsImporter(ctx, SmsParser(), repo, SmsLogRepository(db.smsLogDao()), SettingsRepository(ctx))
        splits = SplitRepository(db.splitDao())
        val fake = object : SplitAiProvider {
            override suspend fun judge(request: SplitAiRequest): String? { aiCalls++; return aiAnswer?.invoke(request) }
        }
        engine = SplitEngine(db.transactionDao(), db.splitDao(), fake, aiEnabled = { aiAnswer != null }, myName = { "Me" })
    }

    @After fun tearDown() { db.close() }

    private suspend fun byMerchant(m: String) = repo.getAll().first { it.merchant.equals(m, true) }
    private fun summary(all: List<com.pft.financetracker.domain.model.Transaction>) =
        Books.of(all).summary(Periods.custom(at(0, 0), at(6, 0)))

    @Test fun ronaksWeekendFromRealSms() = runBlocking {
        weekend().forEach { importer.process(it) }
        assertEquals(CounterpartyKind.PERSON, byMerchant("Rahul").counterpartyKind)
        assertEquals(CounterpartyKind.ORGANISATION, repo.getAll().first { it.amountPaise == 85_000_00L }.counterpartyKind)
        val before = summary(repo.getAll())
        assertEquals(12_780_00L, before.netSpendPaise)

        val r = engine.run()
        assertEquals(2, r.applied)
        assertEquals(1_000_00L, byMerchant("Sbow").amountPaise)
        assertEquals(12_000_00L, byMerchant("Sbow").originalAmountPaise)
        assertEquals(200_00L, byMerchant("Uber").amountPaise)
        assertEquals(180_00L, byMerchant("Rapido").amountPaise)
        assertEquals(Flow.SETTLEMENT, byMerchant("Priya").flow)
        assertEquals(Flow.INCOME, repo.getAll().first { it.amountPaise == 85_000_00L }.flow)

        val after = summary(repo.getAll())
        assertEquals("SBOW 1,000 + Uber 200 + Rapido 180", 1_380_00L, after.netSpendPaise)
        assertEquals("only the salary is income", 85_000_00L, after.incomePaise)
        // The Split tab shows both, applied, with the reasons.
        val ss = splits.all.first()
        assertEquals(2, ss.count { it.isAuto && !it.isSuggestion })
        assertTrue(ss.first { it.title == "Sbow" }.reasons.any { it.contains("11 people paid you back") })

        // Running again changes nothing.
        val again = engine.run()
        assertEquals(0, again.applied); assertEquals(0, again.undone)
        assertEquals(1_380_00L, summary(repo.getAll()).netSpendPaise)
    }

    @Test fun lateFriendJoinsTheGroupOnTheNextRun() = runBlocking {
        weekend().dropLast(2).forEach { importer.process(it) } // Meera hasn't paid yet; no salary
        engine.run()
        assertEquals(2_000_00L, byMerchant("Sbow").amountPaise)
        importer.process(credit("1000.00", "meera@okhdfcbank", at(4, 18)))
        engine.run()
        assertEquals(1_000_00L, byMerchant("Sbow").amountPaise)
        assertEquals(Flow.SETTLEMENT, byMerchant("Meera").flow)
    }

    @Test fun rejectRestoresEverythingAndIsRemembered() = runBlocking {
        weekend().forEach { importer.process(it) }
        engine.run()
        val sbow = splits.all.first().first { it.title == "Sbow" }
        engine.reject(sbow.id)
        assertEquals(12_000_00L, byMerchant("Sbow").amountPaise)
        assertEquals(null, byMerchant("Sbow").originalAmountPaise)
        assertEquals(Flow.INCOME, byMerchant("Rahul").flow)
        // Priya's transfer still pays for the Uber split, so it stays a settlement... but the SBOW part is back.
        engine.run()
        assertTrue("never split again", splits.all.first().none { it.title == "Sbow" })
        assertEquals(12_000_00L, byMerchant("Sbow").amountPaise)
    }

    @Test fun deletingAPaybackUndoesAndRecomputes() = runBlocking {
        weekend().forEach { importer.process(it) }
        engine.run()
        repo.delete(byMerchant("Neha"))
        engine.run()
        assertEquals(2_000_00L, byMerchant("Sbow").amountPaise)
    }

    @Test fun userEditedPaymentIsLeftAlone() = runBlocking {
        weekend().forEach { importer.process(it) }
        repo.update(byMerchant("Sbow").copy(userEdited = true))
        engine.run()
        assertEquals(12_000_00L, byMerchant("Sbow").amountPaise)
    }

    @Test fun aiIsAskedOnlyWhenUnsureAndCached() = runBlocking {
        // Uneven shares: the local rules cannot explain them.
        importer.process(debit("1800.00", "toit.brewpub@ybl", at(0, 21)))
        importer.process(credit("500.00", "rahul@okicici", at(1, 9)))
        importer.process(credit("700.00", "priya@okaxis", at(1, 10)))
        aiAnswer = { req ->
            assertTrue(!req.json.contains("rahul", true) && !req.json.contains("toit", true))
            """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":500},{"incoming":"C2","amount":700}],"people":3,"confidence":88,"reason":"Person A and Person B paid uneven shares"}]}"""
        }
        engine.run()
        assertEquals(1, aiCalls)
        assertEquals(600_00L, byMerchant("Toit").amountPaise)
        assertTrue(splits.all.first().single().reasons.single().contains("Rahul"))
    }

    @Test fun suggestionWaitsForYesThenApplies() = runBlocking {
        importer.process(debit("1200.00", "toit.brewpub@ybl", at(0, 21)))
        importer.process(credit("600.00", "priya@okaxis", at(1, 10)))
        val r = engine.run()
        assertEquals(1, r.suggested)
        assertEquals(1_200_00L, byMerchant("Toit").amountPaise)
        val s = splits.all.first().single()
        assertTrue(s.isSuggestion)
        engine.accept(s.id)
        assertEquals(600_00L, byMerchant("Toit").amountPaise)
        engine.run()
        assertEquals("accepted splits are frozen", 600_00L, byMerchant("Toit").amountPaise)
    }

    /**
     * Found on the emulator: asked about Ronak's weekend, the model put all of Priya's Rs 1,200 on SBOW and the app
     * could only suggest. What the rules explain exactly is no longer sent to the AI at all.
     */
    @Test fun exactLocalAnswersAreNotSentToTheAi() = runBlocking {
        weekend().forEach { importer.process(it) }
        aiAnswer = { """{"groups":[{"payment":"P1","allocations":[{"incoming":"C2","amount":1200}],"confidence":95}]}""" }
        val r = engine.run()
        assertEquals(0, aiCalls)
        assertEquals(2, r.applied)
        assertEquals(1_000_00L, byMerchant("Sbow").amountPaise)
    }

    /** Found on the emulator: a quick run without AI (after an undo, or a new SMS) must not undo an AI-found split. */
    @Test fun localOnlyRunKeepsAiFoundSplits() = runBlocking {
        importer.process(debit("1800.00", "toit.brewpub@ybl", at(0, 21)))
        importer.process(credit("500.00", "rahul@okicici", at(1, 9)))
        importer.process(credit("700.00", "priya@okaxis", at(1, 10)))
        aiAnswer = { """{"groups":[{"payment":"P1","allocations":[{"incoming":"C1","amount":500},{"incoming":"C2","amount":700}],"people":3,"confidence":88}]}""" }
        engine.run()
        assertEquals(600_00L, byMerchant("Toit").amountPaise)
        engine.run(useAi = false)
        assertEquals(600_00L, byMerchant("Toit").amountPaise)
        importer.process(credit("50.00", "neha@okhdfcbank", at(2, 9)))
        engine.run(useAi = false)
        assertEquals(600_00L, byMerchant("Toit").amountPaise)
    }
}
