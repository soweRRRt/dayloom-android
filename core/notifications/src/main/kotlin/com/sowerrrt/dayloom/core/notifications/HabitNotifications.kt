package com.sowerrrt.dayloom.core.notifications

import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.isScheduledOn
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

fun List<Habit>.activeHabitReminders(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledNotification> {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    return flatMap { habit ->
        val minutes = habit.reminderMinutesOfDay ?: return@flatMap emptyList()
        val intervalDays = habit.repeatEveryDays
        val offsets = habit.reminderOffsetsMinutes.ifEmpty { setOf(0) }.sorted()
        val searchDays =
            when {
                habit.scheduledMonthDays.isNotEmpty() -> 377
                intervalDays != null -> intervalDays + 8
                else -> 15
            }
        offsets.mapNotNull { reminderOffset ->
            val nextTrigger =
                (0..searchDays).firstNotNullOfOrNull { dayOffset ->
                    val date = today.plusDays(dayOffset.toLong())
                    val epochDay = date.toEpochDay()
                    if (!habit.isScheduledOn(epochDay) || epochDay in habit.completedEpochDays) {
                        return@firstNotNullOfOrNull null
                    }
                    eventTriggerAt(date, minutes, zoneId)
                        .minus(reminderOffset * 60_000L)
                        .takeIf { it > nowEpochMillis }
                } ?: return@mapNotNull null
            ScheduledNotification(
                id = NotificationId("habit:${habit.id.value}:$reminderOffset"),
                scope = NotificationScope.HABITS,
                triggerAtEpochMillis = nextTrigger,
                title = habit.title,
                reminderOffsetMinutes = reminderOffset,
                weeklyRepeat =
                    if (habit.repeatEveryDays == null && habit.scheduledMonthDays.isEmpty()) {
                        WeeklyNotificationRepeat(
                            isoWeekdays = habit.scheduledWeekdays.mapTo(mutableSetOf()) { it.ordinal + 1 },
                            minutesOfDay = minutes,
                        )
                    } else {
                        null
                    },
                intervalRepeat = habit.repeatEveryDays?.let { IntervalNotificationRepeat(it, minutes) },
                monthlyRepeat =
                    habit.scheduledMonthDays
                        .takeIf(Set<Int>::isNotEmpty)
                        ?.let { MonthlyNotificationRepeat(it, minutes) },
            )
        }
    }
}

private fun eventTriggerAt(
    date: LocalDate,
    minutes: Int,
    zoneId: ZoneId,
): Long =
    date
        .atTime(minutes / 60, minutes % 60)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
