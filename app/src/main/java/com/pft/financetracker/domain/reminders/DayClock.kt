package com.pft.financetracker.domain.reminders

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/** Today's date as a flow that moves on just after midnight, so "due tomorrow" becomes "due today" without new data. */
object DayClock {
    /** Time until one second past the next local midnight. */
    fun millisToNextDay(now: Long, zone: ZoneId = ZoneId.systemDefault()): Long {
        val next = Instant.ofEpochMilli(now).atZone(zone).toLocalDate().plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return next - now + 1_000
    }

    fun today(zone: ZoneId = ZoneId.systemDefault()): Flow<LocalDate> = flow {
        while (true) {
            emit(LocalDate.now(zone))
            delay(millisToNextDay(System.currentTimeMillis(), zone))
        }
    }
}
