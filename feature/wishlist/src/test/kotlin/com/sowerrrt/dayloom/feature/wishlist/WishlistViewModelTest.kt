package com.sowerrrt.dayloom.feature.wishlist

import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.WishContribution
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.storage.WishlistRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class WishlistViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `new goal is selected and contributions update progress`() =
        runTest(dispatcher) {
            val viewModel = WishlistViewModel(FakeWishlistRepository())
            runCurrent()
            assertFalse(viewModel.uiState.value.isLoading)

            viewModel.createGoal("Camera", 100_000, "EUR", WishPriority.HIGH, "Travel")
            runCurrent()
            assertEquals(
                "Camera",
                viewModel.uiState.value.selectedGoal
                    ?.title,
            )

            viewModel.addContribution(EntityId("goal-1"), 25_000, "First step")
            runCurrent()
            assertEquals(
                25_000L,
                viewModel.uiState.value.selectedGoal
                    ?.contributions
                    ?.single()
                    ?.amountMinor,
            )

            viewModel.deleteGoal(EntityId("goal-1"))
            runCurrent()
            assertNull(viewModel.uiState.value.selectedGoal)
        }

    @Test
    fun `amount parser accepts comma and rejects extra precision`() {
        assertEquals(12_345L, parseAmountToMinor("123,45"))
        assertEquals(100L, parseAmountToMinor("1"))
        assertNull(parseAmountToMinor("1.234"))
        assertNull(parseAmountToMinor("0"))
    }
}

private class FakeWishlistRepository : WishlistRepository {
    private var goals = emptyList<WishGoal>()

    override suspend fun loadGoals(): List<WishGoal> = goals

    override suspend fun createGoal(
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
    ): List<WishGoal> {
        goals =
            listOf(
                WishGoal(
                    id = EntityId("goal-1"),
                    title = title,
                    targetMinor = targetMinor,
                    currencyCode = currencyCode,
                    priority = priority,
                    note = note,
                    createdAtEpochMillis = 1L,
                ),
            )
        return goals
    }

    override suspend fun updateGoal(
        id: EntityId,
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
    ): List<WishGoal> = error("Not needed")

    override suspend fun deleteGoal(id: EntityId): List<WishGoal> {
        goals = goals.filterNot { it.id == id }
        return goals
    }

    override suspend fun addContribution(
        goalId: EntityId,
        amountMinor: Long,
        note: String,
    ): List<WishGoal> {
        goals =
            goals.map { goal ->
                if (goal.id == goalId) {
                    goal.copy(
                        contributions =
                            goal.contributions +
                                WishContribution(EntityId("contribution-1"), amountMinor, note, 2L),
                    )
                } else {
                    goal
                }
            }
        return goals
    }

    override suspend fun deleteContribution(
        goalId: EntityId,
        contributionId: EntityId,
    ): List<WishGoal> = error("Not needed")
}
