package com.sowerrrt.dayloom.core.notifications

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import java.time.Instant
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class WorkManagerNotificationScheduler(
    private val context: Context,
    private val clock: () -> Long = System::currentTimeMillis,
) : NotificationScheduler {
    private val workManager: WorkManager
        get() = WorkManager.getInstance(context)

    override suspend fun schedule(notification: ScheduledNotification): Result<Unit> =
        runCatching {
            if (notification.triggerAtEpochMillis <= clock()) {
                workManager.cancelUniqueWork(notification.workName())
                return@runCatching
            }
            workManager.enqueueUniqueWork(
                notification.workName(),
                ExistingWorkPolicy.REPLACE,
                notification.toWorkRequest(clock()),
            )
        }

    override suspend fun cancel(id: NotificationId): Result<Unit> =
        runCatching { workManager.cancelUniqueWork(id.workName()) }

    override suspend fun rescheduleAll(
        scope: NotificationScope,
        notifications: List<ScheduledNotification>,
    ): Result<Unit> =
        runCatching {
            require(notifications.all { it.scope == scope }) { "Notification scope does not match" }
            workManager.cancelAllWorkByTag(scope.workTag())
            notifications
                .filter { it.triggerAtEpochMillis > clock() }
                .forEach { notification ->
                    workManager.enqueueUniqueWork(
                        notification.workName(),
                        ExistingWorkPolicy.REPLACE,
                        notification.toWorkRequest(clock()),
                    )
                }
        }
}

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val scope = inputData.getString(KEY_SCOPE)?.let(::NotificationScope) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
        postNotification(id, title)
        scheduleNextOccurrence(id, scope, title)
        return Result.success()
    }

    private fun postNotification(
        id: String,
        title: String,
    ) {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        createChannel()
        val launchIntent = applicationContext.packageManager.getLaunchIntentForPackage(applicationContext.packageName)
        val contentIntent =
            launchIntent?.let { intent ->
                intent.addFlags(
                    android.content.Intent.FLAG_ACTIVITY_CLEAR_TOP or android.content.Intent.FLAG_ACTIVITY_SINGLE_TOP,
                )
                PendingIntent.getActivity(
                    applicationContext,
                    id.hashCode(),
                    intent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                )
            }
        val notification =
            NotificationCompat
                .Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_popup_reminder)
                .setContentTitle(title)
                .setContentText(applicationContext.getString(R.string.notification_reminder_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
    }

    private fun scheduleNextOccurrence(
        id: String,
        scope: NotificationScope,
        title: String,
    ) {
        val minutes = inputData.getInt(KEY_REPEAT_MINUTES, NO_REPEAT)
        val intervalDays = inputData.getInt(KEY_REPEAT_INTERVAL_DAYS, NO_REPEAT)
        val previousTrigger = inputData.getLong(KEY_TRIGGER_AT, NO_TRIGGER)
        val monthDays =
            inputData
                .getString(KEY_REPEAT_MONTH_DAYS)
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
                ?.filter { it in 1..31 }
                ?.toSet()
                .orEmpty()
        val weekdays =
            inputData
                .getString(KEY_REPEAT_WEEKDAYS)
                ?.split(',')
                ?.mapNotNull(String::toIntOrNull)
                ?.filter { it in 1..7 }
                ?.toSet()
                .orEmpty()
        if (minutes !in 0 until 24 * 60) return

        val now = System.currentTimeMillis()
        val nextTrigger =
            when {
                monthDays.isNotEmpty() -> nextMonthlyTrigger(now, monthDays, minutes)
                intervalDays > 0 -> {
                    var candidate = nextIntervalTrigger(previousTrigger.takeIf { it > 0 } ?: now, intervalDays, minutes)
                    while (candidate <= now) candidate = nextIntervalTrigger(candidate, intervalDays, minutes)
                    candidate
                }
                else -> {
                    if (weekdays.isEmpty()) return
                    nextWeeklyTrigger(now, weekdays, minutes)
                }
            }
        val next =
            ScheduledNotification(
                id = NotificationId(id),
                scope = scope,
                triggerAtEpochMillis = nextTrigger,
                title = title,
                weeklyRepeat = weekdays.takeIf { intervalDays <= 0 }?.let { WeeklyNotificationRepeat(it, minutes) },
                intervalRepeat =
                    intervalDays.takeIf { it > 0 }?.let { IntervalNotificationRepeat(it, minutes) },
                monthlyRepeat =
                    monthDays.takeIf(Set<Int>::isNotEmpty)?.let { MonthlyNotificationRepeat(it, minutes) },
            )
        WorkManager
            .getInstance(applicationContext)
            .enqueueUniqueWork(
                id.workName(),
                ExistingWorkPolicy.APPEND_OR_REPLACE,
                next.toWorkRequest(now),
            )
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_reminders_description)
            },
        )
    }
}

internal fun nextWeeklyTrigger(
    afterEpochMillis: Long,
    isoWeekdays: Set<Int>,
    minutesOfDay: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    require(isoWeekdays.isNotEmpty() && isoWeekdays.all { it in 1..7 })
    require(minutesOfDay in 0 until 24 * 60)
    val today = Instant.ofEpochMilli(afterEpochMillis).atZone(zoneId).toLocalDate()
    for (offset in 0..7) {
        val date = today.plusDays(offset.toLong())
        if (date.dayOfWeek.value !in isoWeekdays) continue
        val candidate =
            date
                .atTime(minutesOfDay / 60, minutesOfDay % 60)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        if (candidate > afterEpochMillis) return candidate
    }
    error("A weekly notification must have a future occurrence")
}

internal fun nextIntervalTrigger(
    afterEpochMillis: Long,
    intervalDays: Int,
    minutesOfDay: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    require(intervalDays > 0)
    require(minutesOfDay in 0 until 24 * 60)
    val nextDate =
        Instant
            .ofEpochMilli(afterEpochMillis)
            .atZone(zoneId)
            .toLocalDate()
            .plusDays(intervalDays.toLong())
    return nextDate
        .atTime(minutesOfDay / 60, minutesOfDay % 60)
        .atZone(zoneId)
        .toInstant()
        .toEpochMilli()
}

internal fun nextMonthlyTrigger(
    afterEpochMillis: Long,
    daysOfMonth: Set<Int>,
    minutesOfDay: Int,
    zoneId: ZoneId = ZoneId.systemDefault(),
): Long {
    require(daysOfMonth.isNotEmpty() && daysOfMonth.all { it in 1..31 })
    require(minutesOfDay in 0 until 24 * 60)
    val today = Instant.ofEpochMilli(afterEpochMillis).atZone(zoneId).toLocalDate()
    for (offset in 0..370) {
        val date = today.plusDays(offset.toLong())
        if (date.dayOfMonth !in daysOfMonth) continue
        val candidate =
            date
                .atTime(minutesOfDay / 60, minutesOfDay % 60)
                .atZone(zoneId)
                .toInstant()
                .toEpochMilli()
        if (candidate > afterEpochMillis) return candidate
    }
    error("A monthly notification must have a future occurrence")
}

private fun ScheduledNotification.toWorkRequest(nowEpochMillis: Long) =
    OneTimeWorkRequestBuilder<ReminderWorker>()
        .setInitialDelay(triggerAtEpochMillis - nowEpochMillis, TimeUnit.MILLISECONDS)
        .setInputData(
            Data
                .Builder()
                .putString(KEY_ID, id.value)
                .putString(KEY_SCOPE, scope.value)
                .putString(KEY_TITLE, title)
                .putLong(KEY_TRIGGER_AT, triggerAtEpochMillis)
                .putString(KEY_REPEAT_WEEKDAYS, weeklyRepeat?.isoWeekdays?.sorted()?.joinToString(","))
                .putInt(
                    KEY_REPEAT_MINUTES,
                    weeklyRepeat?.minutesOfDay
                        ?: intervalRepeat?.minutesOfDay
                        ?: monthlyRepeat?.minutesOfDay
                        ?: NO_REPEAT,
                ).putInt(KEY_REPEAT_INTERVAL_DAYS, intervalRepeat?.days ?: NO_REPEAT)
                .putString(KEY_REPEAT_MONTH_DAYS, monthlyRepeat?.daysOfMonth?.sorted()?.joinToString(","))
                .build(),
        ).addTag(REMINDER_TAG)
        .addTag(scope.workTag())
        .build()

private fun String.workName(): String = "$WORK_PREFIX$this"

private fun NotificationId.workName(): String = value.workName()

private fun ScheduledNotification.workName(): String = id.workName()

private fun NotificationScope.workTag(): String = "$SCOPE_TAG_PREFIX$value"

private const val CHANNEL_ID = "dayloom_plans"
private const val REMINDER_TAG = "dayloom_reminders"
private const val WORK_PREFIX = "dayloom_reminder_"
private const val SCOPE_TAG_PREFIX = "dayloom_reminders_"
private const val KEY_ID = "notification_id"
private const val KEY_SCOPE = "notification_scope"
private const val KEY_TITLE = "notification_title"
private const val KEY_TRIGGER_AT = "notification_trigger_at"
private const val KEY_REPEAT_WEEKDAYS = "notification_repeat_weekdays"
private const val KEY_REPEAT_MINUTES = "notification_repeat_minutes"
private const val KEY_REPEAT_INTERVAL_DAYS = "notification_repeat_interval_days"
private const val KEY_REPEAT_MONTH_DAYS = "notification_repeat_month_days"
private const val NO_REPEAT = -1
private const val NO_TRIGGER = -1L
