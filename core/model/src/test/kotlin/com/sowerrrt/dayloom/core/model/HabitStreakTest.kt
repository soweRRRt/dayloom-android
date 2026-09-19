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

    @Test
    fun `interval schedule repeats from its start day`() {
        val start = LocalDate.of(2026, 9, 1).toEpochDay()
        val habit =
            Habit(
                id = EntityId("interval"),
                title = "Water plants",
                createdAtEpochMillis = 0L,
                startEpochDay = start,
                scheduledWeekdays = emptySet(),
                repeatEveryDays = 10,
                completedEpochDays = setOf(start, start + 10, start + 20),
            )

        assertTrue(habit.isScheduledOn(start))
        assertFalse(habit.isScheduledOn(start + 9))
        assertTrue(habit.isScheduledOn(start + 10))
        assertEquals(3, habit.currentStreak(start + 20))
        assertEquals(3, habit.bestStreak())
    }

    @Test
    fun `monthly schedule supports several month days`() {
        val start = LocalDate.of(2026, 1, 1).toEpochDay()
        val habit =
            Habit(
                id = EntityId("monthly"),
                title = "Review budget",
                createdAtEpochMillis = 0L,
                startEpochDay = start,
                scheduledWeekdays = emptySet(),
                scheduledMonthDays = setOf(3, 15),
            )

        assertTrue(habit.isScheduledOn(LocalDate.of(2026, 2, 3).toEpochDay()))
        assertTrue(habit.isScheduledOn(LocalDate.of(2026, 2, 15).toEpochDay()))
        assertFalse(habit.isScheduledOn(LocalDate.of(2026, 2, 14).toEpochDay()))
    }
}
