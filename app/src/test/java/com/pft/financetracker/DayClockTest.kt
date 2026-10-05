package com.pft.financetracker

import com.pft.financetracker.domain.reminders.DayClock
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId

class DayClockTest {
    private val zone = ZoneId.of("Asia/Kolkata")

    @Test fun waitsUntilJustAfterMidnight() {
        val now = LocalDate.of(2026, 10, 5).atTime(23, 59, 0).atZone(zone).toInstant().toEpochMilli()
        assertEquals(61_000L, DayClock.millisToNextDay(now, zone))   // 60 s to midnight + 1 s margin
    }

    @Test fun justAfterMidnightWaitsAlmostADay() {
        val now = LocalDate.of(2026, 10, 6).atStartOfDay(zone).toInstant().toEpochMilli() + 500
        assertEquals(86_400_000L - 500 + 1_000, DayClock.millisToNextDay(now, zone))
    }
}
