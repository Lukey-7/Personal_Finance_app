package com.pft.financetracker

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.pft.financetracker.data.goals.GoalService
import com.pft.financetracker.data.local.AppDatabase
import com.pft.financetracker.domain.goals.Goal
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
class GoalServiceTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), AppDatabase::class.java).allowMainThreadQueries().build()
    private val goals = GoalService(db.goalDao())
    @After fun tearDown() = db.close()

    @Test fun contributionsAddUpPerGoal() = runBlocking {
        val trip = goals.save(Goal(name = "Trip", targetPaise = 1_00_000_00, targetDate = LocalDate.of(2027, 6, 1), startDate = LocalDate.of(2026, 6, 1)))
        val phone = goals.save(Goal(name = "Phone", targetPaise = 50_000_00, targetDate = null, startDate = LocalDate.of(2026, 6, 1)))
        goals.contribute(trip, 10_000_00); goals.contribute(trip, -2_000_00); goals.contribute(phone, 5_000_00)
        val p = goals.progressOf(db.goalDao().getAll(), db.goalDao().allContributions(), LocalDate.of(2026, 10, 1))
        assertEquals(listOf(8_000_00L, 5_000_00L), p.map { it.savedPaise })
        assertEquals(LocalDate.of(2027, 6, 1), p[0].goal.targetDate)
    }

    @Test fun deletingAGoalDeletesItsMoney() = runBlocking {
        val trip = goals.save(Goal(name = "Trip", targetPaise = 1_00_000_00, targetDate = null))
        goals.contribute(trip, 10_000_00)
        goals.delete(trip)
        assertTrue(db.goalDao().allContributions().isEmpty())
    }
}
