package com.pft.financetracker.data.goals

import com.pft.financetracker.data.local.GoalContributionEntity
import com.pft.financetracker.data.local.GoalDao
import com.pft.financetracker.data.local.GoalEntity
import com.pft.financetracker.domain.goals.Goal
import com.pft.financetracker.domain.goals.GoalMath
import com.pft.financetracker.domain.goals.GoalProgress
import java.time.LocalDate

/** Savings goals and the money put toward them. */
class GoalService(private val dao: GoalDao) {
    suspend fun save(g: Goal): Long = if (g.id == 0L) dao.insert(g.toEntity()) else { dao.update(g.toEntity()); g.id }
    suspend fun delete(id: Long) = dao.delete(id)
    suspend fun contribute(goalId: Long, amountPaise: Long) = dao.contribute(GoalContributionEntity(goalId = goalId, amountPaise = amountPaise))

    fun progressOf(goals: List<GoalEntity>, contributions: List<GoalContributionEntity>, today: LocalDate): List<GoalProgress> {
        val byGoal = contributions.groupBy({ it.goalId }, { it.amountPaise })
        return goals.map { GoalMath.progress(it.toDomain(), byGoal[it.id].orEmpty(), today) }
    }
}

fun GoalEntity.toDomain() = Goal(id, name, targetPaise, targetDay?.let { LocalDate.ofEpochDay(it) }, LocalDate.ofEpochDay(startDay))
fun Goal.toEntity() = GoalEntity(id, name, targetPaise, targetDate?.toEpochDay(), startDate.toEpochDay())
