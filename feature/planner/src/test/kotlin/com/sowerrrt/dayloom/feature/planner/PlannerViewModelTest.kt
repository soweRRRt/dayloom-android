package com.sowerrrt.dayloom.feature.planner

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.Weekday
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
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

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
            val viewModel = PlannerViewModel(habitsRepository, plannerRepository)

            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)
            assertEquals(
                "Drink water",
                viewModel.uiState.value.selectedHabits
                    .single()
                    .title,
            )

            viewModel.createPlan("Call the doctor")
            runCurrent()
            val created =
                viewModel.uiState.value.selectedPlans
                    .single()
            assertEquals("Call the doctor", created.title)

            viewModel.togglePlan(created.id)
            runCurrent()
            assertEquals(
                true,
                viewModel.uiState.value.selectedPlans
                    .single()
                    .completed,
            )

            viewModel.toggleHabit(habitsRepository.habit.id)
            runCurrent()
            assertEquals(
                setOf(selectedDay),
                viewModel.uiState.value.selectedHabits
                    .single()
                    .completedEpochDays,
            )
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
    ): List<Habit> = error("Not needed")

    override suspend fun updateHabit(
        id: EntityId,
        title: String,
        scheduledWeekdays: Set<Weekday>,
    ): List<Habit> = error("Not needed")

    override suspend fun archiveHabit(id: EntityId): List<Habit> = error("Not needed")

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
    ): List<PlanItem> {
        plans =
            plans +
            PlanItem(
                id = EntityId("plan-1"),
                title = title,
                dateEpochDay = dateEpochDay,
                createdAtEpochMillis = 1L,
            )
        return plans
    }

    override suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
    ): List<PlanItem> = error("Not needed")

    override suspend fun toggleCompletion(id: EntityId): List<PlanItem> {
        plans = plans.map { plan -> if (plan.id == id) plan.copy(completed = !plan.completed) else plan }
        return plans
    }

    override suspend fun deletePlan(id: EntityId): List<PlanItem> = error("Not needed")
}
