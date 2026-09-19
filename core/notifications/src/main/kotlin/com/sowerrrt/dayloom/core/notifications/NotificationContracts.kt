package com.sowerrrt.dayloom.core.notifications

import java.util.UUID

@JvmInline
value class NotificationId(
    val value: String,
) {
    companion object {
        fun random(): NotificationId = NotificationId(UUID.randomUUID().toString())
    }
}

data class ScheduledNotification(
    val id: NotificationId,
    val scope: NotificationScope,
    val triggerAtEpochMillis: Long,
    val title: String,
    val weeklyRepeat: WeeklyNotificationRepeat? = null,
)

@JvmInline
value class NotificationScope(
    val value: String,
) {
    companion object {
        val PLANS = NotificationScope("plans")
        val HABITS = NotificationScope("habits")
    }
}

data class WeeklyNotificationRepeat(
    val isoWeekdays: Set<Int>,
    val minutesOfDay: Int,
) {
    init {
        require(isoWeekdays.isNotEmpty() && isoWeekdays.all { it in 1..7 })
        require(minutesOfDay in 0 until 24 * 60)
    }
}

interface NotificationScheduler {
    suspend fun schedule(notification: ScheduledNotification): Result<Unit>

    suspend fun cancel(id: NotificationId): Result<Unit>

    suspend fun rescheduleAll(
        scope: NotificationScope,
        notifications: List<ScheduledNotification>,
    ): Result<Unit>
}
