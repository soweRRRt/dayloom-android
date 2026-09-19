package com.sowerrrt.dayloom.core.notifications

import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.isScheduledOn
import java.time.Instant
import java.time.ZoneId

fun List<Habit>.activeHabitReminders(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledNotification> {
    val today = Instant.ofEpochMilli(nowEpochMillis).atZone(zoneId).toLocalDate()
    return mapNotNull { habit ->
        val minutes = habit.reminderMinutesOfDay ?: return@mapNotNull null
        val intervalDays = habit.repeatEveryDays
        val searchDays =
            when {
                habit.scheduledMonthDays.isNotEmpty() -> 370
                intervalDays != null -> intervalDays + 1
                else -> 8
            }
        val nextTrigger =
            (0..searchDays).firstNotNullOfOrNull { offset ->
                val date = today.plusDays(offset.toLong())
                val epochDay = date.toEpochDay()
                if (!habit.isScheduledOn(epochDay) || epochDay in habit.completedEpochDays) {
                    return@firstNotNullOfOrNull null
                }
                date
                    .atTime(minutes / 60, minutes % 60)
                    .atZone(zoneId)
                    .toInstant()
                    .toEpochMilli()
                    .takeIf { it > nowEpochMillis }
            } ?: return@mapNotNull null
        ScheduledNotification(
            id = NotificationId("habit:${habit.id.value}"),
            scope = NotificationScope.HABITS,
            triggerAtEpochMillis = nextTrigger,
            title = habit.title,
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
