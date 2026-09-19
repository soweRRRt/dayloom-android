package com.sowerrrt.dayloom.feature.planner

import android.net.Uri
import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.notifications.NotificationId
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.NotificationScope
import com.sowerrrt.dayloom.core.notifications.ScheduledNotification
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
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
import java.time.LocalDate
import java.time.ZoneId

@OptIn(ExperimentalCoroutinesApi::class)
class PlannerViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `calendar combines scheduled habits and persistent plans`() =
        runTest(dispatcher) {
            val selectedDay = LocalDate.now().toEpochDay()
            val habitsRepository = FakeHabitsRepository(selectedDay)
            val plannerRepository = FakePlannerRepository()
            val scheduler = FakeNotificationScheduler()
            val viewModel = PlannerViewModel(habitsRepository, plannerRepository, scheduler, FakeAttachmentRepository())

            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(
                "Drink water",
                viewModel.uiState.value.selectedHabits
                    .single()
                    .title,
            )

            viewModel.selectDate(selectedDay + 1)
            viewModel.createPlan("Call the doctor", 18 * 60)
            runCurrent()
            val created =
                viewModel.uiState.value.plans
                    .single()
            assertEquals("Call the doctor", created.title)
            assertEquals(18 * 60, created.reminderMinutesOfDay)
            assertEquals("Call the doctor", scheduler.notifications.single().title)

            viewModel.togglePlan(created.id)
            runCurrent()
            assertEquals(
                true,
                viewModel.uiState.value.plans
                    .single()
                    .completed,
            )
            assertTrue(scheduler.notifications.isEmpty())

            viewModel.selectDate(selectedDay)
            viewModel.toggleHabit(habitsRepository.habit.id)
            runCurrent()
            assertEquals(
                setOf(selectedDay),
                viewModel.uiState.value.selectedHabits
                    .single()
                    .completedEpochDays,
            )
        }

    @Test
    fun `deleting a plan also deletes its private image`() =
        runTest(dispatcher) {
            val selectedDay = LocalDate.now().toEpochDay()
            val plannerRepository = FakePlannerRepository()
            val attachments = FakeAttachmentRepository()
            plannerRepository.createPlan("Trip", selectedDay, null)
            val planId = plannerRepository.loadPlans().single().id
            plannerRepository.setImage(
                planId,
                AttachmentRef(EntityId("attachment-1"), "plan.png", "image/png"),
            )
            val viewModel =
                PlannerViewModel(
                    FakeHabitsRepository(selectedDay),
                    plannerRepository,
                    FakeNotificationScheduler(),
                    attachments,
                )
            runCurrent()

            assertEquals("/private/plan.png", viewModel.uiState.value.planImagePaths[planId])
            viewModel.deletePlan(planId)
            runCurrent()

            assertTrue(
                viewModel.uiState.value.plans
                    .isEmpty(),
            )
            assertEquals(1, attachments.deleted.size)
        }

    @Test
    fun `only future incomplete plans become reminders`() {
        val zone = ZoneId.of("UTC")
        val today = LocalDate.of(2030, 1, 2)
        val now =
            today
                .atTime(12, 0)
                .atZone(zone)
                .toInstant()
                .toEpochMilli()
        val plans =
            listOf(
                PlanItem(EntityId("past"), "Past", today.toEpochDay(), 1L, reminderMinutesOfDay = 9 * 60),
                PlanItem(EntityId("future"), "Future", today.toEpochDay(), 2L, reminderMinutesOfDay = 18 * 60),
                PlanItem(
                    EntityId("done"),
                    "Done",
                    today.plusDays(1).toEpochDay(),
                    3L,
                    completed = true,
                    reminderMinutesOfDay = 10 * 60,
                ),
            )

        val reminder = plans.activeReminders(now, zone).single()

        assertEquals(NotificationId("plan:future"), reminder.id)
        assertEquals("Future", reminder.title)
    }
}

private class FakeHabitsRepository(
    selectedDay: Long,
) : HabitsRepository {
    var habit =
        Habit(
            id = EntityId("habit-1"),
            title = "Drink water",
            createdAtEpochMillis = 1L,
            startEpochDay = selectedDay,
            scheduledWeekdays = Weekday.entries.toSet(),
        )

    override suspend fun loadHabits(): List<Habit> = listOf(habit)

    override suspend fun createHabit(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        startEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<Habit> = error("Not needed")

    override suspend fun updateHabit(
        id: EntityId,
        title: String,
        scheduledWeekdays: Set<Weekday>,
        reminderMinutesOfDay: Int?,
    ): List<Habit> = error("Not needed")

    override suspend fun archiveHabit(id: EntityId): List<Habit> = error("Not needed")

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<Habit> = error("Not needed")

    override suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<Habit> {
        habit = habit.copy(completedEpochDays = habit.completedEpochDays + epochDay)
        return listOf(habit)
    }
}

private class FakePlannerRepository : PlannerRepository {
    private var plans = emptyList<PlanItem>()

    override suspend fun loadPlans(): List<PlanItem> = plans

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<PlanItem> {
        plans =
            plans +
            PlanItem(
                id = EntityId("plan-1"),
                title = title,
                dateEpochDay = dateEpochDay,
                createdAtEpochMillis = 1L,
                reminderMinutesOfDay = reminderMinutesOfDay,
            )
        return plans
    }

    override suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<PlanItem> = error("Not needed")

    override suspend fun toggleCompletion(id: EntityId): List<PlanItem> {
        plans = plans.map { plan -> if (plan.id == id) plan.copy(completed = !plan.completed) else plan }
        return plans
    }

    override suspend fun setImage(
        id: EntityId,
        image: AttachmentRef?,
    ): List<PlanItem> {
        plans = plans.map { plan -> if (plan.id == id) plan.copy(image = image) else plan }
        return plans
    }

    override suspend fun deletePlan(id: EntityId): List<PlanItem> {
        plans = plans.filterNot { it.id == id }
        return plans
    }
}

private class FakeAttachmentRepository : AttachmentRepository {
    val deleted = mutableListOf<AttachmentRef>()

    override suspend fun importImage(uri: Uri): AttachmentRef =
        AttachmentRef(EntityId("attachment-1"), "plan.png", "image/png")

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
    var notifications = emptyList<ScheduledNotification>()

    override suspend fun schedule(notification: ScheduledNotification): Result<Unit> = Result.success(Unit)

    override suspend fun cancel(id: NotificationId): Result<Unit> = Result.success(Unit)

    override suspend fun rescheduleAll(
        scope: NotificationScope,
        notifications: List<ScheduledNotification>,
    ): Result<Unit> {
        this.notifications = notifications
        return Result.success(Unit)
    }
}
