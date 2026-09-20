package com.sowerrrt.dayloom.core.notifications

import com.sowerrrt.dayloom.core.model.PlanItem
import java.time.LocalDate
import java.time.ZoneId

fun List<PlanItem>.activePlanReminders(
    nowEpochMillis: Long = System.currentTimeMillis(),
    zoneId: ZoneId = ZoneId.systemDefault(),
): List<ScheduledNotification> =
    mapNotNull { plan ->
        val minutes = plan.reminderMinutesOfDay ?: return@mapNotNull null
        val trigger =
            LocalDate
                .ofEpochDay(plan.dateEpochDay)
                .atStartOfDay(zoneId)
                .plusMinutes(minutes.toLong())
                .toInstant()
                .toEpochMilli()
        if (plan.completed || trigger <= nowEpochMillis) {
            null
        } else {
            ScheduledNotification(
                id = NotificationId("plan:${plan.id.value}"),
                scope = NotificationScope.PLANS,
                triggerAtEpochMillis = trigger,
                title = plan.title,
            )
        }
    }
