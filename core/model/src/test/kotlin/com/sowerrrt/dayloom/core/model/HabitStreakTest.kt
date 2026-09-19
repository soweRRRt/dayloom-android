package com.sowerrrt.dayloom.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class HabitStreakTest {
    @Test
    fun `weekly schedule counts consecutive scheduled days`() {
        val monday = LocalDate.of(2026, 9, 7).toEpochDay()
        val habit =
            Habit(
                id = EntityId("weekly"),
                title = "Weekly review",
                createdAtEpochMillis = 0L,
                startEpochDay = monday,
                scheduledWeekdays = setOf(Weekday.MONDAY),
                completedEpochDays = setOf(monday, monday + 7, monday + 14),
            )

        assertTrue(habit.isScheduledOn(monday + 7))
        assertFalse(habit.isScheduledOn(monday + 8))
        assertEquals(3, habit.currentStreak(monday + 14))
        assertEquals(3, habit.bestStreak())
    }

    @Test
    fun `missing a scheduled day breaks the streak`() {
        val monday = LocalDate.of(2026, 9, 7).toEpochDay()
        val habit =
            Habit(
                id = EntityId("daily"),
                title = "Read",
                createdAtEpochMillis = 0L,
                startEpochDay = monday,
                completedEpochDays = setOf(monday, monday + 1, monday + 3),
            )

        assertEquals(1, habit.currentStreak(monday + 3))
        assertEquals(2, habit.bestStreak())
    }
}
