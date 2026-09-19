package com.sowerrrt.dayloom.feature.home

import com.sowerrrt.dayloom.core.model.DayList
import com.sowerrrt.dayloom.core.model.DayListItem
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.Habit
import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.PlanItem
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.WishContribution
import com.sowerrrt.dayloom.core.model.WishGoal
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.storage.HabitsRepository
import com.sowerrrt.dayloom.core.storage.ListsRepository
import com.sowerrrt.dayloom.core.storage.PlannerRepository
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
import org.junit.Before
import org.junit.Test
import java.time.LocalDate

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModelTest {
    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    @Test
    fun `overview counts today's work and open list items`() =
        runTest(dispatcher) {
            val today = LocalDate.now().toEpochDay()
            val habits =
                listOf(
                    Habit(
                        id = EntityId("habit-1"),
                        title = "Water",
                        createdAtEpochMillis = 1L,
                        startEpochDay = today,
                        scheduledWeekdays = Weekday.entries.toSet(),
                        completedEpochDays = setOf(today),
                    ),
                )
            val plans =
                listOf(
                    PlanItem(EntityId("plan-1"), "Today", today, 1L, completed = true),
                    PlanItem(EntityId("plan-2"), "Tomorrow", today + 1, 2L),
                )
            val lists =
                listOf(
                    DayList(
                        id = EntityId("list-1"),
                        title = "Groceries",
                        kind = ListKind.SHOPPING,
                        items =
                            listOf(
                                DayListItem(
                                    EntityId("item-1"),
                                    "Milk",
                                    completed = false,
                                    order = 0,
                                    createdAtEpochMillis = 1L,
                                ),
                                DayListItem(
                                    EntityId("item-2"),
                                    "Bread",
                                    completed = true,
                                    order = 1,
                                    createdAtEpochMillis = 2L,
                                ),
                            ),
                        createdAtEpochMillis = 1L,
                    ),
                )
            val viewModel =
                HomeViewModel(
                    habitsRepository = ReadOnlyHabitsRepository(habits),
                    plannerRepository = ReadOnlyPlannerRepository(plans),
                    listsRepository = ReadOnlyListsRepository(lists),
                    wishlistRepository =
                        ReadOnlyWishlistRepository(
                            listOf(
                                WishGoal(
                                    id = EntityId("wish-1"),
                                    title = "Camera",
                                    targetMinor = 10_000,
                                    currencyCode = "EUR",
                                    priority = WishPriority.HIGH,
                                    contributions =
                                        listOf(
                                            WishContribution(EntityId("saving-1"), 10_000, createdAtEpochMillis = 2L),
                                        ),
                                    createdAtEpochMillis = 1L,
                                ),
                            ),
                        ),
                )

            runCurrent()

            assertEquals(1, viewModel.uiState.value.habitsToday)
            assertEquals(1, viewModel.uiState.value.habitsCompletedToday)
            assertEquals(1, viewModel.uiState.value.plansToday)
            assertEquals(1, viewModel.uiState.value.plansCompletedToday)
            assertEquals(1, viewModel.uiState.value.listCount)
            assertEquals(1, viewModel.uiState.value.openListItems)
            assertEquals(1, viewModel.uiState.value.wishCount)
            assertEquals(1, viewModel.uiState.value.completedWishCount)
        }
}

private class ReadOnlyHabitsRepository(
    private val habits: List<Habit>,
) : HabitsRepository {
    override suspend fun loadHabits(): List<Habit> = habits

    override suspend fun createHabit(
        title: String,
        scheduledWeekdays: Set<Weekday>,
        startEpochDay: Long,
        reminderMinutesOfDay: Int?,
        targetAmount: String,
        targetUnit: String,
    ): List<Habit> = error("Read-only fake")

    override suspend fun updateHabit(
        id: EntityId,
        title: String,
        scheduledWeekdays: Set<Weekday>,
        reminderMinutesOfDay: Int?,
        targetAmount: String,
        targetUnit: String,
    ): List<Habit> = error("Read-only fake")

    override suspend fun archiveHabit(id: EntityId): List<Habit> = error("Read-only fake")

    override suspend fun setImage(
        id: EntityId,
        image: com.sowerrrt.dayloom.core.model.AttachmentRef?,
    ): List<Habit> = error("Read-only fake")

    override suspend fun toggleCompletion(
        id: EntityId,
        epochDay: Long,
    ): List<Habit> = error("Read-only fake")
}

private class ReadOnlyPlannerRepository(
    private val plans: List<PlanItem>,
) : PlannerRepository {
    override suspend fun loadPlans(): List<PlanItem> = plans

    override suspend fun createPlan(
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<PlanItem> = error("Read-only fake")

    override suspend fun updatePlan(
        id: EntityId,
        title: String,
        dateEpochDay: Long,
        reminderMinutesOfDay: Int?,
    ): List<PlanItem> = error("Read-only fake")

    override suspend fun toggleCompletion(id: EntityId): List<PlanItem> = error("Read-only fake")

    override suspend fun setImage(
        id: EntityId,
        image: com.sowerrrt.dayloom.core.model.AttachmentRef?,
    ): List<PlanItem> = error("Read-only fake")

    override suspend fun deletePlan(id: EntityId): List<PlanItem> = error("Read-only fake")
}

private class ReadOnlyListsRepository(
    private val lists: List<DayList>,
) : ListsRepository {
    override suspend fun loadLists(): List<DayList> = lists

    override suspend fun createList(
        title: String,
        kind: ListKind,
        customKind: String,
    ): List<DayList> = error("Read-only fake")

    override suspend fun updateList(
        id: EntityId,
        title: String,
        kind: ListKind,
        customKind: String,
    ): List<DayList> = error("Read-only fake")

    override suspend fun deleteList(id: EntityId): List<DayList> = error("Read-only fake")

    override suspend fun addItem(
        listId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList> = error("Read-only fake")

    override suspend fun updateItem(
        listId: EntityId,
        itemId: EntityId,
        title: String,
        quantity: String,
        note: String,
    ): List<DayList> = error("Read-only fake")

    override suspend fun toggleItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList> = error("Read-only fake")

    override suspend fun deleteItem(
        listId: EntityId,
        itemId: EntityId,
    ): List<DayList> = error("Read-only fake")

    override suspend fun moveItem(
        listId: EntityId,
        itemId: EntityId,
        offset: Int,
    ): List<DayList> = error("Read-only fake")
}

private class ReadOnlyWishlistRepository(
    private val goals: List<WishGoal>,
) : WishlistRepository {
    override suspend fun loadGoals(): List<WishGoal> = goals

    override suspend fun createGoal(
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String,
    ): List<WishGoal> = error("Read-only fake")

    override suspend fun updateGoal(
        id: EntityId,
        title: String,
        targetMinor: Long,
        currencyCode: String,
        priority: WishPriority,
        note: String,
        purchaseUrl: String,
    ): List<WishGoal> = error("Read-only fake")

    override suspend fun deleteGoal(id: EntityId): List<WishGoal> = error("Read-only fake")

    override suspend fun setImage(
        id: EntityId,
        image: com.sowerrrt.dayloom.core.model.AttachmentRef?,
    ): List<WishGoal> = error("Read-only fake")

    override suspend fun addContribution(
        goalId: EntityId,
        amountMinor: Long,
        note: String,
    ): List<WishGoal> = error("Read-only fake")

    override suspend fun deleteContribution(
        goalId: EntityId,
        contributionId: EntityId,
    ): List<WishGoal> = error("Read-only fake")
}
