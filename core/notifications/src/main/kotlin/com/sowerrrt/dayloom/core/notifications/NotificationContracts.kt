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
    val reminderOffsetMinutes: Int = 0,
    val weeklyRepeat: WeeklyNotificationRepeat? = null,
    val intervalRepeat: IntervalNotificationRepeat? = null,
    val monthlyRepeat: MonthlyNotificationRepeat? = null,
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

data class IntervalNotificationRepeat(
    val days: Int,
    val minutesOfDay: Int,
) {
    init {
        require(days > 0)
        require(minutesOfDay in 0 until 24 * 60)
    }
}

data class MonthlyNotificationRepeat(
    val daysOfMonth: Set<Int>,
    val minutesOfDay: Int,
) {
    init {
        require(daysOfMonth.isNotEmpty() && daysOfMonth.all { it in 1..31 })
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
