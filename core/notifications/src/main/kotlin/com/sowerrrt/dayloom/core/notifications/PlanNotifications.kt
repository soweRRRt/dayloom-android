package com.sowerrrt.dayloom.core.notifications

import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.isCompletedOn
import com.sowerrrt.dayloom.core.model.occursOn
import java.time.LocalDate
import java.time.ZoneId

fun List<PlanItem>.activePlanReminders(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledNotification> =
    mapNotNull { plan ->
        if (!plan.reminderEnabled) return@mapNotNull null
        val minutes = plan.reminderMinutesOfDay ?: return@mapNotNull null
        val today =
            java.time.Instant
                .ofEpochMilli(nowEpochMillis)
                .atZone(zoneId)
                .toLocalDate()
                .toEpochDay()
        val occurrence =
            (today..today + MAX_REMINDER_LOOKAHEAD_DAYS).firstOrNull { day ->
                if (!plan.occursOn(day) || plan.isCompletedOn(day)) return@firstOrNull false
                triggerAt(day, minutes, zoneId) > nowEpochMillis
            } ?: return@mapNotNull null
        ScheduledNotification(
            id = NotificationId("plan:${plan.id.value}"),
            scope = NotificationScope.PLANS,
            triggerAtEpochMillis = triggerAt(occurrence, minutes, zoneId),
            title = plan.title,
        )
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
