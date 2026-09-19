package com.sowerrrt.dayloom.core.notifications

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HabitNotificationsTest {
    @Test
    fun `next habit reminder skips a day already completed`() {
        val saturday = LocalDate.of(2026, 9, 19)
        val now = saturday.atTime(7, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val habit =
            Habit(
                id = EntityId("habit-1"),
                title = "Stretch",
                createdAtEpochMillis = 1L,
                startEpochDay = saturday.minusDays(10).toEpochDay(),
                scheduledWeekdays = setOf(Weekday.SATURDAY, Weekday.SUNDAY),
                reminderMinutesOfDay = 9 * 60,
                completedEpochDays = setOf(saturday.toEpochDay()),
            )

        val reminder = listOf(habit).activeHabitReminders(now, ZoneOffset.UTC).single()

        assertEquals(
            saturday
                .plusDays(1)
                .atTime(9, 0)
                .toInstant(ZoneOffset.UTC)
                .toEpochMilli(),
            reminder.triggerAtEpochMillis,
        )
        assertEquals(setOf(6, 7), reminder.weeklyRepeat?.isoWeekdays)
    }

    @Test
    fun `weekly trigger always advances after the current occurrence`() {
        val current = Instant.parse("2026-09-19T09:00:00Z").toEpochMilli()

        val next = nextWeeklyTrigger(current, setOf(6), 9 * 60, ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-09-26T09:00:00Z").toEpochMilli(), next)
    }
}
