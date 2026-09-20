package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import com.sowerrrt.dayloom.core.model.WishPriority
import com.sowerrrt.dayloom.core.model.savedMinor
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FileWishlistRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `goal and contribution history can be edited and restored`() =
        runTest {
            val directory = temporaryFolder.newFolder("wishlist")
            val ids =
                ArrayDeque(
                    listOf(
                        EntityId("goal-1"),
                        EntityId("contribution-1"),
                        EntityId("contribution-2"),
                    ),
                )
            var now = 10L
            val repository =
                FileWishlistRepository(
                    directory = directory,
                    clock = { now++ },
                    idFactory = { ids.removeFirst() },
                )

            repository.createGoal(
                "  New laptop  ",
                150_000_00,
                "usd",
                WishPriority.HIGH,
                "For work",
                "https://shop.example/laptop",
            )
            repository.addContribution(EntityId("goal-1"), 20_000_00, "First month")
            repository.addContribution(EntityId("goal-1"), 15_500_00, "Bonus")
            repository.setImage(
                EntityId("goal-1"),
                AttachmentRef(EntityId("00000000-0000-0000-0000-000000000001"), "laptop.png", "image/png"),
            )
            repository.deleteContribution(EntityId("goal-1"), EntityId("contribution-1"))
            repository.updateGoal(
                id = EntityId("goal-1"),
                title = "Laptop and monitor",
                targetMinor = 175_000_00,
                currencyCode = "EUR",
                priority = WishPriority.MEDIUM,
                note = "Work setup",
                purchaseUrl = "https://shop.example/workstation",
            )

            val restored = FileWishlistRepository(directory).loadGoals().single()
            assertEquals("Laptop and monitor", restored.title)
            assertEquals(175_000_00, restored.targetMinor)
            assertEquals("EUR", restored.currencyCode)
            assertEquals(WishPriority.MEDIUM, restored.priority)
            assertEquals("Work setup", restored.note)
            assertEquals("https://shop.example/workstation", restored.purchaseUrl)
            assertEquals(15_500_00, restored.savedMinor)
            assertEquals("Bonus", restored.contributions.single().note)
            assertEquals("laptop.png", restored.image?.displayName)
        }

    @Test
    fun `goal can be deleted`() =
        runTest {
            val repository =
                FileWishlistRepository(
                    directory = temporaryFolder.newFolder("delete"),
                    idFactory = { EntityId("goal-2") },
                )
            repository.createGoal("Temporary", 1_000_00, "RUB", WishPriority.LOW, "")

            assertTrue(repository.deleteGoal(EntityId("goal-2")).isEmpty())
        }

    @Test
    fun `goal category archive restore and retention are persisted`() =
        runTest {
            val week = 7L * 24 * 60 * 60 * 1_000
            var now = 5_000L
            var attachmentDeleted = false
            val repository =
                FileWishlistRepository(
                    directory = temporaryFolder.newFolder("categories-archive"),
                    clock = { now },
                    idFactory = { EntityId("goal-category") },
                    deleteAttachment = {
                        attachmentDeleted = true
                        true
                    },
                )
            repository.createGoal("Camera", 100_00, "EUR", WishPriority.HIGH, "", "https://example.com")
            repository.setCategory(EntityId("goal-category"), "Tech")
            repository.setImage(
                EntityId("goal-category"),
                AttachmentRef(EntityId("00000000-0000-0000-0000-000000000002"), "camera.png", "image/png"),
            )
            repository.archiveGoal(EntityId("goal-category"))
            assertEquals("Tech", repository.loadArchivedGoals().single().category)
            repository.restoreGoal(EntityId("goal-category"))
            assertEquals("Tech", repository.loadGoals().single().category)
            repository.archiveGoal(EntityId("goal-category"))
            now += week
            assertTrue(repository.loadArchivedGoals().isEmpty())
            assertTrue(attachmentDeleted)
        }

    @Test
    fun `purchase link only accepts web addresses`() =
        runTest {
            val repository = FileWishlistRepository(temporaryFolder.newFolder("invalid-url"))

            var failure: Throwable? = null
            try {
                repository.createGoal("Camera", 100_00, "EUR", WishPriority.MEDIUM, "", "ftp://example.com")
            } catch (error: Throwable) {
                failure = error
            }

            assertTrue(failure is IllegalArgumentException)
        }
}
