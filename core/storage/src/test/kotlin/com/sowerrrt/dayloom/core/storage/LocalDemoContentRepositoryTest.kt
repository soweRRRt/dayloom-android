package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.ListKind
import com.sowerrrt.dayloom.core.model.Weekday
import com.sowerrrt.dayloom.core.model.WishPriority
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LocalDemoContentRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `empty sections receive varied sample content only once`() =
        runTest {
            val directory = temporaryFolder.newFolder()
            val habits = FileHabitsRepository(directory)
            val planner = FilePlannerRepository(directory)
            val lists = FileListsRepository(directory)
            val wishlist = FileWishlistRepository(directory)
            val repository = LocalDemoContentRepository(habits, planner, lists, wishlist)
            val today = 20_000L

            val first = repository.seedMissing(testContent(), today)
            val second = repository.seedMissing(testContent(), today)

            assertEquals(2, first.habitsAdded)
            assertEquals(2, first.plansAdded)
            assertEquals(1, first.listsAdded)
            assertEquals(1, first.goalsAdded)
            assertEquals(0, second.totalAdded)
            assertEquals(2, habits.loadHabits().size)
            assertTrue(
                habits
                    .loadHabits()
                    .first()
                    .completedEpochDays
                    .contains(today),
            )
            assertTrue(planner.loadPlans().first { it.title == "Done plan" }.completed)
            assertTrue(
                lists
                    .loadLists()
                    .single()
                    .items
                    .first { it.title == "Done item" }
                    .completed,
            )
            assertEquals(
                2_500L,
                wishlist
                    .loadGoals()
                    .single()
                    .contributions
                    .sumOf { it.amountMinor },
            )
        }

    @Test
    fun `existing user content is preserved while missing examples are added`() =
        runTest {
            val directory = temporaryFolder.newFolder()
            val habits = FileHabitsRepository(directory)
            val planner = FilePlannerRepository(directory)
            val lists = FileListsRepository(directory)
            val wishlist = FileWishlistRepository(directory)
            val repository = LocalDemoContentRepository(habits, planner, lists, wishlist)
            habits.createHabit("User habit", Weekday.entries.toSet(), 20_000)

            val result = repository.seedMissing(testContent(), 20_000)

            assertEquals(2, result.habitsAdded)
            assertEquals(setOf("User habit", "Daily", "Weekdays"), habits.loadHabits().map { it.title }.toSet())
        }

    private fun testContent() =
        DemoContent(
            habits =
                listOf(
                    DemoHabit("Daily", Weekday.entries.toSet(), listOf(-1, 0)),
                    DemoHabit("Weekdays", Weekday.entries.toSet()),
                ),
            plans = listOf(DemoPlan("Done plan", 0, completed = true), DemoPlan("Tomorrow", 1)),
            lists =
                listOf(
                    DemoList(
                        "Groceries",
                        ListKind.SHOPPING,
                        listOf(DemoListItem("Done item", "2", completed = true), DemoListItem("Open item")),
                    ),
                ),
            goals =
                listOf(
                    DemoGoal(
                        title = "Laptop",
                        targetMinor = 100_000,
                        currencyCode = "USD",
                        priority = WishPriority.HIGH,
                        contributionsMinor = listOf(1_000, 1_500),
                    ),
                ),
        )
}
