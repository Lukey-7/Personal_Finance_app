package com.pft.financetracker

import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.ledger.Ledger
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.data.split.SplitEngine
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The split engine's lock: taken before the database, and never waited on by the coroutine that already holds it. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], application = android.app.Application::class)
class SplitEngineLockTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: SplitEngine
    private lateinit var transactor: Ledger.Transactor

    @Before fun setUp() {
        val ctx = ApplicationProvider.getApplicationContext<android.app.Application>()
        db = Room.inMemoryDatabaseBuilder(ctx, AppDatabase::class.java).allowMainThreadQueries().build()
        transactor = object : Ledger.Transactor {
            override suspend fun <T> run(block: suspend () -> T): T = db.withTransaction { block() }
        }
        engine = SplitEngine(db.transactionDao(), db.splitDao(), null, { false }, { "Me" }, transactor = transactor)
    }

    @After fun tearDown() = db.close()

    @Test fun aManualSaveHoldingTheLockCanStillReleaseThePayment() = runBlocking {
        // saveSplit's shape: lock, then transaction, then an engine action inside it. Before v1.5.1 this waited forever.
        withTimeout(5_000) { engine.holding { transactor.run { engine.releaseForManual(42L) } } }
    }

    @Test fun anActionWaitsForTheHolderAndThenRuns() = runBlocking {
        val release = CompletableDeferred<Unit>()
        val entered = CompletableDeferred<Unit>()
        val holder = async(Dispatchers.Default) { engine.holding { entered.complete(Unit); release.await() } }
        entered.await()
        val waiting = async(Dispatchers.Default) { engine.deleteManual(7L); "done" }
        Thread.sleep(200)
        assertFalse(waiting.isCompleted)
        release.complete(Unit)
        holder.await()
        assertEquals("done", withTimeout(5_000) { waiting.await() })
    }
}
