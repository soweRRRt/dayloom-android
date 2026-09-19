package com.sowerrrt.dayloom.feature.habits

import app.cash.turbine.test
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
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
            val viewModel = HabitsViewModel(repository)

            viewModel.uiState.test {
                assertTrue(awaitItem().isLoading)
                assertFalse(awaitItem().isLoading)

                viewModel.createHabit("Drink water")
                val created = awaitItem()
                assertEquals("Drink water", created.habits.single().title)

                viewModel.toggleCompletion(created.habits.single().id)
                assertEquals(1, awaitItem().completedToday)
            }
        }
}

private class FakeHabitsRepository : HabitsRepository {
    private var habits = emptyList<Habit>()

    override suspend fun loadHabits(): List<Habit> = habits

    override suspend fun createHabit(title: String): List<Habit> {
        habits =
            habits +
            Habit(
                id = EntityId("habit-1"),
                title = title,
                createdAtEpochMillis = 1L,
            )
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
