package com.sowerrrt.dayloom.core.model

import org.junit.Assert.assertEquals
import org.junit.Test

class HabitPeriodStatsTest {
    @Test
    fun `period stats count only scheduled completions`() {
        val habit =
            Habit(
                id = EntityId("habit"),
                title = "Walk",
                createdAtEpochMillis = 0L,
                startEpochDay = 100,
                scheduledWeekdays = emptySet(),
                repeatEveryDays = 2,
                completedEpochDays = setOf(100, 101, 104),
            )

        val stats = habit.periodStats(100, 105)

        assertEquals(3, stats.scheduledCount)
        assertEquals(2, stats.completedCount)
        assertEquals(66, stats.completionPercent)
    }

    @Test
    fun `combined stats aggregate habits`() {
        val first = habit("first", setOf(10L, 11L))
        val second = habit("second", setOf(10L))

        val stats = listOf(first, second).periodStats(10, 11)

        assertEquals(4, stats.scheduledCount)
        assertEquals(3, stats.completedCount)
        assertEquals(75, stats.completionPercent)
    }

    private fun habit(
        id: String,
        completed: Set<Long>,
    ) = Habit(
        id = EntityId(id),
        title = id,
        createdAtEpochMillis = 0L,
        startEpochDay = 10,
        scheduledWeekdays = Weekday.entries.toSet(),
        completedEpochDays = completed,
    )
}
