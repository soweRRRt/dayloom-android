package com.sowerrrt.dayloom.core.storage

import com.sowerrrt.dayloom.core.model.AttachmentRef
import com.sowerrrt.dayloom.core.model.EntityId
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class FilePlannerRepositoryTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `plans can be created completed edited and restored`() =
        runTest {
            val directory = temporaryFolder.newFolder("planner")
            val id = EntityId("plan-1")
            val repository =
                FilePlannerRepository(
                    directory = directory,
                    clock = { 42L },
                    idFactory = { id },
                )

            repository.createPlan("  Call the dentist  ", TEST_EPOCH_DAY, 9 * 60 + 30)
            repository.toggleCompletion(id)
            repository.updatePlan(id, "Dentist appointment", TEST_EPOCH_DAY + 1, 14 * 60)
            val image = AttachmentRef(EntityId("image-1"), "dentist.png", "image/png")
            repository.setImage(id, image)

            val restored = FilePlannerRepository(directory).loadPlans().single()
            assertEquals("Dentist appointment", restored.title)
            assertEquals(TEST_EPOCH_DAY + 1, restored.dateEpochDay)
            assertEquals(14 * 60, restored.reminderMinutesOfDay)
            assertTrue(restored.completed)
            assertEquals(image, restored.image)
        }

    @Test
    fun `plan can be deleted`() =
        runTest {
            val id = EntityId("plan-2")
            val repository =
                FilePlannerRepository(
                    directory = temporaryFolder.newFolder("delete"),
                    idFactory = { id },
                )
            repository.createPlan("Temporary", TEST_EPOCH_DAY)

            assertTrue(repository.deletePlan(id).isEmpty())
        }

    @Test
    fun `reminder must be within the selected day`() =
        runTest {
            val repository = FilePlannerRepository(temporaryFolder.newFolder("invalid-reminder"))

            var failure: Throwable? = null
            try {
                repository.createPlan("Invalid", TEST_EPOCH_DAY, 24 * 60)
            } catch (error: Throwable) {
                failure = error
            }
            assertTrue(failure is IllegalArgumentException)
        }

    private companion object {
        const val TEST_EPOCH_DAY = 21_000L
    }
}
