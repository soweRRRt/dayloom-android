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
    val triggerAtEpochMillis: Long,
    val title: String,
)

interface NotificationScheduler {
    suspend fun schedule(notification: ScheduledNotification): Result<Unit>

    suspend fun cancel(id: NotificationId): Result<Unit>

    suspend fun rescheduleAll(notifications: List<ScheduledNotification>): Result<Unit>
}
