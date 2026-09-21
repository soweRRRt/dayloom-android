package com.sowerrrt.dayloom.core.storage

import android.net.Uri
import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
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
            assertEquals(8 * 60, habits.loadHabits().first { it.title == "Daily" }.reminderMinutesOfDay)
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

    @Test
    fun `missing demo image is attached to existing example only once`() =
        runTest {
            val directory = temporaryFolder.newFolder()
            val wishlist = FileWishlistRepository(directory)
            val attachments = FakeAttachmentRepository()
            wishlist.createGoal("Laptop", 100_000, "USD", WishPriority.HIGH, "")
            val repository =
                LocalDemoContentRepository(
                    FileHabitsRepository(directory),
                    FilePlannerRepository(directory),
                    FileListsRepository(directory),
                    wishlist,
                    attachments,
                    DemoImageSource { DemoImageAsset("laptop.png", "image/png", byteArrayOf(1, 2, 3)) },
                )
            val content =
                testContent().copy(
                    habits = emptyList(),
                    plans = emptyList(),
                    lists = emptyList(),
                    goals = listOf(testContent().goals.single().copy(image = DemoImage.LAPTOP)),
                )

            val first = repository.seedMissing(content, 20_000)
            val second = repository.seedMissing(content, 20_000)

            assertEquals(0, first.goalsAdded)
            assertEquals(1, first.imagesAdded)
            assertEquals(0, second.totalAdded)
            assertEquals(1, attachments.imports)
            assertEquals(
                "laptop.png",
                wishlist
                    .loadGoals()
                    .single()
                    .image
                    ?.displayName,
            )
        }

    @Test
    fun `habit demo images are imported only once while plans stay image free`() =
        runTest {
            val directory = temporaryFolder.newFolder()
            val habits = FileHabitsRepository(directory)
            val planner = FilePlannerRepository(directory)
            val attachments = FakeAttachmentRepository()
            val repository =
                LocalDemoContentRepository(
                    habits,
                    planner,
                    FileListsRepository(directory),
                    FileWishlistRepository(directory),
                    attachments,
                    DemoImageSource { image ->
                        DemoImageAsset("${image.name.lowercase()}.png", "image/png", byteArrayOf(1, 2, 3))
                    },
                )
            val content =
                DemoContent(
                    habits = listOf(DemoHabit("Stretch", Weekday.entries.toSet(), image = DemoImage.TRAVEL)),
                    plans = listOf(DemoPlan("Prepare", 1)),
                    lists = emptyList(),
                    goals = emptyList(),
                )

            val first = repository.seedMissing(content, 20_000)
            val second = repository.seedMissing(content, 20_000)

            assertEquals(1, first.habitsAdded)
            assertEquals(1, first.plansAdded)
            assertEquals(1, first.imagesAdded)
            assertEquals(0, second.totalAdded)
            assertEquals(1, attachments.imports)
            assertEquals(
                "travel.png",
                habits
                    .loadHabits()
                    .single()
                    .image
                    ?.displayName,
            )
            assertEquals(null, planner.loadPlans().single().image)
        }

    private fun testContent() =
        DemoContent(
            habits =
                listOf(
                    DemoHabit("Daily", Weekday.entries.toSet(), listOf(-1, 0), reminderMinutesOfDay = 8 * 60),
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

private class FakeAttachmentRepository : AttachmentRepository {
    var imports = 0

    override suspend fun importImage(uri: Uri): AttachmentRef = error("Not needed")

    override suspend fun importImage(
        displayName: String,
        mimeType: String,
        bytes: ByteArray,
    ): AttachmentRef {
        imports++
        return AttachmentRef(
            EntityId("00000000-0000-0000-0000-${imports.toString().padStart(12, '0')}"),
            displayName,
            mimeType,
        )
    }

    override suspend fun delete(attachment: AttachmentRef): Boolean = true

    override fun localPath(attachment: AttachmentRef): String? = null
}
