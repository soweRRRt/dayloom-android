package com.sowerrrt.dayloom.core.notifications

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.Weekday
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset

class HabitNotificationsTest {
    @Test
    fun `habit creates every configured advance reminder`() {
        val date = LocalDate.of(2026, 9, 21)
        val now = date.atTime(6, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val habit =
            Habit(
                id = EntityId("water"),
                title = "Drink water",
                createdAtEpochMillis = 1L,
                startEpochDay = date.toEpochDay(),
                scheduledWeekdays = Weekday.entries.toSet(),
                reminderMinutesOfDay = 9 * 60,
                reminderOffsetsMinutes = setOf(0, 15, 30),
            )

        val reminders = listOf(habit).activeHabitReminders(now, ZoneOffset.UTC)

        assertEquals(listOf(0, 15, 30), reminders.map { it.reminderOffsetMinutes })
        assertEquals(
            listOf("habit:water:0", "habit:water:15", "habit:water:30"),
            reminders.map { it.id.value },
        )
        assertEquals(
            listOf("09:00", "08:45", "08:30"),
            reminders.map {
                Instant
                    .ofEpochMilli(it.triggerAtEpochMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalTime()
                    .toString()
            },
        )
    }

    @Test
    fun `plan creates every configured advance reminder`() {
        val date = LocalDate.of(2026, 9, 21)
        val now = date.atTime(6, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val plan =
            PlanItem(
                id = EntityId("weigh-in"),
                title = "Weigh-in",
                dateEpochDay = date.toEpochDay(),
                createdAtEpochMillis = 1L,
                reminderMinutesOfDay = 8 * 60,
                reminderEnabled = true,
                reminderOffsetsMinutes = setOf(0, 30),
            )

        val reminders = listOf(plan).activePlanReminders(now, ZoneOffset.UTC)

        assertEquals(listOf(0, 30), reminders.map { it.reminderOffsetMinutes })
        assertEquals(listOf("plan:weigh-in:0", "plan:weigh-in:30"), reminders.map { it.id.value })
        assertEquals(
            listOf("08:00", "07:30"),
            reminders.map {
                Instant
                    .ofEpochMilli(it.triggerAtEpochMillis)
                    .atZone(ZoneOffset.UTC)
                    .toLocalTime()
                    .toString()
            },
        )
    }

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

    @Test
    fun `interval habit reminder keeps its exact time`() {
        val start = LocalDate.of(2026, 9, 19)
        val now = start.atTime(4, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val habit =
            Habit(
                id = EntityId("interval-habit"),
                title = "Water plants",
                createdAtEpochMillis = 1L,
                startEpochDay = start.toEpochDay(),
                scheduledWeekdays = emptySet(),
                repeatEveryDays = 10,
                reminderMinutesOfDay = 5 * 60,
            )

        val reminder = listOf(habit).activeHabitReminders(now, ZoneOffset.UTC).single()

        assertEquals(start.atTime(5, 0).toInstant(ZoneOffset.UTC).toEpochMilli(), reminder.triggerAtEpochMillis)
        assertEquals(10, reminder.intervalRepeat?.days)
        assertEquals(5 * 60, reminder.intervalRepeat?.minutesOfDay)
        assertEquals(null, reminder.weeklyRepeat)
        assertEquals(
            Instant.parse("2026-09-29T05:00:00Z").toEpochMilli(),
            nextIntervalTrigger(reminder.triggerAtEpochMillis, 10, 5 * 60, ZoneOffset.UTC),
        )
    }

    @Test
    fun `monthly trigger advances to the next configured month day`() {
        val current = Instant.parse("2026-09-15T05:00:00Z").toEpochMilli()

        val next = nextMonthlyTrigger(current, setOf(3, 15), 5 * 60, ZoneOffset.UTC)

        assertEquals(Instant.parse("2026-10-03T05:00:00Z").toEpochMilli(), next)
    }

    @Test
    fun `monthly habit reminder exposes its repeat rule`() {
        val today = LocalDate.of(2026, 9, 2)
        val now = today.atTime(12, 0).toInstant(ZoneOffset.UTC).toEpochMilli()
        val habit =
            Habit(
                id = EntityId("monthly-habit"),
                title = "Review budget",
                createdAtEpochMillis = 1L,
                startEpochDay = today.minusDays(10).toEpochDay(),
                scheduledWeekdays = emptySet(),
                scheduledMonthDays = setOf(3, 15),
                reminderMinutesOfDay = 5 * 60,
            )

        val reminder = listOf(habit).activeHabitReminders(now, ZoneOffset.UTC).single()

        assertEquals(Instant.parse("2026-09-03T05:00:00Z").toEpochMilli(), reminder.triggerAtEpochMillis)
        assertEquals(setOf(3, 15), reminder.monthlyRepeat?.daysOfMonth)
        assertEquals(5 * 60, reminder.monthlyRepeat?.minutesOfDay)
    }
}
