package com.sowerrrt.dayloom.core.notifications

import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.isCompletedOn
import com.sowerrrt.dayloom.core.model.occursOn
import java.time.LocalDate
import java.time.ZoneId

fun List<PlanItem>.activePlanReminders(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledNotification> =
    flatMap { plan ->
        if (!plan.reminderEnabled) return@flatMap emptyList()
        val minutes = plan.reminderMinutesOfDay ?: return@flatMap emptyList()
        val today =
            java.time.Instant
                .ofEpochMilli(nowEpochMillis)
                .atZone(zoneId)
                .toLocalDate()
                .toEpochDay()
        plan.reminderOffsetsMinutes.ifEmpty { setOf(0) }.sorted().mapNotNull { reminderOffset ->
            val occurrence =
                (today..today + MAX_REMINDER_LOOKAHEAD_DAYS).firstOrNull { day ->
                    if (!plan.occursOn(day) || plan.isCompletedOn(day)) return@firstOrNull false
                    triggerAt(day, minutes, zoneId) - reminderOffset * 60_000L > nowEpochMillis
                } ?: return@mapNotNull null
            ScheduledNotification(
                id = NotificationId("plan:${plan.id.value}:$reminderOffset"),
                scope = NotificationScope.PLANS,
                triggerAtEpochMillis = triggerAt(occurrence, minutes, zoneId) - reminderOffset * 60_000L,
                title = plan.title,
                reminderOffsetMinutes = reminderOffset,
                weeklyRepeat = plan.weeklyRepeat(minutes),
                intervalRepeat = plan.repeatEveryDays?.let { IntervalNotificationRepeat(it, minutes) },
                monthlyRepeat = plan.monthlyRepeat(minutes),
            )
        }
    }

private fun PlanItem.weeklyRepeat(minutes: Int): WeeklyNotificationRepeat? {
    val weekdays =
        when {
            scheduledWeekdays.isNotEmpty() -> scheduledWeekdays.mapTo(mutableSetOf()) { it.ordinal + 1 }
            repeat == PlanRepeat.DAILY -> (1..7).toSet()
            repeat == PlanRepeat.WEEKLY -> setOf(LocalDate.ofEpochDay(dateEpochDay).dayOfWeek.value)
            else -> emptySet()
        }
    return weekdays.takeIf(Set<Int>::isNotEmpty)?.let { WeeklyNotificationRepeat(it, minutes) }
}

private fun PlanItem.monthlyRepeat(minutes: Int): MonthlyNotificationRepeat? {
    val monthDays =
        when {
            scheduledMonthDays.isNotEmpty() -> scheduledMonthDays
            repeat == PlanRepeat.MONTHLY -> setOf(LocalDate.ofEpochDay(dateEpochDay).dayOfMonth)
            else -> emptySet()
        }
    return monthDays.takeIf(Set<Int>::isNotEmpty)?.let { MonthlyNotificationRepeat(it, minutes) }
}

private fun triggerAt(
    epochDay: Long,
    minutes: Int,
    zoneId: ZoneId,
): Long =
    LocalDate
        .ofEpochDay(epochDay)
        .atStartOfDay(zoneId)
        .plusMinutes(minutes.toLong())
        .toInstant()
        .toEpochMilli()

private const val MAX_REMINDER_LOOKAHEAD_DAYS = 3660L
