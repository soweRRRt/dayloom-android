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

    override suspend fun rescheduleAll(notifications: List<ScheduledNotification>): Result<Unit> =
        runCatching {
            workManager.cancelAllWorkByTag(REMINDER_TAG)
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

    private fun ScheduledNotification.toWorkRequest(nowEpochMillis: Long) =
        OneTimeWorkRequestBuilder<ReminderWorker>()
            .setInitialDelay(triggerAtEpochMillis - nowEpochMillis, TimeUnit.MILLISECONDS)
            .setInputData(
                Data
                    .Builder()
                    .putString(KEY_ID, id.value)
                    .putString(KEY_TITLE, title)
                    .build(),
            ).addTag(REMINDER_TAG)
            .build()

    private fun ScheduledNotification.workName(): String = id.workName()

    private fun NotificationId.workName(): String = "$WORK_PREFIX$value"

    private companion object {
        const val WORK_PREFIX = "dayloom_reminder_"
    }
}

class ReminderWorker(
    appContext: Context,
    workerParams: WorkerParameters,
) : CoroutineWorker(appContext, workerParams) {
    override suspend fun doWork(): Result {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return Result.success()
        }
        createChannel()
        val id = inputData.getString(KEY_ID) ?: return Result.failure()
        val title = inputData.getString(KEY_TITLE) ?: return Result.failure()
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
                .setContentText(applicationContext.getString(R.string.notification_plan_body))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setCategory(NotificationCompat.CATEGORY_REMINDER)
                .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
                .setAutoCancel(true)
                .setContentIntent(contentIntent)
                .build()
        NotificationManagerCompat.from(applicationContext).notify(id.hashCode(), notification)
        return Result.success()
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = applicationContext.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                applicationContext.getString(R.string.notification_channel_plans),
                NotificationManager.IMPORTANCE_DEFAULT,
            ).apply {
                description = applicationContext.getString(R.string.notification_channel_plans_description)
            },
        )
    }
}

private const val CHANNEL_ID = "dayloom_plans"
private const val REMINDER_TAG = "dayloom_reminders"
private const val KEY_ID = "notification_id"
private const val KEY_TITLE = "notification_title"
