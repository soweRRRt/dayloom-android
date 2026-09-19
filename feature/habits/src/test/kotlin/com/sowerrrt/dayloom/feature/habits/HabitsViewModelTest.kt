package com.sowerrrt.dayloom.feature.habits

import android.net.Uri
import app.cash.turbine.test
import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.notifications.NotificationId
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.NotificationScope
import com.sowerrrt.dayloom.core.notifications.ScheduledNotification
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class HabitsViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `create and toggle update the screen state`() =
        runTest(dispatcher) {
            val repository = FakeHabitsRepository()
            val scheduler = FakeNotificationScheduler()
            val viewModel = HabitsViewModel(repository, scheduler, FakeAttachmentRepository())

            viewModel.uiState.test {
                assertTrue(awaitItem().isLoading)
                assertFalse(awaitItem().isLoading)

                viewModel.createHabit("Drink water", Weekday.entries.toSet(), 9 * 60)
                val created = awaitItem()
                assertEquals("Drink water", created.habits.single().title)
                assertEquals(9 * 60, created.habits.single().reminderMinutesOfDay)
                assertEquals(NotificationScope.HABITS, scheduler.scope)
                assertEquals(1, scheduler.notifications.size)

                viewModel.toggleCompletion(created.habits.single().id)
                assertEquals(1, awaitItem().completedToday)
            }
        }

    @Test
    fun `habit image is attached removed and its private file is cleaned up`() =
        runTest(dispatcher) {
            val repository = FakeHabitsRepository()
            val attachments = FakeAttachmentRepository()
            repository.createHabit("Stretch", Weekday.entries.toSet(), 20_000L, null)
            val id = repository.loadHabits().single().id
            repository.setImage(
                id,
                AttachmentRef(EntityId("attachment-1"), "habit.png", "image/png"),
            )
            val viewModel = HabitsViewModel(repository, FakeNotificationScheduler(), attachments)
            runCurrent()
            assertEquals("/private/habit.png", viewModel.uiState.value.imagePaths[id])

            viewModel.removeImage(id)
            runCurrent()
            assertEquals(
                null,
                viewModel.uiState.value.habits
                    .single()
                    .image,
            )
            assertEquals(1, attachments.deleted.size)
        }
}

private class FakeHabitsRepository : HabitsRepository {
    private var habits = emptyList<Habit>()

    override suspend fun loadHabits(): List<Habit> = habits

    override suspend fun createHabit(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        startEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<Habit> {
        habits =
            habits +
            Habit(
                id = EntityId("habit-1"),
                title = title,
                createdAtEpochMillis = 1L,
                startEpochDay = startEpochDay,
                scheduledWeekdays = scheduledWeekdays,
                reminderMinutesOfDay = reminderMinutesOfDay,
            )
        return habits
    }

    override suspend fun updateHabit(
        id: EntityId,
        title: String,
        scheduledWeekdays: Set<Weekday>,
        reminderMinutesOfDay: Int?,
    ): List<Habit> {
        habits =
            habits.map {
                if (it.id == id) {
                    it.copy(
                        title = title,
                        scheduledWeekdays = scheduledWeekdays,
                        reminderMinutesOfDay = reminderMinutesOfDay,
                    )
                } else {
                    it
                }
            }
        return habits
    }

    override suspend fun archiveHabit(id: EntityId): List<Habit> {
        habits = habits.filterNot { it.id == id }
        return habits
    }

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<Habit> {
        habits = habits.map { habit -> if (habit.id == id) habit.copy(image = image) else habit }
        return habits
    }

    override suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<Habit> {
        habits =
            habits.map { habit ->
                if (habit.id == id) {
                    habit.copy(completedEpochDays = setOf(epochDay))
                } else {
                    habit
                }
            }
        return habits
    }
}

private class FakeAttachmentRepository : AttachmentRepository {
    val deleted = mutableListOf<AttachmentRef>()

    override suspend fun importImage(uri: Uri): AttachmentRef =
        AttachmentRef(EntityId("attachment-1"), "habit.png", "image/png")

    override suspend fun importImage(
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): AttachmentRef = error("Not needed")

    override suspend fun delete(attachment: AttachmentRef): Boolean {
        deleted += attachment
        return true
    }

    override fun localPath(attachment: AttachmentRef): String = "/private/${attachment.displayName}"
}

private class FakeNotificationScheduler : NotificationScheduler {
    var scope: NotificationScope? = null
    var notifications = emptyList<ScheduledNotification>()

    override suspend fun schedule(notification: ScheduledNotification): Result<Unit> = Result.success(Unit)

    override suspend fun cancel(id: NotificationId): Result<Unit> = Result.success(Unit)

    override suspend fun rescheduleAll(
        scope: NotificationScope,
        notifications: List<ScheduledNotification>,
    ): Result<Unit> {
        this.scope = scope
        this.notifications = notifications
        return Result.success(Unit)
    }
}
