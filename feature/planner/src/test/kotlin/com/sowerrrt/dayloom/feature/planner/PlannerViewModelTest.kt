package com.sowerrrt.dayloom.feature.planner

import android.net.Uri
import com.sowerrrt.dayloom.core.model.AccentPalette
import com.sowerrrt.dayloom.core.model.AppLanguage
import com.sowerrrt.dayloom.core.model.AppSettings
import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.BottomSection
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.HomeSection
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.PlanRepeat
import com.sowerrrt.dayloom.core.model.PresetType
import com.sowerrrt.dayloom.core.model.StartDestination
import com.sowerrrt.dayloom.core.model.ThemeMode
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.notifications.NotificationId
import com.sowerrrt.dayloom.core.notifications.NotificationScheduler
import com.sowerrrt.dayloom.core.notifications.NotificationScope
import com.sowerrrt.dayloom.core.notifications.ScheduledNotification
import com.sowerrrt.dayloom.core.storage.AttachmentRepository
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
import com.sowerrrt.dayloom.core.storage.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
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
    fun `calendar orders habits and plans by time with untimed entries last`() {
        val day = LocalDate.now().toEpochDay()
        val state =
            PlannerUiState(
                selectedEpochDay = day,
                habits =
                    listOf(
                        Habit(EntityId("habit-none"), "Habit without time", 1L, reminderMinutesOfDay = null),
                        Habit(EntityId("habit-late"), "Late habit", 2L, reminderMinutesOfDay = 20 * 60),
                        Habit(EntityId("habit-early"), "Early habit", 3L, reminderMinutesOfDay = 6 * 60),
                    ),
                plans =
                    listOf(
                        PlanItem(EntityId("plan-none"), "Plan without time", day, 1L),
                        PlanItem(EntityId("plan-late"), "Late plan", day, 2L, reminderMinutesOfDay = 19 * 60),
                        PlanItem(EntityId("plan-early"), "Early plan", day, 3L, reminderMinutesOfDay = 8 * 60),
                    ),
            )

        assertEquals(listOf("Early habit", "Late habit", "Habit without time"), state.selectedHabits.map(Habit::title))
        assertEquals(listOf("Early plan", "Late plan", "Plan without time"), state.selectedPlans.map(PlanItem::title))
    }

    @Test
    fun `calendar combines scheduled habits and persistent plans`() =
        runTest(dispatcher) {
            val selectedDay = LocalDate.now().toEpochDay()
            val habitsRepository = FakeHabitsRepository(selectedDay)
            val plannerRepository = FakePlannerRepository()
            val scheduler = FakeNotificationScheduler()
            val viewModel =
                PlannerViewModel(
                    habitsRepository,
                    plannerRepository,
                    scheduler,
                    FakeAttachmentRepository(),
                    FakeSettingsRepository(),
                )

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
                    FakeSettingsRepository(),
                )
            runCurrent()

            viewModel.deletePlan(planId)
            runCurrent()

            assertTrue(
                viewModel.uiState.value.plans
                    .isEmpty(),
            )
            assertEquals(1, attachments.deleted.size)
        }

    @Test
    fun `full preset creates a plan for selected date in one action`() =
        runTest(dispatcher) {
            val selectedDay = LocalDate.now().plusDays(2).toEpochDay()
            val plannerRepository = FakePlannerRepository()
            val viewModel =
                PlannerViewModel(
                    FakeHabitsRepository(LocalDate.now().toEpochDay()),
                    plannerRepository,
                    FakeNotificationScheduler(),
                    FakeAttachmentRepository(),
                    FakeSettingsRepository(),
                )
            runCurrent()
            viewModel.selectDate(selectedDay)
            viewModel.savePreset("Call family", 19 * 60)
            runCurrent()

            viewModel.createFromPreset(
                viewModel.uiState.value.presets
                    .single(),
            )
            runCurrent()

            val plan = plannerRepository.loadPlans().single()
            assertEquals(selectedDay, plan.dateEpochDay)
            assertEquals(19 * 60, plan.reminderMinutesOfDay)
        }

    @Test
    fun `plan time can be saved without scheduling a notification`() =
        runTest(dispatcher) {
            val selectedDay = LocalDate.now().plusDays(1).toEpochDay()
            val plannerRepository = FakePlannerRepository()
            val scheduler = FakeNotificationScheduler()
            val viewModel =
                PlannerViewModel(
                    FakeHabitsRepository(LocalDate.now().toEpochDay()),
                    plannerRepository,
                    scheduler,
                    FakeAttachmentRepository(),
                    FakeSettingsRepository(),
                )
            runCurrent()
            viewModel.selectDate(selectedDay)

            viewModel.createPlan("Deep work", 9 * 60, reminderEnabled = false)
            runCurrent()

            val plan = plannerRepository.loadPlans().single()
            assertEquals(9 * 60, plan.reminderMinutesOfDay)
            assertFalse(plan.reminderEnabled)
            assertTrue(scheduler.notifications.isEmpty())
        }

    @Test
    fun `advanced schedule is preserved by plan preset`() =
        runTest(dispatcher) {
            val plannerRepository = FakePlannerRepository()
            val viewModel =
                PlannerViewModel(
                    FakeHabitsRepository(LocalDate.now().toEpochDay()),
                    plannerRepository,
                    FakeNotificationScheduler(),
                    FakeAttachmentRepository(),
                    FakeSettingsRepository(),
                )
            runCurrent()

            val weekdays = setOf(Weekday.TUESDAY, Weekday.SATURDAY)
            viewModel.savePreset("Training", 7 * 60, scheduledWeekdays = weekdays)
            runCurrent()
            viewModel.createFromPreset(
                viewModel.uiState.value.presets
                    .single(),
            )
            runCurrent()

            val plan = plannerRepository.loadPlans().single()
            assertEquals(weekdays, plan.scheduledWeekdays)
            assertEquals(7 * 60, plan.reminderMinutesOfDay)
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
                    EntityId("silent"),
                    "Silent",
                    today.toEpochDay(),
                    3L,
                    reminderMinutesOfDay = 19 * 60,
                    reminderEnabled = false,
                ),
                PlanItem(
                    EntityId("done"),
                    "Done",
                    today.plusDays(1).toEpochDay(),
                    4L,
                    completed = true,
                    reminderMinutesOfDay = 10 * 60,
                ),
            )

        val reminder = plans.activeReminders(now, zone).single()

        assertEquals(NotificationId("plan:future:0"), reminder.id)
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
        targetAmount: String,
        targetUnit: String,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
        reminderOffsetsMinutes: Set<Int>,
    ): List<Habit> = error("Not needed")

    override suspend fun updateHabit(
        id: EntityId,
        title: String,
        scheduledWeekdays: Set<Weekday>,
        reminderMinutesOfDay: Int?,
        targetAmount: String,
        targetUnit: String,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
        reminderOffsetsMinutes: Set<Int>,
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
    ): List<PlanItem> =
        createPlan(
            title,
            dateEpochDay,
            reminderMinutesOfDay,
            PlanRepeat.NONE,
            null,
            reminderMinutesOfDay != null,
        )

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
    ): List<PlanItem> =
        createPlan(
            title,
            dateEpochDay,
            reminderMinutesOfDay,
            repeat,
            repeatUntilEpochDay,
            reminderEnabled,
            emptySet(),
            null,
            emptySet(),
        )

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
        repeat: PlanRepeat,
        repeatUntilEpochDay: Long?,
        reminderEnabled: Boolean,
        scheduledWeekdays: Set<Weekday>,
        repeatEveryDays: Int?,
        scheduledMonthDays: Set<Int>,
    ): List<PlanItem> {
        plans =
            plans +
            PlanItem(
                id = EntityId("plan-1"),
                title = title,
                dateEpochDay = dateEpochDay,
                createdAtEpochMillis = 1L,
                reminderMinutesOfDay = reminderMinutesOfDay,
                reminderEnabled = reminderEnabled && reminderMinutesOfDay != null,
                repeat = repeat,
                repeatUntilEpochDay = repeatUntilEpochDay,
                scheduledWeekdays = scheduledWeekdays,
                repeatEveryDays = repeatEveryDays,
                scheduledMonthDays = scheduledMonthDays,
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

private class FakeSettingsRepository : SettingsRepository {
    private val state = MutableStateFlow(AppSettings())
    override val settings: Flow<AppSettings> = state

    override suspend fun setThemeMode(value: ThemeMode) = Unit

    override suspend fun setAppLanguage(value: AppLanguage) = Unit

    override suspend fun setAccentPalette(value: AccentPalette) = Unit

    override suspend fun setStartDestination(value: StartDestination) = Unit

    override suspend fun setAutomaticUpdateChecks(enabled: Boolean) = Unit

    override suspend fun setWholeAppLock(enabled: Boolean) = Unit

    override suspend fun setBottomSections(sections: List<BottomSection>) = Unit

    override suspend fun setHomeSections(sections: List<HomeSection>) = Unit

    override suspend fun addPreset(
        type: PresetType,
        title: String,
    ) {
        state.value =
            when (type) {
                PresetType.HABIT -> state.value.copy(habitPresets = state.value.habitPresets + title)
                PresetType.PLAN -> state.value.copy(planPresets = state.value.planPresets + title)
                PresetType.LIST_ITEM -> state.value.copy(listItemPresets = state.value.listItemPresets + title)
            }
    }

    override suspend fun removePreset(
        type: PresetType,
        title: String,
    ) {
        state.value =
            when (type) {
                PresetType.HABIT -> state.value.copy(habitPresets = state.value.habitPresets - title)
                PresetType.PLAN -> state.value.copy(planPresets = state.value.planPresets - title)
                PresetType.LIST_ITEM -> state.value.copy(listItemPresets = state.value.listItemPresets - title)
            }
    }

    override suspend fun markUpdateChecked(epochMillis: Long) = Unit
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
